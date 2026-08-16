import httpx
import pytest
from fastapi.testclient import TestClient

from app import geocoding
from app.config import settings
from app.geocoding import NominatimGeocoder
from app.main import app

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}

# Formato jsonv2 real do Nominatim, encurtado.
NOMINATIM_OK = [
    {"display_name": "MASP, Avenida Paulista, São Paulo", "lat": "-23.5614", "lon": "-46.6559"},
    {"display_name": "Avenida Paulista, São Paulo", "lat": "-23.5630", "lon": "-46.6540"},
]


@pytest.fixture
def nominatim_respondendo(monkeypatch):
    def _instalar(resposta, capturar: dict | None = None):
        def fake_get(url, **kwargs):
            if capturar is not None:
                capturar["url"] = url
                capturar["params"] = kwargs.get("params")
                capturar["headers"] = kwargs.get("headers")
            if isinstance(resposta, Exception):
                raise resposta
            return resposta

        monkeypatch.setattr(geocoding.httpx, "get", fake_get)

    return _instalar


def _resposta(json_body, status: int = 200) -> httpx.Response:
    return httpx.Response(status, json=json_body, request=httpx.Request("GET", "http://nominatim/"))


def test_converte_resultado_do_nominatim(nominatim_respondendo):
    nominatim_respondendo(_resposta(NOMINATIM_OK))

    lugares = NominatimGeocoder("https://nominatim.test").buscar("Avenida Paulista")

    assert len(lugares) == 2
    assert lugares[0].display_name.startswith("MASP")
    # lat/lon vêm como string no JSON do Nominatim — precisam virar float.
    assert lugares[0].lat == -23.5614
    assert isinstance(lugares[0].lon, float)


def test_restringe_a_busca_a_bbox_do_piloto(nominatim_respondendo):
    capturado: dict = {}
    nominatim_respondendo(_resposta(NOMINATIM_OK), capturar=capturado)

    NominatimGeocoder("https://nominatim.test").buscar("Avenida Paulista")

    # bounded=1 + viewbox é o que impede "digitei endereço de outro estado e o
    # roteamento devolveu coisa estranha".
    assert capturado["params"]["bounded"] == 1
    assert capturado["params"]["viewbox"] == "-46.68,-23.54,-46.62,-23.59"
    # Política de uso do Nominatim público exige UA identificável.
    assert "autonomousapi" in capturado["headers"]["User-Agent"]


def test_item_malformado_nao_invalida_os_demais(nominatim_respondendo):
    nominatim_respondendo(_resposta([{"display_name": "sem coordenada"}, NOMINATIM_OK[0]]))

    lugares = NominatimGeocoder("https://nominatim.test").buscar("qualquer")

    assert len(lugares) == 1
    assert lugares[0].display_name.startswith("MASP")


def test_falha_de_rede_vira_lista_vazia(nominatim_respondendo):
    nominatim_respondendo(httpx.ConnectError("dns"))

    assert NominatimGeocoder("https://nominatim.test").buscar("Paulista") == []


def test_consulta_vazia_nao_chama_a_rede():
    assert NominatimGeocoder("https://nominatim.test").buscar("   ") == []


def test_endpoint_exige_token_de_servico():
    assert client.get("/internal/v1/geocode", params={"q": "Paulista"}).status_code == 401


def test_endpoint_rejeita_consulta_curta_demais():
    resp = client.get("/internal/v1/geocode", headers=HEADERS, params={"q": "ab"})
    assert resp.status_code == 422
