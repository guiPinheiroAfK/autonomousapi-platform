from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from geoalchemy2.elements import WKTElement

from app.db import SessionLocal
from app.models import RoadReadinessScore, RoadSegment, VehicleGpsPing
from app.quality_metrics import LIMIAR_CONFIANCA_BAIXA, calcular_metricas_qualidade
from app.road_readiness_v2 import ALGORITHM_VERSION

# Banco de dev compartilhado com outros testes/dado de carga — todo teste aqui mede
# DELTA (antes/depois de criar as fixtures), nunca valor absoluto, pra não depender do
# estado global do banco.
REBUILD_WINDOW_HOURS = 1


def _criar_segmento(offset: float) -> RoadSegment:
    db = SessionLocal()
    try:
        segmento = RoadSegment(
            osm_way_id=uuid4().int % 2_000_000_000,
            name="Via de teste (métricas)",
            highway_type="residential",
            geom=WKTElement(f"LINESTRING(0 {offset}, 0 {offset + 0.001})", srid=4326),
        )
        db.add(segmento)
        db.commit()
        db.refresh(segmento)
        return segmento
    finally:
        db.close()


def _criar_score(segmento_id, bucket, confidence, observation_count):
    db = SessionLocal()
    try:
        score = RoadReadinessScore(
            id=uuid4(),
            road_segment_id=segmento_id,
            time_bucket=bucket,
            score=0.5,
            observation_count=observation_count,
            confidence=confidence,
            algorithm_version=ALGORITHM_VERSION,
        )
        db.add(score)
        db.commit()
        return score.id
    finally:
        db.close()


def _criar_ping(recorded_at):
    db = SessionLocal()
    try:
        ping = VehicleGpsPing(
            vehicle_id=uuid4(),
            recorded_at=recorded_at,
            lat=0.0,
            lon=0.0,
            geom=WKTElement("POINT(0 0)", srid=4326),
        )
        db.add(ping)
        db.commit()
        db.refresh(ping)
        return ping.id
    finally:
        db.close()


def _limpar(*, segmento_ids=(), score_ids=(), ping_ids=()):
    db = SessionLocal()
    try:
        db.query(RoadReadinessScore).filter(RoadReadinessScore.id.in_(score_ids)).delete(
            synchronize_session=False
        )
        db.query(VehicleGpsPing).filter(VehicleGpsPing.id.in_(ping_ids)).delete(
            synchronize_session=False
        )
        db.query(RoadSegment).filter(RoadSegment.id.in_(segmento_ids)).delete(
            synchronize_session=False
        )
        db.commit()
    finally:
        db.close()


def _metricas():
    db = SessionLocal()
    try:
        return calcular_metricas_qualidade(db, rebuild_window_hours=REBUILD_WINDOW_HOURS)
    finally:
        db.close()


def test_cobertura_conta_so_segmento_com_score_v2():
    antes = _metricas()
    sem_score = _criar_segmento(offset=0.40)
    com_score = _criar_segmento(offset=0.41)
    score_id = _criar_score(com_score.id, "UTIL_ENTREPICO", confidence=0.8, observation_count=10)
    try:
        depois = _metricas()
        assert depois["total_segments"] == antes["total_segments"] + 2
        assert depois["segments_with_score"] == antes["segments_with_score"] + 1
    finally:
        _limpar(segmento_ids=[sem_score.id, com_score.id], score_ids=[score_id])


def test_distribuicao_de_confianca():
    antes = _metricas()
    segmento = _criar_segmento(offset=0.42)
    score_ids = [
        _criar_score(segmento.id, "UTIL_MADRUGADA", confidence=0.9, observation_count=25),
        _criar_score(segmento.id, "UTIL_PICO_MANHA", confidence=0.1, observation_count=2),
    ]
    try:
        depois = _metricas()
        assert depois["total_cells"] == antes["total_cells"] + 2

        soma_confianca_antes = (antes["average_confidence"] or 0.0) * antes["total_cells"]
        soma_confianca_depois = depois["average_confidence"] * depois["total_cells"]
        assert soma_confianca_depois - soma_confianca_antes == pytest.approx(0.9 + 0.1)

        soma_obs_antes = (antes["average_observations_per_cell"] or 0.0) * antes["total_cells"]
        soma_obs_depois = depois["average_observations_per_cell"] * depois["total_cells"]
        assert soma_obs_depois - soma_obs_antes == pytest.approx(25 + 2)

        baixa_antes = (antes["low_confidence_cell_ratio"] or 0.0) * antes["total_cells"]
        baixa_depois = depois["low_confidence_cell_ratio"] * depois["total_cells"]
        # Só a célula com confidence=0.1 fica abaixo do limiar.
        assert baixa_depois - baixa_antes == pytest.approx(1)
        assert 0.1 < LIMIAR_CONFIANCA_BAIXA < 0.9
    finally:
        _limpar(segmento_ids=[segmento.id], score_ids=score_ids)


def test_ping_recente_nao_conta_como_atrasado():
    antes = _metricas()
    ping_id = _criar_ping(datetime.now(UTC))
    try:
        depois = _metricas()
        assert depois["total_pings"] == antes["total_pings"] + 1
        atrasados_antes = (antes["late_ping_ratio"] or 0.0) * antes["total_pings"]
        atrasados_depois = depois["late_ping_ratio"] * depois["total_pings"]
        assert atrasados_depois - atrasados_antes == pytest.approx(0)
    finally:
        _limpar(ping_ids=[ping_id])


def test_ping_gravado_fora_da_janela_conta_como_atrasado():
    """D1.2: ping cujo created_at - recorded_at passa da janela de reconstrução de
    passagens nunca vira passagem — a métrica precisa detectar exatamente isso."""
    antes = _metricas()
    # recorded_at bem antes da janela; created_at é definido pelo servidor como "agora"
    # na hora do INSERT — simula um ping que só chegou ao servidor bem depois de ter
    # sido registrado no aparelho (fila offline do app).
    muito_antigo = datetime.now(UTC) - timedelta(hours=REBUILD_WINDOW_HOURS * 5)
    ping_id = _criar_ping(muito_antigo)
    try:
        depois = _metricas()
        assert depois["total_pings"] == antes["total_pings"] + 1
        atrasados_antes = (antes["late_ping_ratio"] or 0.0) * antes["total_pings"]
        atrasados_depois = depois["late_ping_ratio"] * depois["total_pings"]
        assert atrasados_depois - atrasados_antes == pytest.approx(1)
    finally:
        _limpar(ping_ids=[ping_id])
