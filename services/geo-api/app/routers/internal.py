from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, status
from geoalchemy2.elements import WKTElement
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import VehicleGpsPing
from ..security import require_service_token

# Todas as rotas exigem token de serviço. Prefixo /internal deixa explícito que não é público.
router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(require_service_token)],
    tags=["internal"],
)


@router.get("/health")
def health() -> dict[str, str]:
    """Health interno consumido pelo core-api para compor o health agregado."""
    return {"status": "ok", "service": "geo-api"}


class GpsPingIn(BaseModel):
    vehicle_id: UUID
    recorded_at: datetime
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)
    speed: float | None = None
    heading: float | None = None
    accuracy: float | None = None


class GpsPingAccepted(BaseModel):
    id: UUID


@router.post("/gps/pings", status_code=status.HTTP_202_ACCEPTED)
def ingest_ping(ping: GpsPingIn, db: Session = Depends(get_db)) -> GpsPingAccepted:
    """
    Ingestão BRUTA de ping de GPS (Fase 1). Sem map matching ainda — isso é Fase 2.
    Persiste lat/lon e a geometria PostGIS correspondente.
    """
    row = VehicleGpsPing(
        vehicle_id=ping.vehicle_id,
        recorded_at=ping.recorded_at,
        lat=ping.lat,
        lon=ping.lon,
        speed=ping.speed,
        heading=ping.heading,
        accuracy=ping.accuracy,
        geom=WKTElement(f"POINT({ping.lon} {ping.lat})", srid=4326),
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return GpsPingAccepted(id=row.id)
