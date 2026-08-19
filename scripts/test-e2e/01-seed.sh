#!/usr/bin/env bash
#
# Popula um cenário de teste completo via API real do core-api (nada de SQL direto
# no banco — assim qualquer regra de negócio/validação que exista no backend também
# é exercitada, igual ao que o front-end faria):
#
#   1. Cria conta (tenant + gestor) via signup e confirma o e-mail.
#   2. Cadastra veículos.
#   3. Cadastra motoristas.
#   4. Convida um motorista e "aceita" o convite em nome dele — como não há SMTP
#      configurado em dev, o link de convite fica só no log do container
#      (services/core-api/.../email/LoggingEmailSender.java); este script extrai o
#      token de lá. Depois designa um veículo a ele.
#   5. Lança despesas por veículo (combustível, manutenção, pedágio) e de frota
#      (seguro, IPVA) — spec 10.
#
# Uso:
#   bash scripts/test-e2e/01-seed.sh
#
# Pré-requisitos: ambiente já no ar (rode 00-reset-env.sh antes), curl, jq, docker.
# Variáveis de ambiente opcionais para sobrescrever os dados de teste:
#   TENANT_NAME, GESTOR_EMAIL, GESTOR_PASSWORD, MOTORISTA_EMAIL, MOTORISTA_PASSWORD

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/infra/docker-compose.yml"
BASE_URL="${CORE_API_URL:-http://localhost:8080}/v1"

TENANT_NAME="${TENANT_NAME:-RotaTeste E2E}"
GESTOR_EMAIL="${GESTOR_EMAIL:-gestor.e2e@teste.local}"
GESTOR_PASSWORD="${GESTOR_PASSWORD:-senha12345}"
MOTORISTA_EMAIL="${MOTORISTA_EMAIL:-motorista.e2e@teste.local}"
MOTORISTA_PASSWORD="${MOTORISTA_PASSWORD:-senha12345}"

command -v jq >/dev/null 2>&1 || { echo "ERRO: precisa de 'jq' instalado." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "ERRO: precisa de 'curl' instalado." >&2; exit 1; }

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

# Extrai o token de um link logado pelo LoggingEmailSender (sem SMTP em dev, o
# e-mail nunca sai de verdade — só o link aparece no log do container). Tenta por
# alguns segundos, já que pode haver uma pequena defasagem entre a resposta HTTP e
# a linha aparecer no log.
extract_link_token() {
  local marker="$1" email="$2" line token attempt
  for attempt in $(seq 1 10); do
    line=$(compose logs core-api 2>/dev/null | grep -F "$marker" | grep -F "destinatário=${email} " | tail -1 || true)
    if [ -n "$line" ]; then
      token=$(echo "$line" | sed -E 's/.*[?&]token=([^& ]+).*/\1/')
      if [ -n "$token" ] && [ "$token" != "$line" ]; then
        echo "$token"
        return 0
      fi
    fi
    sleep 1
  done
  echo "ERRO: não achei o link/token no log do core-api (marcador: '${marker}', e-mail: ${email})." >&2
  echo "Confira manualmente com: docker compose -f ${COMPOSE_FILE} logs core-api | grep '${marker}'" >&2
  return 1
}

days_ago() {
  date -d "-$1 day" +%F 2>/dev/null || date -v-"$1"d +%F
}

# Manda o corpo JSON via stdin (--data-binary @-) em vez de argumento de linha de
# comando (-d "..."): em curl.exe rodando de dentro do Git Bash no Windows, um
# argumento de processo com acentuação (ex. "Troca de óleo") pode ser reinterpretado
# na fronteira MSYS2 -> processo nativo e chegar corrompido no servidor — o backend
# responde 401 (falha silenciosa, nada a ver com o JWT em si). Passar o corpo por
# stdin sempre manda os bytes exatamente como estão no arquivo do script,
# independente de plataforma — mesmo comportamento em Linux/Mac, só mais robusto
# aqui. Ver INSTRUCOES-claude-code.md.
post_json() {
  local url="$1"; shift
  curl -sf -X POST "$url" -H 'Content-Type: application/json' --data-binary @- "$@"
}

echo "==> [1/6] Criando conta (signup) — ${GESTOR_EMAIL} / tenant '${TENANT_NAME}'"
post_json "${BASE_URL}/auth/signup" <<< "{\"email\":\"${GESTOR_EMAIL}\",\"password\":\"${GESTOR_PASSWORD}\",\"tenantName\":\"${TENANT_NAME}\"}" \
  > /dev/null

echo "    Confirmando e-mail (via link logado, sem SMTP)..."
VERIFY_TOKEN=$(extract_link_token "email de verificação" "$GESTOR_EMAIL")
GESTOR_TOKEN=$(post_json "${BASE_URL}/auth/verify-email" <<< "{\"token\":\"${VERIFY_TOKEN}\"}" | jq -r '.accessToken')
AUTH_HEADER="Authorization: Bearer ${GESTOR_TOKEN}"
echo "    Conta confirmada."

