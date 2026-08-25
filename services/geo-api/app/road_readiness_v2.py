"""
Score de prontidão viária v2 — fluxo + confiabilidade, por (segmento, faixa de tempo),
com confiança separada do score (ADR 0019, Decisões 2 e 3).

Substitui o v1 (app/aggregation.py, `algorithm_version='v1-obs-count'`) no scheduler —
o v1 conta observação por PING, o que inverte o sinal em vias cronicamente
congestionadas: parado no trânsito gera dezenas de pings no mesmo segmento, atravessar
rápido gera 1 (achado da auditoria de cleanup que motivou a ADR 0019). As linhas do v1
recebem `time_bucket='GLOBAL'` (migration 0006) e não são apagadas — ficam como linha
de base histórica, sem colidir com nenhuma célula do v2 na unique key composta.

Fonte de dado: `road_segment_passage` (app/sessionization.py), não mais
`road_segment_observation` — só passagem tem `avg_speed_kmh` de verdade (média sobre a
passagem inteira), instantâneo de um ping isolado não serve pra "velocidade típica".
"""

import logging
import statistics
from datetime import UTC, datetime
from uuid import UUID, uuid4

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from .models import RoadReadinessScore, RoadSegment, RoadSegmentPassage
from .time_buckets import time_bucket

logger = logging.getLogger(__name__)

ALGORITHM_VERSION = "v2-flow-reliability"

# Peso de "razão de fluxo" vs. "confiabilidade" na composição do score (D3.2) — ponto
# de partida documentado, a calibrar com dado real do piloto.
PESO_FLUXO = 0.7
PESO_CONFIABILIDADE = 0.3

# Referência de fluxo livre por highway_type quando o segmento não tem `maxspeed` do
# OSM (D3.1, item 2) — cobertura esparsa no Brasil. Sufixo `_link` herda o valor da via
# principal. Ponto de partida documentado, a calibrar com dado real do piloto.
_PADRAO_KMH_POR_HIGHWAY = {
    "motorway": 90.0,
    "motorway_link": 90.0,
    "trunk": 80.0,
    "trunk_link": 80.0,
    "primary": 60.0,
    "primary_link": 60.0,
    "secondary": 50.0,
    "secondary_link": 50.0,
    "tertiary": 40.0,
    "tertiary_link": 40.0,
    "unclassified": 30.0,
    "residential": 30.0,
    "living_street": 20.0,
    "service": 20.0,
}
_REFERENCIA_PADRAO_KMH = 30.0  # fallback final se highway_type não estiver na tabela

# Confiança (D3.3): teto de passagens a partir do qual a amostra deixa de ser o fator
# limitante, e janela de decaimento por recência da passagem mais nova da célula.
_AMOSTRA_TETO = 30
_RECENCIA_DIAS_MAX_CONFIANCA = 7
_RECENCIA_DIAS_ZERO_CONFIANCA = 90


