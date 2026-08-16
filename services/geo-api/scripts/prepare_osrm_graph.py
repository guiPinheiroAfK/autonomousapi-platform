"""
Prepara o grafo de roteamento do OSRM para a área do piloto (spec 02, DoD Fase 1-2).

Por que Overpass e não um .osm.pbf da Geofabrik: o spec pede "extrato do OpenStreetMap"
para a região do piloto, não o Brasil inteiro. O extrato de São Paulo já passa de meio
giga e leva dezenas de minutos de pré-processamento — inviável de pedir a quem só clonou
o repositório. A Overpass devolve OSM XML válido, que o `osrm-extract` lê nativamente, e
a área do piloto sai em poucos MB e ~30s de pipeline inteiro.

Roda o pipeline MLD (Multi-Level Dijkstra), que é o recomendado para grafos que mudam:
`osrm-extract` -> `osrm-partition` -> `osrm-customize`. O CH (contraction hierarchies)
seria mais rápido em consulta, mas o `osrm-customize` do MLD permite recustomizar pesos
sem reextrair — que é exatamente o que a Fase 3 vai precisar para injetar o
`road_readiness_score` como peso do grafo (spec 02).

Requer Docker (usa a imagem oficial do OSRM; não exige instalar o OSRM na máquina).

Uso:
    cd services/geo-api
    python scripts/prepare_osrm_graph.py
"""

import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx

from app.pilot_area import HIGHWAY_TYPES_VEICULARES, PILOTO_BBOX, bbox_overpass

# Configurável porque a instância pública é compartilhada e rate-limited: quem rodar isso
# com frequência (ou em CI) deve apontar para um mirror ou instância própria.
OVERPASS_URL = os.environ.get("OVERPASS_URL", "https://overpass-api.de/api/interpreter")
OSRM_IMAGE = "ghcr.io/project-osrm/osrm-backend"

# 429 (rate limit) e 504 (fila cheia) são respostas ROTINEIRAS da Overpass pública, não
# defeito da query — tentar de novo com espaço entre as tentativas resolve quase sempre.
TENTATIVAS = 4
ESPERA_INICIAL_SEGUNDOS = 15

# O compose monta este diretório em /data do container do OSRM (ver infra/docker-compose.yml).
DESTINO = Path(__file__).resolve().parents[3] / "infra" / "osrm" / "data"
NOME_BASE = "pilot"


def baixar_osm_xml(destino: Path) -> None:
    """
    `(._;>;)` é o que faz a diferença aqui: recursão "para baixo", trazendo os nós de
    cada via. Sem isso o XML tem `<way>` referenciando nós que não existem no arquivo, e
    o osrm-extract monta um grafo vazio — falha silenciosa, não erro.
    """
    query = f"""
    [out:xml][timeout:180];
    way["highway"~"^({HIGHWAY_TYPES_VEICULARES})$"]{bbox_overpass()};
    (._;>;);
    out body;
    """
    print(f"Baixando extrato OSM da área do piloto {PILOTO_BBOX}...")

    espera = ESPERA_INICIAL_SEGUNDOS
    for tentativa in range(1, TENTATIVAS + 1):
        resp = httpx.post(
            OVERPASS_URL,
            data={"data": query},
            headers={"User-Agent": "autonomousapi-geo-api/0.1 (prepare-osrm-graph script)"},
            timeout=240,
        )
        if resp.status_code == 200:
            destino.write_bytes(resp.content)
            print(f"Extrato salvo em {destino} ({destino.stat().st_size / 1_000_000:.1f} MB)")
            return

        if resp.status_code not in (429, 504) or tentativa == TENTATIVAS:
            raise SystemExit(
                f"Overpass respondeu {resp.status_code} após {tentativa} tentativa(s). "
                "Se persistir, aponte OVERPASS_URL para outro mirror."
            )

        print(
            f"  Overpass {resp.status_code} (tentativa {tentativa}/{TENTATIVAS})"
            f" — aguardando {espera}s..."
        )
        time.sleep(espera)
        espera *= 2


def rodar_osrm(comando: list[str]) -> None:
    """Roda um passo do pipeline na imagem oficial do OSRM, montando DESTINO em /data."""
    docker = [
        "docker",
        "run",
        "--rm",
        "-v",
        f"{DESTINO}:/data",
        OSRM_IMAGE,
        *comando,
    ]
    print(f"$ {' '.join(comando)}")
    resultado = subprocess.run(docker, capture_output=True, text=True)
    if resultado.returncode != 0:
        # O OSRM escreve o progresso em stderr mesmo quando dá certo — só imprime tudo
        # quando falhou de verdade, senão polui a saída do script.
        print(resultado.stdout)
        print(resultado.stderr, file=sys.stderr)
        raise SystemExit(f"Falha em: {' '.join(comando)}")


def preparar() -> None:
    DESTINO.mkdir(parents=True, exist_ok=True)
    osm_xml = DESTINO / f"{NOME_BASE}.osm"
    baixar_osm_xml(osm_xml)

    rodar_osrm(["osrm-extract", "-p", "/opt/car.lua", f"/data/{NOME_BASE}.osm"])
    rodar_osrm(["osrm-partition", f"/data/{NOME_BASE}"])
    rodar_osrm(["osrm-customize", f"/data/{NOME_BASE}"])

    print(
        "\nGrafo pronto. Suba o serviço de roteamento com:\n"
        "    docker compose -f infra/docker-compose.yml --profile routing up -d osrm"
    )


if __name__ == "__main__":
    preparar()
