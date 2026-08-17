# 07 — App do Motorista

## Contexto / problema

Hoje o app mobile do motorista tem só: login, consentimento de localização e uma tela de viagem (iniciar/parar, manda ping de GPS). O motorista não vê o veículo que está dirigindo, não vê se há manutenção pendente, não vê a validade da própria CNH. Isso empurra toda comunicação prática pro WhatsApp — exatamente o problema que o produto deveria resolver.

## Objetivo

Dar ao motorista visibilidade *read-only* sobre o que afeta o trabalho dele (próprio veículo, própria CNH, ordens de serviço do veículo), um canal de reporte (ocorrência) e um canal de comunicação direta com o gestor — sem virar uma central de mensagens completa nem expor dado que não é dele.

## Princípio de design: motorista é operador, não gestor

O painel do gestor (web) é, por natureza, denso — ele administra uma frota inteira, então dashboard, comparativos, múltiplas abas fazem sentido pra ele. **O app do motorista não deve seguir essa mesma lógica.** O motorista é funcionário, não uma empresa administrando algo — ele não precisa "entender o sistema", precisa saber o que fazer agora. Excesso de informação no app dele não é "transparência", é ruído que atrapalha quem só quer terminar a rota do dia.

Isso muda como o escopo abaixo se organiza na prática:

- **Uma tela "Hoje" como home**, agregando só o que exige atenção imediata: rota ativa (próxima parada), alertas pendentes (CNH vencendo, manutenção agendada), e badge de mensagem não lida do chat. Sem gráfico, sem histórico, sem múltiplas abas competindo por atenção.
- **Tudo o resto vira secundário** — acessível a partir de um menu simples, não estampado na home: veículo (dados básicos, não um "prontuário" completo como o gestor vê), CNH (só o essencial: validade), ordens de serviço do veículo (resumidas — "2 pendências" em vez de lista detalhada, com opção de abrir o detalhe só se o motorista quiser), histórico de viagens.
- **Nenhuma tela pede uma decisão de gestão** dele — ele reporta (ocorrência), executa (marca parada concluída), e recebe (notificação, mensagem, rota). Ele não compara, não analisa, não configura nada.

Essa distinção vale tanto pra hierarquia de informação quanto pro tom visual: o app do motorista deve parecer mais com um app de execução de tarefas (tipo apps de entregador) do que com um mini-dashboard administrativo.

**Estado real (implementado no `apps/web`):** a home "Hoje" (`DriverHomePage.tsx`) e o menu secundário "Mais" (`DriverMorePage.tsx`) existem no **web**. O `apps/mobile` já tinha veículo/CNH/OS/ocorrência antes desta rodada (com layout próprio, não revisado por este princípio de design ainda) — portar o mobile pra esse mesmo padrão de "Hoje" fica pra uma rodada futura, não foi tocado aqui.

## Escopo dentro

**Home ("Hoje"):**
1. **Rota ativa / rota do dia** — quando o gestor designa uma rota (spec `02-dados-mapas-rotas.md`, `route_plan`/`route_stop`), a tela muda conforme a `categoria`:
   - **`ROTA`** (multi-coleta/multi-entrega): motorista vê a próxima parada em destaque e a sequência completa abaixo, **marcando cada parada como concluída** ao completá-la.
   - **`TRANSFER`** (trajeto único, origem→destino, às vezes com valor combinado): motorista vê um cartão único — origem, destino, valor (se houver) — e um botão de ação só ("iniciar"/"concluir"), sem lista de paradas. É o mesmo dado por trás, só a apresentação muda pra não tratar um trajeto simples como se fosse uma rota complexa.
   
   Sem rota ativa, a home mostra estado neutro ("sem rota atribuída hoje"), não uma tela vazia genérica.
2. **Alertas que pedem atenção** — CNH vencendo, manutenção agendada, aviso pontual do gestor — resumidos, não uma lista de tudo que já existe no sistema.
3. **Chat** — acesso direto, com indicador de não lida (ver seção dedicada abaixo).

**Secundário (menu, não home):**
4. **Veículo atual** — placa, modelo, km atual, pendências (read-only, versão resumida — não é o mesmo nível de detalhe do painel do gestor).
5. **CNH própria** — validade (só a dele).
6. **Ordens de serviço do veículo atual** — visualização, sem poder criar/editar.
7. **Histórico das próprias viagens** — lista do que já rodou.
8. **Reportar ocorrência** — ação disponível a partir do veículo ou da rota ativa; conecta no endpoint de sinistro já existente (RF016 do documento de entrega); motorista relata, gestor decide/edita.

**Transversal:**
9. **Notificações push** — CNH vencendo, manutenção agendada, aviso do gestor, nova mensagem de chat, nova rota atribuída.