def recalcular_road_readiness_v2(db: Session, pilot_timezone: str) -> int:
    """
    Recalcula TODA célula (segmento, faixa de tempo) com pelo menos uma passagem com
    velocidade — mesmo espírito não-incremental do v1 (idempotente, sem filtro de
    recência; ver docstring de `recalcular_road_readiness` em app/aggregation.py sobre
    por que "incremental" nem faz sentido aqui). Retorna quantas células foram
    gravadas nesta rodada.
    """
    segmentos = {s.id: s for s in db.execute(select(RoadSegment)).scalars().all()}
    if not segmentos:
        return 0

    passagens = db.execute(
        select(
            RoadSegmentPassage.road_segment_id,
            RoadSegmentPassage.entered_at,
            RoadSegmentPassage.avg_speed_kmh,
        ).where(RoadSegmentPassage.avg_speed_kmh.is_not(None))
    ).all()
    if not passagens:
        return 0

    # p85 por segmento (D3.1, item 3) — teto observado sobre TODAS as passagens do
    # segmento (não por célula); só corrige a referência de fluxo livre pra CIMA.
    velocidades_por_segmento: dict[UUID, list[float]] = {}
    for segmento_id, _entered_at, velocidade in passagens:
        velocidades_por_segmento.setdefault(segmento_id, []).append(velocidade)
    p85_por_segmento = {
        segmento_id: _percentil(velocidades, 0.85)
        for segmento_id, velocidades in velocidades_por_segmento.items()
    }

    celulas: dict[tuple[UUID, str], list[tuple[datetime, float]]] = {}
    for segmento_id, entered_at, velocidade in passagens:
        bucket = time_bucket(entered_at, pilot_timezone)
        celulas.setdefault((segmento_id, bucket), []).append((entered_at, velocidade))

    agora = datetime.now(UTC)
    valores = []
    for (segmento_id, bucket), amostras in celulas.items():
        segmento = segmentos.get(segmento_id)
        if segmento is None:
            continue  # segmento removido entre a leitura das passagens e agora

        velocidades = [v for _entrada, v in amostras]
        entradas = [entrada for entrada, _v in amostras]

        referencia = _velocidade_referencia(
            segmento.maxspeed_kmh, segmento.highway_type, p85_por_segmento.get(segmento_id)
        )
        # Mediana, não média: robusta a outlier de GPS (D3.2).
        velocidade_tipica = statistics.median(velocidades)
        razao_fluxo = max(0.0, min(velocidade_tipica / referencia, 1.0))
        confiabilidade = _confiabilidade(velocidades)
        score = PESO_FLUXO * razao_fluxo + PESO_CONFIABILIDADE * confiabilidade

        confianca = _confianca(len(amostras), max(entradas), agora)

        valores.append(
            {
                "id": uuid4(),
                "road_segment_id": segmento_id,
                "time_bucket": bucket,
                "score": score,
                "observation_count": len(amostras),
                "confidence": confianca,
                "algorithm_version": ALGORITHM_VERSION,
            }
        )

    if not valores:
        return 0

    stmt = pg_insert(RoadReadinessScore.__table__).values(valores)
    stmt = stmt.on_conflict_do_update(
        index_elements=[RoadReadinessScore.road_segment_id, RoadReadinessScore.time_bucket],
        set_={
            "score": stmt.excluded.score,
            "observation_count": stmt.excluded.observation_count,
            "confidence": stmt.excluded.confidence,
            "algorithm_version": stmt.excluded.algorithm_version,
            "updated_at": func.now(),
        },
    )
    db.execute(stmt)
    db.commit()

    logger.info("road_readiness v2 recalculado: %d célula(s) atualizada(s)", len(valores))
    return len(valores)


def _velocidade_referencia(
    maxspeed_kmh: float | None, highway_type: str, p85_observado: float | None
) -> float:
    base = (
        maxspeed_kmh
        if maxspeed_kmh is not None
        else _PADRAO_KMH_POR_HIGHWAY.get(highway_type, _REFERENCIA_PADRAO_KMH)
    )
    # Só corrige pra cima — corrigir pra baixo reintroduziria o viés que esta ADR
    # existe pra eliminar (via cronicamente congestionada nunca observa fluxo livre,
    # então um "p85 baixo" não é referência, é sintoma).
    if p85_observado is not None and p85_observado > base:
        return p85_observado
    return base


def _confiabilidade(velocidades: list[float]) -> float:
    """1 - coeficiente de variação, limitado a [0,1] (D3.2). Célula com uma só
    passagem tem desvio 0 por definição — confiabilidade 1.0 é o resultado correto
    aqui (nada de inconsistente foi observado); é papel da CONFIANÇA (D3.3), não
    deste número, sinalizar que a amostra é pequena."""
    media = statistics.mean(velocidades)
    desvio = statistics.pstdev(velocidades)
    if media == 0:
        return 1.0 if desvio == 0 else 0.0
    coeficiente_variacao = desvio / media
    return 1.0 - max(0.0, min(coeficiente_variacao, 1.0))


def _confianca(quantidade_passagens: int, mais_recente: datetime, agora: datetime) -> float:
    """amostra × recência (D3.3) — produto, não média: amostra grande e velha, ou
    amostra nova e minúscula, devem ambas resultar em confiança baixa. Média deixaria
    uma compensar a outra."""
    amostra = min(quantidade_passagens / _AMOSTRA_TETO, 1.0)
    dias = (agora - mais_recente).total_seconds() / 86400
    if dias <= _RECENCIA_DIAS_MAX_CONFIANCA:
        recencia = 1.0
    elif dias >= _RECENCIA_DIAS_ZERO_CONFIANCA:
        recencia = 0.0
    else:
        janela = _RECENCIA_DIAS_ZERO_CONFIANCA - _RECENCIA_DIAS_MAX_CONFIANCA
        recencia = 1.0 - (dias - _RECENCIA_DIAS_MAX_CONFIANCA) / janela
    return amostra * recencia


def _percentil(valores: list[float], p: float) -> float:
    """Percentil por interpolação linear — sem depender de numpy só por isso."""
    ordenados = sorted(valores)
    if len(ordenados) == 1:
        return ordenados[0]
    k = (len(ordenados) - 1) * p
    piso = int(k)
    teto = min(piso + 1, len(ordenados) - 1)
    if piso == teto:
        return ordenados[piso]
    return ordenados[piso] + (ordenados[teto] - ordenados[piso]) * (k - piso)
