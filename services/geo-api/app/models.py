from datetime import datetime
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import (
    BigInteger,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
    func,
)
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
    # Segmento casado na ingestão (app/matching.py), gravado aqui pra não recalcular o
    # match na sessionização (ADR 0019, Decisão 1) — reaproveita o que a ingestão já fez
    # em vez de rodar a mesma consulta espacial de novo. Nulo = ping fora da malha
    # importada. Não fere a anonimização estrutural da ADR 0009: o bruto já tem
    # vehicle_id, essa coluna não adiciona identificabilidade nova; o que nunca carrega
    # vehicle_id é o agregado (road_segment_observation / road_segment_passage).
    road_segment_id: Mapped[UUID | None] = mapped_column(
        PgUUID(as_uuid=True),
        ForeignKey("geo.road_segment.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
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
    # Velocidade de projeto da via, quando o OSM declara (tag `maxspeed`) — parseada em
    # scripts/import_osm_pilot.py (parse_maxspeed_kmh). Referência primária de "fluxo
    # livre" pro score de prontidão viária v2 (ADR 0019, D3.1); nulo quando a tag não
    # existe ou não é parseável (comum no Brasil) — quem consome cai pro padrão por
    # highway_type nesse caso, nunca assume um número.
    maxspeed_kmh: Mapped[float | None] = mapped_column(Float, nullable=True)
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


class RoadSegmentPassage(Base):
    """
    Uma PASSAGEM (corrida contígua de pings do mesmo veículo sobre o mesmo
    `road_segment`) — a entidade que a spec 02 sempre descreveu para
    `road_segment_observation`, mas que a ingestão gravava por ping, não por passagem
    (achado da auditoria de cleanup, ADR 0019). Gerada pelo job de sessionização
    (app/sessionization.py), não na ingestão — precisa ver a corrida inteira antes de
    fechar a passagem, e precisa ser reprocessável (ver docstring de
    `reconstruir_passagens` para o esquema de apagar-e-reconstruir por janela).

    Sem `vehicle_id` — mesma anonimização estrutural desde a origem de
    `road_segment_observation` (ADR 0009): o que fica registrado é o comportamento da
    via, nunca quem passou por ela.
    """

    __tablename__ = "road_segment_passage"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    road_segment_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True),
        ForeignKey("geo.road_segment.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    entered_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    exited_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    avg_speed_kmh: Mapped[float | None] = mapped_column(Float, nullable=True)
    min_speed_kmh: Mapped[float | None] = mapped_column(Float, nullable=True)
    ping_count: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class RoadReadinessScore(Base):
    """
    Agregado por (`road_segment`, `time_bucket`) — spec 02 + ADR 0019 (v2: score por
    faixa de tempo, não um número único por segmento). Nunca calculado on-the-fly na
    requisição — só o job de agregação (ver app/aggregation.py) escreve aqui.

    `time_bucket = 'GLOBAL'` identifica as linhas do algoritmo v1 (contagem de
    observação por ping, sem faixa de tempo) — mantidas como linha de base histórica
    quando o v2 assume o scheduler, nunca apagadas (ADR 0019, D4).
    """

    __tablename__ = "road_readiness_score"
    __table_args__ = (
        UniqueConstraint("road_segment_id", "time_bucket", name="uq_road_readiness_segment_bucket"),
        {"schema": "geo"},
    )

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    road_segment_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("geo.road_segment.id"), nullable=False
    )
    # 'GLOBAL' pro v1 (sem faixa de tempo); ex. 'UTIL_PICO_MANHA' pro v2 (ADR 0019, D2.1).
    time_bucket: Mapped[str] = mapped_column(String(30), nullable=False, default="GLOBAL")
    # Nulo quando a célula não tem nenhuma passagem com velocidade — "não sei", não
    # "via ruim" (ADR 0019, D3.2). O v1 sempre preenche (é só contagem normalizada).
    score: Mapped[float | None] = mapped_column(Float, nullable=True)
    observation_count: Mapped[int] = mapped_column(Integer, nullable=False)
    # Só o v2 preenche (ADR 0019, D3.3) — amostra × recência, nunca embutido no score.
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    algorithm_version: Mapped[str] = mapped_column(String(20), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class ChargingStation(Base):
    """
    Ponto de recarga elétrica agregado de provedor externo (spec 06, item 1). Não
    construímos rede própria de sensores — normalizamos dado de terceiro (Open Charge
    Map hoje). `provider` + `external_id` é a chave de idempotência do sync.
    """

    __tablename__ = "charging_station"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    provider: Mapped[str] = mapped_column(String(40), nullable=False)
    external_id: Mapped[str] = mapped_column(String(100), nullable=False)
    name: Mapped[str | None] = mapped_column(String(200), nullable=True)
    address: Mapped[str | None] = mapped_column(String(300), nullable=True)
    connector_type: Mapped[str | None] = mapped_column(String(60), nullable=True)
    power_kw: Mapped[float | None] = mapped_column(Float, nullable=True)
    lat: Mapped[float] = mapped_column(Float, nullable=False)
    lon: Mapped[float] = mapped_column(Float, nullable=False)
    geom: Mapped[object] = mapped_column(
        Geometry(geometry_type="POINT", srid=4326), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class ChargingStationStatus(Base):
    """
    Status observado de uma estação (spec 06, item 1). Fonte pode ser o provedor externo
    ou "reportado pelo motorista" (fallback quando o provedor não tem tempo real) — o
    campo `source` distingue as duas. Sem status recente = RNF011 (leitura devolve
    "DESCONHECIDO", ver routers/internal.py), não é campo obrigatório na leitura.
    """

    __tablename__ = "charging_station_status"
    __table_args__ = {"schema": "geo"}

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    station_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("geo.charging_station.id"), nullable=False, index=True
    )
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    source: Mapped[str] = mapped_column(String(30), nullable=False)
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
