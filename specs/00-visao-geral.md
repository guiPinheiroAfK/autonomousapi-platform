# 00 — Visão Geral

## Problema

Nenhuma empresa de mobilidade autônoma tem dado real sobre o trânsito brasileiro (moto entre carros, sinalização inconsistente, comportamento imprevisível). Sistemas de dispatch e mapas genéricos foram feitos para outro tipo de trânsito. Quem entender esse terreno primeiro vira o parceiro natural de quem chegar — hoje, zero parceiros locais estão prontos.

## Proposta de valor

1. **Gestão de frota completa** — ativos, manutenção, custo por km. Vendável desde o dia 1, resolve dor real de quem opera frota hoje (entrega, locação, transporte).
2. **Dado real de trânsito como subproduto** — cada frota conectada gera GPS e, quando possível, vídeo de rua durante a operação normal, sem custo de coleta dedicada.
3. **Base para mobilidade autônoma** — o dado vira índice de prontidão viária, licenciado via API para empresas de AV (Waymo, WeRide, Baidu e afins) avaliando o Brasil.

## Para quem (nessa ordem de prioridade)

1. Gestor de frota (comprador e usuário direto, hoje).
2. Empresas de entrega/locação/transporte (cliente pagante, hoje).
3. Empresas de AV avaliando o Brasil (cliente de longo prazo, via API).

## Princípios de produto

- **Cada degrau é vendável sozinho.** Gestão de frota não é "isca" para a API — é um produto SaaS completo por si só. A API de prontidão viária é upsell de longo prazo, não a única fonte de receita no curto/médio prazo.
- **O dado é subproduto da operação, não o objetivo da operação.** Nunca peça ao motorista/gestor para fazer trabalho extra só para "gerar dado" — se a UX de gestão de frota for ruim, o produto morre antes do dado importar.
- **Fundamentos antes de escala.** Antes de "mais features", resolver: autenticação multi-perfil, modelo de dados geoespacial correto (PostGIS desde o início, não depois), estrutura de billing/assinatura compatível com loja de app, e uma arquitetura de repositório que aguente 3+ apps (web, mobile, 2 serviços de backend) sem virar bagunça.
- **Roteamento é NP-difícil (TSP/VRP).** Nunca implementar força bruta; sempre heurística/aproximação (ver `01-arquitetura.md`).
- **Modelos pré-treinados de visão computacional têm viés de domínio** (não viram trânsito misto brasileiro) — qualquer feature de visão computacional deve ser tratada como pesquisa/validação, não como dependência de MVP.

## O que NÃO fazer ainda (fora de escopo até Fase 3+)

- Não construir motor de roteamento próprio do zero (usar OSRM/GraphHopper open source por cima do OSM).
- Não prometer índice de prontidão viária "em produção" para clientes de AV antes de ter dado real validado de pelo menos um piloto supervisionado.
- Não implementar reconhecimento de vídeo/calibração de câmera como dependência do MVP — é problema teórico reconhecido (ver pitch, slide 13), não bloqueador de lançamento.
- Não desenhar billing só para web — o app mobile tem regras de loja diferentes (ver `03-mobile-e-assinaturas.md`) e isso precisa estar decidido antes de começar a cobrar.

## Stack de alto nível (detalhado em `01-arquitetura.md`)

- **Frontend web:** React
- **Mobile:** app nativo/híbrido para Android (Play Store) e iOS (App Store)
- **Backend de orquestração:** Java / Spring Boot (auth, regras de negócio, billing)
- **Backend geoespacial:** Python / FastAPI (GPS, rotas, prontidão viária)
- **Banco:** PostgreSQL + PostGIS (Neon)
- **Dados abertos de apoio:** OpenStreetMap, Mapillary
