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
from sqlalchemy.orm import Session

from .models import RoadReadinessScore, RoadSegmentObservation

logger = logging.getLogger(__name__)

ALGORITHM_VERSION = "v1-obs-count"

# Teto de observações a partir do qual o score satura em 1.0 — arbitrário e documentado
# como tal; recalibrar quando houver volume real de piloto para decidir um número melhor.
TETO_OBSERVACOES_PARA_SCORE_MAXIMO = 50


def recalcular_road_readiness(db: Session) -> int:
    """
    Recalcula o score de todo `road_segment` com pelo menos 1 observação nova desde a
    última passagem. Idempotente: pode rodar quantas vezes quiser, sempre reflete o
    estado atual das observações. Retorna quantos segmentos foram atualizados.
    """
    contagens = db.execute(
        select(
            RoadSegmentObservation.road_segment_id,
            func.count(RoadSegmentObservation.id).label("total"),
        ).group_by(RoadSegmentObservation.road_segment_id)
    ).all()

    atualizados = 0
    for segmento_id, total in contagens:
        score = min(total / TETO_OBSERVACOES_PARA_SCORE_MAXIMO, 1.0)

        existente = db.execute(
            select(RoadReadinessScore).where(RoadReadinessScore.road_segment_id == segmento_id)
        ).scalar_one_or_none()

        if existente:
            existente.score = score
            existente.observation_count = total
            existente.algorithm_version = ALGORITHM_VERSION
        else:
            db.add(
                RoadReadinessScore(
                    id=uuid4(),
                    road_segment_id=segmento_id,
                    score=score,
                    observation_count=total,
                    algorithm_version=ALGORITHM_VERSION,
                )
            )
        atualizados += 1

    db.commit()
    logger.info("road_readiness recalculado: %d segmento(s) atualizado(s)", atualizados)
    return atualizados
