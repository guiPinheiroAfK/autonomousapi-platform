# ADR 0006 — Kafka fora do projeto por ora

**Status:** aceito · **substitui** a versão anterior deste ADR, que apenas adiava
**Data:** 2026-08-12 (revisado no mesmo dia, ver "Correção")

## Contexto

Surgiu a proposta de colocar Kafka no caminho de ingestão de GPS. Hoje o caminho é síncrono
e direto:

```
app do motorista → core-api (POST /v1/trips/{id}/pings/batch) → geo-api → Postgres/PostGIS
```

A fila offline do app já garante que nenhum ping se perde quando o backend está fora: ele
fica no AsyncStorage e é reenviado depois.

## Correção que motivou a revisão

A primeira versão deste ADR dizia "adiar para a Fase 2, com gatilho". Isso era morno demais,
e estava apoiado num número errado.

O modelo de custo calculava os pings sobre **24 horas por dia** (`86400/intervalo`), ignorando
a própria premissa de 3 horas de operação diária que ele declarava. Corrigido o cálculo:

| | Número que sustentava "adiar" | Número real |
|---|---|---|
| Pings/dia por veículo | 8.640 | **1.080** |
| Writes/s com 500 frotas (7.500 veículos) | 6.000 | **750** |

6.000 writes/s é território onde uma fila começa a fazer sentido. **750 writes/s um Postgres
sozinho absorve com folga** — e isso já é o cenário de 7.500 veículos, muito além de onde o
produto está.

## Decisão

**Kafka fica fora do projeto.** Não é "adiado com gatilho": é descartado no horizonte
previsível.

O que ele resolveria — replay do histórico para reprocessar map matching, múltiplos
consumidores do mesmo fluxo, desacoplar ingestão de processamento — pressupõe um pipeline de
processamento que não existe. Hoje há um único consumidor, e ele faz `INSERT`.

O que custaria: dois contêineres a mais, mais uma dependência para subir o ambiente, e
trabalho de garantia de entrega (idempotência, offsets) que a fila do app já cobre.

## O que teria de mudar para reabrir

Não é uma data nem uma fase — é evidência:

1. Um segundo consumidor real do fluxo de pings (o pipeline de map matching da Fase 2 é o
   candidato natural), **e**
2. medição mostrando o `POST /pings/batch` como gargalo de verdade, com o geo-api sem
   absorver a escrita síncrona.

Com um só desses, provavelmente ainda não. Com os dois, vale reabrir.

## Consequências

O ponto de entrada continua preparado: `TripService#submitPings` recebe o lote e o encaminha
via `GeoApiClient`. Trocar esse encaminhamento por um produtor não muda a assinatura do método
nem o contrato com o app — só não vamos fazer isso agora.

**Dívida conhecida, independente de Kafka:** hoje um ping reenviado gera linha duplicada em
`geo.vehicle_gps_ping` (a PK é UUID gerado no servidor). Como o app pode reenviar um lote
parcialmente aceito, duplicata é possível. Para ingestão bruta é tolerável, mas qualquer
agregação futura vai precisar de deduplicação — o caminho natural é uma chave natural
`(vehicle_id, recorded_at)`, que serve de constraint no banco com ou sem fila.
