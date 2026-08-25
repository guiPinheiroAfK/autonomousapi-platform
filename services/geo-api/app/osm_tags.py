"""
Parsing de tags do OSM que precisam de normalização antes de virar coluna (ADR 0019).

`maxspeed` é texto livre por convenção do OSM — "50", "50 mph", "signals", "30;50",
"BR:urban" são todos valores reais que aparecem no extrato. Isolado num módulo próprio
porque é regra de negócio pura (sem I/O), fácil de testar sozinha e reaproveitável fora
do script de import se algum dia for preciso reprocessar sem rebaixar do OSM de novo.
"""

import re

_MPH_PATTERN = re.compile(r"^(\d+(?:\.\d+)?)\s*mph$", re.IGNORECASE)
_KMH_PATTERN = re.compile(r"^(\d+(?:\.\d+)?)(?:\s*km/?h)?$", re.IGNORECASE)
_KM_POR_MILHA = 1.60934


def parse_maxspeed_kmh(raw: str | None) -> float | None:
    """
    Converte o valor bruto da tag `maxspeed` em km/h. `None` para qualquer coisa que
    não seja um número simples — palavras-chave (`signals`, `none`, `walk`,
    `variable`), listas (`30;50`, `50|60`) e defaults por país (`BR:urban`) não têm
    conversão segura; melhor ficar sem dado do que inventar um número.
    """
    if not raw:
        return None
    valor = raw.strip()

    mph = _MPH_PATTERN.match(valor)
    if mph:
        return round(float(mph.group(1)) * _KM_POR_MILHA, 1)

    kmh = _KMH_PATTERN.match(valor)
    if kmh:
        return float(kmh.group(1))

    return None
