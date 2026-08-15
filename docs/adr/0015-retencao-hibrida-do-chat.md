# ADR 0015 — Retenção híbrida do mini-chat gestor↔motorista

**Status:** aceito (design travado; implementação na PR do backend de chat)
**Data:** 2026-08-15

## Contexto

O spec 07 pede um chat 1:1 gestor↔motorista, estilo apps de transporte, sem virar central
de mensagens completa nem pesar o banco com histórico infinito. A decisão de retenção é o
ponto arquitetural do chat — o resto (CRUD de mensagem, poll) é mecânico.

## Decisões

### O servidor é canal de entrega + buffer curto, não arquivo permanente

O servidor guarda apenas uma **janela recente** por conversa — ponto de partida: últimas
50 mensagens ou 7 dias, o que vier primeiro (parâmetro de config, não número travado). A
janela existe só para cobrir o intervalo entre "mensagem enviada" e "dispositivo do gestor
sincronizou", não para servir de histórico.

### O histórico completo vive no dispositivo do gestor

Armazenamento local — IndexedDB na web, SQLite no mobile se o gestor usar app. O motorista
**não** retém histórico longo: vê só a janela do servidor, consistente com o app dele ser
leve e operacional.

### Limpeza só remove o que o gestor já sincronizou

`chat_sync_cursor` (por dispositivo do gestor) registra até qual mensagem já foi baixada e
persistida localmente. Um job periódico remove do servidor as mensagens fora da janela
**apenas** quando o cursor confirma que o gestor já as sincronizou — se o gestor ficou
offline, nada é apagado antes de ele voltar. `chat_message.ainda_no_servidor` (bool)
controla o que já foi removido.

### Modelo de dados (schema `core`)

- `chat_conversation`: gestor (`app_user`), motorista (`driver`), veículo de contexto
  (opcional), criada_em. Uma conversa por par gestor-motorista.
- `chat_message`: conversa, remetente (`app_user`), corpo (texto), enviado_em,
  entregue_em, lido_em, ainda_no_servidor.
- `chat_sync_cursor`: dispositivo do gestor, até qual mensagem sincronizou.

### Entrega por poll no MVP, push para avisar

Conversa 1:1 de baixo volume não justifica WebSocket/SSE agora — poll simples resolve.
Notificação push (ADR 0016) avisa da mensagem nova. Migrar para tempo real só se o uso
mostrar atraso perceptível.

## Trade-off explícito

Se o gestor troca de dispositivo ou desinstala sem backup, o histórico que já saiu da
janela do servidor se perde — não há cópia em nuvem, por design, para não pesar o banco. A
válvula de escape futura (não-MVP) é export manual antes de trocar de aparelho.

## Regras de segurança (não negociáveis)

- Mensagens só circulam dentro do par gestor-motorista da conversa.
- Motorista não lista nem inicia conversa com outro motorista; resolve a própria conversa
  pelo vínculo do token (ADR 0013).

## Reavaliar quando

- O volume real de uso pedir ajuste da janela (50/7d) ou migração para tempo real.
- Surgir demanda por anexo de mídia (hoje texto puro) ou por histórico em nuvem.
