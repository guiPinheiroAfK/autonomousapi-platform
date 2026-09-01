-- V32 — Notificação automática ao passageiro via Telegram (spec 14, parte 2).
--
-- Telegram só deixa um bot mandar mensagem pra quem já iniciou conversa com ELE antes —
-- não dá pra endereçar por telefone, só por chat_id. `telegram_link_token` é o valor que
-- vai no deep-link (t.me/<bot>?start=<token>) que o gestor manda pro passageiro uma vez;
-- quando ele clica e dá /start, o webhook grava `telegram_chat_id` a partir do token.

alter table passenger
    add column telegram_chat_id   bigint,
    add column telegram_link_token varchar(64);

create unique index uq_passenger_telegram_link_token on passenger (telegram_link_token)
    where telegram_link_token is not null;

-- Marca se os passageiros da rota já foram avisados da confirmação (evento ATRIBUIDA) —
-- só existe pra decidir, no cancelamento, se precisa mandar aviso de cancelamento também
-- (spec 14: "não deixar a última mensagem que ele recebeu ser 'confirmado' quando não
-- está mais"). Não precisa granularidade por parada: a notificação de confirmação já sai
-- pra todos os passageiros da rota de uma vez, no mesmo evento.
alter table route_plan add column passageiros_notificados boolean not null default false;
