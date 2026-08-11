# @autonomousapi/shared-types

Tipos TypeScript compartilhados entre `apps/web` e `apps/mobile`.

## Como funciona (ADR 0003)

Os tipos do `core-api` são **gerados**, não escritos à mão, a partir do OpenAPI que o
`core-api` (Springdoc) expõe. Fluxo:

1. `core-api` no ar expõe `/v3/api-docs` (Checkpoint C).
2. O snapshot do OpenAPI é salvo em `packages/contracts/internal/core-api.openapi.json`.
3. `npm run gen:types` (na raiz) gera `src/generated/core-api.ts` com `openapi-typescript`.
4. `web` e `mobile` importam de `@autonomousapi/shared-types`.

**Nunca edite `src/generated/` à mão.** Para mudar um tipo, mude o DTO no `core-api`,
regenere o OpenAPI e rode `gen:types`. O CI valida que o gerado está atualizado.
