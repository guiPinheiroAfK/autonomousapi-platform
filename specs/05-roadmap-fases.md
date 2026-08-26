# 05 — Roadmap por Fases ("a escada de crescimento")

Cada fase é vendável/demonstrável sozinha — não é promessa distante. Trabalhar uma fase de cada vez com o Claude Code; não pular para a Fase 2 sem a Fase 1 com a Definition of Done cumprida.

## Fase 1 — Gestão de frota (SaaS ativo)

**Objetivo:** produto vendável hoje, resolve dor real de custo/manutenção de frota.

Escopo:
- `core-api`: auth multi-perfil, CRUD de veículo/motorista, cálculo de custo por km, alertas de manutenção/vencimento.
- `web`: painel do gestor (cadastro, dashboard, alertas, export de relatório).
- `mobile`: app do motorista com registro de viagem (mesmo que ainda sem uso do dado para prontidão viária).
- Banco: schema `core` completo; schema `geo` criado mas só com ingestão bruta de GPS (sem processamento ainda).
- Repositório e CI/CD no lugar (ver `04-repositorio-e-git-workflow.md`).
- Billing: pelo menos um canal funcional (recomendado: web/Stripe primeiro, ver `03-mobile-e-assinaturas.md`).

**Saída da fase:** um gestor de frota real consegue cadastrar veículos, receber alerta de manutenção, ver custo por km, e pagar assinatura — sem precisar do time para operar manualmente nada disso.

## Próximo pacote de trabalho (priorizado, em cima da Fase 1)

Dois itens identificados depois do MVP inicial, priorizados para entrar juntos no próximo ciclo, antes de avançar para o restante da Fase 2:

- **App do motorista completo**, redesenhado em torno do princípio "operador, não gestor" (tela "Hoje" como home, resto secundário) — spec `07-app-motorista.md`. Hoje o app só tem login, consentimento e tela de viagem, o que empurra toda comunicação prática pro WhatsApp.
- **Mini-chat gestor↔motorista** com acabamento visual equivalente ao dashboard (não genérico) e com atribuição de rota diretamente pela conversa (mensagem estruturada) — spec `07-app-motorista.md`.
- **Aba "Pontos de Coleta"** no web, para suportar rotas multi-coleta/multi-entrega sem precisar redigitar endereço toda vez — spec `02-dados-mapas-rotas.md` (`collection_point`) e `08-decisoes-tecnicas-pendentes.md`, item 5.
- **Tela de veículo migrando de dialog para rota própria** `/frota/:id` (spec `08-decisoes-tecnicas-pendentes.md`, item 1).

Justificativa de priorizar isso antes de seguir a Fase 2 "pura": ambos fecham gaps de uso diário do produto já em operação, enquanto a Fase 2 (prontidão viária) é sobre construir a próxima camada de dado — vale fechar a experiência de quem já usa o produto todo dia antes de empilhar mais escopo novo.

## Novo foco principal — caminho feliz de rotas (em cima do "próximo pacote" acima)

Com o pacote anterior implementado, o item priorizado agora é o fluxo operacional completo de pontos de coleta/entrega/rotas funcionando sem atrito ponta a ponta (gestor monta rota → motorista executa → conclusão), incluindo telemetria de uso do próprio fluxo (não confundir com a telemetria de prontidão viária da Fase 2) — ver `11-caminho-feliz-rotas.md`. Justificativa: é o processo onde a maioria das empresas de frota erra na prática, e "ter o dado" (spec 02) só vale a pena se o fluxo em cima dele for confiável o bastante pro gestor preferir usar em vez de voltar pro WhatsApp/planilha.

Junto, dois itens menores, sem relação direta com rotas mas que entraram na mesma rodada de priorização:
- **Notificação operacional interna** (Discord/Telegram) avisando signup/confirmação de conta — `12-notificacoes-operacionais.md`. Uso interno, não é feature de produto pro cliente final.
- **Itens de manutenção/infra** registrados em `08-decisoes-tecnicas-pendentes.md` (itens 7-11): `npm audit`, code-splitting do bundle web, branch protection em `develop`, padrão de animação nas telas restantes, e-mail transacional/domínio. Nenhum bloqueia o foco principal — encaixar quando sobrar janela.

## Fase 2 — Dados de prontidão viária (score por trecho)

**Objetivo:** o subproduto de dado começa a existir de verdade.

Escopo:
- `geo-api`: pipeline completo de map matching (ping de GPS → `road_segment_observation`).
- Job de agregação para `road_readiness_score`.
- Import de extrato OpenStreetMap da região do piloto.
- Política de retenção/anonimização de dado bruto implementada (não só documentada).
- Roteamento básico via OSRM/GraphHopper exposto no `web`/`mobile` (rota sugerida simples, ainda sem pesar pelo score).

**Saída da fase:** dado real de trânsito sendo coletado e agregado a partir da frota-piloto; score existe e é consultável internamente (ainda não exposto como API pública paga).

### Expansões paralelas à Fase 2 (não bloqueiam a saída da fase, ver `06-parcerias-e-dados-futuros.md`)
- Avaliação manual de motorista (nota do gestor, sem componente automático ainda).
- Valor de mercado (FIPE) por veículo.
- Início da integração de pontos de recarga elétrica (localização, mesmo sem status em tempo real no começo).
- Roteamento multi-coleta/multi-entrega (VRP simples) para o segmento de empresas de entrega, usando OR-Tools sobre o motor de roteamento já existente.

## Fase 3 — Piloto "Vila Inteligente" (validação supervisionada)

**Objetivo:** validar o índice de prontidão viária com escrutínio, antes de vender para alguém de fora.

Escopo:
- Selecionar uma área piloto pequena e bem instrumentada.
- Roteamento passa a considerar `road_readiness_score` como peso (não só distância/tempo), incluindo no roteamento multi-coleta/multi-entrega.
- Primeira validação de vídeo/visão computacional como linha de pesquisa (calibração de câmera, teste de viés de domínio) — só aqui, não antes.
- Métricas de qualidade do score definidas e monitoradas (quantas observações por trecho, confiança do score).
- Avaliação automática de motorista (componente calculado a partir de dado de condução: frenagem, velocidade, desvio de rota).
- Condição do veículo (sinistros + score de saúde do ativo) com dado real acumulado desde a Fase 2.
- Status em tempo real dos pontos de recarga elétrica, se parceria de dado externo estiver disponível.

**Saída da fase:** dado suficientemente validado para ser mostrado a um potencial parceiro de AV com credibilidade técnica, não só como pitch.

## Fase 4 — Parceiro local de AV (operação com empresa internacional)

**Objetivo:** licenciamento real da API de prontidão viária.

Escopo:
- API pública versionada (`/public/v1/road-readiness/...`) com autenticação por chave, rate limiting e billing de licenciamento.
- SLA formal (disponibilidade, latência — ver RNFs no documento de entrega acadêmica).
- Contrato de dados com o parceiro (o que é compartilhado, anonimização, LGPD).

**Saída da fase:** primeiro cliente pagante da API de prontidão viária, contrato assinado, uso monitorado.

## Regra de transição entre fases

Não iniciar uma fase antes da Definition of Done da fase anterior (ver cada spec `01` a `04`) estar cumprida. Se o time achar necessário adiantar algo de uma fase futura, documentar a exceção e o motivo em `docs/` (ADR) em vez de simplesmente pular a ordem.
