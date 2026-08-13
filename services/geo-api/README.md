# geo-api

Serviço geoespacial (Python 3.12 / FastAPI). GPS, rotas e prontidão viária.
**Interno** — não exposto à internet; só o `core-api` chama (spec 01), com token de serviço.

Abrir no **PyCharm** apontando para esta pasta (`services/geo-api`).

## Rodar local

Recomendado via `infra/docker-compose.yml` (Checkpoint E), que sobe Postgres+PostGIS junto.
Avulso, contra um Postgres com PostGIS:

```bash
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
export GEO_DB_URL=postgresql+psycopg://autonomousapi:autonomousapi@localhost:5432/autonomousapi
export GEO_SERVICE_TOKEN=dev-service-token-change-me
alembic upgrade head
uvicorn app.main:app --reload --port 8000
```

## Testes e lint

```bash
pytest
ruff check .
```

## Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| GET  | `/` | pública | Liveness |
| GET  | `/internal/v1/health` | X-Service-Token | Health interno (consumido pelo core-api) |
| POST | `/internal/v1/gps/pings` | X-Service-Token | Ingestão de ping + map matching (Fase 2) |
| POST | `/internal/v1/road-readiness/recalculate` | X-Service-Token | Dispara a agregação de score sob demanda (o scheduler já roda isso periodicamente) |

## Migrations

Alembic, em `alembic/versions/`. Dono **exclusivo** do schema `geo` e da extensão PostGIS
(ADR 0004). A tabela `alembic_version` fica dentro do schema `geo`. **Nunca** toca no
schema `core`.

## Fase 2 (spec 02): map matching, prontidão viária, retenção

- **Map matching** (`app/matching.py`): vizinho mais próximo via PostGIS (`<->` + `ST_DWithin`),
  não OSRM/osrm-match — decisão documentada no topo do próprio arquivo (funciona bem para
  "qual via é essa", não para desambiguar cruzamento complexo; revisitar se isso virar
  problema real de piloto).
- **Agregação** (`app/aggregation.py`): score v1 = contagem de observações normalizada.
  Nunca roda na requisição — só via job periódico ou `POST .../recalculate`.
- **Retenção** (`app/retention.py` + ADR 0009): `vehicle_gps_ping` expurgado depois de
  `GEO_GPS_RETENTION_DAYS` (padrão 30). O agregado (`road_segment_observation`) não é
  afetado — nunca carregou `vehicle_id`.
- **Scheduler** (`app/scheduler.py`): jobs periódicos in-process (APScheduler), sem
  fila/worker novo — mesmo raciocínio da ADR 0006.
- **Import de OSM do piloto** (`scripts/import_osm_pilot.py`): busca vias via Overpass API
  numa bounding box pequena (hoje: Av. Paulista, São Paulo — trocar `PILOTO_BBOX` quando a
  área real do piloto for definida). Idempotente por `osm_way_id`. Rodar com:
  ```bash
  python scripts/import_osm_pilot.py
  ```
