# ADR 0001 — Monorepo único para toda a plataforma

- **Status:** Aceito
- **Data:** 2026-08-11
- **Contexto da spec:** `specs/04-repositorio-e-git-workflow.md`

## Contexto

A plataforma tem 4 entregáveis de código (`web`, `mobile`, `core-api`, `geo-api`) mais
contratos e infra compartilhados, mantidos por um time de 3 pessoas. Foi levantada a
alternativa de um repositório dedicado por parte.

## Decisão

Um único repositório `autonomousapi/platform` (monorepo) para tudo.

## Consequências

**A favor:**
- Mudança que cruza serviços (ex.: novo contrato entre `core-api` e `geo-api`) é **uma PR
  atômica**, não N PRs coordenadas entre repos.
- `packages/contracts` e `packages/shared-types` consumidos por múltiplos apps sem
  publicar/versionar pacotes entre repos.
- Um `git clone` + um `docker compose up` sobem o sistema inteiro (onboarding e review).
- Um único conjunto de branch protection, CODEOWNERS e convenção de commits.

**Contra (e mitigação):**
- CI pode ficar lento rodando tudo a cada commit → **mitigado** com pipeline por escopo
  (filtro por path: só builda o que mudou — ver `.github/workflows/`).
- Checkout maior e 3 stacks numa pasta → **mitigado** abrindo cada subpasta na IDE certa
  (WebStorm/IntelliJ/PyCharm apontando para o mesmo working copy).

## Quando reavaliar (gatilho para split multi-repo)

- Time cresce a ponto de equipes donas de serviços distintos se pisarem.
- Um serviço precisa de ciclo de release totalmente independente.
- Necessidade de permissão de acesso separada por serviço (ex.: terceirizar só o mobile).
