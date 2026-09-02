# 0025 — Permissão por módulo, ajustável por usuário

## Contexto

Até aqui, autorização era só o papel: `GESTOR_FROTA`/`ADMIN` fazia tudo, `DESPACHANTE` lia
tudo e escrevia só em rota, `VISUALIZADOR` só lia (spec 15). Cada um dos ~63
`@PreAuthorize` do backend listava papéis na mão, e o front tinha um único
`usePodeEscrever()` booleano, sem noção de módulo.

Pedido do Guilherme (2026-09-02): poder escolher, por pessoa, o que ela pode ver e alterar —
"entra no detalhe de cada um deles sobre o que podem ou não podem fazer, e o mesmo sobre ver
ou não". Três papéis fixos não cobrem isso: uma frota real quer "esse despachante mexe em
frota também, mas não vê custo", e isso não é nem Despachante nem Visualizador.

## Decisão

**Permissão = módulo + ação**, com duas ações (`VER`, `ESCREVER`) e nove módulos que
espelham o menu lateral — que é como o gestor pensa sobre o sistema, não por endpoint nem por
tabela: Frota, Ordens de serviço (inclui a tela de Manutenção, mesma base), Motoristas,
Mensagens, Rotas (inclui coleta/entrega, pontos de coleta e passageiros), Custos, Relatórios,
Parceiros, Pontos de recarga.

**Granularidade por módulo, não por ação individual** (decisão explícita do Guilherme): "pode
cancelar rota" separado de "pode criar rota" viraria uma matriz grande demais pra configurar
e um catálogo que cresce a cada funcionalidade nova.

**O papel continua existindo como padrão**, não é substituído: o convite segue escolhendo
Despachante/Visualizador (que definem os padrões sensatos), e o ajuste fino por usuário é
opcional, por cima disso. Convidar alguém continua tão simples quanto antes.

**Só a diferença é persistida** (`user_permission_override`, V35): quem nunca teve permissão
ajustada não tem linha nenhuma, e trocar o papel da pessoa volta a valer sozinho. Trocar de
papel zera os ajustes de propósito — manter override antigo por cima produziria combinação
que ninguém escolheu conscientemente ("Visualizador que escreve em Custos", sobrando de
quando a pessoa era Despachante).

**Os padrões reproduzem exatamente o comportamento anterior.** Quem não mexer em nada não
percebe diferença: Despachante segue lendo tudo e escrevendo só em rota, Visualizador segue
só lendo. Essa foi a restrição de projeto mais importante — a migração não muda acesso de
ninguém.

## Como a autorização acontece

Cada permissão efetiva vira uma **authority do Spring dentro do próprio JWT**
(`PERM_ROTAS_ESCREVER`), ao lado do `ROLE_x` que já existia. Assim o `@PreAuthorize` continua
sendo uma anotação simples (`hasAuthority('PERM_ROTAS_ESCREVER')`), sem `PermissionEvaluator`
customizado, e **nenhuma requisição passa a ir ao banco** — a decisão de manter o
`JwtAuthenticationFilter` sem consulta por request (comentário original do `JwtPrincipal`)
continua valendo.

**Consequência assumida: mudança de permissão só vale quando o access token renova (≤15
min).** Não é regressão nem descuido — é exatamente o que remover alguém da equipe já fazia
antes desta ADR, pelo mesmo motivo. `/v1/auth/me` recalcula do banco (o front usa pra esconder
botão), mas a autorização de verdade continua sendo o token.

## Endpoints que continuam por PAPEL, fora do sistema de permissão

Nem tudo virou módulo. Estes seguem exigindo `GESTOR_FROTA`/`ADMIN` porque virar permissão de
módulo daria acesso que hoje não existe:

- **Equipe e Assinatura** — assunto de dono da conta, não delegável (já era assim na spec 15).
- **Cancelar rota** (`POST /v1/routes/plans/{id}/cancel`) — cairia em "Rotas / escrever", que
  o Despachante tem por padrão, e a spec 15 tira isso dele de propósito.
- **Ações de rota pelo chat** (anexar/cancelar/trocar motorista, criar conversa com motorista,
  sync cursor) — gestão da relação com o motorista, mesmo raciocínio do item acima.
- **Avaliação de motorista** (`/v1/drivers/{id}/ratings`, classe inteira) — cairia em
  "Motoristas / ver", que Despachante e Visualizador têm por padrão, e a spec 06 é explícita
  que a nota é visível só a quem contratou. Abrir por engano seria o vazamento que a regra
  existe pra impedir.

Se algum desses precisar virar delegável depois, é decisão de produto — está registrada aqui
justamente pra não parecer esquecimento.

## Endpoints de leitura que estavam sem guarda nenhuma

Achado ao mapear a superfície: vários GETs não tinham `@PreAuthorize` (frota, ordens de
serviço, relatórios, parceiros, pontos de recarga, custos, incidentes/valor de mercado do
veículo, chat). Sem fechá-los, "tirar o ver de Custos" não teria efeito real — a tela sumia do
menu mas a API continuava respondendo. Todos passaram a exigir a permissão `VER` do módulo.

**O app do motorista consome quatro desses pelos mesmos endpoints do painel** (chat, lista de
veículos, pontos de recarga) — por isso `MOTORISTA` recebe `MENSAGENS_VER/ESCREVER`,
`FROTA_VER` e `RECARGA_VER` por padrão. Sem isso, fechar os GETs derrubaria o app; conferido
em `apps/mobile/src/api/client.ts`.

## Frontend

- `usePodeEscrever()` (booleano global) foi substituído por `usePode(modulo, acao)`, aplicado
  nas 8 telas que escondiam botão por papel.
- Menu lateral e rotas escondem/redirecionam por `VER` do módulo — sem isso, revogar acesso
  deixava o item no menu levando a uma tela que só respondia 403.
- Tela de Equipe ganhou o editor: clica no integrante, marca módulo a módulo o que ele pode
  ver e alterar. Só Despachante/Visualizador — mexer na permissão do dono da conta (ou na
  própria) seria uma forma de se trancar pra fora do sistema.

## Alternativas descartadas

- **Consulta por request (com cache em Redis)** — permissão passaria a valer instantaneamente,
  mas cobraria uma ida a mais em toda requisição autenticada e contradiz a decisão original do
  `JwtPrincipal`. Os 15 min de defasagem já eram aceitos pra remoção de membro.
- **Substituir o papel pela permissão granular** — convite ficaria mais trabalhoso (configurar
  tudo do zero por pessoa) e perderia o atalho "despachante padrão", que é o caso comum.
