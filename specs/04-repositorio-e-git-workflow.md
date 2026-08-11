# 04 — Repositório e Git Workflow

## Decisão: monorepo

Um único repositório `autonomousapi/platform` para `web`, `mobile`, `core-api`, `geo-api`, contratos e infra. Justificativa: time pequeno (3 pessoas), mudanças que cruzam serviços são frequentes nesse estágio (ex. mudar um contrato entre `core-api` e `geo-api` toca dois serviços na mesma PR), e monorepo evita o overhead de coordenar versões entre 4 repositórios separados agora. Reavaliar split para multi-repo só se o time crescer bastante ou algum serviço precisar de ciclo de release totalmente independente.

## Estrutura de pastas

```
autonomousapi/
├── apps/
│   ├── web/                 # React
│   └── mobile/               # React Native + Expo
├── services/
│   ├── core-api/              # Java / Spring Boot
│   └── geo-api/                # Python / FastAPI
├── packages/
│   ├── contracts/              # OpenAPI specs versionados (core-api <-> geo-api, API pública)
│   └── shared-types/            # tipos/DTOs compartilhados entre web e mobile (TS)
├── infra/
│   ├── docker-compose.yml       # ambiente local completo
│   ├── migrations/               # (ou dentro de cada serviço, ver nota abaixo)
│   └── terraform/ (ou equivalente, quando existir IaC)
├── specs/                        # esta pasta — spec-driven docs
├── docs/                          # docs operacionais (runbooks, decisões arquiteturais — ADRs)
├── .github/
│   ├── workflows/                  # CI/CD
│   ├── CODEOWNERS
│   └── PULL_REQUEST_TEMPLATE.md
└── README.md
```

Nota: migrations de banco (Flyway/Liquibase para `core-api`, Alembic para `geo-api`) ficam dentro de cada serviço, não em `infra/`, para manter migration versionada junto do código que a usa.

## Convenção de nomenclatura de branches

Padrão: `<tipo>/<escopo>-<descrição-curta>`

**Tipos:**
- `feature/` — nova funcionalidade
- `fix/` — correção de bug (não crítico, vai para `develop`)
- `hotfix/` — correção crítica direto em produção (sai de `main`, volta para `main` e `develop`)
- `chore/` — manutenção, dependências, configuração, sem mudança de comportamento
- `refactor/` — mudança de código sem mudança de comportamento externo
- `spec/` — mudanças nos documentos de `specs/`
- `docs/` — documentação fora de `specs/`
- `release/` — preparação de uma release (versionamento, changelog)

**Escopos** (obrigatório, identifica qual parte do monorepo a branch toca):
`web`, `mobile`, `core-api`, `geo-api`, `contracts`, `infra`, `specs`

**Exemplos:**
```
feature/mobile-registro-viagem-offline
feature/core-api-billing-stripe
feature/geo-api-map-matching-osrm
fix/web-dashboard-custo-km-arredondamento
hotfix/core-api-token-expirando-cedo
chore/infra-atualizar-docker-compose
spec/dados-politica-retencao-gps
release/2026-09-mvp-gestao-frota
```

Se uma mudança cruza mais de um escopo (ex. contrato novo entre `core-api` e `geo-api`), usar o escopo mais amplo ou `contracts`: `feature/contracts-endpoint-road-readiness`.

## Estratégia de branches

Trunk-based adaptado, com duas branches longas:

- **`main`** — sempre reflete o que está em produção. Protegida: sem push direto, exige PR aprovado + checks verdes.
- **`develop`** — integração contínua do time. Protegida: sem push direto, exige PR aprovado + checks verdes.

Fluxo:
1. Toda branch de trabalho (`feature/`, `fix/`, `chore/`, `refactor/`, `spec/`, `docs/`) nasce de `develop` e volta para `develop` via PR.
2. `release/*` nasce de `develop` quando um conjunto de features está pronto para ir a produção; só recebe correções de última hora; vira PR para `main` **e** merge de volta em `develop`.
3. `hotfix/*` nasce de `main` (correção urgente em produção), volta para `main` **e** `develop` em PRs separadas.
4. Branches de trabalho devem ser **curtas** (dias, não semanas) — se uma feature é grande, quebrar em sub-PRs incrementais atrás de feature flag, não manter uma branch viva por semanas acumulando divergência.

```
main     ●────────────────●hotfix────────●release────────────▶
          \                              ↑        \
develop    ●──●──●──●──●──●──●──●──●──●──┴──●──●──●●─────────▶
            \feature/  \fix/      \chore/
```

## Commits

Conventional Commits, sempre com escopo:

```
<tipo>(<escopo>): <descrição curta no imperativo>

[corpo opcional explicando o porquê, não o o-quê]
```

Tipos: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `ci`, `spec`.
Escopo: mesmo vocabulário das branches (`web`, `mobile`, `core-api`, `geo-api`, `contracts`, `infra`, `specs`).

Exemplos:
```
feat(core-api): adicionar endpoint de assinatura por veículo
fix(mobile): corrigir fila offline não sincronizando após reconexão
spec(dados): documentar política de retenção de GPS bruto
```

## Pull Requests

- Título segue o padrão de commit (`tipo(escopo): descrição`).
- Template de PR (`.github/PULL_REQUEST_TEMPLATE.md`) obrigatório com: o que mudou, por quê, como testar, checklist de Definition of Done da spec relevante (linkar o arquivo em `specs/`).
- Nunca mergear sem pelo menos 1 aprovação de outra pessoa do time (mesmo em time de 3 — revisão cruzada é o que evita decisão de arquitetura errada virar dívida técnica cedo).
- CI obrigatório e verde antes de merge: lint, testes, build de cada app/serviço afetado.

## CODEOWNERS

```
/apps/web/            @<dono-web>
/apps/mobile/          @<dono-mobile>
/services/core-api/     @<dono-core-api>
/services/geo-api/       @<dono-geo-api>
/packages/contracts/      @<lider-tecnico>
/specs/                    @<lider-tecnico>
```
Preencher com os handles reais do time (Guilherme H., Guilherme G., Kamilly) por área de responsabilidade principal — mesmo em time pequeno, define quem revisa o quê por padrão.

## Versionamento e releases

- Tags semânticas por release do monorepo inteiro na Fase 1-2 (`v0.1.0`, `v0.2.0`...) — versão única simplifica enquanto os serviços evoluem juntos.
- Reavaliar versionamento independente por serviço (`core-api@1.2.0`, `geo-api@0.4.0`) só quando a API pública de parceiros de AV estiver em uso por clientes externos reais (aí sim, versionamento próprio da API pública é obrigatório e não pode quebrar sem major version).

## CI/CD (mínimo viável desde a Fase 1)

- Pipeline por escopo: só roda build/test do que mudou na PR (evita pipeline lento em monorepo).
- Ambientes: `develop` deploya automaticamente em ambiente de staging; `main` deploya em produção após aprovação manual (gate).
- Segredos (chaves de API, credenciais de banco, credenciais de billing) nunca no repositório — usar secrets manager do provedor de CI/hospedagem desde o primeiro deploy, não "depois".

## Definition of Done (repositório/workflow)

- [ ] Repositório criado com a estrutura de pastas acima.
- [ ] `main` e `develop` protegidas (branch protection rules configuradas).
- [ ] `.github/CODEOWNERS` e `PULL_REQUEST_TEMPLATE.md` no lugar.
- [ ] CI mínimo rodando (lint + testes) em pelo menos um serviço antes da primeira feature real ser desenvolvida.
- [ ] `docker-compose.yml` sobe o ambiente local completo com um comando.
