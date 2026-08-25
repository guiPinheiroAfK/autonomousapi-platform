"""
Import de extrato OSM da região do piloto (spec 02, DoD Fase 1-2).

Deliberadamente NÃO baixa um .osm.pbf do Brasil inteiro (gigabytes, e o spec pede
"extrato da região do piloto", não o país). Usa a Overpass API para pegar só as vias
(`highway=*`) dentro da bbox do piloto — ver `app/pilot_area.py`, que é a fonte única
compartilhada com o preparo do grafo de roteamento (`scripts/prepare_osrm_graph.py`).

Idempotente: roda de novo e faz upsert por `osm_way_id`, então reimportar (ou trocar a
bbox por uma maior depois) não duplica segmento.

Uso:
    cd services/geo-api
    python scripts/import_osm_pilot.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx
from geoalchemy2.elements import WKTElement

from app.db import SessionLocal
from app.models import RoadSegment
from app.osm_tags import parse_maxspeed_kmh
from app.pilot_area import HIGHWAY_TYPES_VEICULARES, PILOTO_BBOX, bbox_overpass

OVERPASS_URL = "https://overpass-api.de/api/interpreter"


def buscar_vias_overpass() -> list[dict]:
    # Mesma lista de tipos de via do grafo de roteamento (app/pilot_area.py): filtrar
    # por inclusão, e não por exclusão, evita que uma tag nova do OSM entre por engano
    # num pipeline e não no outro.
    query = f"""
    [out:json][timeout:60];
    way["highway"~"^({HIGHWAY_TYPES_VEICULARES})$"]{bbox_overpass()};
    out geom;
    """
    # POST (não GET) e User-Agent identificável: a Overpass API pública devolve 406
    # para requisições sem UA reconhecível — bloqueio anti-bot, não erro de query.
    resp = httpx.post(
        OVERPASS_URL,
        data={"data": query},
        headers={"User-Agent": "autonomousapi-geo-api/0.1 (import-osm-pilot script)"},
        timeout=90,
    )
    resp.raise_for_status()
    return resp.json()["elements"]


def importar() -> None:
    print(f"Área do piloto (min_lat, min_lon, max_lat, max_lon): {PILOTO_BBOX}")
    vias = buscar_vias_overpass()
    print(f"Overpass retornou {len(vias)} via(s) na bbox do piloto.")

    db = SessionLocal()
    try:
        novos, atualizados = 0, 0
        for via in vias:
            geometria = via.get("geometry")
            if not geometria or len(geometria) < 2:
                continue  # via sem geometria utilizável (ex. relação incompleta)

            linha_wkt = "LINESTRING(" + ", ".join(f"{p['lon']} {p['lat']}" for p in geometria) + ")"
            osm_way_id = via["id"]
            tags = via.get("tags", {})
            nome = tags.get("name")
            tipo_via = tags.get("highway", "unknown")
            # Referência de fluxo livre pro score de prontidão viária v2 (ADR 0019,
            # D3.1) — a tag já vinha no payload do Overpass (`out geom` inclui tags) e
            # não era lida antes desta mudança.
            maxspeed_kmh = parse_maxspeed_kmh(tags.get("maxspeed"))

            existente = (
                db.query(RoadSegment).filter(RoadSegment.osm_way_id == osm_way_id).one_or_none()
            )
            if existente:
                existente.name = nome
                existente.highway_type = tipo_via
                existente.maxspeed_kmh = maxspeed_kmh
                existente.geom = WKTElement(linha_wkt, srid=4326)
                atualizados += 1
            else:
                db.add(
                    RoadSegment(
                        osm_way_id=osm_way_id,
                        name=nome,
                        highway_type=tipo_via,
                        maxspeed_kmh=maxspeed_kmh,
                        geom=WKTElement(linha_wkt, srid=4326),
                    )
                )
                novos += 1

        db.commit()
        print(f"Import concluído: {novos} segmento(s) novo(s), {atualizados} atualizado(s).")
    finally:
        db.close()


if __name__ == "__main__":
    importar()
