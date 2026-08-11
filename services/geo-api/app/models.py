from datetime import datetime
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import DateTime, Float, func
from sqlalchemy.dialects.postgresql import UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base


class VehicleGpsPing(Base):
    """
    Ping bruto de GPS enviado pelo app do motorista (spec 02).
    Fase 1: apenas ingestão bruta (sem map matching / processamento).

    `vehicle_id` é referência por UUID ao veículo do schema `core` — SEM foreign key
    no banco (ADR 0004); a validação de existência é responsabilidade do core-api.
    """

    __tablename__ = "vehicle_gps_ping"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    vehicle_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False, index=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    lat: Mapped[float] = mapped_column(Float, nullable=False)
    lon: Mapped[float] = mapped_column(Float, nullable=False)
    speed: Mapped[float | None] = mapped_column(Float, nullable=True)
    heading: Mapped[float | None] = mapped_column(Float, nullable=True)
    accuracy: Mapped[float | None] = mapped_column(Float, nullable=True)
    # PostGIS desde o início (princípio da spec 00): ponto WGS84.
    geom: Mapped[object] = mapped_column(
        Geometry(geometry_type="POINT", srid=4326), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
