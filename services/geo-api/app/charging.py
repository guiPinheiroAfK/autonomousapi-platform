"""
Recarga elétrica (spec 06, item 1) — agrega dado de provedor externo (Open Charge Map,
base aberta) em vez de construir rede própria de sensores. Mesmo padrão de credencial já
usado no resto do projeto (EmailSender/PushSender/Stripe no core-api): abstração de
provider com implementação "desabilitada" quando não há chave, e a real quando houver.
Não é uma conta que criamos por conta própria — a chave é do usuário, quando existir.
"""

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass

import httpx

logger = logging.getLogger(__name__)


@dataclass
class EstacaoExterna:
    external_id: str
    name: str | None
    address: str | None
    connector_type: str | None
    power_kw: float | None
    lat: float
    lon: float
    status: str  # DISPONIVEL | OCUPADO | FORA_DE_SERVICO | DESCONHECIDO


class ChargingStationProvider(ABC):
    @abstractmethod
    def buscar_estacoes(self, country_code: str) -> list[EstacaoExterna]:
        """Nunca lança exceção pro chamador (RNF011) — falha vira lista vazia, logada."""


class DisabledChargingStationProvider(ChargingStationProvider):
    """Sem OPEN_CHARGE_MAP_API_KEY configurada — não crio contas em serviço externo por
    conta própria (regra do projeto), então o mecanismo fica pronto e testável agora; só
    a sincronização real espera a chave, exatamente como Stripe/SMTP/push."""

    def buscar_estacoes(self, country_code: str) -> list[EstacaoExterna]:
        logger.info("Open Charge Map desabilitado (sem chave) — nenhuma estação sincronizada")
        return []


# https://openchargemap.org/site/develop/api#/operations/get-poi — StatusType.ID: 50 =
# Operational, 75 = Partly Operational, 100 = Non-Operational. Qualquer outro id (ou
# ausente) vira DESCONHECIDO — não inventamos status que o provedor não confirmou.
_STATUS_MAP = {50: "DISPONIVEL", 75: "OCUPADO", 100: "FORA_DE_SERVICO"}


class OpenChargeMapProvider(ChargingStationProvider):
    BASE_URL = "https://api.openchargemap.io/v3/poi/"

    def __init__(self, api_key: str):
        self._api_key = api_key

    def buscar_estacoes(self, country_code: str) -> list[EstacaoExterna]:
        try:
            resp = httpx.get(
                self.BASE_URL,
                params={
                    "output": "json",
                    "countrycode": country_code,
                    "maxresults": 500,
                    "compact": "true",
                    "verbose": "false",
                },
                headers={"X-API-Key": self._api_key},
                timeout=10.0,
            )
            resp.raise_for_status()
            estacoes = [self._parse(poi) for poi in resp.json()]
            return [e for e in estacoes if e is not None]
        except Exception:
            # Provedor fora do ar/timeout/formato inesperado — RNF011: a leitura já
            # trata "sem estação sincronizada recentemente" como DESCONHECIDO, não erro.
            logger.exception("falha ao consultar Open Charge Map")
            return []

    @staticmethod
    def _parse(poi: dict) -> "EstacaoExterna | None":
        addr = poi.get("AddressInfo") or {}
        lat, lon = addr.get("Latitude"), addr.get("Longitude")
        poi_id = poi.get("ID")
        if lat is None or lon is None or poi_id is None:
            return None
        conexoes = poi.get("Connections") or []
        primeira = conexoes[0] if conexoes else {}
        conector = (primeira.get("ConnectionType") or {}).get("Title")
        status_id = (poi.get("StatusType") or {}).get("ID")
        return EstacaoExterna(
            external_id=str(poi_id),
            name=addr.get("Title"),
            address=addr.get("AddressLine1"),
            connector_type=conector,
            power_kw=primeira.get("PowerKW"),
            lat=lat,
            lon=lon,
            status=_STATUS_MAP.get(status_id, "DESCONHECIDO"),
        )


def obter_provider(api_key: str) -> ChargingStationProvider:
    if api_key:
        return OpenChargeMapProvider(api_key)
    return DisabledChargingStationProvider()
