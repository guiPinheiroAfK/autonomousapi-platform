# 09 — Custo Estimado e Precificação de Rota

> **Amendment (spec 10):** o `fuel_entry` desenhado abaixo foi absorvido por `expense_entry` (spec `10-gestao-de-custos.md`) — não é mais uma tabela própria, é `expense_entry` com `categoria = combustivel` e os campos `litros_ou_kwh`/`odometro` preenchidos. O resto desta spec (fórmula de custo estimado, valor sugerido, evolução do consumo) continua valendo como está; só a tabela de origem do dado de abastecimento mudou de nome/lugar.

## Contexto — duas coisas diferentes, não confundir

O que já existe (`VehicleCostService`) é **custo histórico**: soma de lançamentos já feitos, dividido pelo odômetro atual — responde "quanto esse veículo já custou até hoje". O que esta spec cobre é **custo estimado por rota**, calculado *antes* de rodar, a partir de distância (OSRM) e consumo do veículo — responde "quanto essa corrida específica vai custar". São dois conceitos complementares, não um substituindo o outro; o histórico continua existindo e, no médio prazo, deveria alimentar a precisão do estimado (ver seção de evolução).

## Consumo do veículo — começa manual, evolui pra calculado

**MVP:** `consumo_medio_km_por_litro` (ou `km_por_kwh` para elétrico) como mais um atributo no JSONB `vehicle.atributos` (mesmo padrão já usado para tipo de combustível) — o gestor preenche na hora de cadastrar o veículo. Zero migration nova, reaproveita o que já existe.

**Evolução (o que resolve a pergunta "baseado em quanto o carro roda e quanto abastece"):** criar `fuel_entry` (schema `core`) — um registro por abastecimento: veículo, litros (ou kWh), valor pago, odômetro no momento, data. Com histórico suficiente (ex. últimos 5-10 abastecimentos), um job recalcula `consumo_medio_km_por_litro` como média móvel real (distância percorrida entre dois abastecimentos ÷ litros consumidos no intervalo) e **substitui** o valor manual do JSONB — o campo lido pela fórmula de custo é o mesmo, só muda quem escreve nele (gestor no início, job depois). Isso segue o mesmo padrão já estabelecido para `road_readiness_score`/`vehicle_condition_score`: começa estimado à mão, vira calculado quando há dado real suficiente, nunca fica "hardcoded como definitivo".

Modelo de dados (schema `core`):
- `fuel_entry`: veículo, litros_ou_kwh, valor_pago, odometro, data, fonte (`manual` | futuramente `integração`, sem integração nenhuma prevista por ora).
- `vehicle.atributos.consumo_medio_km_por_litro` (ou `km_por_kwh`): mesmo campo, agora com dois possíveis autores (manual vs. job de recálculo) — marcar `consumo_calculado_em` para o front saber se está mostrando estimativa manual ou valor com base real.

## Preço de referência de combustível — manual, mesmo tratamento da FIPE

Não integrar API de preço de combustível agora — mesma lógica já aplicada à FIPE (spec 06, item 2): tabela de referência simples (`fuel_price_reference`: tipo de combustível, preço, data de atualização), mantida manualmente. Automatizar isso não é o gargalo do produto neste estágio; se algum dia virar, o padrão já é conhecido (avaliar provedor pago vs. curadoria própria, mesma decisão já tomada pra FIPE).

## Fórmula de custo estimado (v1 — só combustível)

```
custo_estimado = distancia_km (do OSRM) × (1 / consumo_medio_km_por_litro) × preco_combustivel_referencia
```

Versionar a fórmula (`pricing_formula_version`) desde o início, mesmo v1 sendo simples — mesma disciplina já usada pro `road_readiness_score`: nunca assumir que a fórmula atual é a definitiva, e permitir comparar resultados entre versões quando ela mudar.

Exposto como campo novo (`custoEstimado`) na resposta de `GET /v1/routes/preview` quando `vehicleId` for informado — rota e custo estimado juntos na mesma resposta.

### Evolução natural da fórmula (v2, não é MVP)

Custo de rodar não é só combustível — o `VehicleCostService` já calcula custo histórico por km incluindo manutenção. Uma v2 da fórmula poderia somar um componente de manutenção amortizada por km (vindo do histórico já calculado) ao custo de combustível, chegando mais perto do custo operacional real. Não implementar agora — só deixar registrado que a v1 é deliberadamente incompleta (só combustível), não um erro de escopo.

## Valor sugerido — conceito novo, camada sobre o custo estimado

```
valor_sugerido = custo_estimado × (1 + margem_configuravel_por_tenant)
```

Margem fixa por tenant pra começar (configurável em cadastro/config da conta) — nada de precificação dinâmica por demanda, hora do dia, ou concorrência: isso é over-engineering pro estágio atual do produto. Mesma disciplina de versionamento da fórmula de custo se aplica aqui.

### Gancho futuro (Fase 3+, quando existir `road_readiness_score` com confiança)

Trechos com prontidão viária ruim implicam mais tempo, mais desgaste, mais risco de dano ao veículo — isso poderia virar um multiplicador adicional no valor sugerido (rota por trecho ruim custa mais caro pro cliente final). Não é ação agora — é só o motivo pelo qual vale manter `valor_sugerido` como campo derivado e versionado desde já, em vez de calculado ad-hoc no front: quando esse gancho existir, muda só a fórmula do backend, não o contrato.

## Definition of Done

- [ ] `consumo_medio_km_por_litro`/`km_por_kwh` no JSONB do veículo, preenchível manualmente no cadastro.
- [ ] `custoEstimado` aparecendo em `GET /v1/routes/preview` quando `vehicleId` informado, com fórmula v1 (só combustível) versionada.
- [ ] `valorSugerido` calculado sobre o custo estimado, com margem configurável por tenant.
- [ ] `fuel_entry` modelado (mesmo que sem tela de cadastro completa no primeiro corte) e job de recálculo de consumo médio funcionando com dado de pelo menos um veículo real ou simulado.
- [ ] Campo indicando se o consumo exibido é manual ou calculado (`consumo_calculado_em` preenchido ou nulo).
