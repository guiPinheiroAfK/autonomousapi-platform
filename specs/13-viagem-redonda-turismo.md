# 13 — Viagem Redonda (Ida e Volta) para Frota de Turismo

## Contexto

Segmento identificado especificamente: empresas de turismo com frota de van (ex. Foz do Iguaçu) — o padrão de operação delas não é "uma rota", é duas rotas ligadas: **leva de manhã, busca de volta à tarde**, mesmos pontos (ou pontos parecidos) na ordem inversa, tipicamente com o mesmo veículo/motorista nas duas pernas. Hoje `route_plan` (spec 02) só modela uma perna por vez — nada impede o gestor de criar duas rotas separadas manualmente, mas elas não têm nenhum vínculo entre si: aparecem como duas linhas soltas na lista, sem noção de "isso é a mesma viagem".

## Desenho — vínculo leve, não uma entidade nova pesada

**Decisão:** `route_plan` ganha um campo opcional `viagem_id` (UUID, nullable) — quando duas (ou mais) `route_plan` compartilham o mesmo `viagem_id`, elas são pernas da mesma viagem redonda. Não é uma tabela nova, não é uma entidade "Viagem" com seu próprio ciclo de vida — é só uma chave de agrupamento. Motivo de não criar uma entidade própria: uma viagem redonda não tem nenhum dado ou comportamento que não pertença já a alguma das pernas individuais (cada perna já tem seu `route_plan` completo, com paradas, motorista, veículo, valor) — criar uma entidade "Viagem" só pra guardar um agrupamento seria duplicar estrutura sem necessidade.

**Por que UUID solto em vez de auto-referência (`route_plan` apontando pra outro `route_plan`):** um `viagem_id` compartilhado permite mais de duas pernas no futuro (ex. um passeio de 3 dias com paradas intermediárias) sem mudar o modelo — uma auto-referência ida↔volta só naturalmente modela exatamente duas pernas.

## Fluxo no web — criar a volta a partir da ida

Ao criar uma `route_plan`, campo novo: "Esta é uma viagem de ida e volta?" (checkbox, opcional, fora do caminho padrão — quem não usa esse padrão não vê fricção nenhuma). Se marcado:
1. Sistema salva a ida normalmente, gera um `viagem_id` novo pra ela.
2. Abre automaticamente o formulário da volta, pré-preenchido com os mesmos pontos em ordem inversa e o mesmo veículo/motorista sugeridos (tudo editável — pré-preenchido não é travado, é só economia de digitação) e o mesmo `viagem_id` da ida.
3. Gestor confirma ou ajusta (ex. horário da volta é diferente do horário da ida, o que é o caso comum) e salva.

Isso não é obrigatório — rota avulsa, sem ida/volta, continua funcionando exatamente como hoje, sem tocar em `viagem_id`.

## Onde isso aparece pro gestor

- Na lista de rotas, as pernas de uma mesma viagem ficam visualmente agrupadas (ex. indentadas ou com um indicador "ida"/"volta"), em vez de aparecerem soltas misturadas com o resto.
- Na tela de custos (spec 10, aba "Custos" → "Rentabilidade de transfers"), quando aplicável, a viagem redonda pode ser somada como um total único (custo/valor combinado das duas pernas) além dos números individuais de cada perna — útil pra saber quanto rendeu a viagem inteira, não só cada trecho.

## Fora de escopo (deliberado, aqui)

- Preço combinado como "pacote" com desconto pela viagem completa (ida+volta mais barato que as duas separadas) — é uma decisão comercial, não técnica; fica pro gestor decidir o `valor` de cada perna manualmente por enquanto. Se algum dia virar uma regra de negócio recorrente, aí sim vira campo/fórmula própria.
- Viagens de mais de duas pernas (multi-dia) — o campo `viagem_id` já suporta isso sem mudança de schema, mas a UI de "criar a volta automaticamente" (passo 2 do fluxo acima) é desenhada só para o caso de duas pernas por ora.

## Definition of Done

- [x] `route_plan.viagem_id` (UUID, nullable) adicionado, sem exigir preenchimento em nenhum fluxo existente (V28).
- [x] Checkbox "ida e volta" no formulário de criação de rota, com o fluxo de pré-preenchimento automático da volta descrito acima.
- [x] Lista de rotas agrupa visualmente pernas com o mesmo `viagem_id` (indicador "Ida"/"Volta" + borda lateral).
- [x] Rentabilidade de transfers (spec 10) soma valor/custo das pernas de uma mesma viagem quando aplicável, mantendo os números individuais também visíveis.

**Status:** implementado (2026-08-31) — verificado ponta a ponta (criação das duas pernas com `viagemId` compartilhado, indicador na lista, total combinado na aba Rentabilidade).
