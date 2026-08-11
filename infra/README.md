# infra — ambiente local

Sobe o backend completo (Postgres+PostGIS, `core-api`, `geo-api`) com um comando.

## Subir

```bash
docker compose -f infra/docker-compose.yml up --build
```

Serviços expostos:

| Serviço | URL local | Observação |
|---|---|---|
| core-api | http://localhost:8080 | **único** ponto de entrada dos clientes (spec 01) |
| geo-api | http://localhost:8000 | interno; exige `X-Service-Token` (não chamar direto do front) |
| Postgres+PostGIS | localhost:5433 | user/pass/db: `autonomousapi` (5433 no host p/ não colidir com Postgres local) |

Na subida, o `core-api` (Flyway) cria o schema `core` e o `geo-api` (Alembic) cria o
schema `geo` + PostGIS — cada um dono do seu, no mesmo banco (ADR 0004).

## Rodar o web contra este backend

O web roda fora do compose (hot-reload). Em outro terminal:

```bash
npm install          # na raiz, uma vez
npm run dev:web      # http://localhost:5173, proxy /api -> core-api:8080
```

## Smoke test da integração

```bash
# health agregado (core-api verifica o geo-api internamente)
curl http://localhost:8080/v1/health
# -> {"status":"ok","services":{"core-api":"up","geo-api":"up"}}

# signup (cria tenant + gestor, devolve tokens)
curl -X POST http://localhost:8080/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"gestor@frota.com","password":"senha12345","tenantName":"Frota Teste"}'

# O geo-api é interno (sem porta publicada). Para provar que exige token de serviço,
# chame-o de dentro da rede do compose — deve dar 401 sem o header:
docker run --rm --network autonomousapi_default curlimages/curl -s -o /dev/null \
  -w '%{http_code}\n' http://geo-api:8000/internal/v1/health   # -> 401

# O health agregado acima já prova o caminho COM token: "geo-api":"up" só aparece
# quando o core-api consegue falar com o geo-api usando o token de serviço.
```

## Config

Variáveis com default no compose; para sobrescrever, copie `.env.example` para `infra/.env`.
O `GEO_SERVICE_TOKEN` precisa ser **igual** em `core-api` e `geo-api`.
