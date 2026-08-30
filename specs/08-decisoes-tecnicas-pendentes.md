# 08 — Decisões Técnicas Pendentes (Front Web, Dados de Veículo e Operacional)

Registro de decisões pontuais discutidas fora do fluxo normal de fase, no formato curto (mini-ADR). Servem pra não virar pergunta recorrente da equipe.

## 1. Tela de veículo: dialog → rota própria (`/frota/:id`) — priorizado

**Situação atual:** o detalhe do veículo abre em dialog/modal.

**Decisão:** migrar para rota própria `/frota/:id`. Já são 4 abas dentro do dialog, que fica apertado; rota própria dá mais espaço, cresce melhor conforme novas abas forem adicionadas, e permite deep-link (compartilhar o link direto do veículo com o gestor de outra unidade, por exemplo).

**Trade-off aceito:** rota própria dá mais trabalho de navegação (breadcrumb, botão voltar) que o dialog ganha de graça — aceito conscientemente.

**Escopo da mudança:**
- Nova rota `/frota/:id` substitui o dialog atual, preservando todas as abas/funcionalidades já existentes.
- Breadcrumb (Frota > placa/modelo) e botão voltar.
- Acesso direto via `/frota/:id` (sem passar pela lista) precisa funcionar — carregar o veículo certo e checar permissão do usuário logado antes de exibir.

**Prioridade:** entra no próximo pacote de trabalho, junto com o app do motorista (spec 07) — ver `05-roadmap-fases.md`.

## 2. Fonte de dado de manutenção por modelo — decisão: manter manual

**Situação atual:** "Próx. preventiva" e "Preventiva por km" são preenchidos manualmente pelo gestor no cadastro/edição do veículo. Não existe integração com fabricante.

**Por que não tem integração:** não existe API pública/gratuita confiável no Brasil que dê, por exemplo, "intervalo de troca de correia dentada do Fiat Strada 2023". A FIPE só devolve valor de mercado (já usado, RF014). As opções reais seriam: (a) um provedor pago de dados técnicos por modelo, do tipo usado por concessionárias/oficinas — custo recorrente, alta precisão; ou (b) uma tabela curada internamente por categoria de veículo (ex. "moto: revisão a cada 3.000km", "van: a cada 10.000km") — mais barata, menos precisa.

**Decisão registrada:** manter manual por enquanto. Não é um gap esquecido, é escolha consciente até existir sinal real de que o produto precisa de mais automação (ex. clientes maiores pedindo isso, ou volume de dado suficiente pra montar a curadoria própria com confiança).

**Reavaliar quando:** a base de clientes crescer o suficiente para justificar o custo de um provedor pago, **ou** o time tiver uma janela para curar uma tabela própria por categoria como projeto à parte. Nenhuma das duas é bloqueadora de nada hoje.

## 3. Catálogo de afiliados — status atual

A estrutura de dados (spec `06-parcerias-e-dados-futuros.md`) já aceita quantos parceiros forem cadastrados, mas hoje existe **apenas 1 parceiro de exemplo** (rastreador, não dashcam). Não existe UI de admin para cadastro — é feito direto no banco, porque a negociação comercial de cada parceria é externa e manual mesmo, e não compensa automatizar isso antes de ter volume de parceiros. Ampliar o catálogo (incluir dashcam, microfone, outros equipamentos) é inserção de linha nova, sem mudança de código.

## 4. Ícone por tipo de veículo — quick win de backlog

