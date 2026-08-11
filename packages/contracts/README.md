# @autonomousapi/contracts

Contratos de API versionados. Dois regimes diferentes (ver ADR 0002):

## `internal/` — core-api ↔ geo-api (code-first)

Contratos internos entre serviços. Fonte de verdade é o **código**:

- `geo-api.openapi.json` — snapshot gerado pelo FastAPI do `geo-api`. O CI regenera e
  compara com este arquivo (drift check): se divergir, o build falha.
- `core-api.openapi.json` — snapshot do OpenAPI do `core-api` (Springdoc). Alimenta a
  geração de `packages/shared-types` (ADR 0003).

Estes arquivos são **gerados** — não editar à mão.

## `public/` — API de parceiros de AV (spec-first)

- `road-readiness.openapi.yaml` — contrato **público** da API de prontidão viária.
  Aqui a fonte de verdade é o **arquivo**: escrito e versionado à mão, o `core-api`
  implementa contra ele. Mudança incompatível exige nova major version
  (`/public/v1` → `/public/v2`), nunca quebra em cima da mesma versão (spec 01).

Validar: `npm run contracts:validate` (na raiz).
