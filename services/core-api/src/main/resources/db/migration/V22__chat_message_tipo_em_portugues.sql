-- V22 — Alinha chat_message.message_type com o vocabulário literal do spec 07
-- (`texto` | `atribuicao_rota` | `sistema`) — a V20 tinha usado TEXT/ROUTE_ASSIGNMENT em
-- inglês, destoando do resto do schema (route_plan.status, route_stop.tipo etc., todos em
-- português). SISTEMA é modelado aqui mesmo sem uso ainda (nenhuma mensagem de sistema é
-- gerada hoje) — só pra não precisar de outra migration quando isso for pedido.

update chat_message set message_type = 'TEXTO' where message_type = 'TEXT';
update chat_message set message_type = 'ATRIBUICAO_ROTA' where message_type = 'ROUTE_ASSIGNMENT';

alter table chat_message alter column message_type set default 'TEXTO';
