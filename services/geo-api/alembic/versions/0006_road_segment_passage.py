"""road_segment_passage + road_segment_id em vehicle_gps_ping + score por time_bucket
(ADR 0019, Decisão 1-3)

Revision ID: 0006
Revises: 0005
Create Date: 2026-08-24

"""
from collections.abc import Sequence

from alembic import op

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Segmento casado na ingestão, gravado no próprio ping — a sessionização (D1.1)
    # reaproveita o match já feito em vez de refazer a consulta espacial por ping.
    # ON DELETE SET NULL: remover/reimportar um road_segment não pode travar nem
    # cascatear em cima do ping bruto — a coluna é só um atalho de leitura, o ping
    # continua válido (e sujeito à retenção normal, ADR 0009) mesmo sem o segmento.
    op.execute(
        "ALTER TABLE geo.vehicle_gps_ping "
        "ADD COLUMN road_segment_id uuid REFERENCES geo.road_segment(id) ON DELETE SET NULL"
    )
    op.execute(
        "CREATE INDEX idx_vehicle_gps_ping_road_segment "
        "ON geo.vehicle_gps_ping (road_segment_id)"
    )

    op.execute(
        """
        CREATE TABLE geo.road_segment_passage (
            id               uuid PRIMARY KEY,
            road_segment_id  uuid NOT NULL REFERENCES geo.road_segment(id) ON DELETE CASCADE,
            entered_at       timestamptz NOT NULL,
            exited_at        timestamptz NOT NULL,
            avg_speed_kmh    double precision,
            min_speed_kmh    double precision,
            ping_count       integer NOT NULL,
            created_at       timestamptz NOT NULL DEFAULT now()
        )
        """
    )
    op.execute(
        "CREATE INDEX idx_road_segment_passage_segment_entered "
        "ON geo.road_segment_passage (road_segment_id, entered_at)"
    )
    # Usado pelo DELETE de reconstrução da janela (app/sessionization.py) — sem esse
    # índice, apagar "tudo que entrou depois de B" varre a tabela inteira toda hora.
    op.execute(
        "CREATE INDEX idx_road_segment_passage_entered "
        "ON geo.road_segment_passage (entered_at)"
    )

    # road_readiness_score passa a ser por (segmento, faixa de tempo) — score v2
    # (ADR 0019, Decisão 2/3). Linhas existentes (só o v1 rodou até aqui) recebem
    # time_bucket='GLOBAL' e viram a linha de base histórica (D4) — nunca apagadas.
    op.execute(
        "ALTER TABLE geo.road_readiness_score "
        "ADD COLUMN time_bucket varchar(30) NOT NULL DEFAULT 'GLOBAL'"
    )
    op.execute("ALTER TABLE geo.road_readiness_score ADD COLUMN confidence double precision")
    # Célula sem passagem com velocidade tem que poder ficar sem score ("não sei", não
    # "via ruim" — D3.2). O v1 sempre preenche; só o v2 usa o nulo.
    op.execute("ALTER TABLE geo.road_readiness_score ALTER COLUMN score DROP NOT NULL")

    op.execute(
        "ALTER TABLE geo.road_readiness_score "
        "DROP CONSTRAINT road_readiness_score_road_segment_id_key"
    )
    op.execute(
        "ALTER TABLE geo.road_readiness_score "
        "ADD CONSTRAINT uq_road_readiness_segment_bucket UNIQUE (road_segment_id, time_bucket)"
    )


def downgrade() -> None:
    op.execute(
        "ALTER TABLE geo.road_readiness_score "
        "DROP CONSTRAINT IF EXISTS uq_road_readiness_segment_bucket"
    )
    op.execute(
        "ALTER TABLE geo.road_readiness_score "
        "ADD CONSTRAINT road_readiness_score_road_segment_id_key UNIQUE (road_segment_id)"
    )
    op.execute("ALTER TABLE geo.road_readiness_score ALTER COLUMN score SET NOT NULL")
    op.execute("ALTER TABLE geo.road_readiness_score DROP COLUMN IF EXISTS confidence")
    op.execute("ALTER TABLE geo.road_readiness_score DROP COLUMN IF EXISTS time_bucket")

    op.execute("DROP TABLE IF EXISTS geo.road_segment_passage")

    op.execute("ALTER TABLE geo.vehicle_gps_ping DROP COLUMN IF EXISTS road_segment_id")
