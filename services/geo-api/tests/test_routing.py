import httpx
import pytest
from fastapi.testclient import TestClient

from app import routing
from app.config import settings
from app.main import app
from app.routing import OsrmRoutingClient

client = TestClient(app)
HEADERS = {"X-Service-Token": settings.service_token}

# Resposta real do osrm-routed, encurtada — trecho da Av. Paulista até o Paraíso, capturado
# do grafo do piloto. Serve de contrato: se o OSRM mudar o formato, o teste quebra aqui e
# não em produção.
OSRM_OK = {
    "code": "Ok",
    "routes": [
        {
            "distance": 3563.8,
            "duration": 400.8,
            "geometry": {"coordinates": [[-46.6559, -23.5614], [-46.6500, -23.5680]]},
            "legs": [
                {
                    "steps": [
                        {
                            "maneuver": {"type": "depart"},
                            "name": "Passagem Subterrânea Daher Elias Cutait",
                            "distance": 365.0,
                            "duration": 40.0,
                        },
                        {
                            "maneuver": {"type": "turn", "modifier": "left"},
                            "name": "",
                            "distance": 137.0,
                            "duration": 18.0,
                        },
                    ]
                }
            ],
        }
    ],
}


@pytest.fixture(autouse=True)
def limpar_cache_de_rota():
    """Vários testes usam as mesmas coordenadas com respostas mockadas diferentes (ex.
    testam distância vs. ordem lon/lat vs. raio de snap) — sem limpar, o cache do módulo
    (app/routing.py) faria um teste "vazar" resultado pro seguinte."""
    routing._rota_cache.clear()
    yield
    routing._rota_cache.clear()


@pytest.fixture
def osrm_respondendo(monkeypatch):
    """Troca o httpx.get do módulo de roteamento por uma resposta controlada."""

    def _instalar(resposta: httpx.Response | Exception, capturar: dict | None = None):
        def fake_get(url, **kwargs):
            if capturar is not None:
                capturar["url"] = url
                capturar["params"] = kwargs.get("params")
            if isinstance(resposta, Exception):
                raise resposta
            return resposta

        monkeypatch.setattr(routing.httpx, "get", fake_get)

    return _instalar


def _resposta(json_body: dict, status: int = 200) -> httpx.Response:
    return httpx.Response(status, json=json_body, request=httpx.Request("GET", "http://osrm/"))


def test_sem_url_configurada_devolve_indisponivel_sem_chamar_rede():
    resultado = OsrmRoutingClient("").rota(-23.56, -46.65, -23.57, -46.64)

    assert resultado.available is False
    assert "não configurado" in resultado.unavailable_reason


def test_traduz_resposta_do_osrm_para_o_nosso_contrato(osrm_respondendo):
    osrm_respondendo(_resposta(OSRM_OK))

    resultado = OsrmRoutingClient("http://osrm:5000").rota(-23.5614, -46.6559, -23.5750, -46.6400)

    assert resultado.available is True
    assert resultado.distance_m == 3563.8
    assert resultado.duration_s == 400.8
    assert resultado.geometry == [[-46.6559, -23.5614], [-46.6500, -23.5680]]
    assert len(resultado.steps) == 2
    assert resultado.steps[0].name == "Passagem Subterrânea Daher Elias Cutait"
    assert resultado.steps[1].modifier == "left"
    # OSRM manda "" para trecho sem nome; normalizamos para None (ver routing.py).
    assert resultado.steps[1].name is None


def test_envia_coordenadas_na_ordem_lon_lat_que_o_osrm_espera(osrm_respondendo):
    capturado: dict = {}
    osrm_respondendo(_resposta(OSRM_OK), capturar=capturado)

    OsrmRoutingClient("http://osrm:5000").rota(-23.5614, -46.6559, -23.5750, -46.6400)

    # lon,lat;lon,lat — trocar essa ordem é o erro clássico de integração com OSRM e não
    # gera exceção nenhuma, só rota errada. Por isso está travado num teste.
    assert capturado["url"].endswith("/route/v1/driving/-46.6559,-23.5614;-46.64,-23.575")


def test_limita_o_raio_de_snap_para_nao_grudar_ponto_distante(osrm_respondendo):
    capturado: dict = {}
    osrm_respondendo(_resposta(OSRM_OK), capturar=capturado)

    OsrmRoutingClient("http://osrm:5000").rota(-23.5614, -46.6559, -23.5750, -46.6400)

    # Sem `radiuses`, o OSRM aceita qualquer coordenada e devolve code=Ok com uma rota
    # até a borda da área do piloto — pedir rota para Londres "funcionaria". Este teste
    # existe para essa regressão específica.
    assert capturado["params"]["radiuses"] == "1000;1000"