Nota de nomenclatura: "ordem de serviço do veículo" (item 6, manutenção) e "rota do dia" (item 1) são duas entidades diferentes no banco (`maintenance_order` vs. `route_plan`), mesmo sendo as duas chamadas de "OS" informalmente no dia a dia — vale manter os nomes técnicos distintos pra não confundir na hora de codar.

## Fora de escopo (regra de negócio, não é falta de tempo)

- **Avaliação do motorista nunca é visível a ele.** RF018/HU28 já estabelecem essa regra para o produto como um todo — aqui reforça-se que nenhuma tela nem endpoint do app do motorista pode expor `driver_rating`, mesmo indiretamente (ex. num campo "resumo do veículo" que vaze o dado sem querer).
- Dados de outros motoristas ou de veículos que não são o dele.
- Mensageria em grupo ou broadcast pra frota inteira — fica só 1:1, gestor com o motorista designado.
- Anexo de mídia no chat (texto puro no MVP; mídia fica pra depois, se for pedido).

## Modelo de dados (schema `core`)

- Endpoints read-only sobre entidades que já existem: `vehicle` (o veículo atual designado ao motorista), `driver_license` (CNH), `maintenance_order` (ordens de serviço).
- `vehicle_incident` (já existe, RF016) recebe o POST do reporte feito pelo motorista.
- **`driver_vehicle_assignment`** — relação atual motorista→veículo, com histórico. Necessária para responder corretamente "qual é o meu veículo agora" em frotas onde o motorista roda veículos diferentes ao longo do tempo. Já implementada (ADR 0014, migration `V15__app_do_motorista_fundacao.sql`).
- Histórico de viagens usa o `trip` que já existe no schema `geo` (RF007/HU12) — endpoint agregado `GET /v1/me/trips` já expõe isso pro motorista.
- Rota do dia usa `route_plan`/`route_stop` (schema `core`, spec 02) — `GET /v1/routes/plans/active` lista as paradas do plano ativo do motorista, `POST /v1/routes/plans/stops/{id}/complete` é o endpoint de escrita restrito que só atualiza `route_stop.ordem_real_executada`/`concluida_em`, nunca a rota em si, a sequência sugerida ou dados de outra parada que não seja a dele.

## Mini-chat gestor↔motorista

### Acabamento visual — não é ponto secundário

O chat não pode parecer um componente genérico plugado por cima do resto do produto — precisa seguir a mesma linguagem visual e o mesmo nível de acabamento do resto do web: paleta, tipografia, componentes (bolha de mensagem, indicador de digitando/lido, avatar/iniciais, empty state desenhado — não um placeholder cinza). Isso é requisito de design, não só de funcionalidade — tratado como parte do Definition of Done da spec, não como polish opcional de depois.

### Atribuição de rota diretamente pelo chat

O gestor pode designar uma rota pro motorista **a partir da própria conversa**, sem precisar sair do chat pra outra tela: um botão no composer ("anexar rota") abre a seleção de um `route_plan` já cadastrado e o anexa como uma **mensagem estruturada** — um cartão de rota dentro do chat, não texto solto — mostrando resumo (quantidade de paradas). O motorista toca no cartão e vai direto pra tela de rota ativa (item 1 do escopo).

Modelo pedido: parecido com apps tipo Uber — conversa 1:1, com retenção híbrida para não pesar o banco:

- **O servidor guarda só uma janela recente** por conversa (ponto de partida sugerido: últimas 50 mensagens ou últimos 7 dias, o que vier primeiro). Número deliberadamente enxuto: a janela existe só para cobrir o intervalo entre "mensagem enviada" e "dispositivo do gestor sincronizou", não para servir de histórico — quem quiser guardar mais tempo, guarda local. Ajustar com o time depois de ver volume real de uso (é parâmetro, não decisão travada).
- **O histórico completo vive no dispositivo do gestor** (armazenamento local — SQLite no mobile, IndexedDB no web), não no servidor. O servidor funciona como canal de entrega + buffer curto, não como arquivo permanente.
- Um job periódico de limpeza remove do servidor as mensagens fora da janela, **só depois de confirmar que o dispositivo do gestor já sincronizou** aquele trecho — evita perder mensagem se o gestor ficou um tempo offline.
- O motorista não precisa (nem deveria) reter histórico longo no dispositivo dele — ele só vê a janela recente do servidor, o que é consistente com o app dele ser mais leve e operacional.

### Modelo de dados (schema `core`)

- `chat_conversation`: gestor, motorista, veículo de contexto (opcional), criada_em.
- `chat_message`: conversa, remetente, `tipo` (`TEXTO` | `ATRIBUICAO_ROTA` | `SISTEMA`), conteúdo (texto — sempre preenchido, mesmo em mensagem estruturada, como fallback pra quem não interpreta o tipo), `route_plan_id` (preenchido só quando `tipo = ATRIBUICAO_ROTA`), enviado_em, entregue_em, lido_em, `ainda_no_servidor` (bool — controla o que já foi removido pelo job de limpeza).
- `chat_sync_cursor`: por dispositivo do gestor, até qual mensagem já foi baixada e persistida localmente — é o que autoriza o job de limpeza a agir.

