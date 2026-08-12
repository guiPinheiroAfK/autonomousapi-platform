"""
Map matching (spec 02, Fase 2) — v1, deliberadamente simples.

O spec pede matching "usando lib madura (osrm-match ou equivalente), não escrever isso
do zero". Rodar um servidor OSRM completo (extrair + contrair um grafo de roteamento)
é justificado quando o produto também precisar de ROTEAMENTO — ainda não é o caso.

Para só responder "qual via é essa?" (o que o Fase 2 precisa: virar ping em
observação), uma consulta de vizinho mais próximo no PostGIS já resolve, é o que o
próprio Postgres foi desenhado pra fazer bem (índice GIST), e não é reimplementar
nenhum algoritmo de matching — é usar a ferramenta madura que já está no banco.

Trade-off consciente: isso erra em cruzamentos complexos e vias paralelas muito
próximas (onde OSRM usa continuidade de rota para desambiguar). Documentado aqui para
não virar suposição errada implícita — se o pping mostrar isso como problema real
(muita ambiguidade num piloto real), aí sim vale trocar por integração com OSRM/osrm-match,
não antes.
"""

from uuid import UUID

from sqlalchemy import text
from sqlalchemy.orm import Session

# Ping mais longe que isso de qualquer via conhecida não vira observação — normalmente
# significa via ainda não importada (fora do extrato do piloto) ou ruído de GPS.
DISTANCIA_MAXIMA_METROS = 30


def encontrar_segmento_mais_proximo(db: Session, lat: float, lon: float) -> UUID | None:
    """
    Vizinho mais próximo via operador `<->` do PostGIS (usa o índice GIST em
    `road_segment.geom`), filtrado por distância real em metros (`ST_DWithin` com cast
    para `geography`, que é o que dá distância correta e não distorcida por latitude).
    """
    row = db.execute(
        text(
            """
            SELECT id
            FROM geo.road_segment
            WHERE ST_DWithin(
                geom::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :distancia_maxima
            )
            ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
            LIMIT 1
            """
        ),
        {"lat": lat, "lon": lon, "distancia_maxima": DISTANCIA_MAXIMA_METROS},
    ).first()
    return row[0] if row else None
