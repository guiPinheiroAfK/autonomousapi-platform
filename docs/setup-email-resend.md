# Configurar e-mail transacional (Resend)

O `core-api` já tem suporte a SMTP genérico embutido (`EmailConfig`/`SmtpEmailSender`,
`services/core-api/src/main/resources/application.yml:29-39`) — sem `MAIL_SMTP_HOST`
preenchido, ele cai automaticamente no `LoggingEmailSender` (loga o link de
confirmação/convite em vez de mandar de verdade, é o que estava acontecendo até agora).
Não precisa de nenhuma mudança de código pra ligar um provedor real, só preencher as
variáveis de ambiente.

Escolhido o [Resend](https://resend.com): 3.000 e-mails grátis/mês (100/dia), sem cartão
de crédito pra começar.

## 1. Criar a conta (você, não eu — ver nota de segurança no fim)

1. [resend.com/signup](https://resend.com/signup) — cria a conta.
2. No painel, vai em **API Keys** → **Create API Key** → guarda a chave (começa com `re_`,
   só aparece uma vez).

## 2. Domínio verificado — necessário pra mandar pra pessoas de verdade

Sem domínio verificado, o Resend só deixa mandar e-mail de teste pro seu **próprio**
e-mail (o que você usou no cadastro) — nenhum destinatário real recebe nada. Pra
convite/confirmação chegarem nos usuários piloto de verdade, precisa:

1. Ter um domínio (ex. comprado na Namecheap/Registro.br — vocês ainda não têm um pro
   projeto, é a mesma decisão pendente do domínio do Netlify).
2. No painel do Resend: **Domains** → **Add Domain** → adiciona os registros DNS
   (SPF, DKIM, geralmente 2-3 registros TXT/CNAME) que ele pedir, no provedor onde o
   domínio foi registrado.
3. Espera propagar (geralmente minutos, às vezes até 24h) até o Resend marcar o domínio
   como **Verified**.

**Enquanto não tiver domínio**: dá pra testar o fluxo end-to-end contigo mesmo usando o
remetente de sandbox `onboarding@resend.dev` como `MAIL_FROM`, mas só recebendo no
e-mail da sua própria conta Resend — não serve pra outros usuários ainda.

## 3. Preencher no `.env.prod` da VM (core-api)

```bash
nano services/core-api/.env.prod
```

Adiciona (ou atualiza) essas linhas:

```
MAIL_SMTP_HOST=smtp.resend.com
MAIL_SMTP_PORT=587
MAIL_SMTP_USER=resend
MAIL_SMTP_PASSWORD=re_SUA_API_KEY_AQUI
MAIL_FROM=onboarding@resend.dev
```

(`MAIL_SMTP_USER` é literalmente a palavra `resend`, não é um typo — é assim que a API
key vira credencial SMTP no Resend. Troque `MAIL_FROM` pelo endereço do seu domínio
verificado assim que tiver um, ex. `contato@seudominio.com.br`.)

Salva (`Ctrl+O`, `Enter`, `Ctrl+X`) e reinicia só o core-api:

```bash
docker compose -f infra/docker-compose.prod.yml up -d core-api
```

## 4. Testar

Cadastra uma conta nova pelo `https://autonomousapi.netlify.app` usando o e-mail da sua
própria conta Resend (enquanto não tiver domínio verificado) e confere se o e-mail de
confirmação chega de verdade, em vez de aparecer só no log.
