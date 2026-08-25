"""
Métricas de qualidade do score de prontidão viária (ADR 0019, passo 6 — DoD da Fase 3,
spec 05: "métricas de qualidade do score definidas e monitoradas: quantas observações
por trecho, confiança do score").

Read-only, computadas sob demanda a partir do que já está gravado — diferente do score
em si (spec 02: "nunca calcular em tempo real na requisição"), aqui não há nada sendo
agregado pela primeira vez, só lido e resumido; portanto não é o mesmo tipo de operação
que a regra do spec proíbe.

Quatro métricas, escolhidas para responder exatamente ao que o spec pede mais os dois
riscos que o resto da ADR 0019 já tinha identificado como mensuráveis, mas não medidos:

1. **Cobertura** — fração dos segmentos da malha do piloto que têm pelo menos uma
   célula de score v2. Segmento sem cobertura é malha importada que ninguém atravessou
   ainda (ou atravessou fora da janela de passagens) — sem isso não dá pra saber se o
   piloto está "maduro" ou só começando.
2. **Distribuição de confiança** — média + fração de células com confiança abaixo de
   `LIMIAR_CONFIANCA_BAIXA`. É literalmente o "confiança do score" que o spec pede;
   sem isso, `road_readiness_score` é só um número sem contexto de quão maduro ele é.
3. **Densidade de observação** — média de passagens por célula. É o "quantas
   observações por trecho" do spec, na unidade certa (passagem, não ping — ADR 0019).
4. **Taxa de ping atrasado** — fração de `vehicle_gps_ping` cujo `created_at -
   recorded_at` passa da janela de reconstrução de passagens. Mede exatamente a
   limitação documentada em `app/sessionization.py` (D1.2): ping que chega tarde
   demais nunca vira passagem. Sem medir, essa limitação é uma suposição, não um fato.
"""

from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .models import RoadReadinessScore, RoadSegment, VehicleGpsPing
from .road_readiness_v2 import ALGORITHM_VERSION

# Confiança abaixo disso conta como "baixa" pra fim de relatório — ponto de partida
# documentado, mesma natureza dos outros números "a calibrar" desta ADR; ajustar
# quando houver critério real (ex. abaixo de que confiança um consumidor de roteamento
# decide ignorar o score da célula).
LIMIAR_CONFIANCA_BAIXA = 0.3


def calcular_metricas_qualidade(db: Session, rebuild_window_hours: int) -> dict:
    total_segmentos = db.execute(select(func.count()).select_from(RoadSegment)).scalar_one()

    segmentos_com_score = db.execute(
        select(func.count(func.distinct(RoadReadinessScore.road_segment_id))).where(
            RoadReadinessScore.algorithm_version == ALGORITHM_VERSION
        )
    ).scalar_one()
    cobertura = (segmentos_com_score / total_segmentos) if total_segmentos else None

    celulas_v2 = db.execute(
        select(RoadReadinessScore.confidence, RoadReadinessScore.observation_count).where(
            RoadReadinessScore.algorithm_version == ALGORITHM_VERSION
        )
    ).all()
    total_celulas = len(celulas_v2)

    confiancas = [c for c, _n in celulas_v2 if c is not None]
    confianca_media = (sum(confiancas) / len(confiancas)) if confiancas else None
    fracao_confianca_baixa = (
        sum(1 for c in confiancas if c < LIMIAR_CONFIANCA_BAIXA) / len(confiancas)
        if confiancas
        else None
    )

    observacoes = [n for _c, n in celulas_v2]
    media_observacoes_por_celula = (sum(observacoes) / len(observacoes)) if observacoes else None

    limite = timedelta(hours=rebuild_window_hours)
    total_pings = db.execute(select(func.count()).select_from(VehicleGpsPing)).scalar_one()
    pings_atrasados = db.execute(
        select(func.count())
        .select_from(VehicleGpsPing)
        .where(VehicleGpsPing.created_at - VehicleGpsPing.recorded_at > limite)
    ).scalar_one()
    taxa_ping_atrasado = (pings_atrasados / total_pings) if total_pings else None

    return {
        "total_segments": total_segmentos,
        "segments_with_score": segmentos_com_score,
        "coverage_ratio": cobertura,
        "total_cells": total_celulas,
        "average_confidence": confianca_media,
        "low_confidence_cell_ratio": fracao_confianca_baixa,
        "average_observations_per_cell": media_observacoes_por_celula,
        "total_pings": total_pings,
        "late_ping_ratio": taxa_ping_atrasado,
    }