Cosmético, sem dependência de API — usa o campo `tipo` que o veículo já tem (carro, moto, van, etc.) para trocar o ícone/SVG exibido na lista e no detalhe. Implementado (PR #49).

## 5. Nova aba "Pontos de Coleta" — necessária para rotas multi-parada

**Situação atual:** rotas multi-coleta/multi-entrega (spec `02-dados-mapas-rotas.md`) precisam de um endereço por parada. Sem cadastro reutilizável, o gestor digita o mesmo endereço toda vez que monta uma rota nova — atrito desnecessário para pontos que se repetem (depósito, filial, cliente recorrente).

**Decisão:** criar aba própria no web para CRUD de `collection_point` — nome, endereço, lat/lon (geocodificado a partir do endereço, não digitado manualmente), ativo/inativo. Ao montar uma rota, o gestor escolhe entre um ponto já cadastrado ou um endereço avulso pontual — os dois fluxos coexistem, cadastro não é obrigatório.

**Geocodificação: Nominatim, não Google Maps — decisão deliberada.** O projeto já resolve endereço→coordenada via Nominatim (mesma base de dado do OSM que o OSRM usa para rotear). Trocar por Google Maps introduziria custo por chamada, uma chave de API pra gerenciar, e — o problema real — uma segunda fonte de coordenadas que pode divergir da malha que o OSRM conhece. Nominatim garante que todo ponto geocodificado é, por definição, um ponto que o motor de rota também entende. Mesma filosofia já aplicada em `charging.py` — não pagar por API externa antes de precisar de verdade.

**Ajuste manual do pino — implementado sem mapa.** O projeto não tem lib de mapa/tiles (mesma decisão minimalista já usada em `RoutesPage.tsx`, que desenha o traçado em SVG puro sem basemap). Em vez de arrastar um pino visualmente, o gestor busca o endereço de novo no Nominatim e escolhe outro resultado — o backend detecta a mudança de lat/lon em relação ao valor salvo e marca `posicaoAjustada = true` automaticamente. Resolve o mesmo problema (corrigir geocodificação imprecisa) sem introduzir uma lib de mapa só pra isso; se um mapa de verdade virar necessidade real (não só deste caso), é decisão maior, à parte.

**Status:** implementado (`CollectionPointsPage.tsx`, `com.autonomousapi.core.collectionpoint`).

## 6. Acabamento visual do chat — mesmo padrão do dashboard

**Situação identificada:** a UI do mini-chat (spec `07-app-motorista.md`) estava genérica/"sem sal", destoando do resto do produto.

**Decisão:** tratar acabamento visual do chat (paleta, tipografia, bolha de mensagem, indicador de lido/digitando, avatar, empty state desenhado) como critério de Definition of Done da spec 07, não como polish opcional. Reaproveitar os tokens/componentes já definidos no resto do web em vez de criar um sistema visual paralelo só para o chat.

**Status:** implementado — `Card`/`Avatar`/ícones do design system já em uso no resto do painel, prévia de última mensagem + horário relativo na lista de conversas, empty states com ícone, indicador de lido (`Check`/`CheckCheck`) e "digitando..." (poll de 2s, estado em memória no servidor via `TypingIndicatorService`).

## 7. `npm audit` no `apps/web` — 1 crítica + 22 altas + 12 moderadas

**Situação atual:** o audit não travou nenhum build/deploy ainda (nenhuma delas foi explorada em produção, até onde se sabe), mas o volume é grande o suficiente para não ser "mais uma linha de warning ignorada".

**Decisão:** não é uma correção de emergência (nada travou), mas também não é opcional — entra como item de manutenção a rodar num momento calmo (fora de janela de release de feature), não espremido no meio de outra entrega. `npm audit fix` resolve a maior parte automaticamente; o que sobrar (normalmente breaking changes de major version de dependência) precisa de revisão manual, não `--force` cego — `npm audit fix --force` pode subir major version de uma lib usada em produção sem aviso, o que troca "vulnerabilidade não explorada" por "build quebrado" — não é troca boa.

**Prioridade:** antes de qualquer novo cliente maior/enterprise entrar (due diligence de segurança costuma pedir isso), e de qualquer forma antes de virar produto licenciado pra parceiro de AV (Fase 4). Não bloqueia o trabalho atual.

**Status:** implementado (2026-08-27) — `npm audit fix` resolveu o que dava sem breaking change; o que sobrava (postcss, preso à versão do Vite) foi junto do upgrade do item 8. `npm audit` no `apps/web` hoje: **0 vulnerabilidades**.

## 8. Bundle do `web` grande (`index.js` ~698KB) — code-splitting pendente

**Situação atual:** o bundle de produção do `apps/web` está em ~698KB para o `index.js` principal, sem divisão por rota — tudo carrega de uma vez no primeiro acesso, mesmo telas que o usuário talvez nunca abra na sessão (ex. gestor que só olha o Dashboard nunca carrega o código da aba Custos, mas hoje carrega de qualquer forma).

**Decisão:** code-splitting via `import()` dinâmico por rota (`React.lazy` + `Suspense`, já é o padrão nativo do Vite/React Router, sem lib nova) — cada página principal (Dashboard, Frota, Motoristas, Custos, Pontos de Coleta, etc.) vira um chunk separado, carregado só quando o usuário navega até ela. Não é reescrever nada de lógica, é só trocar `import` estático por `import()` nos pontos de rota.

**Prioridade:** não bloqueia nada hoje (o produto funciona, é questão de tempo de carregamento inicial) — fica para a próxima vez que alguém for mexer em performance do web, não como projeto isolado. Vale medir o "antes/depois" com Lighthouse ou equivalente pra ter número real do ganho, não só "parece mais rápido".

**Status:** implementado (2026-08-27) — as páginas já eram todas `React.lazy`/`Suspense` por rota antes deste item (achado na auditoria: a decisão registrada acima já tinha sido aplicada em sessão anterior, sem atualizar este documento). O ganho real veio de outro lugar: upgrade do Vite 5→8, que troca o bundler interno pra Rolldown — muito mais esperto em split automático de vendor, sem precisar escrever `manualChunks` na mão. Medido antes/depois do build de produção:

| | antes (Vite 5) | depois (Vite 8) |
|---|---|---|
| `index.js` principal | 704.86 KB (gzip 223.98 KB) | 383.45 KB (gzip 120.77 KB) |
| aviso de chunk > 500KB | sim | não (nenhum chunk passa de 500KB) |

`react-dom`, Motion e utilitários compartilhados (~230KB) saíram do chunk principal pra chunks próprios automaticamente. Verificado manualmente no navegador depois do upgrade (Dashboard, Relatórios com gráfico, modal de nova rota) — sem erro de console, sem regressão visual.

## 9. Branch protection em `develop` — só `main` está protegida hoje

**Situação atual:** a proteção de branch (PR obrigatória, sem push direto) foi ligada em `main`, mas `develop` — que é de onde toda branch de feature sai, por `04-repositorio-e-git-workflow.md` — ainda aceita push direto.

**Decisão:** replicar a mesma regra (exigir PR antes de merge, sem push direto) em `develop`, sem exigir número de aprovações (mesmo raciocínio já aplicado em `main`: time é o próprio Guilherme hoje, exigir aprovação de terceiro bloquearia o próprio fluxo de trabalho) — só a disciplina de "sempre via PR" é o que importa aqui, não um segundo revisor.

**Prioridade:** questão de tempo/orçamento — não é urgente (não houve incidente), mas fecha a mesma lacuna que já foi fechada em `main`. Baixo esforço (configuração, não código) — pode ser feito a qualquer momento que sobrar uma janela curta.

**Status:** implementado (2026-08-27) — `develop` replicando exatamente a configuração de `main` (PR obrigatória, `required_approving_review_count: 0`, sem force-push, sem deleção, conversas precisam estar resolvidas).

## 10. Animações — espalhar o padrão de transição de página

**Situação atual:** Dashboard e Frota já têm transição de página bem resolvida (parte do redesign "FrotaOS"). As demais telas (Motoristas, Custos, Pontos de Coleta, detalhe de veículo, etc.) ainda não seguem o mesmo padrão — inconsistência de acabamento entre uma tela e outra.

**Decisão:** reaproveitar o mesmo padrão/componente de transição já validado em Dashboard e Frota nas telas restantes, em vez de criar uma segunda abordagem — mesmo raciocínio já registrado no item 6 (chat) e no princípio geral do redesign: não plugar um componente com cara diferente do resto do produto.

**Fora de escopo aqui:** landing page — levantamento à parte, feito separadamente, não faz parte deste item.

**Prioridade:** polish, não bloqueia nada — mas como é reaproveitamento de um padrão já pronto (não é design novo), é um item de esforço baixo/médio quando sobrar janela de front-end.

**Status:** implementado (2026-08-27) — `StaggerGroup`/`StaggerItem` aplicado nas 9 telas que faltavam (Motoristas, Ordens de Serviço, Custos, Coleta & Entrega, Pontos de Recarga, Parceiros, Manutenção, Relatórios, detalhe de Veículo). O componente compartilhado (`Stagger.tsx`) ganhou suporte a `ul`/`ol`/`li` além de `div`/`tbody`/`tr` pra cobrir telas em lista, não só tabela/grid.

## 11. E-mail transacional — Resend configurado, falta domínio verificado

**Situação atual (2026-08-25):** o piloto (Oracle + Neon + Netlify) já está no ar. E-mail transacional (confirmação de conta, convite de motorista) já está configurado via Resend (`MAIL_SMTP_*` no `.env.prod` do `core-api`, documentado em `docs/setup-email-resend.md`) — mas sem domínio verificado no Resend, `MAIL_FROM` fica em `onboarding@resend.dev`, que só entrega no e-mail da própria conta Resend. Contas de piloto reais (qualquer e-mail que não seja o do dono da conta) ainda dependem de confirmação manual direto no Neon (flip do campo de confirmação na tabela).

**Decisão de resolução (já conhecida, só falta executar):** verificar um domínio próprio no Resend via DNS. Existe mais de um domínio próprio disponível — falta decidir qual usar e configurar os registros DNS (Resend + também Netlify, já que o mesmo domínio provavelmente serve o front hospedado lá).

**Prioridade:** não bloqueia teste com o próprio Guilherme, mas bloqueia qualquer usuário piloto real se cadastrando sem intervenção manual — resolver antes de convidar mais gente pra testar.

## 12. Notificações in-app — sino no topbar

**Situação identificada:** o sino de notificação no topbar existia só como enfeite — sempre visível, sem contador real, sem lista, sem forma de marcar como lida ou ver o histórico completo.

**Decisão:** sistema real, não só ajuste visual — tabela `notification` própria (schema `core`), com tipo (`ORCAMENTO_ALERTA`, `CNH_VENCENDO`, `MANUTENCAO_AGENDADA`, `AVISO_GESTOR`), lida/não lida e link de destino. Os jobs que já existiam (`BudgetAlertJob`, `AlertPushJob`, `DriverNotificationService`) passaram a gravar aqui em vez de só disparar push — o push continua acontecendo, mas agora como efeito colateral de `NotificationService.notify(...)`, não como único registro do aviso.

**Status:** implementado — `com.autonomousapi.core.notification` (migration `V27`), dropdown com itens reais e contador dinâmico no `Topbar.tsx`, página "ver todas" (`NotificationsPage.tsx`).

## 13. Login com Google + sessão persistente via refresh token

**Situação identificada:** relogar depois de deslogar era um ponto de atrito citado explicitamente — sem opção de entrar com Google (padrão esperado em qualquer site moderno) e sem sessão persistente (token expirava e jogava pro login sem aviso).

**Decisão:**
- **Login/cadastro com Google** — fluxo de ID token do Google Identity Services, verificado no backend via `GoogleIdTokenVerifier` (sem client secret, só o Client ID como audience). Primeiro login com um e-mail novo cria tenant + usuário + trial de 7 dias automaticamente, mesmo caminho de quem se cadastra manual.
- **Refresh token silencioso** — access token de 15min, refresh token de 30 dias (rotacionado a cada uso). Um 401 dispara retry automático via refresh antes de deslogar de verdade; chamadas concorrentes de refresh compartilham a mesma promise pra não invalidar o token rotacionado umas das outras.

**Pendência real:** o código está completo e no ar, mas **login com Google ainda não funciona em produção** — falta criar o OAuth Client ID no Google Cloud Console e configurar `GOOGLE_CLIENT_ID` (VM, `.env.prod` do `core-api`) + `VITE_GOOGLE_CLIENT_ID` (Netlify). Sem isso, o endpoint responde `503 google_auth_not_configured` de propósito (erro claro, não quebra silenciosamente). Login manual e refresh token já funcionam normalmente, independem dessa configuração.

**Status:** código implementado; ativação em produção depende de uma ação fora do repo (console do Google Cloud).

## 14. Otimizações de fetch no front — brainstorm de performance percebida

**Situação identificada:** sensação de lentidão que não vinha de servidor/banco (latência medida ficou em 150-200ms) — o suspeito era refetch redundante e falta de aproveitamento entre navegações, não capacidade de infraestrutura.

**Decisão — conjunto de técnicas complementares, todas no `apps/web`, sem lib nova:**
- **Cache de lista com TTL diferenciado por natureza do dado** (`client.ts`) — 30s pra listas operacionais, 5min pra dado editável que muda pouco (pontos de recarga/coleta), 1h pra catálogo global gerido pela plataforma (parceiros afiliados). Persistido em `sessionStorage`, sobrevive a reload dentro da mesma aba.
- **Dedupe de requisição em voo** — duas telas pedindo a mesma lista ao mesmo tempo compartilham uma única chamada de rede em vez de duas.
- **Prefetch no hover do menu lateral** — passar o mouse num item do menu já dispara o `import()` dinâmico da página, chegando em cache quando o clique realmente navega.
- **Lazy por aba no detalhe de veículo** — abas (OS, FIPE, sinistros) só buscam dado no primeiro clique, não todas de uma vez no mount.
- **Guarda contra resposta obsoleta** — trocar um filtro rapidamente (frota, custos) não deixa uma resposta antiga sobrescrever uma mais nova que chegou primeiro por causa da rede.
- **Update otimista** — ativar/desativar ponto de coleta reflete na tela na hora do clique, sem esperar o servidor confirmar (desfaz se der erro).
- **Cache-Control/ETag — avaliado e descartado.** O Spring Security já manda `no-store` por padrão em toda resposta, então o navegador nunca guardaria o corpo pra revalidar depois — um `ETag` no backend ficaria sem efeito sem reimplementar a revalidação inteira na mão no front. Pra listas pequenas que já têm cache de sessão com TTL de minutos/hora, não compensou a complexidade.

**Status:** implementado — sem medição formal de "antes/depois" (não é o tipo de ganho que Lighthouse capta bem, é sensação de fluidez em navegação repetida dentro da sessão), verificado manualmente em cada técnica.

## Definition of Done

- [x] Rota `/frota/:id` no ar substituindo o dialog, com breadcrumb, botão voltar e acesso direto por link funcionando (PR #44).
- [x] Decisão de dado de manutenção (item 2) também registrada como ADR no repositório real — [`docs/adr/0017-manutencao-por-modelo-manual.md`](../docs/adr/0017-manutencao-por-modelo-manual.md).
- [x] Ícone por tipo de veículo implementado (PR #49).
- [x] Aba "Pontos de Coleta" no ar, com CRUD básico e geocodificação de endereço.
- [x] Chat revisado visualmente para o mesmo padrão do dashboard.
- [x] `npm audit fix` rodado no `apps/web`, resto revisado manualmente (item 7).
- [x] Code-splitting por rota no `apps/web`, com medição de antes/depois (item 8).
- [x] `develop` com a mesma proteção de branch já ativa em `main` (item 9).
- [x] Padrão de transição de página (Dashboard/Frota) replicado nas demais telas (item 10).
- [ ] Domínio verificado no Resend + Netlify, confirmação de e-mail deixa de depender do workaround manual (item 11).
- [x] Sistema de notificações in-app no ar, substituindo o sino decorativo (item 12).
- [x] Login com Google e refresh token silencioso implementados no código; ativação em produção pendente de Client ID (item 13).
- [x] Otimizações de fetch do front (cache TTL, prefetch, dedupe, guarda de resposta obsoleta, update otimista) implementadas (item 14).
