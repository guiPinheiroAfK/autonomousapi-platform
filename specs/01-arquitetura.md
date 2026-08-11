# 01 — Arquitetura

## Visão de componentes

```
┌────────────┐     ┌──────────────┐
│  Web (React)│     │ Mobile (RN)  │
└──────┬─────┘     └──────┬───────┘
       │  HTTPS/REST      │ HTTPS/REST
       └────────┬─────────┘
                 ▼
        ┌──────────────────┐
        │  core-api          │  Java / Spring Boot
        │  auth, billing,     │  orquestra o geo-api
        │  regras de negócio  │  internamente
        └─────────┬──────────┘
                   │ chamada interna (rede privada)
                   ▼
        ┌──────────────────┐
        │  geo-api            │  Python / FastAPI
        │  GPS, rota,          │
        │  prontidão viária    │
        └─────────┬──────────┘
                   ▼
        ┌──────────────────┐
        │ PostgreSQL+PostGIS │  Neon (compartilhado)
        └──────────────────┘
```

Regra de ouro: **frontend (web e mobile) fala só com `core-api`.** `core-api` orquestra `geo-api` internamente. Nenhum cliente externo (web, mobile, parceiro de API) acessa `geo-api` diretamente — isso mantém auth, rate limit e billing centralizados em um único ponto.

## Serviços

### `core-api` (Java / Spring Boot)
Responsabilidades:
- Autenticação e autorização (perfis: gestor de frota, motorista, admin, parceiro de API).
- CRUD de veículos, motoristas, empresas/tenants.
- Regras de negócio de manutenção/alertas.
- Billing e assinaturas (ver `03-mobile-e-assinaturas.md`).
- Gateway de API para parceiros de AV (autenticação por chave, rate limiting, contratos de licenciamento).
- Orquestra chamadas ao `geo-api` e agrega a resposta para o cliente.

### `geo-api` (Python / FastAPI)
Responsabilidades:
- Ingestão de pontos de GPS e trajetos.
- Processamento geoespacial (PostGIS): mapear ponto/trajeto para segmento de via (map matching).
- Cálculo do índice de prontidão viária por trecho.
- Cálculo de rota/roteamento — usar biblioteca/engine existente (OSRM ou GraphHopper) sobre dados do OpenStreetMap, nunca implementar TSP/VRP do zero.
- Endpoints internos apenas (não expostos diretamente à internet pública; `core-api` é o único chamador).

### `web` (React)
- Painel do gestor de frota: cadastro, dashboard de custo, alertas, relatórios.
- Painel administrativo (interno) para billing e parceiros de API.
- Fala apenas com `core-api`.

### `mobile` (recomendado: React Native + Expo)
- App do motorista: registro de rota/viagem, alertas.
- App do gestor (mesma base de código, telas reduzidas) para uso em campo.
- Justificativa do React Native: uma única base de código para Android + iOS, equipe pequena, reaproveita conhecimento de React do time, ecossistema maduro para GPS em background e notificações push. Reavaliar apenas se surgir necessidade pesada de processamento de vídeo/câmera em tempo real no dispositivo (nesse caso, módulo nativo específico).

## Banco de dados

- PostgreSQL + PostGIS, hospedado no Neon, **compartilhado entre `core-api` e `geo-api`** na Fase 1-2 (decisão consciente de custo/velocidade, ver pitch slide 13).
- Cada serviço deve ter seu próprio *schema* dentro do banco (`core` e `geo`), nunca tabelas soltas sem namespace — isso permite separar em bancos distintos depois sem reescrever tudo.
- Nenhuma tabela de `geo` deve ter foreign key direta para tabela de `core` no nível do banco — referência por ID (UUID) apenas, validada em nível de aplicação. Isso é o que viabiliza separar os bancos no futuro.

## Contratos entre serviços

- `core-api` ↔ `geo-api`: REST interno, JSON, versionado (`/v1/...`). Definir contrato em OpenAPI e versionar o arquivo de spec dentro do repo (`packages/contracts/` — ver `04-repositorio-e-git-workflow.md`).
- Autenticação interna `core-api` → `geo-api`: token de serviço (não é o token do usuário final), para poder auditar separadamente.
- API pública para parceiros de AV: chave de API + contrato de rate limit, versionada desde o dia 1 (`/public/v1/road-readiness/...`), mesmo que só tenha 1 cliente no futuro — trocar de contrato depois com clientes pagantes é caro.

## Decisões técnicas já tomadas (não reabrir sem motivo forte)

| Decisão | Motivo |
|---|---|
| PostGIS desde o início, mesmo no MVP | Retrofit de geoespacial em schema relacional comum depois é extremamente caro |
| Roteamento via OSRM/GraphHopper, não motor próprio | TSP/VRP é NP-difícil; heurística de biblioteca madura > implementação própria |
| `core-api` como único ponto de entrada | Centraliza auth, billing e rate limit; evita duplicar essas regras em dois serviços |
| Schemas separados por serviço no mesmo banco | Caminho de menor atrito para separar bancos fisicamente depois |
| React Native para mobile (não nativo separado) | Equipe pequena, uma base de código, sem necessidade atual de processamento nativo pesado |

## Definition of Done (arquitetura, Fase 1)

- [ ] `core-api` e `geo-api` sobem localmente via `docker-compose` com um comando.
- [ ] Contrato OpenAPI entre os dois serviços versionado no repo.
- [ ] Autenticação de usuário funcional (signup/login/refresh) no `core-api`.
- [ ] Schema `core` e `geo` criados no Postgres com migrations versionadas (Flyway/Liquibase para Java, Alembic para Python).
- [ ] Web consegue autenticar e chamar pelo menos um endpoint de cada serviço via `core-api`.
