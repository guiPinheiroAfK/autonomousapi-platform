# ADR 0014 — Designação motorista↔veículo (`driver_vehicle_assignment`)

**Status:** aceito
**Data:** 2026-08-15

## Contexto

O spec 07 precisa responder "qual é o meu veículo agora?" para o motorista logado. Em
frotas reais um motorista roda veículos diferentes ao longo do tempo, e um veículo passa
por motoristas diferentes — então "veículo atual" não é um campo no `driver` nem no
`vehicle`, é uma **relação com vigência**.

## Decisões

### Tabela de designação com histórico, `ended_at` nulo = ativa

`driver_vehicle_assignment` (driver_id, vehicle_id, tenant_id, started_at, ended_at,
created_at). A designação ativa é a de `ended_at is null`. Guardar o histórico (em vez de
sobrescrever um campo "veículo atual") permite depois comparar planejado vs. realizado,
auditar quem dirigia o quê e quando, e é pré-requisito natural do histórico de viagens.

### No máximo uma designação ativa por motorista **e** por veículo

Dois índices únicos parciais:
`unique (driver_id) where ended_at is null` e `unique (vehicle_id) where ended_at is null`.
Isso torna a invariante uma regra de banco, não convenção de código: um motorista tem no
máximo um veículo ativo, e um veículo no máximo um motorista ativo (não se dirige o mesmo
carro em dois lugares ao mesmo tempo neste modelo). Para trocar, o gestor encerra a
designação vigente antes de abrir a nova — o service faz isso de forma explícita, nunca
silenciosamente sobrescrevendo.

### "Meu veículo" é leitura da designação ativa, resolvida pelo token

O endpoint `GET /v1/me/vehicle` resolve o `driver` pelo token (ADR 0013), busca a
designação ativa dele e devolve o veículo — read-only. O motorista nunca informa
`vehicleId`; a associação vem do servidor.

### Escrita é só do gestor

Criar/encerrar designação é `GESTOR_FROTA`/`ADMIN`. O motorista não se designa a um
veículo — isso é decisão operacional do gestor.

## Trade-off aceito

A invariante "um veículo, um motorista ativo" não modela frotas onde dois motoristas
compartilham formalmente o mesmo veículo em turnos sobrepostos. Não é o caso do público
inicial (entrega/transporte com motorista por veículo); se aparecer, relaxar o índice
único do veículo (mantendo o do motorista) é mudança localizada.

## Reavaliar quando

- Aparecer necessidade real de turnos sobrepostos no mesmo veículo.
- O histórico de designação precisar alimentar relatório de utilização de ativo (aí vale
  indexar por período, não só por "ativo").
