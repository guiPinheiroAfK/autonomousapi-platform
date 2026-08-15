# 07 — App do Motorista

## Contexto / problema

Hoje o app mobile do motorista tem só: login, consentimento de localização e uma tela de viagem (iniciar/parar, manda ping de GPS). O motorista não vê o veículo que está dirigindo, não vê se há manutenção pendente, não vê a validade da própria CNH. Isso empurra toda comunicação prática pro WhatsApp — exatamente o problema que o produto deveria resolver.

## Objetivo

Dar ao motorista visibilidade *read-only* sobre o que afeta o trabalho dele (próprio veículo, própria CNH, ordens de serviço do veículo), um canal de reporte (ocorrência) e um canal de comunicação direta com o gestor — sem virar uma central de mensagens completa nem expor dado que não é dele.

## Escopo dentro

1. **Veículo atual** — placa, modelo, km atual, pendências (read-only, sem editar).
2. **CNH própria** — validade e alerta de vencimento (só a dele).
3. **Ordens de serviço do veículo atual** — visualização, sem poder criar/editar.
4. **Reportar ocorrência** — conecta no endpoint de sinistro já existente (RF016 do documento de entrega); motorista relata, gestor decide/edita.
5. **Notificações push** — CNH vencendo, manutenção agendada, aviso do gestor, nova mensagem de chat.
6. **Histórico das próprias viagens** — não só o botão iniciar/parar, uma lista do que já rodou.
7. **Mini-chat gestor↔motorista** — ver seção dedicada abaixo.
8. **Rota do dia / OS de trabalho com paradas definidas** — quando o gestor planeja uma rota multi-coleta/multi-entrega (spec `02-dados-mapas-rotas.md`, `route_plan`/`route_stop`), o motorista designado vê a sequência de paradas já otimizada e **marca cada parada como concluída** (coleta feita / entrega feita) ao completá-la. Isso não é a mesma coisa que "ordem de serviço de manutenção" do item 3 — são duas entidades diferentes, ambas chamadas de "OS" no dia a dia, o que gerou a dúvida inicial; nomear diferente no código (`maintenance_order` vs. `route_plan`) evita confusão.

## Fora de escopo (regra de negócio, não é falta de tempo)

- **Avaliação do motorista nunca é visível a ele.** RF018/HU28 já estabelecem essa regra para o produto como um todo — aqui reforça-se que nenhuma tela nem endpoint do app do motorista pode expor `driver_rating`, mesmo indiretamente (ex. num campo "resumo do veículo" que vaze o dado sem querer).
- Dados de outros motoristas ou de veículos que não são o dele.
- Mensageria em grupo ou broadcast pra frota inteira — fica só 1:1, gestor com o motorista designado.
- Anexo de mídia no chat (texto puro no MVP; mídia fica pra depois, se for pedido).

## Modelo de dados (schema `core`)

- Endpoints read-only sobre entidades que já existem: `vehicle` (o veículo atual designado ao motorista), `driver_license` (CNH), `maintenance_order` (ordens de serviço).
- `vehicle_incident` (já existe, RF016) recebe o POST do reporte feito pelo motorista.
- **`driver_vehicle_assignment`** (nova, se ainda não existir): relação atual motorista→veículo, com histórico. Necessária para responder corretamente "qual é o meu veículo agora" em frotas onde o motorista roda veículos diferentes ao longo do tempo — sem isso, "veículo atual" vira ambíguo.
- Histórico de viagens usa o `trip` que já existe no schema `geo` (RF007/HU12) — só precisa de um endpoint agregado voltado pro motorista ver as próprias viagens.
- Rota do dia usa `route_plan`/`route_stop` (schema `geo`, spec 02) — endpoint de leitura pra listar as paradas do plano ativo do motorista, **mais um endpoint de escrita restrito** que só atualiza `route_stop.ordem_real_executada` / status da parada (concluída), nunca a rota em si, a sequência sugerida ou dados de outra parada que não seja a dele.

## Mini-chat gestor↔motorista

Modelo pedido: parecido com apps tipo Uber — conversa 1:1, com retenção híbrida para não pesar o banco:

