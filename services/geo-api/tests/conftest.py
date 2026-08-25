from uuid import uuid4

from geoalchemy2.elements import WKTElement

from app.db import SessionLocal
from app.models import RoadSegment


def criar_segmento_de_teste(nome: str = "Via de teste") -> tuple[RoadSegment, float, float]:
    """
    Um trecho reto e curto perto da origem — não precisa ser via real, só precisa
    existir no banco pro matching por proximidade ter o que encontrar. Devolve
    (segmento, lat, lon) de um ponto sobre ele, pronto pra usar num ping.

    Geometria com offset ÚNICO por chamada (derivado de um uuid4), não fixa em
    (0,0)-(0,0.001): achado real ao investigar um teste instável — um segmento
    zumbi de uma execução anterior que não limpou direito, com geometria IDÊNTICA
    à deste helper, fez o "vizinho mais próximo" empatar; o empate não favorece o
    registro novo, e a observação foi pro segmento errado (o zumbi), fazendo o
    teste falhar de um jeito que parecia bug de matching, não sujeira de dado.
    Offset único elimina a possibilidade de empate mesmo que uma limpeza falhe nesta
    execução.
    """
    offset = (uuid4().int % 100_000) / 1_000_000  # ~0 a 0.1 grau, único por chamada
    db = SessionLocal()
    try:
        segmento = RoadSegment(
            osm_way_id=uuid4().int % 2_000_000_000,  # único por rodada de teste
            name=nome,
            highway_type="residential",
            geom=WKTElement(f"LINESTRING(0 {offset}, 0 {offset + 0.001})", srid=4326),
        )
        db.add(segmento)
        db.commit()
        db.refresh(segmento)
        return segmento, offset + 0.0002, 0.0
    finally:
        db.close()
