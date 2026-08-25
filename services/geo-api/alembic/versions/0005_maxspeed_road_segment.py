"""maxspeed_kmh em road_segment (ADR 0019, D3.1 — referência de fluxo livre do score v2)

Revision ID: 0005
Revises: 0004
Create Date: 2026-08-24

"""
from collections.abc import Sequence

from alembic import op

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute("ALTER TABLE geo.road_segment ADD COLUMN maxspeed_kmh double precision")


def downgrade() -> None:
    op.execute("ALTER TABLE geo.road_segment DROP COLUMN IF EXISTS maxspeed_kmh")
