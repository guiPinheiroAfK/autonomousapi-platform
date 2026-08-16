"""
Componentes de avaliação automática de motorista (spec 06, item 3) — calculados a
partir do ping de GPS bruto de uma viagem. "Desvio de rota" fica de fora desta rodada:
precisa de rota planejada como referência, que depende de roteamento (spec 05, último
pedaço da Fase 2, ainda não construído).

v1 deliberadamente simples: limiares fixos, sem diferenciar por tipo de via (não temos
limite de velocidade por `road_segment`). Documentado como heurística a calibrar com
dado real do piloto — mesmo padrão do resto da Fase 2 (`road_readiness_score`).
"""

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import VehicleGpsPing

# Nenhum destes três números é regulatório — são o ponto de partida documentado, a
# recalibrar quando houver volume real de viagens para validar contra o que um gestor
# consideraria "condução arriscada" de verdade.
LIMITE_VELOCIDADE_KMH = 100.0
JANELA_FRENAGEM_SEGUNDOS = 5.0
LIMIAR_FRENAGEM_KMH = 20.0


@dataclass
class DrivingEvents:
    ping_count: int
    hard_braking_count: int
    overspeed_count: int


def calcular_driving_events(
    db: Session, vehicle_id: UUID, de: datetime, ate: datetime
) -> DrivingEvents:
    """
    Só considera pings com `speed` preenchido — nem todo ping do app tem essa leitura
    (spec 02: campo opcional). `ping_count` reflete os pings realmente usados, não o
    total da viagem, pra quem consome saber se a amostra é pequena demais pra confiar.
    """
    pings = (
        db.execute(
            select(VehicleGpsPing)
            .where(
                VehicleGpsPing.vehicle_id == vehicle_id,
                VehicleGpsPing.recorded_at >= de,
                VehicleGpsPing.recorded_at <= ate,
                VehicleGpsPing.speed.is_not(None),
            )
            .order_by(VehicleGpsPing.recorded_at)
        )
        .scalars()
        .all()
    )

    hard_braking = 0
    overspeed = 0
    anterior: VehicleGpsPing | None = None
    for ping in pings:
        if ping.speed > LIMITE_VELOCIDADE_KMH:
            overspeed += 1
        if anterior is not None:
            delta_segundos = (ping.recorded_at - anterior.recorded_at).total_seconds()
            queda_kmh = anterior.speed - ping.speed
            # Janela curta é o que separa frenagem brusca de desaceleração normal — a
            # mesma queda de velocidade ao longo de um minuto não é o mesmo evento.
            if 0 < delta_segundos <= JANELA_FRENAGEM_SEGUNDOS and queda_kmh >= LIMIAR_FRENAGEM_KMH:
                hard_braking += 1
        anterior = ping

    return DrivingEvents(
        ping_count=len(pings), hard_braking_count=hard_braking, overspeed_count=overspeed
    )
