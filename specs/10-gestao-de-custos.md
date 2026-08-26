# 10 — Gestão de Custos

## Contexto — o que já existe, disperso

O produto já tem várias peças de custo, cada uma resolvendo um pedaço: custo histórico por km (`VehicleCostService`, RF003/HU03), comparação entre veículos e frotas (HU15), valor de mercado via FIPE e nota de condição por sinistro/manutenção (spec 06), custo estimado por rota a partir de consumo real de combustível (spec 09), e valor combinado pra transfer (spec 02). O que falta é o que amarra tudo isso: despesas categorizadas (hoje é "lançamento genérico", sem estrutura), orçamento com alerta, e um jeito de saber se as estimativas estão batendo com a realidade.

## 1. Categorização de despesas — `expense_entry` como tabela única

Hoje "lançamento manual" (o que alimenta `VehicleCostService`) não tem categoria — é só um valor solto. Substituir por `expense_entry` (schema `core`), estruturado:

- `expense_entry`: tenant, veículo (nullable — despesa pode ser da frota inteira, ex. seguro corporativo, não de um veículo específico), categoria (`combustivel`, `manutencao`, `seguro`, `ipva`, `multa`, `pedagio`, `lavagem`, `outro`), valor, data, descrição, fonte (`manual` | `route_plan` quando vier de um transfer concluído | futuramente outras integrações).
- Quando categoria = `combustivel`, dois campos adicionais ficam disponíveis (nullable pras outras categorias): `litros_ou_kwh`, `odometro` — é exatamente o que a spec 09 desenhou como `fuel_entry`. **Consolidação:** `fuel_entry` não vira uma tabela separada — é `expense_entry` com `categoria = combustivel` e esses dois campos preenchidos. Isso evita manter duas tabelas de "gasto" que precisariam ser somadas juntas toda vez que alguém quiser ver custo total. O job de recálculo de consumo médio (spec 09) passa a ler `expense_entry` filtrado por categoria, em vez de uma tabela própria.

`VehicleCostService.summary()` passa a agregar `expense_entry` por categoria, período e veículo/frota — o que já existia (custo por km) continua funcionando, só com o dado embaixo mais estruturado, permitindo abrir "quanto foi combustível vs. quanto foi manutenção" pela primeira vez.

## 2. Orçamento e alerta de estouro

- `budget`: tenant, escopo (veículo específico **ou** frota inteira), categoria (opcional — pode ser orçamento geral ou só de uma categoria, ex. "R$2.000/mês só de manutenção"), período (mensal, ponto de partida — outros períodos ficam pra quando pedirem), valor_limite.
- Job periódico soma `expense_entry` do escopo/categoria/período contra o `valor_limite` e dispara alerta (reaproveita o mecanismo de notificação já existente, RF012) em dois patamares: 80% (aviso) e 100%+ (estouro). Não bloqueia nenhum lançamento — é aviso, não trava; ninguém deveria ser impedido de registrar uma despesa real só porque passou do orçamento.

## 3. Estimado x realizado — dois níveis diferentes, não confundir

Vale separar isso com cuidado, porque a comparação ingênua ("essa rota custou o que a gente estimou?") não é operacionalmente viável — motorista não abastece exatamente no fim de cada rota, então não dá pra atribuir uma despesa de combustível específica a uma viagem específica com confiança. Duas comparações diferentes, essas sim viáveis:

### Rentabilidade por transfer (nível individual, viável)

Pra `route_plan` de categoria `TRANSFER` (spec 02), que já tem `valor` combinado e `custo_estimado` calculado na criação (spec 09), dá pra calcular `margem_realizada = valor - custo_estimado` assim que a rota é concluída — não depende de saber o gasto real de combustível daquela viagem específica, só compara receita combinada contra a estimativa de custo que já existia. Isso é imediatamente possível e informa se a margem configurada (spec 09) está gerando lucro de verdade ou só no papel.