def test_ponto_fora_da_area_vira_indisponivel_e_nao_erro_de_infra(osrm_respondendo):
    # O OSRM responde 400 nesse caso; se tratássemos 4xx como exceção, o usuário leria
    # "motor indisponível" em vez de "seu ponto está fora da área".
    osrm_respondendo(_resposta({"code": "NoSegment"}, status=400))

    resultado = OsrmRoutingClient("http://osrm:5000").rota(-23.56, -46.65, 51.5, -0.1)

    assert resultado.available is False
    assert "fora da área coberta" in resultado.unavailable_reason


def test_sem_ligacao_viaria_tem_motivo_proprio(osrm_respondendo):
    osrm_respondendo(_resposta({"code": "NoRoute", "routes": []}, status=400))

    resultado = OsrmRoutingClient("http://osrm:5000").rota(-23.56, -46.65, -23.57, -46.64)

    assert resultado.available is False
    assert "ligação viária" in resultado.unavailable_reason


def test_mesma_origem_destino_de_novo_nao_chama_o_osrm_de_novo(monkeypatch):
    chamadas = {"n": 0}

    def fake_get(url, **kwargs):
        chamadas["n"] += 1
        return _resposta(OSRM_OK)

    monkeypatch.setattr(routing.httpx, "get", fake_get)

    cliente = OsrmRoutingClient("http://osrm:5000")
    primeira = cliente.rota(-23.5614, -46.6559, -23.5750, -46.6400)
    segunda = cliente.rota(-23.5614, -46.6559, -23.5750, -46.6400)

    assert chamadas["n"] == 1
    assert primeira.distance_m == segunda.distance_m == 3563.8


def test_par_de_coordenadas_diferente_nao_bate_no_cache_do_outro_par(osrm_respondendo):
    capturado: dict = {}
    osrm_respondendo(_resposta(OSRM_OK), capturar=capturado)

    OsrmRoutingClient("http://osrm:5000").rota(-23.5614, -46.6559, -23.5750, -46.6400)
    capturado.clear()
    OsrmRoutingClient("http://osrm:5000").rota(-23.9999, -46.6559, -23.5750, -46.6400)

    # Se batesse no cache da chamada anterior, capturado nunca seria repopulado.
    assert capturado.get("url") is not None


def test_resultado_indisponivel_nao_fica_em_cache(osrm_respondendo):
    osrm_respondendo(httpx.ConnectError("connection refused"))
    cliente = OsrmRoutingClient("http://osrm:5000")
    cliente.rota(-23.56, -46.65, -23.57, -46.64)

    capturado: dict = {}
    osrm_respondendo(_resposta(OSRM_OK), capturar=capturado)
    resultado = cliente.rota(-23.56, -46.65, -23.57, -46.64)

    # OSRM "voltou a responder" — se o indisponível tivesse sido cacheado, isso não chamaria
    # a rede de novo e continuaria devolvendo indisponível.
    assert resultado.available is True
    assert capturado.get("url") is not None


def test_motor_fora_do_ar_vira_indisponivel_e_nao_propaga_excecao(osrm_respondendo):
    osrm_respondendo(httpx.ConnectError("connection refused"))

    resultado = OsrmRoutingClient("http://osrm:5000").rota(-23.56, -46.65, -23.57, -46.64)

    assert resultado.available is False
    assert "indisponível" in resultado.unavailable_reason


def test_endpoint_exige_token_de_servico():
    resp = client.get(
        "/internal/v1/route",
        params={"from_lat": -23.56, "from_lon": -46.65, "to_lat": -23.57, "to_lon": -46.64},
    )
    assert resp.status_code == 401


def test_endpoint_responde_200_com_available_false_quando_motor_desligado():
    # settings.osrm_url é "" no ambiente de teste — o caminho que o CI exercita.
    resp = client.get(
        "/internal/v1/route",
        headers=HEADERS,
        params={"from_lat": -23.56, "from_lon": -46.65, "to_lat": -23.57, "to_lon": -46.64},
    )

    assert resp.status_code == 200
    corpo = resp.json()
    assert corpo["available"] is False
    assert corpo["unavailable_reason"]
    assert corpo["steps"] == []


def test_endpoint_valida_coordenada_fora_de_faixa():
    resp = client.get(
        "/internal/v1/route",
        headers=HEADERS,
        params={"from_lat": 999, "from_lon": -46.65, "to_lat": -23.57, "to_lon": -46.64},
    )
    assert resp.status_code == 422


