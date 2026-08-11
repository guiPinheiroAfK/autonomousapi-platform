"""bootstrap do schema geo: PostGIS + vehicle_gps_ping

Revision ID: 0001
Revises:
Create Date: 2026-08-11

"""
from collections.abc import Sequence

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # geo-api é dono do schema `geo` e da extensão PostGIS (ADR 0004). Idempotente.
    op.execute("CREATE SCHEMA IF NOT EXISTS geo")
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis")

    op.execute(
        """
        CREATE TABLE geo.vehicle_gps_ping (
            id          uuid PRIMARY KEY,
            -- referência ao veículo do schema core por UUID, SEM FK (ADR 0004)
            vehicle_id  uuid NOT NULL,
            recorded_at timestamptz NOT NULL,
            lat         double precision NOT NULL,
            lon         double precision NOT NULL,
            speed       double precision,
            heading     double precision,
            accuracy    double precision,
            geom        geometry(Point, 4326) NOT NULL,
            created_at  timestamptz NOT NULL DEFAULT now()
        )
        """
    )
    op.execute("CREATE INDEX idx_vehicle_gps_ping_vehicle ON geo.vehicle_gps_ping (vehicle_id)")
    op.execute(
        "CREATE INDEX idx_vehicle_gps_ping_geom ON geo.vehicle_gps_ping USING GIST (geom)"
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS geo.vehicle_gps_ping")
    # Não removemos a extensão PostGIS nem o schema no downgrade (podem ser usados por outras migs).
