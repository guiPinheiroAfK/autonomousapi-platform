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

## Endpoints (Fase 1)

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| GET  | `/` | pública | Liveness |
| GET  | `/internal/v1/health` | X-Service-Token | Health interno (consumido pelo core-api) |
| POST | `/internal/v1/gps/pings` | X-Service-Token | Ingestão bruta de ping de GPS (Fase 1) |

## Migrations

Alembic, em `alembic/versions/`. Dono **exclusivo** do schema `geo` e da extensão PostGIS
(ADR 0004). A tabela `alembic_version` fica dentro do schema `geo`. **Nunca** toca no
schema `core`.