# ---------------------------------------------------------------------------
# /table — matriz de distância/duração (spec 02, "Evolução pendente")
# ---------------------------------------------------------------------------

OSRM_TABLE_OK = {
    "code": "Ok",
    "distances": [[0, 1200.5, 3400.0], [1200.5, 0, 2200.0], [3400.0, 2200.0, 0]],
    "durations": [[0, 150.0, 300.0], [150.0, 0, 220.0], [300.0, 220.0, 0]],
}

PONTOS_3 = [(-23.5614, -46.6559), (-23.5700, -46.6400), (-23.5500, -46.6300)]


def test_table_sem_url_configurada_devolve_indisponivel_sem_chamar_rede():
    resultado = OsrmRoutingClient("").table(PONTOS_3)

    assert resultado.available is False
    assert "não configurado" in resultado.unavailable_reason


def test_table_traduz_resposta_do_osrm_para_o_nosso_contrato(osrm_respondendo):
    osrm_respondendo(_resposta(OSRM_TABLE_OK))

    resultado = OsrmRoutingClient("http://osrm:5000").table(PONTOS_3)

    assert resultado.available is True
    assert resultado.distances_m == OSRM_TABLE_OK["distances"]
    assert resultado.durations_s == OSRM_TABLE_OK["durations"]


def test_table_envia_coordenadas_na_ordem_lon_lat(osrm_respondendo):
    capturado: dict = {}
    osrm_respondendo(_resposta(OSRM_TABLE_OK), capturar=capturado)

    OsrmRoutingClient("http://osrm:5000").table(PONTOS_3)

    assert capturado["url"].endswith(
        "/table/v1/driving/-46.6559,-23.5614;-46.64,-23.57;-46.63,-23.55"
    )


def test_table_envia_um_raio_de_snap_por_ponto(osrm_respondendo):
    capturado: dict = {}
    osrm_respondendo(_resposta(OSRM_TABLE_OK), capturar=capturado)

    OsrmRoutingClient("http://osrm:5000").table(PONTOS_3)

    assert capturado["params"]["radiuses"] == "1000;1000;1000"
    assert capturado["params"]["annotations"] == "distance,duration"


def test_table_rejeita_menos_de_dois_pontos_sem_chamar_rede():
    resultado = OsrmRoutingClient("http://osrm:5000").table([(-23.56, -46.65)])

    assert resultado.available is False
    assert "ao menos 2 pontos" in resultado.unavailable_reason


def test_table_rejeita_acima_do_teto_de_pontos_sem_chamar_rede():
    pontos = [(-23.56 + i * 0.001, -46.65) for i in range(31)]

    resultado = OsrmRoutingClient("http://osrm:5000").table(pontos)

    assert resultado.available is False
    assert "Máximo de 30 pontos" in resultado.unavailable_reason


def test_table_motor_fora_do_ar_vira_indisponivel_e_nao_propaga_excecao(osrm_respondendo):
    osrm_respondendo(httpx.ConnectError("connection refused"))

    resultado = OsrmRoutingClient("http://osrm:5000").table(PONTOS_3)

    assert resultado.available is False
    assert "indisponível" in resultado.unavailable_reason


def test_table_ponto_fora_da_area_vira_indisponivel(osrm_respondendo):
    osrm_respondendo(_resposta({"code": "NoSegment"}, status=400))

    resultado = OsrmRoutingClient("http://osrm:5000").table(PONTOS_3)

    assert resultado.available is False
    assert "fora da área coberta" in resultado.unavailable_reason


def test_table_endpoint_exige_token_de_servico():
    resp = client.post("/internal/v1/table", json={"points": [{"lat": -23.56, "lon": -46.65}]})
    assert resp.status_code == 401


def test_table_endpoint_responde_200_com_available_false_quando_motor_desligado():
    resp = client.post(
        "/internal/v1/table",
        headers=HEADERS,
        json={"points": [{"lat": -23.56, "lon": -46.65}, {"lat": -23.57, "lon": -46.64}]},
    )

    assert resp.status_code == 200
    corpo = resp.json()
    assert corpo["available"] is False
    assert corpo["unavailable_reason"]
    assert corpo["distances_m"] == []


def test_table_endpoint_valida_coordenada_fora_de_faixa():
    resp = client.post(
        "/internal/v1/table",
        headers=HEADERS,
        json={"points": [{"lat": 999, "lon": -46.65}, {"lat": -23.57, "lon": -46.64}]},
    )
    assert resp.status_code == 422
