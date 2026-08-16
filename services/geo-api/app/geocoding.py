"""
Geocodificação (endereço -> coordenada) para a área do piloto.

Não é uma feature pedida por nome no spec, mas sem ela o roteamento não é utilizável por
uma pessoa: a spec 02 pede roteamento "exposto no web/mobile", e ninguém digita lat/lon.

Usa Nominatim (o geocoder do próprio OpenStreetMap) — mesma base de dado do grafo de
roteamento, então endereço encontrado aqui é endereço que o OSRM sabe rotear. Sem chave,
mas COM política de uso a respeitar: User-Agent identificável e volume baixo. A URL é
configurável justamente para trocar por uma instância própria quando o volume crescer.

Busca limitada à bbox do piloto (`bounded=1`): além de deixar o resultado relevante,
impede de vez o caso "digitei um endereço de outro estado e recebi uma rota estranha".
"""

import logging
from dataclasses import dataclass

import httpx

from .pilot_area import PILOTO_BBOX

logger = logging.getLogger(__name__)

MAX_RESULTADOS = 5


@dataclass
class Lugar:
    display_name: str
    lat: float
    lon: float


class NominatimGeocoder:
    """Nunca lança para o chamador: falha de rede/timeout vira lista vazia, logada."""

    def __init__(self, base_url: str, timeout_seconds: float = 5.0):
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout_seconds

    def buscar(self, consulta: str) -> list[Lugar]:
        if not self._base_url or not consulta.strip():
            return []

        min_lat, min_lon, max_lat, max_lon = PILOTO_BBOX
        try:
            resp = httpx.get(
                f"{self._base_url}/search",
                params={
                    "q": consulta,
                    "format": "jsonv2",
                    "limit": MAX_RESULTADOS,
                    # viewbox do Nominatim é left,top,right,bottom = min_lon,max_lat,max_lon,min_lat
                    "viewbox": f"{min_lon},{max_lat},{max_lon},{min_lat}",
                    "bounded": 1,
                },
                headers={"User-Agent": "autonomousapi-geo-api/0.1 (fleet routing)"},
                timeout=self._timeout,
            )
            resp.raise_for_status()
            resultados = resp.json()
        except Exception:
            logger.exception("falha ao consultar o Nominatim")
            return []

        lugares = []
        for item in resultados:
            try:
                lugares.append(
                    Lugar(
                        display_name=item["display_name"],
                        lat=float(item["lat"]),
                        lon=float(item["lon"]),
                    )
                )
            except (KeyError, TypeError, ValueError):
                continue  # item malformado não invalida os outros
        return lugares
