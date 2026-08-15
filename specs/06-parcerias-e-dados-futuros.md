# 06 — Parcerias e Dados Futuros

Este spec cobre quatro expansões de produto identificadas depois do MVP inicial: recarga de veículos elétricos, avaliação de condição do veículo, avaliação privada de motoristas e receita por afiliados. Nenhuma delas é bloqueadora das Fases 1-2 (`05-roadmap-fases.md`), mas todas têm implicação de modelo de dados que vale desenhar cedo para não quebrar schema depois.

## 1. Pontos de recarga de veículos elétricos (EV charging)

**Por que interessa:** é o mesmo padrão de negócio da prontidão viária — agregamos dado de localização + status em tempo real de algo que hoje é fragmentado no Brasil, e isso pode virar tanto uma feature de retenção (motorista de frota elétrica planeja rota com recarga) quanto, no futuro, uma segunda fonte de dado licenciável.

**Recomendação de arquitetura:** não construir uma rede própria de sensores de carregador. Começar agregando dados de provedores existentes (ex. Open Charge Map como base aberta, e APIs de redes de recarga parceiras quando disponíveis via parceria comercial). O papel da AutonomousAPI aqui é **normalizar e cruzar** esse dado com rota/frota — não ser dona da infraestrutura física.

Modelo de dados (schema `geo`):
- `charging_station`: localização, provedor de origem, tipo de conector, potência.
- `charging_station_status`: status em tempo real (disponível/ocupado/fora de serviço), timestamp da última atualização, fonte (provedor externo vs. reportado por usuário da plataforma).
- Fallback: se o provedor externo não expõe status em tempo real, permitir status "reportado pelo motorista" como sinal complementar (mais barato que construir sensor próprio, e já gera engajamento no app).

Requisito de resiliência: se o provedor externo cair, o mapa deve mostrar "status indesconhecido" em vez de quebrar a tela (RNF011 no documento de entrega).

**Fase recomendada:** depois da Fase 2 (a arquitetura de mapas/geo já precisa existir). Tratar como feature paralela, não pré-requisito de nenhuma fase anterior.

## 2. Avaliação de condição do veículo (FIPE + saúde do ativo)

**Por que interessa:** transforma "gestão de frota" de painel operacional em ferramenta de decisão patrimonial — e cria um segundo ativo de dado interessante para seguradoras no médio prazo (ver RF020/HU20 sobre exportação para seguradoras).

Modelo de dados (schema `core`):
- `vehicle_market_value`: veículo, valor FIPE, data de referência, código FIPE usado na consulta.
- `vehicle_incident` (sinistro/batida): veículo, data, severidade, descrição, custo de reparo (se houver).
- `vehicle_condition_score`: score calculado, versão do algoritmo, timestamp — nunca calculado on-the-fly na tela, sempre um valor agregado recalculado por job (mesmo padrão do `road_readiness_score` em `02-dados-mapas-rotas.md`).

Fatores de entrada do score (a calibrar com o time, não travar a fórmula agora): frequência de manutenção em dia vs. atrasada, quilometragem acumulada, quantidade/severidade de sinistros, idade do veículo. Tratar a fórmula inicial como heurística simples e versionada — vai mudar conforme dado real aparecer.

**Integração FIPE:** usar API pública/terceirizada de consulta FIPE (não manter tabela própria) — atualização periódica (ex. mensal), cacheada no schema `core`, nunca chamada em tempo real na tela do usuário.

**Fase recomendada:** Fase 2-3, depende só do `core-api` já ter CRUD de veículo maduro (Fase 1).

## 3. Avaliação de motoristas — visível apenas ao contratante

**Por que interessa:** ferramenta de gestão de equipe para o gestor de frota, não um sistema de reputação pública estilo app de transporte de passageiros. Essa distinção é o ponto mais importante deste item.

**Regra de acesso (não negociável):** `driver_rating` nunca é exposto a passageiros, terceiros, nem ao próprio motorista por padrão — só ao gestor/tenant que contratou aquele motorista. Isso deve ser reforçado a nível de autorização no `core-api` (não é uma opção de UI escondida, é uma regra de acesso no backend).

**Cuidado de compliance (LGPD):** avaliação de desempenho de trabalhador com componente automatizado (ver item abaixo) é sensível. Recomendações:
- O motorista deve ser informado de que existe avaliação de desempenho (transparência), mesmo sem ver a nota — isso deve constar no consentimento de onboarding (`03-mobile-e-assinaturas.md`, HU18).
- Definir e documentar por quanto tempo a nota é retida e se há direito de contestação — tratar isso como decisão de RH/jurídico do cliente (tenant), não só de engenharia.

