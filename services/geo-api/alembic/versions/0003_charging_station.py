"""Recarga elétrica (spec 06, item 1): charging_station, charging_station_status

Revision ID: 0003
Revises: 0002
Create Date: 2026-08-16

"""
from collections.abc import Sequence

from alembic import op

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE geo.charging_station (
            id              uuid PRIMARY KEY,
            -- provider + external_id é a chave de idempotência do sync (ver
            -- app/charging_sync.py) — reimportar atualiza em vez de duplicar.
            provider        varchar(40) NOT NULL,
            external_id     varchar(100) NOT NULL,
            name            varchar(200),
            address         varchar(300),
            connector_type  varchar(60),
            power_kw        double precision,
            lat             double precision NOT NULL,
            lon             double precision NOT NULL,
            geom            geometry(Point, 4326) NOT NULL,
            created_at      timestamptz NOT NULL DEFAULT now(),
            updated_at      timestamptz NOT NULL DEFAULT now()
        )
        """
    )
    op.execute(
        "CREATE UNIQUE INDEX idx_charging_station_provider_external "
        "ON geo.charging_station (provider, external_id)"
    )
    op.execute("CREATE INDEX idx_charging_station_geom ON geo.charging_station USING GIST (geom)")

    op.execute(
        """
        CREATE TABLE geo.charging_station_status (
            id           uuid PRIMARY KEY,
            station_id   uuid NOT NULL REFERENCES geo.charging_station(id),
            -- DISPONIVEL | OCUPADO | FORA_DE_SERVICO | DESCONHECIDO (o último é o
            -- fallback gracioso do RNF011 quando não há status recente — nunca omitido).
            status       varchar(20) NOT NULL,
            -- PROVEDOR_EXTERNO | REPORTADO_POR_USUARIO (spec 06: fallback quando o
            -- provedor não expõe tempo real).
            source       varchar(30) NOT NULL,
            observed_at  timestamptz NOT NULL,
            created_at   timestamptz NOT NULL DEFAULT now()
        )
        """
    )
    op.execute(
        "CREATE INDEX idx_charging_station_status_station "
        "ON geo.charging_station_status (station_id, observed_at DESC)"
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS geo.charging_station_status")
    op.execute("DROP TABLE IF EXISTS geo.charging_station")
