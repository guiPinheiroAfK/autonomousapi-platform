"""
Área do piloto — fonte única da verdade da bbox (spec 02).

Existe como módulo próprio porque DOIS pipelines dependem de cobrir exatamente a mesma
área, e divergir entre eles é um bug silencioso:

- `scripts/import_osm_pilot.py` popula `geo.road_segment` (map matching e score de
  prontidão viária).
- `scripts/prepare_osrm_graph.py` prepara o grafo do OSRM (roteamento).

Se o grafo de roteamento cobrisse uma área maior que o `road_segment`, o produto
devolveria rotas por trechos sobre os quais não temos observação nenhuma — e o peso por
`road_readiness_score` da Fase 3 simplesmente não teria dado ali.
"""

# min_lat, min_lon, max_lat, max_lon — recorte central de São Paulo (~5,5 x 6 km):
# Avenida Paulista, Jardins, Consolação e Paraíso. Escolhido por ser malha densa e
# variada (via expressa, arterial, local) — o suficiente para roteamento dar rota
# alternativa de verdade, não só "siga reto". Trocar quando a área real do piloto for
# definida; os dois scripts acima seguem a mudança automaticamente.
PILOTO_BBOX = (-23.59, -46.68, -23.54, -46.62)

# Tipos de via que entram no grafo/import. Exclui o que carro não trafega — a mesma
# lista serve ao roteamento (perfil `car` do OSRM) e ao map matching de ping veicular.
HIGHWAY_TYPES_VEICULARES = (
    "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street"
    "|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"
)


def bbox_overpass() -> str:
    """Formato que a Overpass API espera num filtro de área: (min_lat,min_lon,max_lat,max_lon)."""
    return "({},{},{},{})".format(*PILOTO_BBOX)
