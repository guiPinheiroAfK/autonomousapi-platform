-- Odômetro no momento em que o veículo foi cadastrado na plataforma. Imutável depois de
-- criado (nunca é alterado por um PUT em /v1/vehicles/{id}) — é o "km zero" a partir do
-- qual o custo por km passa a ser calculado, em vez do odômetro total do carro (que já
-- vinha rodado antes de entrar no sistema e não tem relação com o gasto rastreado aqui).
alter table vehicle add column odometro_inicial integer;

-- Backfill honesto: para veículos já cadastrados, não existe histórico de "odômetro no
-- cadastro" — assume-se o odômetro atual, ou seja, custo por km desses veículos volta a
-- zerar e só passa a existir depois que o odômetro for atualizado novamente.
update vehicle set odometro_inicial = odometer_km where odometro_inicial is null;

alter table vehicle alter column odometro_inicial set not null;
