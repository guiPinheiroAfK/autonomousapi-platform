# ADR 0002 — Estratégia de contratos OpenAPI (code-first interno, spec-first público)

- **Status:** Aceito
- **Data:** 2026-08-11
- **Contexto da spec:** `specs/01-arquitetura.md` (Contratos entre serviços)

## Contexto

Há dois contratos de natureza diferente:
1. **Interno** `core-api` → `geo-api`: consumido por um único cliente (o `core-api`), muda
   junto com o código, na mesma PR. O `geo-api` (FastAPI) já **gera** OpenAPI automaticamente.
2. **Público** `/public/v1/road-readiness/...`: consumido por parceiros de AV pagantes.
   Quebrar contrato aqui é caro e exige versionamento formal desde o dia 1 (spec 01).

Tratar os dois com o mesmo regime seria errado: burocratizar o interno atrasa; deixar o
público "seguir o código" arrisca quebrar cliente externo.

## Decisão

- **Contrato interno core↔geo = code-first.** O `geo-api` gera o OpenAPI; um snapshot é
  commitado em `packages/contracts/internal/geo-api.openapi.json` e validado no CI (o CI
  falha se o código divergir do snapshot commitado). Fonte de verdade: o código do geo-api.
- **API pública de parceiros = spec-first.** O arquivo
  `packages/contracts/public/road-readiness.openapi.yaml` é escrito e versionado à mão,
  é a fonte de verdade, e o `core-api` implementa contra ele. Mudança incompatível exige
  nova major version (`/public/v1` → `/public/v2`), nunca quebra em cima da mesma versão.

## Consequências

- Desenvolvimento interno rápido, sem cerimônia de spec-first para algo que só o core consome.
- Contrato público estável e auditável, com histórico versionado no Git desde antes do
  primeiro cliente.
- CI precisa de um passo que regenera o OpenAPI do geo-api e compara com o snapshot (drift check).
