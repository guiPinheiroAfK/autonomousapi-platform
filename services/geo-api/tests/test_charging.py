from uuid import uuid4

from fastapi.testclient import TestClient
from geoalchemy2.elements import WKTElement

from app.charging import ChargingStationProvider, DisabledChargingStationProvider, EstacaoExterna
from app.charging_sync import sincronizar_estacoes
from app.config import settings
from app.db import SessionLocal
from app.main import app
from app.models import ChargingStation, ChargingStationStatus

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}


class FakeProvider(ChargingStationProvider):
    def __init__(self, estacoes: list[EstacaoExterna]):
        self._estacoes = estacoes

    def buscar_estacoes(self, country_code: str) -> list[EstacaoExterna]:
        return self._estacoes


def _limpar(station_id):
    db = SessionLocal()
    try:
        db.query(ChargingStationStatus).filter(
            ChargingStationStatus.station_id == station_id
        ).delete()
        db.query(ChargingStation).filter(ChargingStation.id == station_id).delete()
        db.commit()
    finally:
        db.close()


def test_provider_desabilitado_devolve_lista_vazia_sem_lancar():
    provider = DisabledChargingStationProvider()

    assert provider.buscar_estacoes("BR") == []


def test_sincronizar_estacoes_cria_estacao_e_status():
    external_id = str(uuid4().int % 1_000_000)
    estacao = EstacaoExterna(
        external_id=external_id,
        name="Posto Teste",
        address="Rua Teste, 123",
        connector_type="Tipo 2",
        power_kw=22.0,
        lat=-23.56,
        lon=-46.65,
        status="DISPONIVEL",
    )
    db = SessionLocal()
    try:
        total = sincronizar_estacoes(db, FakeProvider([estacao]), "BR")
        assert total == 1

        salva = (
            db.query(ChargingStation)
            .filter(
                ChargingStation.provider == "OPEN_CHARGE_MAP",
                ChargingStation.external_id == external_id,
            )
            .one()
        )
        assert salva.name == "Posto Teste"

        status = (
            db.query(ChargingStationStatus)
            .filter(ChargingStationStatus.station_id == salva.id)
            .one()
        )
        assert status.status == "DISPONIVEL"
        assert status.source == "PROVEDOR_EXTERNO"

        station_id = salva.id
    finally:
        db.close()
    _limpar(station_id)


def test_sincronizar_de_novo_atualiza_em_vez_de_duplicar():
    external_id = str(uuid4().int % 1_000_000)
    v1 = EstacaoExterna(
        external_id=external_id,
        name="Nome Antigo",
        address=None,
        connector_type=None,
        power_kw=None,
        lat=-23.56,
        lon=-46.65,
        status="DISPONIVEL",
    )
    v2 = EstacaoExterna(
        external_id=external_id,
        name="Nome Novo",
        address=None,
        connector_type=None,
        power_kw=None,
        lat=-23.56,
        lon=-46.65,
        status="OCUPADO",
    )
    db = SessionLocal()
    try:
        sincronizar_estacoes(db, FakeProvider([v1]), "BR")
        sincronizar_estacoes(db, FakeProvider([v2]), "BR")

        todas = (
            db.query(ChargingStation)
            .filter(
                ChargingStation.provider == "OPEN_CHARGE_MAP",
                ChargingStation.external_id == external_id,
            )
            .all()
        )
        assert len(todas) == 1
        assert todas[0].name == "Nome Novo"

        historico = (
            db.query(ChargingStationStatus)
            .filter(ChargingStationStatus.station_id == todas[0].id)
            .all()
        )
        assert len(historico) == 2  # histórico preservado, não sobrescrito

        station_id = todas[0].id
    finally:
        db.close()
    _limpar(station_id)


def test_endpoint_devolve_desconhecido_quando_sem_status():
    db = SessionLocal()
    try:
        station = ChargingStation(
            id=uuid4(),
            provider="OPEN_CHARGE_MAP",
            external_id=str(uuid4().int % 1_000_000),
            name="Sem status",
            address=None,
            connector_type=None,
            power_kw=None,
            lat=-23.56,
            lon=-46.65,
            geom=WKTElement("POINT(-46.65 -23.56)", srid=4326),
        )
        db.add(station)
        db.commit()
        db.refresh(station)
        station_id = station.id
    finally:
        db.close()

    try:
        resp = client.get("/internal/v1/charging-stations", headers=HEADERS)
        assert resp.status_code == 200
        body = resp.json()
        item = next(s for s in body["stations"] if s["id"] == str(station_id))
        assert item["status"] == "DESCONHECIDO"
    finally:
        _limpar(station_id)


def test_endpoint_filtra_por_raio_quando_lat_lon_informados():
    db = SessionLocal()
    try:
        perto = ChargingStation(
            id=uuid4(),
            provider="OPEN_CHARGE_MAP",
            external_id=str(uuid4().int % 1_000_000),
            name="Perto",
            address=None,
            connector_type=None,
            power_kw=None,
            lat=0.0,
            lon=0.0,
            geom=WKTElement("POINT(0 0)", srid=4326),
        )
        longe = ChargingStation(
            id=uuid4(),
            provider="OPEN_CHARGE_MAP",
            external_id=str(uuid4().int % 1_000_000),
            name="Longe",
            address=None,
            connector_type=None,
            power_kw=None,
            lat=10.0,
            lon=10.0,
            geom=WKTElement("POINT(10 10)", srid=4326),
        )
        db.add_all([perto, longe])
        db.commit()
        db.refresh(perto)
        db.refresh(longe)
        perto_id, longe_id = perto.id, longe.id
    finally:
        db.close()

    try:
        resp = client.get(
            "/internal/v1/charging-stations",
            params={"lat": 0.0, "lon": 0.0, "radius_km": 5},
            headers=HEADERS,
        )
        assert resp.status_code == 200
        ids = {s["id"] for s in resp.json()["stations"]}
        assert str(perto_id) in ids
        assert str(longe_id) not in ids
    finally:
        _limpar(perto_id)
        _limpar(longe_id)


def test_endpoint_exige_token_de_servico():
    resp = client.get("/internal/v1/charging-stations")
    assert resp.status_code == 401