- **O servidor guarda só uma janela recente** por conversa (ponto de partida sugerido: últimas 50 mensagens ou últimos 7 dias, o que vier primeiro). Número deliberadamente enxuto: a janela existe só para cobrir o intervalo entre "mensagem enviada" e "dispositivo do gestor sincronizou", não para servir de histórico — quem quiser guardar mais tempo, guarda local. Ajustar com o time depois de ver volume real de uso (é parâmetro, não decisão travada).
- **O histórico completo vive no dispositivo do gestor** (armazenamento local — SQLite no mobile, IndexedDB no web), não no servidor. O servidor funciona como canal de entrega + buffer curto, não como arquivo permanente.
- Um job periódico de limpeza remove do servidor as mensagens fora da janela, **só depois de confirmar que o dispositivo do gestor já sincronizou** aquele trecho — evita perder mensagem se o gestor ficou um tempo offline.
- O motorista não precisa (nem deveria) reter histórico longo no dispositivo dele — ele só vê a janela recente do servidor, o que é consistente com o app dele ser mais leve e operacional.

### Modelo de dados (schema `core`)

- `chat_conversation`: gestor, motorista, veículo de contexto (opcional), criada_em.
- `chat_message`: conversa, remetente, conteúdo (texto), enviado_em, entregue_em, lido_em, `ainda_no_servidor` (bool — controla o que já foi removido pelo job de limpeza).
- `chat_sync_cursor`: por dispositivo do gestor, até qual mensagem já foi baixada e persistida localmente — é o que autoriza o job de limpeza a agir.

### Trade-off explícito (decisão consciente, não bug)

Se o gestor trocar de dispositivo ou desinstalar o app sem fazer backup, o histórico que já saiu da janela do servidor se perde — não há cópia em nuvem por design, é a troca feita para não pesar o banco. Se isso incomodar na prática mais pra frente, a evolução natural é oferecer export manual (backup em arquivo) antes de trocar de aparelho. Não é MVP, mas fica anotado como válvula de escape caso vire reclamação real.

### Entrega das mensagens

Notificação push avisa sobre mensagem nova (reaproveita o mesmo mecanismo do item 5, RF012). A entrega em si pode ser poll simples no MVP — é conversa 1:1 de baixo volume, não justifica infraestrutura de tempo real (WebSocket/SSE) desde já. Migrar pra tempo real só se o padrão de uso pedir (ex. gestor reclamando de atraso perceptível).

## Regras de segurança (não negociáveis)

- Todo endpoint do app do motorista filtra por `driver_id` extraído do token de autenticação — nunca aceita `driver_id` como parâmetro vindo do cliente.
- Endpoint de avaliação (`driver_rating_*`, spec 06) não é acessível com token de motorista, ponto final — mesmo em caso de tentativa de chamada forjada direto na API.
- Mensagens de chat só circulam dentro do par gestor-motorista da conversa; motorista não lista nem inicia conversa com outro motorista.
- No endpoint de conclusão de parada, o motorista só pode alterar paradas do `route_plan` ativo dele — nunca de outro motorista, e nunca campos além do status de conclusão (não pode reordenar nem editar dados da parada).

## Definition of Done

- [ ] Motorista vê seu veículo atual, CNH, ordens de serviço (read-only) e histórico de viagens no app.
- [ ] Endpoint de reporte de ocorrência funcional, aparece pro gestor revisar.
- [ ] Notificações push cobrindo os 4 eventos listados (CNH vencendo, manutenção agendada, aviso do gestor, nova mensagem).
- [ ] Chat 1:1 funcionando com retenção híbrida (janela recente no servidor + persistência completa no dispositivo do gestor).
- [ ] Motorista vê a rota do dia (quando existir `route_plan` ativo) com paradas na ordem sugerida, e consegue marcar cada parada como concluída.
- [ ] Teste de segurança específico: token de motorista não consegue ler `driver_rating`, dado de outro motorista/veículo, reordenar/editar parada, nem iniciar conversa fora do par autorizado.
