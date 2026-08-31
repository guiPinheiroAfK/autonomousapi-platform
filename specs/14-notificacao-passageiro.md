# 14 — Notificação Automática ao Passageiro/Cliente Final (bot Telegram)

## Contexto

Diferente da spec `12-notificacoes-operacionais.md` (que avisa a **equipe interna** sobre signup), isso aqui é voltado para o **passageiro/cliente final** da frota — alguém que, na maioria dos casos, nunca interagiu com o sistema, não tem conta, não é motorista nem gestor. É a pessoa esperando a van do passeio, ou o cliente que vai receber uma entrega/transfer. Hoje esse aviso ("motorista está a caminho", "rota confirmada") é feito manualmente por WhatsApp pelo gestor, ou nem é feito — automatizar isso fecha um gap real de experiência, principalmente no segmento de turismo (spec `13-viagem-redonda-turismo.md`).

**Decisão de canal:** Telegram primeiro. WhatsApp é onde o cliente final brasileiro realmente está no dia a dia, mas exige WhatsApp Business API — custo por mensagem e processo de aprovação antes de sair do papel. Telegram é gratuito, sem aprovação de terceiro, no ar em horas — permite validar se o fluxo de notificação automática entrega valor de verdade antes de investir no canal mais caro/lento de configurar. Migrar ou expandir para WhatsApp depois é decisão separada, não bloqueada por nada deste desenho.

## Modelo de dados — reaproveitar `route_stop`, não criar cadastro de cliente paralelo

**Decisão:** cada `route_stop` (spec 02) ganha um contato opcional: `contato_nome`, `contato_telefone`. Quando preenchido, o passageiro/cliente daquela parada específica recebe notificação automática nos eventos relevantes. Motivo de não criar uma entidade "Cliente"/"Passageiro" própria: o dado que importa pra notificação já pertence à parada (quem está sendo buscado ali, nesse trajeto específico) — um cadastro de cliente reutilizável entre rotas é um passo maior (CRM de passageiro), que não foi pedido e não deveria entrar de carona aqui. Se o padrão de reuso aparecer na prática (mesmo cliente em várias viagens), isso é decisão futura, no mesmo espírito de `collection_point` ter nascido depois que o atrito de redigitar endereço ficou óbvio — não antecipar.

## Ponto de atenção: isso é dado de terceiro sem consentimento direto — diferente de tudo que já existe no produto

**Não confundir com o consentimento do motorista (spec 02/03):** o motorista se cadastra no app, aceita termos, sabe que está sendo rastreado. O passageiro aqui **não interage com o sistema em nenhum momento antes da notificação chegar** — o dado (nome, telefone) é digitado pelo gestor, terceiro à relação. Duas implicações práticas, não teóricas:
- A mensagem enviada deve deixar claro, na primeira interação, quem está mandando e por quê (nome da empresa de frota, não "sistema desconhecido") — reduz risco de ser marcado como spam/golpe, e é o mínimo de transparência esperável.
- Reter esse telefone/nome além do necessário (ex. depois que a rota é concluída e não há viagem futura vinculada) não tem justificativa de negócio — vale mesma disciplina de retenção documentada em spec 02 pra dado de GPS: não guardar indefinidamente por padrão.

Isso não bloqueia o desenvolvimento, mas precisa estar registrado e revisado antes de considerar o item "pronto" — é o tipo de detalhe que vira problema de verdade se ignorado agora.

## Eventos que disparam notificação

Reaproveitando o ciclo de vida já mapeado em `route_plan_event` (spec 11) — a notificação ao passageiro é mais um consumidor desses eventos, não uma lógica paralela:

1. **Rota atribuída ao motorista** (evento `atribuida`) — "Sua viagem com [empresa] está confirmada para [data/horário]."
2. **Rota iniciada / motorista a caminho** (evento `iniciada`, ou primeira `parada_concluida` — a decisão de qual desses marca "início" já está em aberto na spec 11 e se resolve lá, não aqui) — "O motorista está a caminho."
3. **Parada do próprio passageiro concluída** (`parada_concluida` da `route_stop` que tem esse contato) — confirmação de embarque, útil principalmente pro caso de ida/volta (spec 13): "Embarque confirmado" de manhã, e o mesmo aviso de novo na volta.

Não notificar em `ordem_ajustada_manualmente`, `cancelada` sem contexto, ou qualquer evento operacional interno — o passageiro só precisa saber o que afeta ele diretamente. Cancelamento é exceção: se a rota for cancelada e o passageiro já tinha sido avisado da confirmação, ele precisa ser avisado do cancelamento também — não deixar a última mensagem que ele recebeu ser "confirmado" quando não está mais.

## Falha do bot não pode quebrar o fluxo operacional

Mesma regra já estabelecida em `12-notificacoes-operacionais.md`: chamada fire-and-forget, timeout curto, erro capturado e logado, nunca propagado — o gestor/motorista opera normalmente mesmo se o Telegram estiver fora do ar ou o número do passageiro for inválido.

## Definition de Done

- [ ] `route_stop.contato_nome`/`contato_telefone` (opcionais) adicionados.
- [ ] Bot Telegram configurado, mesmo padrão de `NotificationWebhookSender` (spec 12) — no-op quando não configurado.
- [ ] Notificação disparada nos 3 eventos listados, reaproveitando `route_plan_event` como gatilho.
- [ ] Mensagem inicial identifica claramente a empresa remetente.
- [ ] Política de retenção do contato do passageiro documentada (quando/se é descartado após a viagem).
- [ ] Cancelamento de rota com passageiro já notificado dispara aviso de cancelamento.
