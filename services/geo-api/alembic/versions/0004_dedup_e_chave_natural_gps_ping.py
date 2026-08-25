"""Dedup + chave natural em vehicle_gps_ping (ADR 0019, pré-requisito B)

Fecha a dívida registrada na ADR 0006: um lote reenviado pelo app (retry parcial da
fila offline, apps/mobile/src/offline/pingQueue.ts) gera ping duplicado — tolerável
para ingestão bruta, mas a agregação de road_readiness_score (ADR 0019) conta
observação por ping, então duplicata infla o score diretamente.

Revision ID: 0004
Revises: 0003
Create Date: 2026-08-24

"""
from collections.abc import Sequence

from alembic import op

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Dedup antes da constraint — qualquer ambiente que já rodou o app tem duplicata
    # hoje (ADR 0006), a constraint falharia ao ser criada sem isso. Mantém a linha
    # de menor ctid por par (vehicle_id, recorded_at); a escolha entre duplicatas é
    # arbitrária de propósito, os campos além da chave já eram idênticos por vir do
    # mesmo ping reenviado.
    op.execute(
        """
        DELETE FROM geo.vehicle_gps_ping a
        USING geo.vehicle_gps_ping b
        WHERE a.vehicle_id = b.vehicle_id
          AND a.recorded_at = b.recorded_at
          AND a.ctid > b.ctid
        """
    )
    op.execute(
        "ALTER TABLE geo.vehicle_gps_ping "
        "ADD CONSTRAINT uq_vehicle_gps_ping_vehicle_recorded_at "
        "UNIQUE (vehicle_id, recorded_at)"
    )


def downgrade() -> None:
    op.execute(
        "ALTER TABLE geo.vehicle_gps_ping "
        "DROP CONSTRAINT IF EXISTS uq_vehicle_gps_ping_vehicle_recorded_at"
    )
