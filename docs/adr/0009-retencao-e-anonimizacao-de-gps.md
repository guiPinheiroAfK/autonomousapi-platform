# ADR 0009 — Retenção e anonimização de GPS bruto

**Status:** aceito
**Data:** 2026-08-12

## Contexto

Spec 02 exige que a política de retenção/anonimização de `vehicle_gps_ping` esteja
"documentada e implementada, não só planejada" antes de a Fase 2 ser considerada
completa. `vehicle_gps_ping` é dado pessoal enquanto existir vinculado a `vehicle_id` —
e, transitivamente, ao motorista daquela viagem (rastreável via `core-api`).

## Decisão

**Duas camadas de dado, dois destinos:**

| Dado | Identificável? | Retenção |
|---|---|---|
| `vehicle_gps_ping` (bruto: lat/lon, `vehicle_id`, timestamp) | Sim | 30 dias, depois apagado |
| `road_segment_observation` (agregado: segmento, velocidade média, timestamp) | Não — nunca carregou `vehicle_id` | Indefinida |

A anonimização não acontece "antes de apagar o bruto" como um passo de expurgo — ela é
estrutural desde a origem: `road_segment_observation` (`app/models.py`) **nunca teve**
coluna de veículo ou motorista. O map matching (`app/matching.py`) lê o ping, decide o
segmento, e grava só isso. Não existe um `UPDATE` que "anonimiza depois"; o dado
identificável e o dado agregado nascem em requisições separadas e não têm chave em comum
no schema (por design, não por convenção de código).

**Janela de 30 dias** (`GEO_GPS_RETENTION_DAYS`, `app/config.py`) é ponto de partida
documentado, não número regulatório — dá margem para reprocessar map matching (ex. depois
de importar mais segmentos de OSM numa área) sem depender de o app reenviar o ping. Ajustar
conforme necessidade real de reprocessamento apareça.

**Execução:** job periódico in-process (`app/scheduler.py`, `purgar_pings_antigos` rodando
1x/dia) — ver ADR de arquitetura do scheduler em `app/scheduler.py` (mesmo raciocínio da
ADR 0006: sem fila/worker novo enquanto o volume não pedir).

## Consequências

- `road_readiness_score`, o agregado final exposto via API (Fase 4), nunca teve como
  carregar dado de motorista — não porque foi filtrado, mas porque a coluna nunca existiu
  em nenhuma tabela no caminho até ele.
- Reprocessar matching de um ping só é possível dentro da janela de 30 dias — depois disso,
  a observação já gravada (se houve match) é tudo que resta.
- Se o produto precisar de retenção mais longa do bruto (ex. auditoria, disputa de custo por
  km), isso é uma decisão de produto/jurídico a reabrir explicitamente aqui, não um ajuste
  silencioso de config.
