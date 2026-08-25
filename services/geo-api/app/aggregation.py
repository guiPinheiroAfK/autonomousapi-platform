"""
Job de agregação de road_readiness_score (spec 02, Fase 2).

Regra do spec: "nunca calcular road_readiness_score em tempo real na requisição" — só
este job escreve na tabela; a leitura (quando existir API pública de prontidão viária,
Fase 4) é sempre do valor já agregado.

v1 do algoritmo: score = contagem de observações, normalizada por um teto simples. O
próprio spec 02 sugere começar assim ("mesmo que score seja simples, ex. contagem de
observações") — a fórmula real (cruzando velocidade, eventos, recência) é trabalho de
Fase 3, com dado real do piloto para calibrar, não escolha arbitrária feita agora.
"""

import logging
from uuid import uuid4

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from .models import RoadReadinessScore, RoadSegmentObservation

logger = logging.getLogger(__name__)

ALGORITHM_VERSION = "v1-obs-count"

# Teto de observações a partir do qual o score satura em 1.0 — arbitrário e documentado
# como tal; recalibrar quando houver volume real de piloto para decidir um número melhor.
TETO_OBSERVACOES_PARA_SCORE_MAXIMO = 50


def recalcular_road_readiness(db: Session) -> int:
    """
    Recalcula o score de TODO `road_segment` com pelo menos 1 observação, a cada
    chamada — não é incremental (a versão anterior deste docstring dizia "só
    observação nova desde a última passagem", mas o código nunca filtrou por
    recência; achado da auditoria de cleanup, ADR 0019). Idempotente: rodar de
    novo sem nenhuma observação nova produz o mesmo resultado. Retorna quantos
    segmentos foram atualizados.

    Um único `INSERT ... ON CONFLICT DO UPDATE` (upsert em massa) em vez de um
    `SELECT` + escrita por segmento em laço Python — o volume de segmentos com
    observação cresce com a malha do piloto, não faz sentido pagar uma
    ida-e-volta ao banco por segmento a cada 5 minutos (intervalo do scheduler).
    """
    contagens = db.execute(
        select(
            RoadSegmentObservation.road_segment_id,
            func.count(RoadSegmentObservation.id).label("total"),
        ).group_by(RoadSegmentObservation.road_segment_id)
    ).all()

    if not contagens:
        return 0

    valores = [
        {
            "id": uuid4(),
            "road_segment_id": segmento_id,
            # Linha do v1 — sem faixa de tempo. ADR 0019 (D4): mantida como linha de
            # base histórica mesmo depois do v2 assumir o scheduler.
            "time_bucket": "GLOBAL",
            "score": min(total / TETO_OBSERVACOES_PARA_SCORE_MAXIMO, 1.0),
            "observation_count": total,
            "algorithm_version": ALGORITHM_VERSION,
        }
        for segmento_id, total in contagens
    ]

    stmt = pg_insert(RoadReadinessScore.__table__).values(valores)
    stmt = stmt.on_conflict_do_update(
        index_elements=[RoadReadinessScore.road_segment_id, RoadReadinessScore.time_bucket],
        set_={
            "score": stmt.excluded.score,
            "observation_count": stmt.excluded.observation_count,
            "algorithm_version": stmt.excluded.algorithm_version,
            "updated_at": func.now(),
        },
    )
    db.execute(stmt)
    db.commit()

    atualizados = len(valores)
    logger.info("road_readiness recalculado: %d segmento(s) atualizado(s)", atualizados)
    return atualizados
