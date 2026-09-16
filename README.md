# AutonomousAPI — `platform`

Monorepo da plataforma AutonomousAPI: gestão de frota (SaaS) hoje, dado de prontidão
viária como subproduto, base para licenciamento de API a empresas de mobilidade autônoma.

Platform monorepo for AutonomousAPI: fleet management (SaaS) today, road-readiness
data as a by-product, and the base for licensing an API to autonomous-mobility companies.

**Fonte de verdade do produto:** a pasta [`specs/`](./specs). Leia `specs/00-visao-geral.md`
até `specs/05-roadmap-fases.md`, nessa ordem, antes de implementar qualquer coisa.
Trabalhe **uma fase por vez** (`specs/05-roadmap-fases.md`). Pra saber rápido o que já
está pronto, pela metade ou ainda não começou, sem ler spec por spec, veja
[`specs/16-status-das-funcionalidades.md`](./specs/16-status-das-funcionalidades.md).

**Product source of truth:** the [`specs/`](./specs) folder. Read `specs/00-visao-geral.md`
through `specs/05-roadmap-fases.md`, in that order, before implementing anything.
Work **one phase at a time** (`specs/05-roadmap-fases.md`). To quickly check what's
already done, in progress, or not started, without reading every spec, see
[`specs/16-status-das-funcionalidades.md`](./specs/16-status-das-funcionalidades.md).

## Estrutura / Structure

```
autonomousapi/
├── apps/
│ ├── web/ # React (Vite + TS) — painel do gestor / admin
│ └── mobile/ # React Native + Expo — app do motorista/gestor
├── services/
│ ├── core-api/ # Java / Spring Boot — auth, billing, regras de negócio
│ └── geo-api/ # Python / FastAPI — GPS, rotas, prontidão viária
├── packages/
│ ├── contracts/ # OpenAPI (interno core↔geo + API pública de parceiros)
│ └── shared-types/ # tipos TS gerados a partir do OpenAPI do core-api
├── infra/ # docker-compose, IaC
├── specs/ # spec-driven docs (fonte de verdade)
├── docs/adr/ # Architecture Decision Records
└── .github/ # CI/CD, CODEOWNERS, template de PR
```

**Regra de ouro (spec 01):** frontend (web e mobile) fala **só** com `core-api`.
`core-api` orquestra `geo-api` internamente. Nenhum cliente externo acessa `geo-api` direto.

**Golden rule (spec 01):** the frontend (web and mobile) talks **only** to `core-api`.
`core-api` orchestrates `geo-api` internally. No external client ever accesses `geo-api` directly.

## Como abrir (JetBrains) / How to open (JetBrains)

É **um** repositório, **um** clone. Abra as subpastas nas IDEs certas — todas apontando
para o mesmo working copy:

It's **one** repository, **one** clone. Open the subfolders in the right IDEs — all
pointing to the same working copy:

| IDE | Abrir / Open |
|---|---|
| WebStorm | a raiz (ou `apps/web`) — cobre `apps/web`, `apps/mobile`, `packages/` / the root (or `apps/web`) — covers `apps/web`, `apps/mobile`, `packages/` |
| IntelliJ IDEA | `services/core-api` |
| PyCharm | `services/geo-api` |

## Rodar local / Running locally

Pré-requisito: Docker. As toolchains de backend (Java 17+, Python 3.12) **não** são
necessárias para subir o ambiente — os serviços rodam em container.

Prerequisite: Docker. Backend toolchains (Java 17+, Python 3.12) are **not** required
to run the environment — the services run in containers.

```bash
docker compose -f infra/docker-compose.yml up
```

## Workflow de Git / Git Workflow

Ver [`specs/04-repositorio-e-git-workflow.md`](./specs/04-repositorio-e-git-workflow.md).
Resumo: branches saem de `develop`, nomes `<tipo>/<escopo>-<descrição>`, commits em
Conventional Commits com escopo, PR obrigatória (sem exigência de segundo aprovador
hoje — ver `specs/08-decisoes-tecnicas-pendentes.md` item 9). `main` e `develop` são
protegidas.

See [`specs/04-repositorio-e-git-workflow.md`](./specs/04-repositorio-e-git-workflow.md).
Summary: branches come off `develop`, named `<type>/<scope>-<description>`, commits
follow Conventional Commits with scope, PR required (no second-approver requirement
today — see `specs/08-decisoes-tecnicas-pendentes.md` item 9). `main` and `develop`
are protected.