### Recalibração agregada por período (nível de frota/veículo, não por rota)

Pra saber se a fórmula de custo estimado (spec 09) está precisa de verdade, a comparação certa é por período: somar `custo_estimado` de todos os `route_plan` concluídos num mês, por veículo, contra a soma real de `expense_entry(categoria=combustivel)` do mesmo veículo no mesmo mês. A diferença entre os dois indica se `consumo_medio_km_por_litro` (spec 09) está desatualizado — e alimenta o mesmo job de recálculo já desenhado lá, fechando o loop: estimativa → uso real → ajuste → estimativa melhor.

## Modelo de dados (schema `core`) — resumo

- `expense_entry` (substitui/absorve `fuel_entry` da spec 09).
- `budget`.
- Consulta agregada (view ou query) por tenant/veículo/categoria/período — reaproveitada tanto pelo `VehicleCostService` quanto pelo job de comparação estimado x realizado.

## Front-end (web) — nova aba "Custos"

Gestor-only, como o resto do painel administrativo — motorista não tem (nem deveria ter) acesso a isso, consistente com o princípio "operador, não gestor" da spec 07. Item novo no `Sidebar` (ícone tipo `Wallet`/`DollarSign`), lazy-loaded como as demais páginas, seguindo o mesmo padrão de `Card` + empty states desenhados já estabelecido (não repetir o erro identificado no chat antes do redesign — nada de tela genérica).

A página se organiza em 4 sub-visões (abas internas ou seções na mesma tela, a decisão de UI fica a critério de quem implementa — funcionalmente são 4 blocos distintos):

1. **Visão geral** — resumo por categoria e por período (seletor de mês/intervalo), reaproveitando o mesmo padrão de gráfico já usado no Dashboard (PR #18, gráficos de custo mensal) — aqui quebrado por categoria (combustível, manutenção, seguro, etc.) em vez de só total. Reaproveita `VehicleCostService.summary()` estendido para agrupar por categoria.
2. **Despesas** — lista de `expense_entry` (padrão tabela/lista como `DriversPage.tsx`), com filtro por veículo/categoria/período. Botão "Nova despesa" abre modal (reaproveitar componente `Modal` já usado em outros fluxos) com categoria, valor, data, veículo (opcional) e, quando categoria = combustível, os campos extras de litros/odômetro.
3. **Orçamento** — lista de `budget` configurados (por veículo ou por frota, por categoria opcional), cada um com barra de progresso mostrando % já consumido do limite — muda de cor ao cruzar 80% (aviso) e 100% (estourado), mesmo padrão visual de alerta já usado em outros lugares do produto (ex. CNH vencendo). Botão "Novo orçamento" com formulário simples.
4. **Rentabilidade de transfers** — tabela dos `route_plan` de categoria `TRANSFER` concluídos, mostrando valor combinado, custo estimado e `margem_realizada` calculada, com totalizador no período selecionado. Empty state se não houver nenhum transfer concluído ainda.

Nenhuma dessas telas introduz componente visual novo do zero — reaproveita `Card`, `Modal`, tabela/lista já padronizados e os componentes de gráfico já usados no Dashboard, só com dado e agrupamento diferentes.

## Definition of Done

- [ ] `expense_entry` implementado, substituindo o "lançamento genérico" atual, com as categorias listadas.
- [ ] Dashboard de custo (RF003) mostra quebra por categoria, não só total por km.
- [ ] `budget` funcional com alerta em 80% e 100% do limite, por veículo ou por frota.
- [ ] `margem_realizada` calculada e visível para `route_plan` de categoria `TRANSFER` já concluídos.
- [ ] Comparação agregada mensal (custo estimado x despesa real de combustível) rodando por veículo, alimentando o job de recalibração de consumo da spec 09.
- [ ] Aba "Custos" no web com as 4 sub-visões (visão geral, despesas, orçamento, rentabilidade de transfers), visível só para gestor.
