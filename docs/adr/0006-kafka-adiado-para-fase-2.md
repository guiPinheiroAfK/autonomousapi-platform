# ADR 0006 — Kafka fica para a Fase 2, com gatilho definido

**Status:** aceito
**Data:** 2026-08-12

## Contexto

Surgiu a proposta de colocar Kafka no caminho de ingestão de GPS agora. Hoje o caminho é
síncrono e direto:

```
app do motorista → core-api (POST /v1/trips/{id}/pings/batch) → geo-api → Postgres/PostGIS
```

O volume atual é de uma frota de demonstração. A fila offline do app já garante que nenhum
ping se perde quando o backend está fora: ele fica no AsyncStorage e é reenviado depois.

## Decisão

**Não adotar Kafka nesta fase.** Adotar na Fase 2, quando o pipeline de map matching existir.

O que Kafka resolveria de verdade — e que hoje ninguém pede:

- **Replay.** Reprocessar o histórico de pings quando o algoritmo de map matching mudar.
  Sem pipeline de processamento, não há o que reprocessar.
- **Múltiplos consumidores.** O mesmo fluxo de pings alimentando map matching, agregação de
  `road_readiness_score` e avaliação de motorista. Hoje há um consumidor: gravar a linha.
- **Desacoplar ingestão de processamento.** Hoje não há processamento — só INSERT.

O que ele custaria agora: dois contêineres a mais no compose, mais uma dependência
operacional para subir o ambiente, e o trabalho de garantia de entrega (idempotência,
offsets) que a fila do app já cobre para o caso de uso atual.

## Gatilho para reavaliar

Adotar quando **qualquer um** destes for verdade:

1. O pipeline de map matching da Fase 2 começar (spec 05) — este é o gatilho esperado.
2. A ingestão de pings precisar de mais de um consumidor.
3. `POST /pings/batch` virar gargalo mensurável, com o geo-api sem conseguir absorver a
   escrita síncrona.

## Consequências

O ponto de entrada do Kafka já está preparado: `TripService#submitPings` recebe o lote e o
encaminha via `GeoApiClient`. Trocar esse encaminhamento por um produtor Kafka não muda a
assinatura do método nem o contrato com o app.

**Dívida conhecida a resolver junto com o Kafka:** hoje um ping reenviado gera linha
duplicada em `geo.vehicle_gps_ping` (a PK é um UUID gerado no servidor). Como o app pode
reenviar um lote parcialmente aceito, duplicata é possível. Para ingestão bruta isso é
tolerável, mas o pipeline de agregação vai precisar de deduplicação — o caminho natural é
uma chave natural `(vehicle_id, recorded_at)`, que serve tanto para constraint no banco
quanto para chave de partição no Kafka.