echo "==> [2/6] Cadastrando veículos..."
VEHICLE_IDS=()
VEHICLE_DEFS=(
  'RTE1A11|Fiat|Fiorino|2022|32000|ATIVO|VAN'
  'RTE1B22|Fiat|Strada|2023|18500|ATIVO|CARRO'
  'RTE1C33|Volkswagen|Saveiro|2021|54200|ATIVO|CARRO'
  'RTE1D44|Iveco|Daily|2021|62700|MANUTENCAO|CAMINHAO'
)
for def in "${VEHICLE_DEFS[@]}"; do
  IFS='|' read -r plate brand model year km status tipo <<< "$def"
  id=$(post_json "${BASE_URL}/vehicles" -H "$AUTH_HEADER" \
    <<< "{\"plate\":\"${plate}\",\"brand\":\"${brand}\",\"model\":\"${model}\",\"modelYear\":${year},\"odometerKm\":${km},\"status\":\"${status}\",\"tipo\":\"${tipo}\"}" \
    | jq -r '.id')
  echo "    - ${plate} (${brand} ${model}) -> ${id}"
  VEHICLE_IDS+=("$id")
done

echo "==> [3/6] Cadastrando motoristas..."
DRIVER_IDS=()
DRIVER_ID_LINKED=""
DRIVER_DEFS=(
  "Motorista E2E Um|11122233344|11988887777|${MOTORISTA_EMAIL}"
  "Motorista E2E Dois|22233344455|11988886666|"
)
for def in "${DRIVER_DEFS[@]}"; do
  IFS='|' read -r name cnh phone email <<< "$def"
  emailJson="null"
  [ -n "$email" ] && emailJson="\"${email}\""
  id=$(post_json "${BASE_URL}/drivers" -H "$AUTH_HEADER" \
    <<< "{\"name\":\"${name}\",\"cnh\":\"${cnh}\",\"phone\":\"${phone}\",\"status\":\"ATIVO\",\"email\":${emailJson}}" \
    | jq -r '.id')
  echo "    - ${name} -> ${id}"
  DRIVER_IDS+=("$id")
  [ -n "$email" ] && DRIVER_ID_LINKED="$id"
done

if [ -n "$DRIVER_ID_LINKED" ]; then
  echo "==> [4/6] Convidando e vinculando motorista (${MOTORISTA_EMAIL})..."
  curl -sf -X POST "${BASE_URL}/drivers/${DRIVER_ID_LINKED}/invite" -H "$AUTH_HEADER" > /dev/null
  INVITE_TOKEN=$(extract_link_token "email de convite de motorista" "$MOTORISTA_EMAIL")
  post_json "${BASE_URL}/auth/accept-invite" \
    <<< "{\"token\":\"${INVITE_TOKEN}\",\"password\":\"${MOTORISTA_PASSWORD}\"}" > /dev/null
  echo "    Login do motorista habilitado: ${MOTORISTA_EMAIL} / ${MOTORISTA_PASSWORD}"

  echo "    Designando veículo ${VEHICLE_IDS[0]} a ele..."
  post_json "${BASE_URL}/drivers/${DRIVER_ID_LINKED}/assignment" -H "$AUTH_HEADER" \
    <<< "{\"vehicleId\":\"${VEHICLE_IDS[0]}\"}" > /dev/null
else
  echo "==> [4/6] (pulado — nenhum motorista com e-mail definido)"
fi

echo "==> [5/6] Lançando despesas por veículo..."
for vid in "${VEHICLE_IDS[@]}"; do
  post_json "${BASE_URL}/vehicles/${vid}/costs" -H "$AUTH_HEADER" \
    <<< "{\"categoria\":\"COMBUSTIVEL\",\"valor\":220.50,\"descricao\":\"Abastecimento (seed e2e)\",\"data\":\"$(days_ago 5)\",\"litrosOuKwh\":35.5,\"odometro\":1000}" > /dev/null
  post_json "${BASE_URL}/vehicles/${vid}/costs" -H "$AUTH_HEADER" \
    <<< "{\"categoria\":\"MANUTENCAO\",\"valor\":380.00,\"descricao\":\"Troca de óleo (seed e2e)\",\"data\":\"$(days_ago 20)\"}" > /dev/null
  post_json "${BASE_URL}/vehicles/${vid}/costs" -H "$AUTH_HEADER" \
    <<< "{\"categoria\":\"PEDAGIO\",\"valor\":48.50,\"descricao\":\"Pedágio (seed e2e)\",\"data\":\"$(days_ago 2)\"}" > /dev/null
done

echo "==> [6/6] Lançando despesas de frota (sem veículo específico)..."
post_json "${BASE_URL}/expenses" -H "$AUTH_HEADER" \
  <<< "{\"categoria\":\"SEGURO\",\"valor\":1450.00,\"descricao\":\"Seguro corporativo mensal (seed e2e)\",\"data\":\"$(days_ago 10)\"}" > /dev/null
post_json "${BASE_URL}/expenses" -H "$AUTH_HEADER" \
  <<< "{\"categoria\":\"IPVA\",\"valor\":890.00,\"descricao\":\"IPVA (seed e2e)\",\"data\":\"$(days_ago 15)\"}" > /dev/null

cat <<EOF

==================================================================
Cenário de teste pronto.

Gestor:    ${GESTOR_EMAIL} / ${GESTOR_PASSWORD}
$( [ -n "$DRIVER_ID_LINKED" ] && echo "Motorista: ${MOTORISTA_EMAIL} / ${MOTORISTA_PASSWORD}" )

Veículos criados:   ${#VEHICLE_IDS[@]}
Motoristas criados: ${#DRIVER_IDS[@]}

Front-end: npm run dev:web (http://localhost:5173) e logue com o gestor acima.
==================================================================
EOF
