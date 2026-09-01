-- V33 — Chat entre membros da equipe (Gestor/Despachante/Visualizador com qualquer outro do
-- mesmo tenant), aditivo ao chat gestor↔motorista existente (V17) — reaproveita a mesma
-- tabela chat_conversation (e, por consequência, todo o resto: chat_message, reações,
-- responder/editar/excluir/encaminhar, ADR 0015/0022) em vez de duplicar o modelo inteiro.
--
-- `gestor_user_id` passa a significar "participante A" de qualquer conversa (o nome da
-- coluna não muda pra não obrigar renomear em todo canto que já lê getGestorUserId() —
-- documentado na entidade). Pra GESTOR_MOTORISTA (kind default, linhas existentes) segue
-- exatamente como antes: gestor de um lado, motorista (driver_id) do outro. Pra EQUIPE,
-- driver_id/vehicle_id ficam nulos e o segundo participante vai em participant_b_user_id —
-- sempre o par ordenado (menor UUID em gestor_user_id, maior em participant_b_user_id) pra
-- não nascer duas linhas pro mesmo par em chamadas concorrentes/nas duas direções.

alter table chat_conversation
    alter column driver_id drop not null;

alter table chat_conversation
    add column kind varchar(20) not null default 'GESTOR_MOTORISTA',
    add column participant_b_user_id uuid references app_user (id);

create unique index uq_chat_conversation_equipe_par
    on chat_conversation (tenant_id, gestor_user_id, participant_b_user_id)
    where kind = 'EQUIPE';