Mensagem do tipo `ATRIBUICAO_ROTA` **nunca sai da janela de retenção do servidor antes de o `route_plan` referenciado estar `CONCLUIDA`** — é a única exceção à regra de limpeza por tempo/quantidade, porque apagar essa mensagem cedo demais quebraria o vínculo entre "o motorista recebeu a rota" e a própria rota. (`RoutePlanStatus` ainda não tem um status "cancelada" — a exceção cobre hoje só "ainda não concluída".)

### Trade-off explícito (decisão consciente, não bug)

Se o gestor trocar de dispositivo ou desinstalar o app sem fazer backup, o histórico que já saiu da janela do servidor se perde — não há cópia em nuvem por design, é a troca feita para não pesar o banco. Se isso incomodar na prática mais pra frente, a evolução natural é oferecer export manual (backup em arquivo) antes de trocar de aparelho. Não é MVP, mas fica anotado como válvula de escape caso vire reclamação real.

### Entrega das mensagens

Notificação push avisa sobre mensagem nova e sobre rota atribuída (reaproveita o mesmo mecanismo do item 9, RF012). A entrega em si é poll simples (5s) — conversa 1:1 de baixo volume, não justifica infraestrutura de tempo real (WebSocket/SSE). Migrar pra tempo real só se o padrão de uso pedir (ex. gestor reclamando de atraso perceptível). O indicador de "digitando" usa o mesmo poll (2s, mais curto), com estado guardado em memória no servidor — nunca em banco, porque é um sinal que expira sozinho em segundos (ver `TypingIndicatorService`); só funciona corretamente com uma instância do core-api — mover pra Redis é a evolução natural se isso virar problema real.

## Regras de segurança (não negociáveis)

- Todo endpoint do app do motorista filtra por `driver_id` extraído do token de autenticação — nunca aceita `driver_id` como parâmetro vindo do cliente.
- Endpoint de avaliação (`driver_rating_*`, spec 06) não é acessível com token de motorista, ponto final — mesmo em caso de tentativa de chamada forjada direto na API.
- Mensagens de chat só circulam dentro do par gestor-motorista da conversa; motorista não lista nem inicia conversa com outro motorista.
- No endpoint de conclusão de parada, o motorista só pode alterar paradas do `route_plan` ativo dele — nunca de outro motorista, e nunca campos além do status de conclusão (não pode reordenar nem editar dados da parada).
- Só o gestor pode enviar mensagem do tipo `ATRIBUICAO_ROTA` (o motorista não "atribui rota pra si mesmo" pelo chat) — o backend valida isso pelo perfil do remetente (`@PreAuthorize` no endpoint), não confia em flag vinda do cliente.

## Definition of Done

- [x] Home "Hoje" mostra rota ativa (próxima parada em destaque), alertas resumidos e badge de chat — sem exigir navegação para ver o que precisa de atenção agora. (`DriverHomePage.tsx`)
- [x] Veículo, CNH, ordens de serviço e histórico de viagens acessíveis via menu secundário, em versão resumida. (`DriverMorePage.tsx`, web; mobile já tinha isso antes desta rodada, layout próprio)
- [x] Endpoint de reporte de ocorrência funcional, aparece pro gestor revisar. (`POST /v1/me/incidents`, já existia; exposto no web via `DriverMorePage.tsx` nesta rodada)
- [x] Notificações push cobrindo os eventos listados (CNH vencendo, manutenção agendada, aviso do gestor, nova mensagem, nova rota atribuída). Verificado estruturalmente (o mecanismo dispara pra cada evento) — não testado com dispositivo físico real recebendo o push.
- [x] Chat 1:1 funcionando com retenção híbrida (janela recente no servidor + persistência completa no dispositivo do gestor), **com acabamento visual equivalente ao dashboard** — indicador de lido/digitando, prévia de última mensagem, empty states desenhados, tudo com os componentes já usados no resto do web.
- [x] Gestor consegue anexar/atribuir um `route_plan` direto pela conversa, motorista recebe como cartão estruturado e navega direto pra rota ativa.
- [x] Motorista vê a rota do dia (quando existir `route_plan` ativo) com paradas na ordem sugerida, e consegue marcar cada parada como concluída. Categoria `TRANSFER` renderiza como cartão único (origem/destino/valor), categoria `ROTA` como lista.
- [x] Teste de segurança específico: token de motorista não consegue ler `driver_rating` (`DriverRatingAuthorizationTest`), dado de outro motorista/veículo (`DriverControllerAuthorizationTest`), reordenar/editar parada (`RoutePlanServiceTest`), enviar mensagem de atribuição de rota nem iniciar conversa fora do par autorizado (`ChatSecurityTest`).
