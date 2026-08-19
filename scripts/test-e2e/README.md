# scripts/test-e2e — onde colocar estes arquivos

Branch já criada no GitHub a partir de `develop`: **`chore/scripts-seed-e2e`**.

Coloque os 3 arquivos dentro dela em:

```
scripts/test-e2e/00-reset-env.sh
scripts/test-e2e/01-seed.sh
scripts/test-e2e/run-e2e-test.sh
```

Forma mais rápida: peça pro Claude Code (que já tem acesso de escrita ao repo) criar
essa pasta com esses 3 arquivos nessa branch e commitar. Ou copie manualmente e rode:

```bash
git checkout chore/scripts-seed-e2e
mkdir -p scripts/test-e2e
# copiar os 3 arquivos pra lá
chmod +x scripts/test-e2e/*.sh
git add scripts/test-e2e
git commit -m "chore(scripts): script de reset + seed e2e para teste manual local"
git push -u origin chore/scripts-seed-e2e
```

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
