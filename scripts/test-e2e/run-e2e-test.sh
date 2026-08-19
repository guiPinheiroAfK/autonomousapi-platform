#!/usr/bin/env bash
#
# Orquestra o teste e2e completo: reseta o ambiente do zero (00-reset-env.sh) e
# popula o cenário de teste (01-seed.sh). É o "apaga e cria" — não tenta ser
# incremental; cada execução garante um estado limpo e conhecido para inspecionar
# no front-end, em vez de acumular dado de rodadas anteriores.
#
# Uso:
#   bash scripts/test-e2e/run-e2e-test.sh
#
# Depois de rodar, suba o front-end (fora do compose, com hot-reload):
#   npm run dev:web   # http://localhost:5173

set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "${DIR}/00-reset-env.sh"
bash "${DIR}/01-seed.sh"