Modelo de dados (schema `core`):
- `driver_rating_manual`: motorista, gestor avaliador, nota, comentário, timestamp.
- `driver_rating_auto`: motorista, componente (frenagem brusca, excesso de velocidade, desvio de rota — cruza com dado do schema `geo`), score do componente, período de referência.
- `driver_rating_summary`: agregação dos dois anteriores em uma nota única exibida ao gestor — outro caso de "nunca calcular em tempo real", sempre job de agregação.

**Fase recomendada:** a parte manual (avaliação simples pelo gestor) pode entrar já na Fase 1-2. A parte automática (cruzando com dado de condução) depende do pipeline de GPS da Fase 2 estar maduro.

## 4. Receita por afiliados (equipamentos, ex. dashcam)

**Por que interessa:** fonte de receita adicional de baixo esforço de engenharia, aproveita a base de gestores de frota já dentro do produto no momento em que eles mais precisam de equipamento (setup de novo veículo).

Modelo de dados (schema `core`):
- `affiliate_partner`: nome, categoria (dashcam, rastreador, etc.), link base.
- `affiliate_click`: usuário, partner, timestamp, veículo de contexto (se aplicável) — para métricas de conversão.
- Se o parceiro fornecer webhook/API de conversão confirmada (compra efetivada), registrar em `affiliate_conversion` separado — não assumir que clique = venda.

**Fase recomendada:** baixa prioridade de engenharia, pode entrar a qualquer momento a partir da Fase 1 (é essencialmente um link rastreado + uma tela), mas só vale priorizar depois que a base de gestores ativos justificar o esforço de negociar parcerias.

**Status atual (registrado):** a estrutura já está pronta para múltiplos parceiros, mas hoje só existe 1 parceiro de exemplo cadastrado (rastreador, não dashcam ainda). Não existe UI de admin para cadastro de parceiro — é feito direto no banco, decisão consciente porque a negociação comercial de cada parceria é externa e manual, e não compensa construir uma UI antes de ter volume de parceiros. Ampliar o catálogo é inserção de linha nova, sem mudança de código (ver `08-decisoes-tecnicas-pendentes.md`, item 3).

## Resumo de priorização sugerida

| Item | Complexidade técnica | Dependência | Prioridade sugerida |
|---|---|---|---|
| Avaliação manual de motorista | Baixa | `core-api` de auth/perfis | Pode entrar já na Fase 1-2 |
| Afiliados | Baixa | Nenhuma (só precisa de usuários ativos) | Oportunista, qualquer fase |
| FIPE / valor de mercado | Baixa-Média | CRUD de veículo | Fase 2 |
| Sinistro / condição do veículo | Média | CRUD de veículo + job de agregação | Fase 2-3 |
| Avaliação automática de motorista | Média-Alta | Pipeline de GPS maduro (Fase 2) | Fase 3 |
| Pontos de recarga elétrica | Média | Arquitetura `geo` madura + parceria externa de dado | Fase 2-3, paralelo |

## Definition of Done (parcerias e dados futuros)

- [x] Modelo de dados de cada item acima criado como migration (mesmo que a feature não esteja com UI completa ainda).
      (migration V10 — exceto recarga elétrica, ver nota abaixo; `driver_rating_auto` também
      fica de fora, depende do pipeline de GPS maduro, spec já marca isso como Fase 3)
- [x] Regra de acesso de `driver_rating` implementada (todas as rotas de
      `DriverRatingController`, inclusive leitura, são `GESTOR_FROTA`/`ADMIN` via
      `@PreAuthorize` de classe — não existe rota que um MOTORISTA autenticado consiga
      chamar). **Não testado com um usuário MOTORISTA real**: não há hoje nenhum fluxo que
      crie login com essa role (signup só cria GESTOR_FROTA — spec 03 trata isso como
      feature futura de convite), então o caso "motorista tenta ler a própria nota" ainda
      não tem como ser exercitado ponta a ponta. Verificado apenas estruturalmente
      (`@EnableMethodSecurity` ativo, mesmo mecanismo já em uso no resto do app).
- [ ] Fallback gracioso implementado para provedor externo de recarga fora do ar.
      (recarga elétrica não entrou nesta rodada — sem provedor de dado real identificado
      ainda, ver spec 06 item 1)
- [ ] Consentimento de avaliação de desempenho incluído no fluxo de onboarding do motorista.
      (depende do app mobile ter uma tela de onboarding de motorista com login próprio —
      ainda não existe, mesma dependência do item de acesso acima)

### Nota sobre FIPE (item 2)

O spec pede "usar API pública/terceirizada de consulta FIPE, cacheada". `vehicle_market_value`
já é essa tabela de cache — o que falta é a consulta automática em si: as APIs públicas de
FIPE (ex. parallelum) exigem resolver marca/modelo do nosso cadastro para o código de
catálogo delas, que é um problema de matching à parte, não coberto nesta rodada. Por ora o
gestor lança o valor manualmente via `POST /v1/vehicles/{id}/market-value`.
