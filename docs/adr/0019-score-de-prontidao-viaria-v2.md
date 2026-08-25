# ADR 0019 — Score de prontidão viária v2: passagem, tempo e confiança

**Status:** implementado — todos os 6 passos da "Sequência sugerida", incluindo as
métricas de qualidade que fecham a DoD da Fase 3 (spec 05)
**Data:** 2026-08-24
**Contexto da spec:** `specs/02-dados-mapas-rotas.md`, `specs/05-roadmap-fases.md` (Fase 3)
**Relacionado:** ADR 0006 (dívida de deduplicação), ADR 0009 (retenção de 30 dias)

**Implementado nesta rodada (mesmo dia, dois passes — Opus arquitetou, Sonnet codou):**
- Pré-requisito B — migration `0004`: dedup + constraint única `(vehicle_id,
  recorded_at)` em `geo.vehicle_gps_ping`.
- Pré-requisito A — `POST /internal/v1/gps/pings/batch`, contrato A1/A2 do anexo
  coberto por teste. `GeoApiClient`/`TripService#submitPings` (core-api) chamam o lote
  inteiro numa transação só.
- `recalcular_road_readiness` (v1) corrigido: upsert em massa em vez de laço por
  segmento; docstring alinhado ao que o código de fato faz.
- `maxspeed` do OSM (D3.1) — migration `0005` (`road_segment.maxspeed_kmh`),
  `app/osm_tags.py` (parser) e `scripts/import_osm_pilot.py` atualizados; a tag já
  vinha no payload do Overpass e era descartada.
- Decisão 1 — migration `0006`: `road_segment_passage` + `vehicle_gps_ping.road_segment_id`
  (com `ON DELETE SET NULL`/`CASCADE` corretos). `app/sessionization.py`
  (`reconstruir_passagens`, D1.1/D1.2) + endpoint manual + job no scheduler (1h).
- Decisões 2/3 — `app/time_buckets.py` (D2.1) + `app/road_readiness_v2.py`
  (`recalcular_road_readiness_v2`, D3.1-D3.3) + endpoint manual + **substituiu o v1 no
  scheduler** (D4) — v1 continua acessível via endpoint manual, linha `GLOBAL`
  preservada como linha de base histórica.
- `road_readiness_score` ganhou `time_bucket`/`confidence`, unique composta.
- Contrato público (app↔core-api) não mudou em nenhum passo — só o caminho interno
  core-api↔geo-api e o schema do `geo`.

**Bug de teste real encontrado e corrigido no caminho:** os testes de matching usavam
geometria fixa idêntica entre execuções; um segmento não limpo de uma sessão anterior
(zumbi) fazia o "vizinho mais próximo" empatar e silenciosamente casar com o registro
errado, mascarando o resultado como se fosse bug de produção. Corrigido dando offset
geométrico único por chamada de `_criar_segmento_de_teste()` nos três arquivos de teste
que criam segmento (`test_road_readiness.py`, `test_gps_ping_batch.py`,
`test_sessionization.py`) — elimina a possibilidade de empate mesmo que uma limpeza
falhe numa execução futura.

