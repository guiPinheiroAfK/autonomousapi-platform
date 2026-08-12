"""
Retenção/anonimização de GPS bruto (spec 02, seção de privacidade + ADR 0009).

`vehicle_gps_ping` é dado pessoal enquanto guarda lat/lon vinculado a `vehicle_id` (e,
transitivamente, ao motorista daquela viagem no core-api). O que precisa sobreviver
indefinidamente é só o agregado anônimo (`road_segment_observation`, que já nasce sem
essa referência — ver app/models.py). O ping bruto é expurgado depois de uma janela.
"""

import logging
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete
from sqlalchemy.orm import Session

from .models import VehicleGpsPing

logger = logging.getLogger(__name__)


def purgar_pings_antigos(db: Session, retencao_dias: int) -> int:
    """Apaga `vehicle_gps_ping` mais velho que `retencao_dias`. Retorna quantas linhas."""
    limite = datetime.now(UTC) - timedelta(days=retencao_dias)
    resultado = db.execute(delete(VehicleGpsPing).where(VehicleGpsPing.recorded_at < limite))
    db.commit()
    apagados = resultado.rowcount or 0
    if apagados:
        logger.info(
            "retenção de GPS: %d ping(s) mais velho(s) que %dd apagado(s)", apagados, retencao_dias
        )
    return apagados
