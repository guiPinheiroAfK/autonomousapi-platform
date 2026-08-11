# ADR 0004 — Ownership de schema e migrations no banco compartilhado

- **Status:** Aceito
- **Data:** 2026-08-11
- **Contexto da spec:** `specs/01-arquitetura.md` (Banco de dados), `specs/04` (migrations)

## Contexto

`core-api` e `geo-api` compartilham um único Postgres+PostGIS (Neon) na Fase 1-2, cada um
com seu schema (`core` e `geo`). Duas ferramentas de migration diferentes apontam para o
mesmo banco: **Flyway** (core-api, Java) e **Alembic** (geo-api, Python). Sem regra clara,
os dois podem tentar criar/alterar o mesmo objeto e colidir.

## Decisão

1. **Cada serviço é dono exclusivo do seu schema.** Flyway só toca no schema `core`;
   Alembic só toca no schema `geo`. Nenhum dos dois altera objeto do outro.
2. **Criação dos schemas é bootstrap idempotente.** Cada serviço garante o seu schema com
   `CREATE SCHEMA IF NOT EXISTS <nome>` como primeira migration; a extensão PostGIS
   (`CREATE EXTENSION IF NOT EXISTS postgis`) é criada uma vez (dona: `geo-api`, que é quem
   depende dela), também idempotente.
3. **Flyway e Alembic mantêm suas tabelas de histórico dentro do próprio schema**
   (`core.flyway_schema_history`, `geo.alembic_version`) — nunca em `public` — para não
   competirem por uma tabela de controle compartilhada.
4. **Zero foreign key cross-schema no nível do banco** (regra da spec 01): referência entre
   `core` e `geo` é por UUID, validada na aplicação. É isso que viabiliza separar os bancos
   fisicamente no futuro sem reescrever schema.
5. Migrations moram **dentro de cada serviço** (`services/core-api/.../db/migration`,
   `services/geo-api/alembic/`), versionadas junto do código que as usa — não em `infra/`.

## Consequências

- Os dois serviços podem migrar independentemente sem corrida por objeto compartilhado.
- Separar `core` e `geo` em bancos distintos depois é uma mudança de config de conexão,
  não uma reescrita de schema.
- Ordem de subida não importa para criação de schema (idempotente), mas o `geo-api` deve
  garantir a extensão PostGIS antes de criar tabelas geográficas.
