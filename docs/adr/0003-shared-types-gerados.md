# ADR 0003 — `shared-types` gerados a partir do OpenAPI do core-api

- **Status:** Aceito
- **Data:** 2026-08-11
- **Contexto da spec:** `specs/01-arquitetura.md`, `specs/04-repositorio-e-git-workflow.md`

## Contexto

`web` e `mobile` (TypeScript) consomem DTOs expostos pelo `core-api`, que é **Java**.
Manter os tipos TS à mão, espelhando os DTOs Java, garante divergência (drift) silenciosa:
o backend muda um campo e o frontend só descobre em runtime.

## Decisão

`packages/shared-types` é **gerado**, não escrito à mão, a partir do documento OpenAPI que
o `core-api` expõe (Springdoc/OpenAPI). O gerador (ex.: `openapi-typescript`) roda como
script do workspace e produz os tipos consumidos por `web` e `mobile`.

- Fonte de verdade: o OpenAPI do `core-api`.
- Os arquivos gerados são commitados (para review e para não exigir o backend no ar em todo
  build de frontend), mas **nunca editados à mão** — regenerar é a única forma de mudar.
- O CI valida que o gerado está atualizado (falha se alguém mudou o contrato e esqueceu de
  regenerar).

## Consequências

- Frontend e backend não divergem em tipos sem o CI acusar.
- Frontend não precisa saber Java; consome só o contrato.
- Ordem de build: contrato do core-api disponível → gerar shared-types → buildar web/mobile.
