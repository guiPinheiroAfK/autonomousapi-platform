from datetime import datetime
from zoneinfo import ZoneInfo

from app.time_buckets import time_bucket

TZ = "America/Sao_Paulo"


def test_madrugada_dia_util():
    # 2026-08-24 é segunda-feira.
    instante = datetime(2026, 8, 24, 3, 0, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "UTIL_MADRUGADA"


def test_pico_manha_dia_util():
    instante = datetime(2026, 8, 24, 7, 30, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "UTIL_PICO_MANHA"


def test_entrepico_dia_util():
    instante = datetime(2026, 8, 24, 12, 0, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "UTIL_ENTREPICO"


def test_pico_tarde_dia_util():
    instante = datetime(2026, 8, 24, 18, 0, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "UTIL_PICO_TARDE"


def test_noite_dia_util():
    instante = datetime(2026, 8, 24, 22, 0, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "UTIL_NOITE"


def test_fim_de_semana():
    # 2026-08-22 é sábado.
    instante = datetime(2026, 8, 22, 10, 0, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "FDS_ENTREPICO"

    # 2026-08-23 é domingo.
    instante = datetime(2026, 8, 23, 10, 0, tzinfo=ZoneInfo(TZ))
    assert time_bucket(instante, TZ) == "FDS_ENTREPICO"


def test_fronteiras_das_faixas_sao_inclusivas_no_inicio():
    tz = ZoneInfo(TZ)
    assert time_bucket(datetime(2026, 8, 24, 6, 0, tzinfo=tz), TZ) == "UTIL_PICO_MANHA"
    assert time_bucket(datetime(2026, 8, 24, 9, 59, tzinfo=tz), TZ) == "UTIL_PICO_MANHA"
    assert time_bucket(datetime(2026, 8, 24, 10, 0, tzinfo=tz), TZ) == "UTIL_ENTREPICO"


def test_conversao_de_fuso_muda_a_faixa():
    """Um instante que é madrugada em UTC pode já ser outra faixa no fuso do piloto —
    é exatamente o bug que a conversão existe para evitar (ADR 0019, D2.1)."""
    # 06:00 UTC = 03:00 em America/Sao_Paulo (UTC-3) — madrugada local, não pico.
    instante_utc = datetime(2026, 8, 24, 6, 0, tzinfo=ZoneInfo("UTC"))
    assert time_bucket(instante_utc, TZ) == "UTIL_MADRUGADA"
