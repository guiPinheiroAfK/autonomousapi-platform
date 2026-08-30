# AutonomousAPI — `platform`

Monorepo da plataforma AutonomousAPI: gestão de frota (SaaS) hoje, dado de prontidão
viária como subproduto, base para licenciamento de API a empresas de mobilidade autônoma.

> **Fonte de verdade do produto:** a pasta [`specs/`](./specs). Leia `specs/00-visao-geral.md`
> até `specs/05-roadmap-fases.md`, nessa ordem, antes de implementar qualquer coisa.
> Trabalhe **uma fase por vez** (`specs/05-roadmap-fases.md`).

## Estrutura

```
autonomousapi/
├── apps/
│   ├── web/            # React (Vite + TS) — painel do gestor / admin
│   └── mobile/         # React Native + Expo — app do motorista/gestor
├── services/
│   ├── core-api/       # Java / Spring Boot — auth, billing, regras de negócio
│   └── geo-api/        # Python / FastAPI — GPS, rotas, prontidão viária
├── packages/
│   ├── contracts/      # OpenAPI (interno core↔geo + API pública de parceiros)
│   └── shared-types/   # tipos TS gerados a partir do OpenAPI do core-api
├── infra/              # docker-compose, IaC
├── specs/              # spec-driven docs (fonte de verdade)
├── docs/adr/           # Architecture Decision Records
└── .github/            # CI/CD, CODEOWNERS, template de PR
```

**Regra de ouro (spec 01):** frontend (web e mobile) fala **só** com `core-api`.
`core-api` orquestra `geo-api` internamente. Nenhum cliente externo acessa `geo-api` direto.

## Como abrir (JetBrains)

É **um** repositório, **um** clone. Abra as subpastas nas IDEs certas — todas apontando
para o mesmo working copy:

| IDE | Abrir |
|---|---|
| WebStorm | a raiz (ou `apps/web`) — cobre `apps/web`, `apps/mobile`, `packages/` |
| IntelliJ IDEA | `services/core-api` |
| PyCharm | `services/geo-api` |

## Rodar local

Pré-requisito: Docker. As toolchains de backend (Java 17+, Python 3.12) **não** são
necessárias para subir o ambiente — os serviços rodam em container.

```bash
docker compose -f infra/docker-compose.yml up
```

## Workflow de Git

Ver [`specs/04-repositorio-e-git-workflow.md`](./specs/04-repositorio-e-git-workflow.md).
Resumo: branches saem de `develop`, nomes `<tipo>/<escopo>-<descrição>`, commits em
Conventional Commits com escopo, PR obrigatória (sem exigência de segundo aprovador
hoje — ver `specs/08-decisoes-tecnicas-pendentes.md` item 9). `main` e `develop` são
protegidas.
