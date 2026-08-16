-- V18 — Tipo do veículo (spec 08, item 4). Coluna própria, não dentro de `atributos`:
-- é usado pra filtrar/escolher ícone em listagem, e o comentário de `atributos` já
-- diz o princípio ("nunca guardar aqui o que é filtrado ou ordenado em listagem").
-- Nullable e sem backfill — veículo existente sem tipo cadastrado continua funcionando,
-- só sem ícone diferenciado até o gestor preencher.
alter table vehicle add column tipo varchar(20);
