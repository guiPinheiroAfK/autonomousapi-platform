from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

client = TestClient(app)


def test_root_liveness_sem_token():
    resp = client.get("/")
    assert resp.status_code == 200
    assert resp.json()["service"] == "geo-api"


def test_health_exige_service_token():
    resp = client.get("/internal/v1/health")
    assert resp.status_code == 401


def test_health_ok_com_service_token():
    resp = client.get(
        "/internal/v1/health",
        headers={"X-Service-Token": settings.service_token},
    )
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok", "service": "geo-api"}
