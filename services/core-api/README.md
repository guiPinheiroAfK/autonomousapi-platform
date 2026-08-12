# core-api

Backend de orquestração (Java 17 / Spring Boot). Auth, billing, regras de negócio e
gateway para o `geo-api`. **Único ponto de entrada** dos clientes (spec 01).

Abrir no **IntelliJ IDEA** apontando para esta pasta (`services/core-api`).

## Rodar local

Precisa de um Postgres com o schema `core` (o Flyway cria as tabelas). O jeito
recomendado é subir tudo junto via `infra/docker-compose.yml` (Checkpoint E). Para rodar
só este serviço contra um Postgres existente:

```bash
export CORE_DB_URL=jdbc:postgresql://localhost:5432/autonomousapi
export CORE_DB_USER=autonomousapi
export CORE_DB_PASSWORD=autonomousapi
export CORE_JWT_SECRET=um-segredo-com-no-minimo-32-bytes-aqui-123456
./mvnw spring-boot:run
```

Não precisa de Maven instalado — o wrapper (`./mvnw`) baixa o Maven 3.9.9. Precisa de
JDK 17+ (`JAVA_HOME`).

## Build e testes

```bash
./mvnw clean package
```

### Testes de integração (Postgres de verdade)

Os testes que herdam de `IntegrationTestBase` rodam contra um Postgres real, porque teste
com repositório mockado não executa SQL e por isso não pega migration quebrada, JPQL
inválido nem tipo que estoura o range da coluna. Duas formas de fornecer o banco:

```bash
# 1. Apontando para um Postgres existente (é o que o CI faz, via service container).
#    Use um banco SEPARADO: a suíte limpa tabelas entre os testes.
docker exec autonomousapi-db-1 psql -U autonomousapi -d postgres -c "create database autonomousapi_test"

CORE_TEST_DB_URL=jdbc:postgresql://localhost:5433/autonomousapi_test \
CORE_TEST_DB_USER=autonomousapi CORE_TEST_DB_PASSWORD=autonomousapi \
./mvnw test

# 2. Sem a variável, o Testcontainers sobe um Postgres sozinho (zero config).
./mvnw test
```

> **Docker Desktop no Windows:** o Testcontainers pode falhar com "Could not find a valid
> Docker environment" mesmo com o Docker rodando — o named pipe do Docker Desktop recusa o
> handshake do cliente Java. Nesse caso use a forma 1, apontando para o Postgres do
> `infra/docker-compose.yml`. No Linux/CI o modo 2 funciona direto.

## Endpoints (Fase 1, fundação)

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/v1/auth/signup` | pública | Cria tenant + gestor de frota, devolve tokens |
| POST | `/v1/auth/login` | pública | Login por e-mail/senha, devolve tokens |
| POST | `/v1/auth/refresh` | pública | Rotaciona o refresh token, devolve novo par |
| GET  | `/v1/auth/me` | Bearer | Usuário autenticado atual |
| GET  | `/v1/health` | pública | Health agregado (checa o geo-api internamente) |
| GET  | `/v3/api-docs` | pública | OpenAPI (fonte do `packages/shared-types`, ADR 0003) |

## Migrations

Flyway, em `src/main/resources/db/migration`. É dono **exclusivo** do schema `core`
(ADR 0004) — nunca toca no schema `geo`.

## Dado de demonstração (opcional)

`com.autonomousapi.core.demo.DemoDataSeeder` popula uma frota fictícia completa
(tenant "RotaCerta Entregas Expressas", 1 gestor, 12 veículos, 7 motoristas, histórico
de custo) — útil para ver telas com dado real sem cadastrar tudo na mão. **Desligado
por padrão**, só roda com o profile Spring `demo` ativo:

```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
# ou, via docker-compose (na raiz do monorepo):
CORE_PROFILES=demo docker compose -f infra/docker-compose.yml up --build
```

Login de demo: `demo@rotacerta.com.br` / `demo12345`.

Idempotente (não duplica ao reiniciar) e **fácil de remover por completo**: é um
único arquivo, sem nenhum outro ponto do código dependendo dele — basta apagar
`src/main/java/com/autonomousapi/core/demo/`.
