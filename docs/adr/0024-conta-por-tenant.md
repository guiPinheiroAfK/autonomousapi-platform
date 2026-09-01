# 0024 — Uma conta por empresa, não uma conta pra sempre

## Contexto

Um Gestor tentou convidar um amigo (Despachante) que já tinha conta própria em outra
empresa cadastrada no sistema — o convite foi recusado com "E-mail já cadastrado". Investigando:
`app_user.email` era único **globalmente** desde o início do projeto (`V1__auth_e_tenant.sql`),
não por empresa — uma pessoa só podia ter uma conta na plataforma inteira, pra sempre, mesmo
que quisesse participar de outra empresa com um papel diferente (ex: é Gestor da própria
frota e também Despachante de um amigo). Investigando mais a fundo, achamos um segundo
problema relacionado: "remover" alguém da equipe (`TeamService.remove`) só desativava
(`enabled = false`) — nunca liberava o e-mail, então nem re-convidar a mesma pessoa pra
**mesma** empresa depois de removida funcionava.

## Decisão — e-mail único por tenant, não mais global

`app_user.email` passa a ser único **por tenant** (`V34__app_user_email_per_tenant.sql`) —
uma pessoa pode ter uma linha (papel e senha próprios, independentes) por empresa em que
participa. Aditivo: os ~76 pontos do código que já leem `tenantId` do JWT
(`JwtPrincipal.tenantId()`) não mudam nada — o token continua carregando um tenant fixo por
sessão, a pessoa escolhe qual "conta" usar no login, nunca fica logada em várias ao mesmo
tempo.

**Por que não juntar numa identidade única compartilhada (uma senha só pra pessoa, tipo
Slack):** seria o desenho mais "correto" a longo prazo, mas é um redesenho bem maior —
separar "quem eu sou" (login) de "onde eu tenho acesso" (uma linha por tenant+papel) muda a
JWT, o login, e potencialmente o app mobile. Como o problema relatado (convidar alguém que já
tem conta em outro lugar) não precisa disso — só precisa que contas em tenants diferentes
não colidam — ficou registrado como ideia futura, não implementado agora.

## Login com múltiplas contas — escolha de empresa, não "senha errada" nem adivinhação

Como cada aceite de convite pede senha nova (nunca existiu senha compartilhada entre
contas), a mesma pessoa pode legitimamente reusar a mesma senha em duas empresas — plausível
mesmo sem querer, muita gente reusa senha. `AuthService.login` testa a senha contra **todas**
as linhas do e-mail (sem short-circuit no primeiro match, pra manter tempo de resposta
estável) e:

- Nenhuma bate → `InvalidCredentialsException` (mesma exceção de sempre, nunca revela se o
  e-mail existe).
- Uma bate → emite os tokens direto, exatamente como antes.
- Mais de uma bate → devolve um token curto (`pending_login`, 5 minutos, mesma chave HS256 do
  access token) listando as empresas candidatas, em vez de escolher uma arbitrariamente. O
  cliente completa com `POST /v1/auth/select-tenant`. Posse desse token já prova que a senha
  bateu em todas as contas listadas nele — trocar por tokens de qualquer uma delas não é mais
  fraco que o login normal.

Web ganhou uma tela pequena pra esse caso raro (nome da empresa + papel, escolhe e entra) —
`LoginPage.tsx`, sem rota nova, é um render condicional no mesmo formulário.

## Exclusão real de conta — tenta apagar de verdade, cai pro comportamento de antes se não der

`TeamService.remove()` agora tenta um hard delete de verdade primeiro (libera o e-mail pra
reuso — em outro tenant, ou reconvidando pra este mesmo depois) e só cai pro desativar de
antes se o alvo já tiver histórico próprio (rota criada, mensagem enviada, refresh token de
uma sessão anterior — qualquer FK sem `ON DELETE` recusa o DELETE). Mesmo botão de sempre na
tela de Equipe, nenhuma mudança de UI — o comportamento por trás é que ficou melhor.

**Achado real durante a implementação, não hipotético:** tentar o delete dentro da mesma
transação de `remove()` (com `try`/`catch` em volta de `flush()`) parecia funcionar no
código, mas quebrava ao vivo — assim que o Postgres recusa o DELETE por violação de FK, o
Spring marca a transação inteira como *rollback-only*, e capturar a exceção não desfaz essa
marca: o commit no fim do método falhava com `UnexpectedRollbackException`, que a cadeia de
segurança confundia com "não autenticado" (401 genérico em vez do 204 esperado). A correção:
isolar a tentativa numa transação própria (`UserHardDeleteAttempt`, `REQUIRES_NEW`) — ela
sofre o rollback sozinha quando a FK barra, sem tocar na transação de fora, que segue livre
pra fazer o `setEnabled(false)` de fallback normalmente.

## O que fica de fora, registrado pra depois

- **Identidade unificada de verdade** (uma senha só pra pessoa em todas as empresas, trocar
  de empresa sem sair da sessão) — ver "Por que não" acima.
- **App mobile** não ganhou nenhuma mudança — motorista nunca teve múltiplas contas
  (`Role.MOTORISTA` não participa desse fluxo), e o app não expõe login administrativo.
- **`googleAuth`** com e-mail em múltiplas contas pega a primeira determinística, sem tela de
  escolha (Google só prova posse do e-mail, não escolhe entre elas) — ambiguidade real fica
  pra decisão futura, não vale um segundo fluxo de escolha só pro login via Google agora.
- **`resendVerification`/`forgotPassword`** com e-mail em múltiplas contas agora agem em
  **todas** as contas pendentes/existentes (reenvia confirmação pra cada uma pendente, manda
  link de redefinição pra cada uma) — evita escolher uma arbitrariamente e ignorar as outras
  silenciosamente, sem precisar de UI nova pra esses dois fluxos de baixo volume.
