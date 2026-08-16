from datetime import UTC, datetime, timedelta
from uuid import uuid4

from fastapi.testclient import TestClient
from geoalchemy2.elements import WKTElement

from app.config import settings
from app.db import SessionLocal
from app.driving_events import calcular_driving_events
from app.main import app
from app.models import VehicleGpsPing

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}


def _salvar_ping(vehicle_id, recorded_at, speed):
    db = SessionLocal()
    try:
        ping = VehicleGpsPing(
            vehicle_id=vehicle_id,
            recorded_at=recorded_at,
            lat=0.0,
            lon=0.0,
            speed=speed,
            geom=WKTElement("POINT(0 0)", srid=4326),
        )
        db.add(ping)
        db.commit()
        db.refresh(ping)
        return ping.id
    finally:
        db.close()


def _limpar(*ping_ids):
    db = SessionLocal()
    try:
        db.query(VehicleGpsPing).filter(VehicleGpsPing.id.in_(ping_ids)).delete(synchronize_session=False)
        db.commit()
    finally:
        db.close()


def test_detecta_frenagem_brusca_em_janela_curta():
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    ids = [
        _salvar_ping(vehicle_id, inicio, 80.0),
        _salvar_ping(vehicle_id, inicio + timedelta(seconds=2), 40.0),  # queda de 40 em 2s
    ]
    try:
        eventos = _calcular(
            vehicle_id, inicio - timedelta(seconds=1), inicio + timedelta(seconds=10)
        )
        assert eventos.hard_braking_count == 1
        assert eventos.overspeed_count == 0
        assert eventos.ping_count == 2
    finally:
        _limpar(*ids)


def test_nao_conta_desaceleracao_lenta_como_frenagem_brusca():
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    ids = [
        _salvar_ping(vehicle_id, inicio, 80.0),
        _salvar_ping(vehicle_id, inicio + timedelta(seconds=30), 40.0),  # mesma queda, 30s
    ]
    try:
        eventos = _calcular(
            vehicle_id, inicio - timedelta(seconds=1), inicio + timedelta(seconds=40)
        )
        assert eventos.hard_braking_count == 0
    finally:
        _limpar(*ids)


def test_detecta_excesso_de_velocidade():
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    ids = [_salvar_ping(vehicle_id, inicio, 120.0)]
    try:
        eventos = _calcular(
            vehicle_id, inicio - timedelta(seconds=1), inicio + timedelta(seconds=1)
        )
        assert eventos.overspeed_count == 1
    finally:
        _limpar(*ids)


def test_ignora_pings_sem_velocidade():
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    ids = [_salvar_ping(vehicle_id, inicio, None)]
    try:
        eventos = _calcular(
            vehicle_id, inicio - timedelta(seconds=1), inicio + timedelta(seconds=1)
        )
        assert eventos.ping_count == 0
    finally:
        _limpar(*ids)


def test_endpoint_exige_token_de_servico():
    resp = client.get(
        "/internal/v1/driving-events",
        params={
            "vehicle_id": str(uuid4()),
            "from": datetime.now(UTC).isoformat(),
            "to": datetime.now(UTC).isoformat(),
        },
    )
    assert resp.status_code == 401


def test_endpoint_devolve_contagens():
    vehicle_id = uuid4()
    inicio = datetime.now(UTC)
    ids = [_salvar_ping(vehicle_id, inicio, 120.0)]
    try:
        resp = client.get(
            "/internal/v1/driving-events",
            headers=HEADERS,
            params={
                "vehicle_id": str(vehicle_id),
                "from": (inicio - timedelta(seconds=1)).isoformat(),
                "to": (inicio + timedelta(seconds=1)).isoformat(),
            },
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["ping_count"] == 1
        assert body["overspeed_count"] == 1
        assert body["hard_braking_count"] == 0
    finally:
        _limpar(*ids)


def _calcular(vehicle_id, de, ate):
    db = SessionLocal()
    try:
        return calcular_driving_events(db, vehicle_id, de, ate)
    finally:
        db.close()
