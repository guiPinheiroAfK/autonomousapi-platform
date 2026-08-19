#!/usr/bin/env bash
#
# Reseta o ambiente local por completo: derruba os containers e APAGA o volume do
# Postgres (perde todo o dado local, de propósito) e sobe tudo de novo do zero, já
# com o schema recriado por Flyway (core-api) e Alembic (geo-api).
#
# É o "apaga e cria" pedido: cada rodada do teste e2e começa de um banco vazio,
# nunca acumulando lixo de rodadas anteriores — diferente de um seed que insere e
# nunca limpa, que faria os IDs/telas do front-end acumularem dado de todas as
# execuções passadas.
#
# Uso:
#   bash scripts/test-e2e/00-reset-env.sh
#
# Pré-requisitos: Docker + Docker Compose. Rode a partir de qualquer diretório —
# o script acha a raiz do repo sozinho.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/infra/docker-compose.yml"
CORE_API_URL="${CORE_API_URL:-http://localhost:8080}"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERRO: não achei ${COMPOSE_FILE}. Rode este script de dentro do repo." >&2
  exit 1
fi

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

echo "==> Derrubando containers e apagando volumes (down -v)..."
compose down -v

echo "==> Subindo o ambiente do zero (up -d --build)..."
compose up -d --build

echo "==> Aguardando core-api ficar saudável em ${CORE_API_URL}/v1/health ..."
ok=""
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${CORE_API_URL}/v1/health" || echo "000")
  if [ "$code" = "200" ]; then
    ok="1"
    break
  fi
  sleep 2
done

if [ -z "$ok" ]; then
  echo "ERRO: core-api não respondeu 200 em ${CORE_API_URL}/v1/health a tempo." >&2
  echo "Investigue com: docker compose -f ${COMPOSE_FILE} logs core-api" >&2
  exit 1
fi

echo "==> Ambiente no ar e saudável."
