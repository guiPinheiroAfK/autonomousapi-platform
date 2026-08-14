# ADR 0011 — Confirmação de e-mail obrigatória no cadastro

**Status:** aceito
**Data:** 2026-08-14

## Contexto

Signup criava tenant, usuário e trial de 7 dias (ADR 0010) imediatamente, com qualquer
e-mail — sem validar que quem digitou aquele e-mail tem acesso a ele. Combinado com o
trial automático, isso significa: qualquer pessoa (ou script) consegue gerar tenants sem
fim, cada um com 7 dias de uso livre da infraestrutura (banco, e eventualmente Stripe/e-mail
de verdade) — exatamente o cenário citado como motivação ("criança curiosa gastando com
banco de dados").

## Decisão

**Signup cria o usuário desabilitado (`app_user.enabled = false`) e não devolve tokens.**
O `AuthController.login` já rejeitava usuário desabilitado antes desta ADR — a trava de
login já existia, só faltava a metade que impede a conta de nascer habilitada.

Um token de confirmação (mesmo padrão de hash SHA-256 do refresh token, nunca guardado em
claro) é gerado, válido por 24h, e enviado por e-mail com um link para
`{WEB_APP_URL}/verificar-email?token=...`. `POST /v1/auth/verify-email` valida o token,
habilita o usuário e já emite os tokens de acesso — o clique no link é a prova de posse
da conta, não precisa logar de novo depois.

**Envio de e-mail é "front técnico"**, mesmo raciocínio da ADR 0010 com a Stripe: sem
`spring.mail.host` configurado (padrão dev/demo), o link só é logado
(`LoggingEmailSender`) — o fluxo inteiro (signup → pegar o link do log → confirmar →
logar) é testável sem nenhuma credencial de provedor. Assim que houver SMTP real
(Resend, SES, Mailgun, Gmail com senha de app — qualquer um funciona, é SMTP genérico),
`SmtpEmailSender` assume sozinho.

`EmailConfig` decide entre as duas implementações com um `isBlank()` explícito, não com
`@ConditionalOnProperty`: essa anotação do Spring considera "propriedade presente e vazia"
como "presente" — e é exatamente isso que `${MAIL_SMTP_HOST:}` sempre resolve quando a env
var não existe. Usar `@ConditionalOnProperty` teria ativado o `SmtpEmailSender` sem host
nenhum configurado.

**`resend-verification` sempre responde 202, tenha o e-mail conta ou não, e mesmo se já
confirmado.** Diferenciar a resposta permitiria descobrir se um e-mail está cadastrado só
tentando reenviar confirmação pra ele.

## Consequências

- `SignupResponse` substitui `TokenResponse` como retorno de `POST /v1/auth/signup` —
  **breaking change** de contrato. `packages/shared-types` precisa ser regenerado
  (`npm run gen:types`); o front (`AuthContext`/`SignupPage`) precisa parar de assumir
  login automático depois do cadastro.
- Reenviar confirmação invalida (marca como usado) qualquer token pendente anterior do
  mesmo usuário — só o link mais recente funciona, evita confusão de "cliquei no link
  errado" quando a pessoa pediu reenvio.
- `DemoDataSeeder` não muda: cria o usuário direto via `new User(...)`, que continua
  nascendo habilitado por padrão — o ambiente de demonstração não passa por confirmação.
