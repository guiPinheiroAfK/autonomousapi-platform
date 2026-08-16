"""
Roteamento ponto-a-ponto via OSRM (spec 02, "Estratégia de roteamento").

Spec: "Fase 1-2: usar OSRM ou GraphHopper (open source) [...] sem lógica própria de
otimização". Aqui não há algoritmo nosso nenhum — só tradução do contrato do OSRM para o
nosso, mais degradação explícita quando o motor não está no ar.

O peso por `road_readiness_score` (Fase 3) entra depois, e entra no OSRM (via
`osrm-customize`), não aqui: continuar usando o motor open source como base é decisão do
próprio spec, não reimplementar roteamento por cima dele.
"""

import logging
from dataclasses import dataclass, field

import httpx

logger = logging.getLogger(__name__)

# Raio máximo (metros) para "grudar" um ponto pedido na via mais próxima do grafo.
#
# Sem esse limite o OSRM aceita QUALQUER coordenada: pedir rota para Londres devolve, com
# `code: Ok`, uma rota até a via mais próxima da borda da área do piloto — resposta
# silenciosamente errada, que é pior que um erro. Com o limite, o OSRM responde
# `NoSegment` e a tela diz que o ponto está fora da área coberta.
#
# 1 km é folgado o bastante para endereço impreciso ou ponto no meio de uma quadra, e
# apertado o bastante para rejeitar qualquer coisa fora da área do piloto.
RAIO_MAXIMO_SNAP_METROS = 1000


@dataclass
class RouteStep:
    instruction_type: str
    modifier: str | None
    name: str | None
    distance_m: float
    duration_s: float


@dataclass
class Route:
    available: bool
    distance_m: float | None = None
    duration_s: float | None = None
    # [[lon, lat], ...] — ordem do GeoJSON, não invertida para lat/lon de propósito:
    # é o que qualquer biblioteca de mapa espera receber.
    geometry: list[list[float]] = field(default_factory=list)
    steps: list[RouteStep] = field(default_factory=list)
    unavailable_reason: str | None = None

    @staticmethod
    def indisponivel(motivo: str) -> "Route":
        return Route(available=False, unavailable_reason=motivo)


class OsrmRoutingClient:
    """
    Cliente do `osrm-routed`. Nunca lança para o chamador: motor fora do ar, timeout ou
    par de coordenadas sem rota viram um `Route` com `available=False` e um motivo legível
    — a tela do gestor não pode quebrar porque um serviço de infraestrutura caiu.
    """

    def __init__(self, base_url: str, timeout_seconds: float = 5.0):
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout_seconds

    def rota(self, from_lat: float, from_lon: float, to_lat: float, to_lon: float) -> Route:
        if not self._base_url:
            return Route.indisponivel("Motor de roteamento não configurado.")

        # OSRM recebe lon,lat (ordem GeoJSON), não lat,lon — trocar aqui é o erro clássico
        # de integração com OSRM, e ele não acusa: devolve rota errada do outro lado do mundo.
        coords = f"{from_lon},{from_lat};{to_lon},{to_lat}"
        url = f"{self._base_url}/route/v1/driving/{coords}"

        try:
            resp = httpx.get(
                url,
                params={
                    "overview": "full",
                    "geometries": "geojson",
                    "steps": "true",
                    "radiuses": f"{RAIO_MAXIMO_SNAP_METROS};{RAIO_MAXIMO_SNAP_METROS}",
                },
                timeout=self._timeout,
            )
            corpo = resp.json()
        except Exception:
            logger.exception("falha ao consultar o OSRM")
            return Route.indisponivel("Motor de roteamento indisponível no momento.")

        # Sem raise_for_status de propósito: o OSRM responde 400 para NoSegment/NoRoute,
        # que são RESULTADOS legítimos ("esse ponto está fora da área"), não falha de
        # infraestrutura. Tratar como exceção daria a mensagem errada ao usuário.
        codigo = corpo.get("code")
        if codigo != "Ok" or not corpo.get("routes"):
            return Route.indisponivel(_motivo_para(codigo, resp.status_code))

        return _para_route(corpo["routes"][0])


def _motivo_para(codigo: str | None, http_status: int) -> str:
    """
    Traduz o `code` do OSRM para algo que o gestor entenda. A distinção importa: "ponto
    fora da área" é problema do que ele digitou, "sem ligação viária" é problema do mapa,
    e qualquer outra coisa é problema nosso — três ações diferentes para quem lê.
    """
    if codigo == "NoSegment":
        return "Origem ou destino fora da área coberta pelo mapa do piloto."
    if codigo == "NoRoute":
        return "Não há ligação viária entre os dois pontos no mapa do piloto."
    logger.warning("OSRM devolveu code=%s (HTTP %s)", codigo, http_status)
    return "Não foi possível calcular a rota."


def _para_route(rota_osrm: dict) -> Route:
    pernas = rota_osrm.get("legs") or []
    passos = [
        RouteStep(
            instruction_type=(passo.get("maneuver") or {}).get("type", "unknown"),
            modifier=(passo.get("maneuver") or {}).get("modifier"),
            # O OSRM devolve "" (não null) para trecho sem nome — normalizar aqui evita
            # o front ter que decidir se "" é nome de rua.
            name=passo.get("name") or None,
            distance_m=passo.get("distance", 0.0),
            duration_s=passo.get("duration", 0.0),
        )
        for perna in pernas
        for passo in (perna.get("steps") or [])
    ]

    return Route(
        available=True,
        distance_m=rota_osrm.get("distance"),
        duration_s=rota_osrm.get("duration"),
        geometry=(rota_osrm.get("geometry") or {}).get("coordinates", []),
        steps=passos,
    )
