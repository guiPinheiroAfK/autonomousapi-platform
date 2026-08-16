"""
Jobs periódicos in-process (spec 02: agregação e retenção precisam ser "job assíncrono
(batch ou stream)", nunca calculados na requisição).

Deliberadamente sem fila/worker separado (Celery, etc.) — no volume de piloto (spec 05,
Fase 1-2), um scheduler dentro do próprio processo FastAPI já cumpre "job periódico" sem
introduzir infraestrutura nova. Mesmo raciocínio da ADR 0006 (Kafka descartado por
agora): construir o que o estágio atual pede, não o que a escala futura talvez peça.
"""

import logging

from apscheduler.schedulers.background import BackgroundScheduler

from .aggregation import recalcular_road_readiness
from .charging import obter_provider
from .charging_sync import sincronizar_estacoes
from .config import settings
from .db import SessionLocal
from .retention import purgar_pings_antigos

logger = logging.getLogger(__name__)

scheduler = BackgroundScheduler(timezone="UTC")


def _job_recalcular_road_readiness() -> None:
    db = SessionLocal()
    try:
        recalcular_road_readiness(db)
    except Exception:
        logger.exception("falha ao recalcular road_readiness")
    finally:
        db.close()


def _job_purgar_pings_antigos() -> None:
    db = SessionLocal()
    try:
        purgar_pings_antigos(db, settings.gps_retention_days)
    except Exception:
        logger.exception("falha ao purgar pings antigos")
    finally:
        db.close()


def _job_sincronizar_estacoes_recarga() -> None:
    db = SessionLocal()
    try:
        provider = obter_provider(settings.open_charge_map_api_key)
        sincronizar_estacoes(db, provider, settings.charging_sync_country_code)
    except Exception:
        logger.exception("falha ao sincronizar estações de recarga")
    finally:
        db.close()


def iniciar_scheduler() -> None:
    scheduler.add_job(
        _job_recalcular_road_readiness,
        "interval",
        minutes=settings.road_readiness_recalc_interval_minutes,
        id="recalcular_road_readiness",
        replace_existing=True,
    )
    scheduler.add_job(
        _job_purgar_pings_antigos,
        "interval",
        hours=24,
        id="purgar_pings_antigos",
        replace_existing=True,
    )
    scheduler.add_job(
        _job_sincronizar_estacoes_recarga,
        "interval",
        hours=24,
        id="sincronizar_estacoes_recarga",
        replace_existing=True,
    )
    scheduler.start()


def parar_scheduler() -> None:
    scheduler.shutdown(wait=False)
