# scripts/test-e2e

Scripts pra testar o ambiente local ponta a ponta: apaga tudo, sobe de novo, e
popula um cenário de teste completo via API real do `core-api` — pra dar pra ver
como o front-end fica depois de cada mudança, sempre a partir de um estado
limpo e conhecido.

## O que cada um faz

- **`00-reset-env.sh`** — `docker compose down -v` (apaga o volume do Postgres) +
  `up -d --build`, espera o `core-api` responder `200` em `/v1/health`. Isso é o
  "apaga" do pedido: cada rodada começa de um banco vazio de verdade, não de um
  estado acumulado de execuções anteriores.

- **`01-seed.sh`** — o "cria": popula tudo via chamadas reais à API do `core-api`
  (não SQL direto), então qualquer validação/regra de negócio do backend também é
  exercitada, igual ao que o front-end faria:
  1. Signup (cria tenant + gestor) e confirmação de e-mail.
  2. 4 veículos.
  3. 2 motoristas — um deles com e-mail preenchido.
  4. Convite + aceite do motorista com e-mail (cria o login dele) e designação de
     um veículo. Como não há SMTP configurado em dev, o link de convite só aparece
     no log do container (`docker compose logs core-api`) — o script já extrai o
     token de lá sozinho, não precisa fazer nada manual.
  5. Despesas por veículo (combustível, manutenção, pedágio) e de frota (seguro,
     IPVA) — spec 10.
  6. 2 pontos de coleta reutilizáveis (spec 08 item 5).
  7. 2 rotas designadas ao motorista: uma multi-parada (`ROTA`, combinando pontos
     cadastrados + endereço avulso) e uma `TRANSFER` com valor combinado (spec 02).
  8. 2 ordens de serviço (uma preventiva já concluída, uma corretiva aberta) — é o
     que alimenta a aba Relatórios (financeiro de OS).
  9. Uma conversa de chat gestor→motorista com uma mensagem (ADR 0015).

  No final imprime o login do gestor e do motorista pra você logar no front-end e
  conferir.

- **`run-e2e-test.sh`** — roda os dois em sequência. É esse que você chama.

## Uso

```bash
bash scripts/test-e2e/run-e2e-test.sh
# em outro terminal, depois que terminar:
npm run dev:web   # http://localhost:5173
```

Pré-requisitos: Docker, `curl`, `jq`.

## Por que não SQL direto

Dava pra "injetar" truncando tabelas via `psql` direto, mas isso pula toda a
validação que o backend faz (ex.: `expense_entry` com CHECK constraints, a trava de
`data_execucao` não poder ser no passado, hash de senha, etc.) — um dado inserido
assim podia estar em um estado que a API nunca deixaria existir de verdade, e você
estaria testando o front contra um cenário que não reflete o sistema real. Por isso
os scripts sempre passam pela API, e o "apaga" é a nível de ambiente inteiro
(`docker compose down -v`), não de linha de tabela.

## Cuidado ao rodar os testes de integração depois da seed

Os testes de integração do `core-api` (`ExpenseEntryQueriesIntegrationTest`,
`VehicleAtributosJsonbIntegrationTest`, etc.) fazem `deleteAll()` em tabelas como
`vehicle`/`expense_entry` no `@BeforeEach`, presumindo um banco "só deles". Se você
rodar `bash scripts/test-e2e/run-e2e-test.sh` e, **sem resetar de novo**, rodar
`.\mvnw.cmd test` (ou `./mvnw test`) apontando pro mesmo Postgres (porta 5433), os
veículos/despesas que a seed deixou lá podem colidir com esse `deleteAll()` (erro de
FK, tipo `driver_vehicle_assignment_vehicle_id_fkey`). Não é bug — é o mesmo banco
sendo usado pelos dois propósitos ao mesmo tempo. Rode `00-reset-env.sh` de novo
antes da suíte de testes se isso acontecer, ou aponte os testes pra um Postgres
separado.

## Nota sobre acentuação (Windows)

`01-seed.sh` manda todo corpo de requisição via stdin (`--data-binary @-`), nunca
como argumento `-d "..."` do curl. Em `curl.exe` rodando de dentro do Git Bash no
Windows, um argumento de processo com acentuação (ex. "Troca de óleo") pode ser
corrompido na fronteira MSYS2 → processo nativo, e o backend responde `401` sem
relação nenhuma com o JWT — nada a ver com autenticação, é só corrupção de bytes
antes de chegar no servidor. Passar o corpo por stdin evita isso em qualquer
plataforma.
