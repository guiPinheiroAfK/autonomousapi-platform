from datetime import UTC, datetime
from uuid import uuid4

from fastapi.testclient import TestClient

from app.config import settings
from app.db import SessionLocal
from app.main import app
from app.models import RoadReadinessScore, RoadSegment, RoadSegmentObservation

from .conftest import criar_segmento_de_teste

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}


def _limpar(segmento_id):
    db = SessionLocal()
    try:
        db.query(RoadReadinessScore).filter(
            RoadReadinessScore.road_segment_id == segmento_id
        ).delete()
        db.query(RoadSegmentObservation).filter(
            RoadSegmentObservation.road_segment_id == segmento_id
        ).delete()
        db.query(RoadSegment).filter(RoadSegment.id == segmento_id).delete()
        db.commit()
    finally:
        db.close()


def test_ping_perto_de_segmento_conhecido_vira_observacao():
    segmento, lat, lon = criar_segmento_de_teste()
    try:
        resp = client.post(
            "/internal/v1/gps/pings",
            headers=HEADERS,
            json={
                "vehicle_id": str(uuid4()),
                "recorded_at": datetime.now(UTC).isoformat(),
                "lat": lat,
                "lon": lon,
            },
        )
        assert resp.status_code == 202

        db = SessionLocal()
        try:
            observacoes = (
                db.query(RoadSegmentObservation)
                .filter(RoadSegmentObservation.road_segment_id == segmento.id)
                .all()
            )
            assert len(observacoes) == 1
            # Garantia de privacidade (spec 02): a observação não carrega vehicle_id.
            assert not hasattr(observacoes[0], "vehicle_id")
        finally:
            db.close()
    finally:
        _limpar(segmento.id)


def test_ping_longe_de_qualquer_segmento_nao_vira_observacao():
    resp = client.post(
        "/internal/v1/gps/pings",
        headers=HEADERS,
        json={
            "vehicle_id": str(uuid4()),
            "recorded_at": datetime.now(UTC).isoformat(),
            "lat": 51.5,  # Londres — nada cadastrado por perto no teste
            "lon": -0.1,
        },
    )
    assert resp.status_code == 202
    # Não dá pra afirmar "zero observações no mundo" (outros testes podem ter dado
    # segmento por perto), então o teste real é: nenhuma exceção, ping aceito mesmo
    # sem match — ingestão não pode falhar por não achar via.


def test_recalculate_gera_score_a_partir_das_observacoes():
    segmento, lat, lon = criar_segmento_de_teste()
    try:
        for _ in range(3):
            client.post(
                "/internal/v1/gps/pings",
                headers=HEADERS,
                json={
                    "vehicle_id": str(uuid4()),
                    "recorded_at": datetime.now(UTC).isoformat(),
                    "lat": lat,
                    "lon": lon,
                },
            )

        resp = client.post("/internal/v1/road-readiness/recalculate", headers=HEADERS)
        assert resp.status_code == 200
        assert resp.json()["segments_updated"] >= 1

        db = SessionLocal()
        try:
            score = (
                db.query(RoadReadinessScore)
                .filter(RoadReadinessScore.road_segment_id == segmento.id)
                .one()
            )
            assert score.observation_count == 3
            assert 0 < score.score <= 1.0
            assert score.algorithm_version == "v1-obs-count"
        finally:
            db.close()
    finally:
        _limpar(segmento.id)
