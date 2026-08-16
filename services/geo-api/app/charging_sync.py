"""
Sincronização de estações de recarga (spec 06, item 1) — job periódico, nunca calculado
na requisição (mesmo padrão do road_readiness_score, ver app/aggregation.py).
"""

import logging
from datetime import UTC, datetime
from uuid import uuid4

from geoalchemy2.elements import WKTElement
from sqlalchemy import select
from sqlalchemy.orm import Session

from .charging import ChargingStationProvider
from .models import ChargingStation, ChargingStationStatus

logger = logging.getLogger(__name__)

PROVIDER_NAME = "OPEN_CHARGE_MAP"


def sincronizar_estacoes(db: Session, provider: ChargingStationProvider, country_code: str) -> int:
    """Upsert por (provider, external_id) — idempotente, roda quantas vezes quiser sem
    duplicar. Cada rodada também grava um novo status (histórico), não sobrescreve."""
    estacoes = provider.buscar_estacoes(country_code)

    for estacao in estacoes:
        existente = db.execute(
            select(ChargingStation).where(
                ChargingStation.provider == PROVIDER_NAME,
                ChargingStation.external_id == estacao.external_id,
            )
        ).scalar_one_or_none()

        ponto = WKTElement(f"POINT({estacao.lon} {estacao.lat})", srid=4326)

        if existente:
            existente.name = estacao.name
            existente.address = estacao.address
            existente.connector_type = estacao.connector_type
            existente.power_kw = estacao.power_kw
            existente.lat = estacao.lat
            existente.lon = estacao.lon
            existente.geom = ponto
            station = existente
        else:
            station = ChargingStation(
                id=uuid4(),
                provider=PROVIDER_NAME,
                external_id=estacao.external_id,
                name=estacao.name,
                address=estacao.address,
                connector_type=estacao.connector_type,
                power_kw=estacao.power_kw,
                lat=estacao.lat,
                lon=estacao.lon,
                geom=ponto,
            )
            db.add(station)
            db.flush()  # garante station.id preenchido antes do status referenciar

        db.add(
            ChargingStationStatus(
                id=uuid4(),
                station_id=station.id,
                status=estacao.status,
                source="PROVEDOR_EXTERNO",
                observed_at=datetime.now(UTC),
            )
        )

    db.commit()
    logger.info("sincronização de recarga: %d estação(ões) processada(s)", len(estacoes))
    return len(estacoes)
