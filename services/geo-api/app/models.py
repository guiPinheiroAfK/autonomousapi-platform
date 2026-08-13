from datetime import datetime
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import BigInteger, DateTime, Float, ForeignKey, Integer, String, func
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


class RoadSegment(Base):
    """
    Trecho de via importado do OpenStreetMap (spec 02). `osm_way_id` é a chave de
    idempotência do import — ver scripts/import_osm_pilot.py.
    """

    __tablename__ = "road_segment"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    osm_way_id: Mapped[int] = mapped_column(BigInteger, nullable=False, unique=True)
    name: Mapped[str | None] = mapped_column(String(200), nullable=True)
    highway_type: Mapped[str] = mapped_column(String(40), nullable=False)
    geom: Mapped[object] = mapped_column(
        Geometry(geometry_type="LINESTRING", srid=4326), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class RoadSegmentObservation(Base):
    """
    Uma passagem observada sobre um `road_segment` (spec 02). Sem referência a
    veículo/motorista de propósito: o que fica registrado é o comportamento da via, não
    quem passou por ela (spec 02, seção de privacidade — aplicado aqui desde a origem,
    não só no agregado final).
    """

    __tablename__ = "road_segment_observation"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    road_segment_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("geo.road_segment.id"), nullable=False, index=True
    )
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    avg_speed_kmh: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class RoadReadinessScore(Base):
    """
    Agregado por `road_segment` (spec 02). Nunca calculado on-the-fly na requisição —
    só o job de agregação (ver app/aggregation.py) escreve aqui.
    """

    __tablename__ = "road_readiness_score"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    road_segment_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("geo.road_segment.id"), nullable=False, unique=True
    )
    score: Mapped[float] = mapped_column(Float, nullable=False)
    observation_count: Mapped[int] = mapped_column(Integer, nullable=False)
    algorithm_version: Mapped[str] = mapped_column(String(20), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
