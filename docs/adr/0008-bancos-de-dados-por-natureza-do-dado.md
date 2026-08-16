# ADR 0008 — Um banco por natureza de dado, não por entidade

**Status:** aceito
**Data:** 2026-08-12

## Contexto

Surgiu a proposta de separar em três bancos: usuários (relacional), GPS + vídeo, e frota —
esta última em não-relacional, com o argumento de que veículo "muda bastante".

## Decisão

**Duas tecnologias de banco, não três**, e o vídeo fora de banco.

| Dado | Onde | Por quê |
|---|---|---|
| Usuários, tenants, billing | Postgres | Precisa de ACID forte e é onde mora o overhead de compliance financeiro |
| Frota (veículo, motorista, custo, viagem) | Postgres, com `jsonb` nos atributos variáveis | Ver abaixo |
| GPS bruto | Postgres/PostGIS hoje; Timescale quando o volume pedir | Série temporal estruturada, linha pequena e homogênea |
| Vídeo | Object storage (S3 e afins), com só o metadado no banco | Blob em banco é sempre caro e sempre errado |

### Mutabilidade não é critério para escolher não-relacional

Este é o ponto que motivou o ADR. Postgres lida muito bem com `UPDATE` frequente — é o
trabalho dele. Frequência de escrita não é argumento para trocar de tecnologia.

O argumento **real** a favor de documento é **heterogeneidade de schema**: veículo elétrico
tem campo que veículo a combustão não tem, e a lista de atributos vai continuar crescendo
conforme o produto anda (valor FIPE, histórico de sinistro, nota de condição do ativo).

Só que trocar Postgres por Mongo para resolver isso custa os `JOIN` com manutenção, custo e
viagem — que são relacionamentos de verdade, não coincidência de modelagem.

**`jsonb` resolve os dois lados**: schema flexível no que varia, relacional no que se
relaciona. O que é filtrado, ordenado ou indexado continua sendo coluna de verdade (placa,
status, odômetro); o que é atributo solto vai para `atributos jsonb`, com índice GIN.

### O custo escondido de rodar três bancos

Três tecnologias é três vezes: expertise da equipe, monitoramento, estratégia de backup,
plano de restore, e superfície de incidente. Isso é custo operacional recorrente, não custo
de infraestrutura — e é pago por uma equipe que hoje é pequena.

O modelo de custo mostra o outro lado: no cenário de 50 frotas, o custo por veículo é
**maior** que no piloto, justamente porque começa a pagar instâncias separadas antes de ter
volume que dilua. Separar cedo demais custa dinheiro e atenção.

## Quando reavaliar

- **Frota sair do `jsonb`**: se os atributos virarem bagunça incontrolável na prática — muitos
  documentos com formatos conflitantes, consulta que precisa de agregação sobre o JSON. Aí é
  documento de verdade, e a migração terá dados reais para guiar o modelo.
- **GPS sair do Postgres**: quando a tabela de pings passar a exigir particionamento manual
  ou a consulta por janela de tempo degradar. Timescale é o passo natural; ClickHouse/Cassandra
  só passado o degrau de ~20-30k veículos, conforme a planilha de custo já sinaliza.

## Consequências

- Uma tecnologia de banco para operar, testar e restaurar no curto e médio prazo.
- `vehicle.atributos` (`jsonb`, migration V9) passa a ser onde entram campos que variam por
  tipo de veículo, sem migration nova a cada atributo.
- Vídeo nunca entra em banco, mesmo quando o produto existir — só a URL e o metadado.
- Exceção deliberada: a **categoria** do veículo (carro/moto/van/caminhão/ônibus, migration
  V18, coluna `tipo`) saiu do `jsonb` e virou coluna própria. Não é um atributo que varia por
  tipo — é o próprio discriminador, um enum pequeno e fechado, usado para filtro e ícone na
  UI. `jsonb` continua sendo o lugar certo só para o que de fato varia (cilindrada de moto,
  autonomia de elétrico, capacidade de carga).
