# 14 — Notificação Automática ao Passageiro/Cliente Final

## Contexto

Diferente da spec `12-notificacoes-operacionais.md` (que avisa a **equipe interna** sobre signup), isso aqui é voltado para o **passageiro/cliente final** da frota — alguém que, na maioria dos casos, nunca interagiu com o sistema, não tem conta, não é motorista nem gestor. É a pessoa esperando a van do passeio, ou o cliente que vai receber uma entrega/transfer. Hoje esse aviso ("motorista está a caminho", "rota confirmada") é feito manualmente por WhatsApp pelo gestor, ou nem é feito — automatizar isso fecha um gap real de experiência, principalmente no segmento de turismo (spec `13-viagem-redonda-turismo.md`).

**Decisão de canal — abstração pensando em migrar pra WhatsApp em breve:** Telegram é o canal ativo agora (gratuito, sem aprovação de terceiro, no ar em horas), mas a intenção explícita é migrar para WhatsApp assim que fizer sentido (é onde o cliente final brasileiro realmente está no dia a dia — WhatsApp Business API tem custo por mensagem e processo de aprovação, por isso não é o ponto de partida). Por causa disso, o envio é modelado atrás de uma interface `PassengerNotificationSender` (mesmo espírito de `NotificationWebhookSender`, spec 12) — hoje com uma única implementação Telegram, mas o resto do sistema (gatilhos automáticos, botão manual, service) nunca fala com a API do Telegram diretamente. Trocar ou adicionar WhatsApp depois é escrever uma segunda implementação da mesma interface, não redesenhar o fluxo.

## Modelo de dados — `passenger`, cadastro reutilizável (decisão revista)

**Decisão (revista a partir do desenho original deste documento):** existe uma entidade própria `passenger` (schema `core`, tenant-scoped) — nome, telefone — com CRUD dedicado no web, mesmo padrão já usado por `collection_point`: o gestor cadastra uma vez, reaproveita em quantas viagens quiser, sem redigitar. Cada `route_stop` (spec 02) ganha `passenger_id` (nullable, FK). Quando preenchido, esse passageiro recebe notificação automática nos eventos relevantes daquela parada.

**Por que mudou do desenho original (campo solto em `route_stop`):** o desenho anterior evitava de propósito um cadastro reutilizável, no mesmo espírito de "não antecipar" que orienta o resto do projeto. Decisão explícita do Guilherme foi na direção contrária — cliente recorrente (comum em turismo — mesmo passageiro em passeios diferentes) torna o cadastro reutilizável genuinamente útil desde já, não uma antecipação especulativa.

## Botão de envio manual — motorista dispara quando achar necessário

Além dos gatilhos automáticos (próxima seção), o motorista tem um botão "Avisar passageiro" na parada que tem um `passenger_id` vinculado — dispara a mesma notificação (mesmo `PassengerNotificationSender`, mesmo texto-base do evento correspondente ao estado atual da parada) sob demanda, sem esperar o gatilho automático. Cobre o caso em que o motorista quer confirmar algo fora do timing dos 3 eventos padrão (ex. atraso, mudança de ponto de encontro combinada por telefone). Mesma regra de falha silenciosa da seção "Falha do bot" abaixo — botão nunca trava a tela do motorista esperando confirmação de entrega da mensagem.

## Ponto de atenção: isso é dado de terceiro sem consentimento direto — diferente de tudo que já existe no produto

**Não confundir com o consentimento do motorista (spec 02/03):** o motorista se cadastra no app, aceita termos, sabe que está sendo rastreado. O passageiro aqui **não interage com o sistema em nenhum momento antes da notificação chegar** — o dado (nome, telefone) é digitado pelo gestor, terceiro à relação. Implicações práticas, não teóricas:
- A mensagem enviada deve deixar claro, na primeira interação, quem está mandando e por quê (nome da empresa de frota, não "sistema desconhecido") — reduz risco de ser marcado como spam/golpe, e é o mínimo de transparência esperável.
- Sendo cadastro reutilizável (não mais "descartado depois da viagem"), a disciplina de retenção muda de forma: não é "apagar após a rota concluir", é o gestor poder **excluir o passageiro do cadastro** quando não faz mais sentido mantê-lo (cliente que não volta) — exclusão real, não soft-delete, dado que é justamente o tipo de dado que não deveria acumular sem motivo de negócio.

Isso não bloqueia o desenvolvimento, mas precisa estar registrado e revisado antes de considerar o item "pronto" — é o tipo de detalhe que vira problema de verdade se ignorado agora.

## Eventos que disparam notificação

Reaproveitando o ciclo de vida já mapeado em `route_plan_event` (spec 11) — a notificação ao passageiro é mais um consumidor desses eventos, não uma lógica paralela:

1. **Rota atribuída ao motorista** (evento `atribuida`) — "Sua viagem com [empresa] está confirmada para [data/horário]."
2. **Rota iniciada / motorista a caminho** (evento `iniciada`, ou primeira `parada_concluida` — a decisão de qual desses marca "início" já está em aberto na spec 11 e se resolve lá, não aqui) — "O motorista está a caminho."
3. **Parada do próprio passageiro concluída** (`parada_concluida` da `route_stop` que tem esse contato) — confirmação de embarque, útil principalmente pro caso de ida/volta (spec 13): "Embarque confirmado" de manhã, e o mesmo aviso de novo na volta.

Não notificar em `ordem_ajustada_manualmente`, `cancelada` sem contexto, ou qualquer evento operacional interno — o passageiro só precisa saber o que afeta ele diretamente. Cancelamento é exceção: se a rota for cancelada e o passageiro já tinha sido avisado da confirmação, ele precisa ser avisado do cancelamento também — não deixar a última mensagem que ele recebeu ser "confirmado" quando não está mais.

## Falha do bot não pode quebrar o fluxo operacional

Mesma regra já estabelecida em `12-notificacoes-operacionais.md`: chamada fire-and-forget, timeout curto, erro capturado e logado, nunca propagado — o gestor/motorista opera normalmente mesmo se o Telegram estiver fora do ar ou o número do passageiro for inválido.

## Definition of Done

- [ ] Entidade `passenger` (nome, telefone, tenant-scoped) com CRUD próprio no web, mesmo padrão de `collection_point`.
- [ ] `route_stop.passenger_id` (nullable, FK) adicionado.
- [ ] Interface `PassengerNotificationSender` com implementação Telegram; no-op quando não configurado (mesmo padrão de `NotificationWebhookSender`, spec 12).
- [ ] Notificação automática disparada nos 3 eventos listados, reaproveitando `route_plan_event` como gatilho.
- [ ] Botão "Avisar passageiro" no app do motorista (mobile e/ou web) pra disparo manual, na parada com `passenger_id`.
- [ ] Mensagem inicial identifica claramente a empresa remetente.
- [ ] Exclusão de passageiro do cadastro disponível (dado de terceiro, sem justificativa de retenção indefinida).
- [ ] Cancelamento de rota com passageiro já notificado dispara aviso de cancelamento.
