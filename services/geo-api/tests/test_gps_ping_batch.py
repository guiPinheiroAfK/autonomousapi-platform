from datetime import UTC, datetime, timedelta
from uuid import uuid4

from fastapi.testclient import TestClient
from geoalchemy2.elements import WKTElement

from app.config import settings
from app.db import SessionLocal
from app.main import app
from app.models import RoadSegment, RoadSegmentObservation, VehicleGpsPing

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}


def _criar_segmento_de_teste() -> tuple[RoadSegment, float, float]:
    """Devolve (segmento, lat, lon) — geometria com offset único por chamada, ver
    a mesma nota em tests/test_road_readiness.py (evita colisão com zumbi)."""
    offset = (uuid4().int % 100_000) / 1_000_000
    db = SessionLocal()
    try:
        segmento = RoadSegment(
            osm_way_id=uuid4().int % 2_000_000_000,
            name="Via de teste (batch)",
            highway_type="residential",
            geom=WKTElement(f"LINESTRING(0 {offset}, 0 {offset + 0.001})", srid=4326),
        )
        db.add(segmento)
        db.commit()
        db.refresh(segmento)
        return segmento, offset + 0.0002, 0.0
    finally:
        db.close()


def _limpar_segmento(segmento_id):
    db = SessionLocal()
    try:
        db.query(RoadSegmentObservation).filter(
            RoadSegmentObservation.road_segment_id == segmento_id
        ).delete()
        db.query(RoadSegment).filter(RoadSegment.id == segmento_id).delete()
        db.commit()
    finally:
        db.close()


def _limpar_pings(vehicle_id):
    db = SessionLocal()
    try:
        db.query(VehicleGpsPing).filter(VehicleGpsPing.vehicle_id == vehicle_id).delete()
        db.commit()
    finally:
        db.close()


def _contar_pings(vehicle_id) -> int:
    db = SessionLocal()
    try:
        return db.query(VehicleGpsPing).filter(VehicleGpsPing.vehicle_id == vehicle_id).count()
    finally:
        db.close()


def test_endpoint_exige_token_de_servico():
    resp = client.post("/internal/v1/gps/pings/batch", json={"pings": []})
    assert resp.status_code == 401


def test_lote_vazio_devolve_zero():
    resp = client.post("/internal/v1/gps/pings/batch", headers=HEADERS, json={"pings": []})
    assert resp.status_code == 202
    assert resp.json() == {"accepted": 0, "received": 0}


def test_lote_novo_e_aceito_por_inteiro_e_gera_uma_observacao_por_ping():
    segmento, lat, lon = _criar_segmento_de_teste()
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    try:
        resp = client.post(
            "/internal/v1/gps/pings/batch",
            headers=HEADERS,
            json={
                "pings": [
                    {
                        "vehicle_id": str(vehicle_id),
                        "recorded_at": (inicio + timedelta(seconds=i)).isoformat(),
                        "lat": lat,
                        "lon": lon,
                    }
                    for i in range(3)
                ]
            },
        )
        assert resp.status_code == 202
        assert resp.json() == {"accepted": 3, "received": 3}

        assert _contar_pings(vehicle_id) == 3
        db = SessionLocal()
        try:
            assert (
                db.query(RoadSegmentObservation)
                .filter(RoadSegmentObservation.road_segment_id == segmento.id)
                .count()
                == 3
            )
        finally:
            db.close()
    finally:
        _limpar_pings(vehicle_id)
        _limpar_segmento(segmento.id)


def test_reenvio_do_mesmo_lote_e_aceito_sem_duplicar_ping_nem_observacao():
    """
    Contrato central do ADR 0019 (Anexo, A1/A2): a fila offline do app reenvia lote
    parcialmente aceito. Reenviar um ping já gravado não pode virar erro (accepted
    tem que contar ele de novo, senão a fila trava — A1) nem gerar observação nova
    (A2, senão a dedup não impede o score de inflar).
    """
    segmento, lat, lon = _criar_segmento_de_teste()
    vehicle_id = uuid4()
    ping = {
        "vehicle_id": str(vehicle_id),
        "recorded_at": datetime.now(UTC).isoformat(),
        "lat": lat,
        "lon": lon,
    }
    try:
        primeira = client.post(
            "/internal/v1/gps/pings/batch", headers=HEADERS, json={"pings": [ping]}
        )
        segunda = client.post(
            "/internal/v1/gps/pings/batch", headers=HEADERS, json={"pings": [ping]}
        )

        assert primeira.status_code == 202
        assert segunda.status_code == 202
        # accepted conta o ping como tratado nas duas vezes, mesmo a segunda sendo
        # puro conflito (A1) — é o que permite a fila offline descartar e seguir.
        assert primeira.json() == {"accepted": 1, "received": 1}
        assert segunda.json() == {"accepted": 1, "received": 1}

        assert _contar_pings(vehicle_id) == 1
        db = SessionLocal()
        try:
            assert (
                db.query(RoadSegmentObservation)
                .filter(RoadSegmentObservation.road_segment_id == segmento.id)
                .count()
                == 1
            )
        finally:
            db.close()
    finally:
        _limpar_pings(vehicle_id)
        _limpar_segmento(segmento.id)


def test_lote_misto_com_pings_novos_e_ja_existentes():
    segmento, lat, lon = _criar_segmento_de_teste()
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    ping_repetido = {
        "vehicle_id": str(vehicle_id),
        "recorded_at": inicio.isoformat(),
        "lat": lat,
        "lon": lon,
    }
    try:
        client.post(
            "/internal/v1/gps/pings/batch", headers=HEADERS, json={"pings": [ping_repetido]}
        )

        resp = client.post(
            "/internal/v1/gps/pings/batch",
            headers=HEADERS,
            json={
                "pings": [
                    ping_repetido,  # já existe — deve ser ignorado, não gerar 2ª observação
                    {
                        "vehicle_id": str(vehicle_id),
                        "recorded_at": (inicio + timedelta(seconds=1)).isoformat(),
                        "lat": lat,
                        "lon": lon,
                    },
                ]
            },
        )
        assert resp.status_code == 202
        assert resp.json() == {"accepted": 2, "received": 2}

        assert _contar_pings(vehicle_id) == 2
        db = SessionLocal()
        try:
            assert (
                db.query(RoadSegmentObservation)
                .filter(RoadSegmentObservation.road_segment_id == segmento.id)
                .count()
                == 2
            )
        finally:
            db.close()
    finally:
        _limpar_pings(vehicle_id)
        _limpar_segmento(segmento.id)
