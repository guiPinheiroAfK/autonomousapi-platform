# scripts/stress-test

Teste de carga do `core-api` local (docker-compose). Duas peças:

- **`seed-bulk.sql`** — gera volume (milhares de veículos/despesas) direto no Postgres,
  em cima de um tenant já criado via `scripts/test-e2e/01-seed.sh` (que passa pela API de
  verdade). Aqui é só volume de linha, não fluxo de negócio — por isso vai direto no banco
  em vez de milhares de chamadas HTTP sequenciais (ver comentário no topo do arquivo pra
  mais detalhe do porquê).
- **`locustfile.py`** — gera a carga em cima da API (Locust/Python), autenticado com um
  único login compartilhado entre todos os usuários simulados (ver docstring do arquivo:
  o endpoint de login tem rate limit, então logar em loop por usuário mediria o rate
  limiter, não os endpoints que interessam).

## Observabilidade

O `core-api` expõe `/actuator/health`, `/actuator/metrics` e `/actuator/prometheus` sem
JWT (mesma postura que `/v3/api-docs` já tinha) — dá pra acompanhar HikariCP, JVM e
latência por endpoint (`http.server.requests`, com percentis habilitados) durante o teste:

```bash
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending
curl -s "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/v1/vehicles"
docker stats --no-stream autonomousapi-core-api-1 autonomousapi-db-1
```

## Uso

```bash
# 1. Ambiente limpo e no ar
bash scripts/test-e2e/00-reset-env.sh

# 2. Cenário base (tenant + gestor com login real, alguns veículos)
bash scripts/test-e2e/01-seed.sh

# 3. Pegue o tenant_id gerado (a saída do passo 2 não imprime, então:)
docker exec autonomousapi-db-1 psql -U autonomousapi -d autonomousapi \
  -tAc "select id from core.tenant where name = 'RotaTeste E2E';"

# 4. Volume pra teste de carga (ajuste n_vehicles/expenses_per_vehicle à vontade)
docker exec -i autonomousapi-db-1 psql -U autonomousapi -d autonomousapi \
  -v tenant_id="'<uuid-do-passo-3>'" -v n_vehicles=2000 -v expenses_per_vehicle=5 \
  -f - < scripts/stress-test/seed-bulk.sql

# 5. Ambiente Python isolado (uma vez só)
cd scripts/stress-test
python -m venv .venv
./.venv/Scripts/python -m pip install -r requirements.txt   # Windows: Scripts/, Unix: bin/

# 6. Rodar
./.venv/Scripts/python -m locust -f locustfile.py --host http://localhost:8080
# abre http://localhost:8089 pra configurar usuários/spawn rate na UI

# ...ou headless com relatório em CSV:
./.venv/Scripts/python -m locust -f locustfile.py --host http://localhost:8080 \
  --headless -u 150 -r 15 -t 2m --csv=resultado
```

`GESTOR_EMAIL`/`GESTOR_PASSWORD` em `locustfile.py` batem com o padrão do
`01-seed.sh` — se você sobrescreveu essas variáveis de ambiente ao rodar o seed, ajuste
as constantes no topo do `locustfile.py` também.

## Histórico dos testes

**Primeiro teste (150 usuários, 2 min, 2000 veículos / 10k despesas), antes da paginação —**
achou que `GET /v1/vehicles` e `GET /v1/expenses` devolviam a base inteira do tenant numa
request só (p99 ~68ms e ~140ms, picos de 330-370ms). 0% de falha, pool HikariCP nunca
enfileirou nesse volume — o gargalo era payload, não conexão.

**Correção — paginação em ambos os endpoints** (`PageResponse<T>`, `core/common`), size
default 20/máx 500, com busca/status server-side pra não "esconder" resultado fora da
página carregada. Mesmo teste a 500 usuários, mesmo volume: p50 de 220ms → 7ms, p99 de
1.200ms → 96ms, throughput de ~224 → ~312 req/s.

**Achando o teto real (500 usuários), depois da paginação, com 20k veículos / 100k
despesas —** subir a concorrência além disso não é mais sobre payload nem sobre pool: é
CPU pura do container (`core-api`, 12 cores alocados ao Docker Desktop).

| usuários simulados | req/s | p50 | pool HikariCP | CPU `core-api` |
|---|---|---|---|---|
| 500 | ~312 | 7ms | folgado | normal |
| 750 | ~100 | 4,9s | saturado (10/10, fila ~190) | 620-910% |
| 1.000 (pool=10) | ~107 | 7,1s | saturado (10/10, fila ~190) | 750-900% |
| 1.000 (pool=30) | ~73 | 11s | folgado (30/30 nunca bate) | ~1000%+ |

A quebra fica entre 500 (limpo) e 750 (já degradado 3x) usuários simulados. **Aumentar o
pool não ajuda nem nesse nível de concorrência — piora**: com pool=30 sobrando conexão,
o throughput caiu ainda mais (73 vs 107 req/s) porque mais conexões só criam mais threads
brigando pelo mesmo CPU, aumentando o custo de context-switch sem aumentar o trabalho útil
feito. Em nenhum dos testes houve erro/timeout — a degradação é só latência subindo, o
processo nunca cai.
