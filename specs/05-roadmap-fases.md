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
