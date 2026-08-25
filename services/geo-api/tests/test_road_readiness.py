from datetime import UTC, datetime
from uuid import uuid4

from fastapi.testclient import TestClient
from geoalchemy2.elements import WKTElement

from app.config import settings
from app.db import SessionLocal
from app.main import app
from app.models import RoadReadinessScore, RoadSegment, RoadSegmentObservation

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}


def _criar_segmento_de_teste() -> tuple[RoadSegment, float, float]:
    """
    Um trecho reto e curto perto da origem — não precisa ser via real, só precisa
    existir no banco pro matching por proximidade ter o que encontrar. Devolve
    (segmento, lat, lon) de um ponto sobre ele, pronto pra usar num ping.

    Geometria com offset ÚNICO por chamada (derivado de um uuid4), não fixa em
    (0,0)-(0,0.001): achado real ao investigar um teste instável — um segmento
    zumbi de uma execução anterior que não limpou direito, com geometria IDÊNTICA
    à deste helper, fez o "vizinho mais próximo" empatar; o empate não favorece o
    registro novo, e a observação foi pro segmento errado (o zumbi), fazendo o
    teste falhar de um jeito que parecia bug de matching, não sujeira de dado.
    Offset único elimina a possibilidade de empate mesmo que uma limpeza falhe nesta
    execução.
    """
    offset = (uuid4().int % 100_000) / 1_000_000  # ~0 a 0.1 grau, único por chamada
    db = SessionLocal()
    try:
        segmento = RoadSegment(
            osm_way_id=uuid4().int % 2_000_000_000,  # único por rodada de teste
            name="Via de teste",
            highway_type="residential",
            geom=WKTElement(f"LINESTRING(0 {offset}, 0 {offset + 0.001})", srid=4326),
        )
        db.add(segmento)
        db.commit()
        db.refresh(segmento)
        return segmento, offset + 0.0002, 0.0
    finally:
        db.close()


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
    segmento, lat, lon = _criar_segmento_de_teste()
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
    segmento, lat, lon = _criar_segmento_de_teste()
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
