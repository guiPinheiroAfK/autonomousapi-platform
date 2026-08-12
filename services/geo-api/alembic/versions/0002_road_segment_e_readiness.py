"""Fase 2 (spec 02): road_segment, road_segment_observation, road_readiness_score

Revision ID: 0002
Revises: 0001
Create Date: 2026-08-12

"""
from collections.abc import Sequence

from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE geo.road_segment (
            id             uuid PRIMARY KEY,
            -- ID da via no OpenStreetMap. Chave de idempotência do import: reimportar o
            -- mesmo extrato atualiza em vez de duplicar (ver scripts/import_osm_pilot.py).
            osm_way_id     bigint NOT NULL,
            name           varchar(200),
            highway_type   varchar(40) NOT NULL,
            geom           geometry(LineString, 4326) NOT NULL,
            created_at     timestamptz NOT NULL DEFAULT now()
        )
        """
    )
    op.execute("CREATE UNIQUE INDEX idx_road_segment_osm_way ON geo.road_segment (osm_way_id)")
    op.execute("CREATE INDEX idx_road_segment_geom ON geo.road_segment USING GIST (geom)")

    op.execute(
        """
        CREATE TABLE geo.road_segment_observation (
            id               uuid PRIMARY KEY,
            road_segment_id  uuid NOT NULL REFERENCES geo.road_segment(id),
            -- SEM vehicle_id/motorista aqui de propósito (spec 02, seção de privacidade):
            -- o que fica é o comportamento da via, não quem passou por ela.
            observed_at      timestamptz NOT NULL,
            avg_speed_kmh    double precision,
            created_at       timestamptz NOT NULL DEFAULT now()
        )
        """
    )
    op.execute(
        "CREATE INDEX idx_road_segment_observation_segment "
        "ON geo.road_segment_observation (road_segment_id)"
    )
    op.execute(
        "CREATE INDEX idx_road_segment_observation_observed_at "
        "ON geo.road_segment_observation (observed_at)"
    )

    op.execute(
        """
        CREATE TABLE geo.road_readiness_score (
            id                 uuid PRIMARY KEY,
            road_segment_id    uuid NOT NULL UNIQUE REFERENCES geo.road_segment(id),
            score              double precision NOT NULL,
            observation_count  integer NOT NULL,
            algorithm_version  varchar(20) NOT NULL,
            updated_at         timestamptz NOT NULL DEFAULT now()
        )
        """
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS geo.road_readiness_score")
    op.execute("DROP TABLE IF EXISTS geo.road_segment_observation")
    op.execute("DROP TABLE IF EXISTS geo.road_segment")