**Passo 6 — métricas de qualidade (`app/quality_metrics.py`, `GET
/internal/v1/road-readiness/quality-metrics`):** quatro métricas, escolhidas pra
responder exatamente ao que a spec 05 pede ("quantas observações por trecho, confiança
do score") mais dois riscos que o resto desta ADR já tinha identificado como
mensuráveis mas não medidos:
1. **Cobertura** — fração dos segmentos da malha com pelo menos 1 célula de score v2.
2. **Distribuição de confiança** — média + fração de células abaixo de
   `LIMIAR_CONFIANCA_BAIXA = 0.3` (ponto de partida documentado, a calibrar).
3. **Densidade de observação** — média de passagens por célula (o "quantas
   observações por trecho" do spec, na unidade certa — passagem, não ping).
4. **Taxa de ping atrasado** — fração de `vehicle_gps_ping` cujo `created_at -
   recorded_at` passa da janela de reconstrução de passagens; mede diretamente a
   limitação de D1.2 (ping tarde demais nunca vira passagem), em vez de deixá-la como
   suposição não verificada.
Read-only, computado sob demanda (barato — só leitura e resumo do que já está
gravado); sem job agendado, porque não há consumidor automatizado ainda que precise
do valor sempre fresco em background.

**Verificação:** 90 testes (30 novos: `test_osm_tags.py`, `test_sessionization.py`,
`test_time_buckets.py`, `test_road_readiness_v2.py`, `test_quality_metrics.py`), `ruff
check` limpo, suíte completa estável em 3 execuções consecutivas contra Postgres/PostGIS
real. Migrations 0004-0006 aplicadas e testadas contra o banco de dev com dado de teste
de carga real já presente (20 mil veículos). Os testes de métricas medem DELTA
(antes/depois de criar a fixture), não valor absoluto — o banco de dev é compartilhado
com outros testes e dado de carga, então ler contagem global exigiria isolamento que
não existe hoje. **Não aplicado ao ambiente Docker da demonstração** — os contêineres
`core-api`/`geo-api` seguem rodando a imagem anterior; só o Postgres compartilhado
(mesmo usado pelos contêineres) recebeu as migrations diretamente.

## Contexto

A Definition of Done da Fase 2 (spec 02) está cumprida e o código sustenta isso: import
de OSM, map matching, agregação periódica, retenção, roteamento OSRM e VRP com OR-Tools
estão no ar. O pipeline roda ponta a ponta.

O problema não é que falte etapa. É que **o número que sai da ponta ainda não significa
"prontidão viária"** — e a Fase 3 depende exatamente disso, porque ela usa o score como
peso de roteamento e exige "métricas de qualidade do score definidas e monitoradas".

Hoje (`app/aggregation.py`, `ALGORITHM_VERSION = "v1-obs-count"`):

```python
score = min(total_de_observacoes / 50, 1.0)
```

Três problemas se compõem aqui, todos verificados no código:

### 1. "Observação" hoje é ping, não passagem

O spec 02 define `road_segment_observation` como "**uma passagem** observada por um
veículo sobre um `road_segment`". Mas `routers/internal.py` grava uma observação **por
ping**: cada ping que cai a menos de 30 m de um segmento vira uma linha.

Consequência direta: um veículo parado num congestionamento sobre um segmento, mandando
ping a cada 10 s por 5 minutos, gera ~30 observações para **uma** passagem. O mesmo
veículo atravessando aquele segmento em fluxo livre gera ~1.

Como `score = contagem / 50`, **o score é mais alto exatamente onde o trânsito é pior**.
O sinal está invertido em relação a qualquer leitura intuitiva de "prontidão".

### 2. A dívida de deduplicação da ADR 0006 venceu

A ADR 0006 registrou, como dívida conhecida e então tolerável:

> hoje um ping reenviado gera linha duplicada em `geo.vehicle_gps_ping` (a PK é UUID
> gerado no servidor) [...] Para ingestão bruta é tolerável, mas **qualquer agregação
> futura vai precisar de deduplicação**.

Essa agregação futura agora existe e consome a contagem diretamente. A fila offline do
app reenvia lote parcialmente aceito por design (`TripService#submitPings` para no
primeiro erro e o app retenta o resto) — então duplicata não é hipótese, é o caminho
normal de quem volta de área sem sinal. Cada duplicata infla o score.

### 3. `avg_speed_kmh` não é média

O campo guarda a velocidade instantânea do ping (`avg_speed_kmh=ping.speed`), não uma
média sobre a passagem. É o único campo que carregaria sinal real de condição da via, e
hoje está semanticamente errado em relação ao próprio nome.

### O que isso implica para a Fase 3

A Fase 3 pede "quantas observações por trecho, **confiança** do score" como métrica de
qualidade. Isso só é expressável se contagem e score forem **dois números distintos**.
Hoje eles são o mesmo número, o que torna os dois inúteis: não dá para distinguir "via
ruim, muito observada" de "via boa, pouco observada".

## Decisão

Três mudanças estruturais, mais dois pré-requisitos mecânicos.

### Decisão 1 — Introduzir `road_segment_passage` (sessionização)

Nova entidade no schema `geo`, que é a "passagem" que o spec 02 sempre descreveu:

| Campo | Papel |
|---|---|
| `road_segment_id` | segmento atravessado |
| `entered_at` / `exited_at` | janela real da passagem |
| `avg_speed_kmh` | média **de verdade**, sobre os pings da passagem |
| `min_speed_kmh` | pega parada/lentidão dentro da passagem |
| `ping_count` | qualidade da amostra daquela passagem |

Sem `vehicle_id`, pelo mesmo princípio já aplicado em `road_segment_observation`: a
anonimização é estrutural desde a origem (ADR 0009), não um passo de expurgo posterior.

**Como é gerado:** job periódico que lê `vehicle_gps_ping` dentro da janela de retenção,
ordena por veículo e tempo, detecta transições de segmento e emite uma passagem por
corrida contígua de pings sobre o mesmo segmento.

**Por que job e não síncrono no `ingest_ping`:** manter o caminho quente burro e rápido,
e — principalmente — porque assim a sessionização é **reprocessável** dentro dos 30 dias.
Essa é literalmente a justificativa que a ADR 0009 deu para reter o bruto por 30 dias
("dá margem para reprocessar map matching"). A arquitetura já tinha previsto este passo.

**Ganho colateral em qualidade de matching:** uma corrida de pings dá contexto de
continuidade. Um ping isolado que "pula" para uma via paralela pode ser corrigido dentro
da passagem por regra de maioria/continuidade — que é exatamente a ambiguidade que
`app/matching.py` documenta como limitação do vizinho-mais-próximo. Ou seja: melhora o
matching **sem** adotar OSRM `/match` e sem infraestrutura nova.

### Decisão 2 — Score por faixa de tempo, não score único por segmento

Um segmento que flui às 3h e trava às 18h não tem um score único honesto. Agregar tudo
junto faz a média engolir o sinal inteiro.

`road_readiness_score` passa a ser chaveado por `(road_segment_id, time_bucket)`, onde
`time_bucket` = tipo de dia (útil/fim de semana) × faixa horária. Começar grosso
(ex. 4 faixas: madrugada, pico manhã, entrepico, pico tarde) — granularidade fina sem
volume de dado só produz células vazias.

### Decisão 3 — Score é qualidade de fluxo; confiança é campo separado

**Score** = combinação de dois componentes, ambos derivados das passagens:

1. **Razão de fluxo** — velocidade típica da faixa ÷ velocidade de referência do segmento.

   > **Correção sobre a primeira versão desta ADR.** A versão inicial propunha referência
   > **auto-calibrada** (p85 das passagens do próprio segmento). Isso está errado e foi
   > descartado ao detalhar a implementação: um segmento **cronicamente** congestionado
   > nunca observa fluxo livre, então o p85 dele também é uma velocidade ruim — a razão dá
   > ≈1 e a via campeã de engarrafamento pontua como excelente. Auto-calibrar destrói
   > justamente a comparabilidade entre vias, que é o propósito do índice.
   >
   > Referência correta é **externa ao histórico de tráfego**: a velocidade de projeto da
   > via. Ver detalhamento abaixo (`maxspeed` do OSM → padrão por `highway_type`).
2. **Confiabilidade** — dispersão das velocidades observadas (desvio padrão normalizado).
   Uma via que ora anda a 50, ora a 5, é menos "pronta" para operação autônoma do que uma
   que anda consistentemente a 30. Para o cliente-alvo de AV (spec 02: o dado existe para
   parceiro de veículo autônomo), **previsibilidade importa tanto quanto velocidade** —
   e é barato de calcular.

**Confiança** = campo próprio, derivado de número de passagens + recência da mais nova.
Nunca embutida no score.

Regra que decorre disso, e que é a armadilha central da Fase 3:
**score de baixa confiança nunca influencia peso de roteamento silenciosamente.** O
consumidor precisa poder distinguir "0,3 com 2 passagens" de "0,3 com 400 passagens".

`ALGORITHM_VERSION` passa a `v2-flow-reliability`. A coluna já existe e já é gravada —
v1 e v2 podem coexistir durante a validação sem migração destrutiva.

### Pré-requisito A — Endpoint de ingestão em lote

Estado atual verificado: o app manda lote para o core-api (`POST /v1/trips/{id}/pings/batch`),
mas `TripService#submitPings` itera o lote chamando `GeoApiClient#ingestGpsPing` **uma
vez por ping** — e cada chamada é um POST HTTP para `/internal/v1/gps/pings` com transação
e commit próprios.

Um motorista voltando de área sem sinal com 500 pings na fila = 500 chamadas HTTP
sequenciais, 500 transações, 500 consultas espaciais.

Criar `POST /internal/v1/gps/pings/batch`: uma transação, um passe de matching.

**Isto não reabre a ADR 0006 — reforça a decisão dela.** Aquele ADR listou dois gatilhos
para reconsiderar Kafka: (1) um segundo consumidor real do fluxo, e (2) medição mostrando
o `/pings/batch` como gargalo real. O gargalo aqui não é a escrita no Postgres (750
writes/s, que a ADR mostrou que o Postgres absorve com folga) — é o *fan-out HTTP
desnecessário*, que se resolve com um endpoint, não com uma fila.

### Pré-requisito B — Chave natural em `vehicle_gps_ping`

Constraint única em `(vehicle_id, recorded_at)`, com `ON CONFLICT DO NOTHING` na ingestão
— o caminho que a própria ADR 0006 já apontou como natural. Precisa vir **antes** de
qualquer coisa que conte passagens, senão a contagem herda o problema.

## Detalhamento das Decisões 1-3 (nível de implementação)

Escrito depois de implementados os pré-requisitos, com o código já lido. Cada número
arbitrário aqui é config, não constante mágica, e está marcado como "a calibrar".

### D1.1 — Detecção de passagem

Para os pings de **um veículo**, ordenados por `recorded_at`, cada um já casado a um
segmento (ou `NULL`):

- **Passagem** = corrida contígua maximal de pings sobre o mesmo `road_segment_id`.
- **Quebra a corrida** quando: (a) o segmento muda, ou (b) o intervalo entre pings
  consecutivos excede `GEO_PASSAGE_GAP_MAX_MINUTES` (padrão 5 min). O caso (b) separa
  "atravessou a via" de "desligou o veículo e voltou à mesma via horas depois" — sem
  ele, as duas viram uma passagem só, com duração absurda.
- Corridas com segmento `NULL` são descartadas (ping fora da malha importada).

**Não filtrar passagem por número mínimo de pings.** É tentador exigir ≥2 pings ("1 ping
não dá média"), mas isso reintroduz exatamente o viés que esta ADR existe para corrigir:
atravessar um segmento curto em velocidade alta gera 1 ping, ficar parado nele gera
dezenas. Filtrar por amostra mínima descarta preferencialmente as passagens rápidas.
`ping_count` fica registrado na linha e a **confiança** downstream trata amostra pequena
— que é o lugar certo para isso.

### D1.2 — Reprocessamento idempotente e a tensão com a ADR 0009

O job precisa saber o que já processou. As três saídas óbvias esbarram no desenho de
privacidade:

- *Marcar ping como processado* — mutação em dado bruto, e a coluna morre junto com o
  ping na purga de 30 dias.
- *Deduzir das passagens existentes* — **impossível por construção**: `road_segment_passage`
  não tem `vehicle_id` (ADR 0009, anonimização estrutural), então não dá para perguntar
  "até quando já processei o veículo X".
- *Marca d'água global por `recorded_at`* — quebra com a fila offline: um ping gravado
  ontem pode chegar hoje, já "atrás" da marca d'água, e nunca virar passagem.

**Decisão: apagar-e-reconstruir sobre janela limitada.** A cada execução, com
`B = agora - GEO_PASSAGE_REBUILD_WINDOW_HOURS` (padrão 72 h):

1. `DELETE FROM geo.road_segment_passage WHERE entered_at >= B`
2. Reconstruir a partir dos pings com `recorded_at >= B - margem`, onde
   `margem = 1 h` (folga maior que qualquer passagem plausível, dado o corte de 5 min).
3. Emitir **apenas** as passagens com `entered_at >= B`.

O passo 3 é o que evita duplicata: uma passagem iniciada antes de `B` não é apagada no
passo 1, então também não pode ser reinserida. A margem do passo 2 existe só para que
essa passagem seja *reconhecida* e descartada, em vez de virar uma passagem parcial nova.

Idempotente por construção, sem estado por veículo, sem `vehicle_id` persistido, e
tolerante a ping atrasado dentro da janela.

**Limitação documentada:** ping que chega mais de 72 h depois de gravado nunca vira
passagem. É mensurável (`created_at - recorded_at > janela`) e vira uma das métricas de
qualidade da Fase 3 — se o número for relevante no piloto, aumentar a janela.

**Frequência:** este job não precisa dos 5 min do `road_readiness`. De hora em hora basta
— passagem não é dado de tempo real.

### D2.1 — Faixas de tempo

`time_bucket` = `{tipo_de_dia}_{faixa}`, ex. `UTIL_PICO_MANHA`:

| Faixa | Horas (local) |
|---|---|
| `MADRUGADA` | 00–05 |
| `PICO_MANHA` | 06–09 |
| `ENTREPICO` | 10–16 |
| `PICO_TARDE` | 17–20 |
| `NOITE` | 21–23 |

Tipo de dia: `UTIL` (seg–sex) / `FDS` (sáb–dom). Feriado não é tratado na v1 — cai em
`UTIL` e polui a média; documentado, não silencioso.

**Fuso é obrigatório, não detalhe.** `recorded_at` é `timestamptz` (UTC). "Pico da manhã"
em UTC não significa nada — a conversão para o fuso do piloto (`America/Sao_Paulo`, ver
`app/pilot_area.py`) tem que acontecer antes de derivar a faixa. Sai como config
(`GEO_PILOT_TIMEZONE`) porque a área do piloto é declaradamente provisória.

### D3.1 — Velocidade de referência (o que substitui o p85 auto-calibrado)

Em ordem de preferência, por segmento:

1. **`maxspeed` do OSM**, quando presente. **Requer uma mudança pequena no import:**
   `scripts/import_osm_pilot.py` já recebe as `tags` do Overpass (`out geom`) e lê só
   `name` e `highway` — `maxspeed` **chega no payload e é jogado fora hoje**. Passar a
   ler e persistir custa uma coluna em `road_segment` e zero requisição extra.
2. **Padrão por `highway_type`** quando `maxspeed` falta (cobertura de `maxspeed` no
   Brasil é irregular). Tabela inicial, a calibrar: `motorway` 90, `trunk` 80,
   `primary` 60, `secondary` 50, `tertiary` 40, `residential` 30, `living_street` 20,
   `service` 20; sufixo `_link` herda o valor do pai.
3. **Teto observado**: se o p85 das passagens do segmento for **maior** que a referência
   acima, usar o p85. Só corrige para cima — uma via que comprovadamente flui mais rápido
   que o esperado pelo tipo/tag. Nunca para baixo, que é o que causava a falha original.

### D3.2 — Fórmulas

Por `(segmento, faixa)`, sobre as passagens daquela célula com `avg_speed_kmh` não nulo:

```
velocidade_tipica = mediana(avg_speed_kmh das passagens)     # mediana, não média:
                                                             # robusta a outlier de GPS
razao_fluxo       = clamp(velocidade_tipica / referencia, 0, 1)

cv                = desvio_padrao / media                    # coeficiente de variação
confiabilidade    = 1 - clamp(cv, 0, 1)

score             = 0.7 * razao_fluxo + 0.3 * confiabilidade
```

Os pesos `0.7/0.3` são ponto de partida documentado. A justificativa de existir o segundo
termo está na Decisão 3 (previsibilidade importa para o cliente de AV); a proporção entre
eles só se decide com dado do piloto.

Célula sem nenhuma passagem com velocidade: score `NULL`, não zero. Zero significaria
"via péssima"; `NULL` significa "não sei" — e a distinção é o ponto inteiro desta ADR.

### D3.3 — Confiança

```
amostra   = min(passagens / 30, 1.0)                # 30 = referência a calibrar
recencia  = 1.0 se a passagem mais nova tem < 7d
            decaindo linearmente até 0 aos 90d
confianca = amostra * recencia
```

Produto, não média: amostra grande porém velha **e** amostra nova porém minúscula devem
ambas resultar em confiança baixa. Média deixaria uma compensar a outra.

### D4 — Esquema e convivência com o v1

`geo.road_segment_passage` (nova):

| Coluna | Tipo | Nota |
|---|---|---|
| `id` | uuid PK | |
| `road_segment_id` | uuid FK | |
| `entered_at` / `exited_at` | timestamptz | janela real da passagem |
| `avg_speed_kmh` | double, nulo | média sobre os pings com velocidade |
| `min_speed_kmh` | double, nulo | pega lentidão dentro da passagem |
| `ping_count` | int | qualidade da amostra |
| `created_at` | timestamptz | |

Sem `vehicle_id` (ADR 0009). Índice em `(road_segment_id, entered_at)` e em `entered_at`
(o `DELETE` da janela usa este).

`geo.road_segment` ganha `maxspeed_kmh` (double, nulo).

`geo.road_readiness_score`: ganha `time_bucket` (varchar) e `confidence` (double, nulo);
a unique passa de `road_segment_id` para `(road_segment_id, time_bucket)`.

**Convivência:** as linhas do v1 recebem `time_bucket = 'GLOBAL'` na migração, o que as
mantém válidas sob a nova unique sem colidir com nenhuma célula do v2. O v2 **substitui**
o v1 no scheduler — não faz sentido gastar duas passagens por ciclo para manter vivo um
número que esta ADR documenta como invertido. As linhas `GLOBAL` ficam como linha de base
histórica para comparação, distinguíveis por `algorithm_version`, e não são apagadas.

## Alternativas consideradas

**Adotar OSRM `/match` agora.** Descartado por ora: a sessionização já entrega boa parte
do ganho de desambiguação de graça (ver Decisão 1), e `app/matching.py` documenta o
trade-off atual conscientemente. Gatilho para reabrir, agora mensurável: taxa alta de
*flapping* de segmento dentro de uma mesma passagem — que só dá para medir depois que a
passagem existir como entidade.

**Manter score como contagem e só renomear para "cobertura".** Honesto, e resolveria o
problema semântico — mas deixa a Fase 3 sem sinal nenhum para usar como peso de
roteamento, que é o objetivo declarado da fase. Cobertura vira o campo de confiança, que
é o papel correto dela.

**Sessionizar no `ingest_ping` (síncrono).** Exigiria manter estado de passagem aberta por
(veículo, segmento) no caminho quente, e perderia a reprocessabilidade dentro dos 30 dias.

## Consequências

- **Migração:** nova tabela `road_segment_passage`; `road_readiness_score` ganha
  `time_bucket` (muda a unique key de `road_segment_id` para o par) e `confidence`.
  `road_segment_observation` permanece durante a transição — v1 continua calculável até
  v2 ser validado com dado do piloto.
- **O score v2 só fica confiável com volume real.** Fluxo livre por p85 sobre 3 passagens
  é ruído. O fallback por `highway_type` cobre o início, e o campo de confiança torna essa
  imaturidade **legível para o consumidor** em vez de silenciosa — que é precisamente o que
  a Fase 3 pede.
- **A agregação atual não escala como está, independente desta ADR.** `recalcular_road_readiness`
  recalcula todos os segmentos com observação a cada 5 minutos, com um `SELECT` por segmento
  em laço Python. Além disso, o docstring afirma que só recalcula segmentos "com pelo menos
  1 observação nova desde a última passagem", o que **não** é o que o código faz (não há
  filtro de recência) — divergência a corrigir junto, seja no doc, seja no código.
- **Nada disso muda contrato público.** Não existe API pública de prontidão viária ainda
  (Fase 4). A janela para mudar o modelo do score sem quebrar consumidor externo é agora.

## Sequência sugerida

Cada passo é verificável sozinho:

1. ~~Pré-requisito B (chave natural + dedup)~~ — feito
2. ~~Pré-requisito A (endpoint em lote)~~ — feito
3. ~~`maxspeed` no import de OSM (D3.1 item 1)~~ — feito
4. ~~Decisão 1 (`road_segment_passage` + job de sessionização, D1.1/D1.2)~~ — feito
5. ~~Decisão 2 + 3 (score v2 por faixa de tempo e confiança, D2.1/D3.1/D3.2/D3.3)~~ — feito
6. ~~Métricas de qualidade do score (DoD da Fase 3)~~ — feito (`app/quality_metrics.py`)

Todos os 6 passos implementados. O passo 6 exigia decisão de produto (o que medir, que
limiar dispara ação) — as quatro métricas escolhidas e seus limiares estão documentados
logo acima, no início deste ADR.

## Anexo — contrato da ingestão em lote

Três restrições não óbvias. Todas foram derivadas de ler `apps/mobile/src/offline/pingQueue.ts`
e `TripService#submitPings`; violar qualquer uma quebra em produção de forma silenciosa,
por isso ficam registradas aqui e não só no código.

### A1. `accepted` conta pings *tratados*, nunca linhas inseridas

`flushBatch` (pingQueue.ts) descarta da fila exatamente os `accepted` primeiros itens, e
para quando `accepted <= 0`. Com dedup por `ON CONFLICT DO NOTHING`, um lote de 10 pings
em que 3 já existiam insere 7 linhas.

Se a resposta disser `accepted: 7`, o app descarta 7, retenta os 3 restantes, esses 3 são
deduplicados de novo → `accepted: 0` → `flushBatch` para e **a fila trava para sempre**
com 3 pings que já estavam salvos no servidor.

Portanto: `accepted` = pings durably tratados = **inseridos + já existentes**. Um ping
duplicado é sucesso do ponto de vista do cliente, não falha. O único caso de `accepted`
menor que o lote é falha real de escrita.

### A2. Ping deduplicado não pode gerar observação

Hoje `ingest_ping` grava a `road_segment_observation` incondicionalmente depois de inserir
o ping. Se o `INSERT` do ping for descartado por conflito mas a observação continuar sendo
criada, a deduplicação não corrige nada no score — que é justamente o motivo de existir do
Pré-requisito B (ver Contexto, item 2).

A criação da observação precisa ficar condicionada ao ping ter sido de fato inserido
(ex.: `RETURNING id` no upsert, e só seguir para o matching com as linhas retornadas).

### A3. A migração precisa deduplicar antes de criar a constraint

`geo.vehicle_gps_ping` já contém duplicatas em qualquer ambiente que tenha rodado o app —
a constraint única em `(vehicle_id, recorded_at)` falha ao ser criada. A migration Alembic
precisa, na ordem: apagar duplicatas mantendo uma linha por par (ex. `ctid` mínimo), depois
criar a constraint.

### Consequência boa: retry de lote inteiro passa a ser seguro

Com A1 e A2 no lugar, a ingestão vira idempotente por construção. Isso permite simplificar
`TripService#submitPings`, hoje obrigado a processar ping a ping e parar no primeiro erro
para não duplicar: o lote passa a ser uma transação só, e falha parcial vira `accepted: 0`
com o app reenviando o lote inteiro sem risco.
