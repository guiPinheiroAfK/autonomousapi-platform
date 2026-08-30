"""Índice para a sessionização horária (auditoria de performance)

`reconstruir_passagens` (app/sessionization.py) roda a cada hora
(ROAD_SEGMENT_PASSAGE_RECALC_INTERVAL_MINUTES=60) e filtra `vehicle_gps_ping` por
`recorded_at >= desde_leitura AND road_segment_id IS NOT NULL` — sem filtro por
`vehicle_id`. O índice único de 0004 (`vehicle_id, recorded_at`) não serve essa query:
`vehicle_id` é a coluna líder e não entra no WHERE, então o planner não consegue usar
esse índice pra restringir a janela de tempo — cada rodada varre a tabela inteira
(a maior do schema, crescendo por frota × 30 dias de retenção, ADR 0009).

Revision ID: 0007
Revises: 0006
Create Date: 2026-08-30

"""
from collections.abc import Sequence

from alembic import op

revision: str = "0007"
down_revision: str | None = "0006"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Parcial: só as linhas com road_segment_id preenchido interessam pra sessionização
    # (o resto é ping sem match espacial, ADR 0019) — índice menor, mais barato de manter
    # a cada ingestão, e casa exatamente com o filtro da query.
    op.execute(
        "CREATE INDEX idx_vehicle_gps_ping_recorded_at_com_segmento "
        "ON geo.vehicle_gps_ping (recorded_at) "
        "WHERE road_segment_id IS NOT NULL"
    )


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS geo.idx_vehicle_gps_ping_recorded_at_com_segmento")
