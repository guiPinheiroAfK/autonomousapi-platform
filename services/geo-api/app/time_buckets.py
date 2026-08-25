"""
Faixas de tempo do score de prontidão viária v2 (ADR 0019, Decisão 2, D2.1).

"Pico da manhã" só significa algo depois de converter de UTC (`recorded_at`/
`entered_at`, sempre timestamptz) pro fuso da área do piloto — sem isso a faixa não
corresponde a nada que um humano reconheceria como "hora do dia". Módulo isolado (sem
I/O) pra ser fácil de testar sozinho.
"""

from datetime import datetime
from zoneinfo import ZoneInfo

# (hora_inicio, hora_fim_exclusiva, nome) — cobre 0-23 sem lacuna nem sobreposição.
_FAIXAS = (
    (0, 6, "MADRUGADA"),
    (6, 10, "PICO_MANHA"),
    (10, 17, "ENTREPICO"),
    (17, 21, "PICO_TARDE"),
    (21, 24, "NOITE"),
)


def time_bucket(instante: datetime, timezone: str) -> str:
    """
    'UTIL_PICO_MANHA', 'FDS_ENTREPICO', etc. Feriado não é tratado nesta versão — cai
    em UTIL e polui a média; documentado (ADR 0019, D2.1), não silencioso.
    """
    local = instante.astimezone(ZoneInfo(timezone))
    tipo_dia = "UTIL" if local.weekday() < 5 else "FDS"
    for inicio, fim, nome in _FAIXAS:
        if inicio <= local.hour < fim:
            return f"{tipo_dia}_{nome}"
    raise AssertionError(f"hora {local.hour} não caiu em nenhuma faixa — _FAIXAS mal configurada")
