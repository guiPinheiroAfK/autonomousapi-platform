"""
Sessionização de pings em passagens (spec 02 / ADR 0019, Decisão 1).

`road_segment_observation` é gravada por PING desde a origem (achado da auditoria de
cleanup que motivou a ADR 0019) — mas o spec 02 sempre descreveu a entidade como "uma
PASSAGEM observada... velocidade média". Este módulo constrói essa entidade de verdade
(`road_segment_passage`), a partir dos pings já casados a segmento na ingestão
(`vehicle_gps_ping.road_segment_id`, ver app/routers/internal.py).

## D1.1 — o que é uma passagem

Pra um veículo, pings ordenados por `recorded_at`: passagem = corrida contígua maximal
sobre o MESMO segmento. Quebra quando o segmento muda OU quando o intervalo entre pings
consecutivos passa de `gap_max_minutes` (separa "atravessou a via" de "voltou horas
depois à mesma via"). Ping sem segmento (fora da malha importada) não entra em nenhuma
passagem.

Deliberadamente SEM filtro de amostra mínima (ex. "só conta se ping_count >= 2"): isso
reintroduziria o viés que esta ADR existe pra corrigir — atravessar rápido gera 1 ping,
ficar parado gera dezenas; filtrar por amostra mínima descartaria preferencialmente as
passagens rápidas. `ping_count` fica gravado e a CONFIANÇA (D3.3), não a elegibilidade
da passagem, é o lugar certo pra tratar amostra pequena.

## D1.2 — apagar-e-reconstruir, não incremental

`road_segment_passage` não tem `vehicle_id` (mesma anonimização estrutural desde a
origem já aplicada em `road_segment_observation`, ADR 0009) — então não dá pra
perguntar "até onde já processei o veículo X" pra fazer sessionização incremental de
verdade. Marcar o ping como "processado" também não serve: seria mutação em dado bruto
que morre na purga de 30 dias (ADR 0009). E uma marca d'água global por `recorded_at`
quebra com a fila offline do app — um ping gravado ontem pode chegar hoje, atrás da
marca d'água, e nunca virar passagem.

Solução: a cada rodada, apaga toda passagem com `entered_at >= limite` (janela de
`rebuild_window_hours`) e reconstrói a partir dos pings com `recorded_at` dentro dessa
janela MAIS uma margem de leitura — a margem existe só pra RECONHECER (e descartar) uma
passagem que já começou antes do limite; sem ela essa passagem viraria uma passagem
parcial nova, duplicada. Idempotente por construção, sem estado por veículo.

Limitação documentada: ping que chega mais de `rebuild_window_hours` depois de gravado
nunca vira passagem (mensurável via `created_at - recorded_at`; vira métrica de
qualidade da Fase 3, não falha silenciosa). Igualmente, uma passagem que atravessa a
fronteira da janela (veículo parado gerando ping contínuo no mesmo segmento por mais
tempo que a janela) fica congelada no estado observado na primeira rodada em que cruzou
`entered_at < limite` — limitação aceita para v1, mesma natureza dos outros números
"a calibrar" desta ADR.
"""

import logging
from datetime import UTC, datetime, timedelta
from uuid import uuid4

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from .models import RoadSegmentPassage, VehicleGpsPing

logger = logging.getLogger(__name__)

# Folga de leitura antes do início da janela reconstruída — maior que qualquer passagem
# plausível dado o corte de `gap_max_minutes`, o suficiente pra reconhecer (e descartar)
# uma passagem já em andamento antes do limite, sem duplicá-la (D1.2).
MARGEM_LEITURA = timedelta(hours=1)


def reconstruir_passagens(db: Session, gap_max_minutes: int, rebuild_window_hours: int) -> int:
    """
    Reconstrói `road_segment_passage` para a janela de `rebuild_window_hours` a partir
    de agora. Idempotente: rodar de novo sem ping novo na janela produz o mesmo
    resultado. Retorna quantas passagens foram gravadas nesta rodada.
    """
    agora = datetime.now(UTC)
    limite = agora - timedelta(hours=rebuild_window_hours)
    desde_leitura = limite - MARGEM_LEITURA
    gap_max = timedelta(minutes=gap_max_minutes)

    db.execute(delete(RoadSegmentPassage).where(RoadSegmentPassage.entered_at >= limite))

    pings = db.execute(
        select(
            VehicleGpsPing.vehicle_id,
            VehicleGpsPing.road_segment_id,
            VehicleGpsPing.recorded_at,
            VehicleGpsPing.speed,
        )
        .where(
            VehicleGpsPing.recorded_at >= desde_leitura,
            VehicleGpsPing.road_segment_id.is_not(None),
        )
        .order_by(VehicleGpsPing.vehicle_id, VehicleGpsPing.recorded_at)
    ).all()

    novas = [p for p in _agrupar_em_passagens(pings, gap_max) if p["entered_at"] >= limite]

    for p in novas:
        db.add(RoadSegmentPassage(**p))
    db.commit()

    logger.info("sessionização: %d passagem(ns) reconstruída(s)", len(novas))
    return len(novas)


def _agrupar_em_passagens(pings, gap_max: timedelta):
    """
    `pings` já vem ordenado por (vehicle_id, recorded_at) — um único passe, O(n).
    Corrida = pings contíguos do mesmo veículo sobre o mesmo segmento, sem intervalo
    maior que `gap_max` entre dois consecutivos (D1.1).
    """
    corrida: dict | None = None

    for vehicle_id, segmento_id, recorded_at, speed in pings:
        continua = (
            corrida is not None
            and vehicle_id == corrida["vehicle_id"]
            and segmento_id == corrida["road_segment_id"]
            and recorded_at - corrida["exited_at"] <= gap_max
        )
        if continua:
            corrida["exited_at"] = recorded_at
            corrida["velocidades"].append(speed)
            corrida["ping_count"] += 1
        else:
            if corrida is not None:
                yield _fechar(corrida)
            corrida = {
                "vehicle_id": vehicle_id,
                "road_segment_id": segmento_id,
                "entered_at": recorded_at,
                "exited_at": recorded_at,
                "velocidades": [speed],
                "ping_count": 1,
            }

    if corrida is not None:
        yield _fechar(corrida)


def _fechar(corrida: dict) -> dict:
    velocidades = [v for v in corrida["velocidades"] if v is not None]
    return {
        "id": uuid4(),
        "road_segment_id": corrida["road_segment_id"],
        "entered_at": corrida["entered_at"],
        "exited_at": corrida["exited_at"],
        "avg_speed_kmh": sum(velocidades) / len(velocidades) if velocidades else None,
        "min_speed_kmh": min(velocidades) if velocidades else None,
        "ping_count": corrida["ping_count"],
    }
