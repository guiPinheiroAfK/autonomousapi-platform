# Deploy do piloto — Oracle Cloud (VM) + Neon (banco) + Netlify (front)

Runbook pra rodar **na VM**, via SSH (`ssh -i "sua-chave.key" opc@163.176.224.227`).
Não precisa colar nada sensível de volta pro chat — só me avise se algum passo der erro
(cola o output do comando que falhou).

## 1. Instalar Docker (Oracle Linux)

```bash
sudo dnf install -y dnf-utils
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

Depois do `usermod`, desconecta e reconecta o SSH (`exit`, roda o `ssh ...` de novo) pra
o grupo `docker` valer sem precisar de `sudo` nos comandos seguintes.

## 2. Liberar as portas 80/443 no firewall da própria VM

O Oracle Linux vem com `firewalld` bloqueando por padrão, além da Security List que já
liberamos no console (isso é *outra* camada, tem que liberar as duas):

```bash
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
```

## 3. Clonar o repositório

```bash
git clone https://github.com/guiPinheiroAfK/autonomousapi-platform.git
cd autonomousapi-platform
```

Se o repo for privado, o `git clone` vai pedir usuário/token do GitHub — usa um
[personal access token](https://github.com/settings/tokens) no lugar da senha.

## 4. Criar os `.env.prod`

```bash
cp services/core-api/.env.prod.example services/core-api/.env.prod
cp services/geo-api/.env.prod.example services/geo-api/.env.prod
nano services/core-api/.env.prod
nano services/geo-api/.env.prod
```

Preenche com a connection string **direta** do Neon (sem `-pooler`) e um
`CORE_JWT_SECRET` novo — os comentários dentro de cada arquivo explicam cada campo.
O `GEO_SERVICE_TOKEN` tem que ser **o mesmo valor** nos dois arquivos.

## 5. Subir os containers

```bash
docker compose -f infra/docker-compose.prod.yml up -d --build
```

Primeira vez demora uns minutos (build do Java + Python). Acompanha com:

```bash
docker compose -f infra/docker-compose.prod.yml logs -f
```

Ctrl+C sai do log sem derrubar os containers.

## 6. Testar

Da sua própria máquina (não precisa estar na VM):

```bash
curl -i https://163-176-224-227.sslip.io/v1/auth/login
```

Uma resposta HTTP (mesmo que erro 400/401 — o importante é responder, não dar timeout
nem erro de certificado) confirma que Caddy conseguiu o certificado TLS e está roteando
pro `core-api`. Se der erro de certificado, espera ~1 min (Let's Encrypt) e tenta de novo.

## 7. Publicar o `apps/web` no Netlify

Isso já não é mais comigo por SSH — é você direto no [app.netlify.com](https://app.netlify.com):
1. "Add new site" → "Import an existing project" → conecta o GitHub → escolhe este repo.
2. O `netlify.toml` na raiz já configura build/publish/proxy automaticamente — não
   precisa preencher nada manual no formulário de build settings.
3. Depois do primeiro deploy, pega a URL que o Netlify gerou (tipo
   `algumnome.netlify.app`) e atualiza `WEB_APP_URL` no `.env.prod` do `core-api` na
   VM (passo 4) com essa URL — depois `docker compose -f infra/docker-compose.prod.yml
   up -d core-api` pra aplicar sem rebuildar tudo de novo.

## Redeploy depois de um `git push`

```bash
cd autonomousapi-platform
git pull
docker compose -f infra/docker-compose.prod.yml up -d --build
```

## Quando tiver um domínio de verdade

Troca `163-176-224-227.sslip.io` por ele em **dois lugares**: `infra/Caddyfile` (na VM)
e `netlify.toml` (no repo, redeploy do Netlify). Caddy tira certificado novo sozinho.
