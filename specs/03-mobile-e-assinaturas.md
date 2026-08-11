# 03 — Mobile e Assinaturas

## Apps

Um único app mobile (React Native + Expo), com dois modos/perfis dentro do mesmo binário: **motorista** (registro de viagem, alertas) e **gestor** (visão reduzida da gestão de frota em campo). Evita manter dois apps nas lojas e duas pipelines de release.

- Publicação: Google Play Store e Apple App Store.
- GPS em background é feature crítica (coleta de trajeto do motorista) — nas duas plataformas isso exige declaração explícita de uso de localização em background e justificativa na revisão da loja. Preparar a descrição de uso desde a Fase 1, não deixar para a submissão.
- Consentimento de coleta de localização deve ser tela própria no onboarding, linguagem clara, não escondida em termos de uso.

## Modelo de assinatura

Do pitch: hoje é SaaS por veículo ativo na plataforma de gestão de frota. Isso precisa de uma decisão de billing que funcione simultaneamente em web e nas duas lojas de app — **essa decisão precisa ser tomada antes de codar billing**, porque muda a arquitetura.

### O problema a resolver antes de codar

Apple e Google exigem uso do sistema de compra interna (IAP) deles para desbloquear conteúdo/funcionalidade **dentro do app**, com comissão de até 30% (15% em alguns programas de pequeno porte). Isso é desenhado para apps de conteúdo digital (jogos, streaming) — para SaaS B2B "físico" (gestão de frota real, custo por km real) a linha é cinzenta e as políticas mudam com frequência.

**Recomendação para não travar o produto nisso:**

1. **Cobrança principal via web (Stripe ou similar), fora do app.** O app mobile é ferramenta operacional (registro de rota, alertas) para quem já é assinante — a gestão de assinatura/pagamento acontece no painel web, como é comum em SaaS B2B ("reader apps" / apps corporativos).
2. **Usar um agregador (ex. RevenueCat) desde o início** se decidir vender assinatura dentro do app também — ele abstrai IAP da Apple, Play Billing e Stripe atrás de uma única API, evitando reescrever billing 3 vezes.
3. **Antes de submeter à App Store/Play Store, validar a política vigente** (as regras mudam) — não assumir que a estratégia acima está 100% aprovada sem checar a documentação oficial da Apple/Google no momento da submissão.

Esta é uma decisão de produto/negócio com implicação técnica direta — sinalizar para o time antes de implementar o módulo de billing no `core-api`.

## Modelo de dados de assinatura (schema `core`)

- `tenant` (empresa cliente) → `subscription` (plano, status, ciclo, veículos inclusos) → `subscription_item` (por veículo ativo, se cobrança for por veículo).
- `subscription.billing_source`: enum (`web_stripe`, `ios_iap`, `android_iap`) — mesmo que só um canal exista no início, modelar para múltiplos desde já.
- Nunca liberar feature no app checando estado local — sempre validar `subscription.status` via `core-api` (evita bypass e mantém uma única fonte de verdade).

## Offline

- App deve funcionar offline para registro de viagem (fila local de pings de GPS), sincronizando ao reconectar — motorista frequentemente está em área de sinal ruim.
- Alertas de manutenção/vencimento devem ser cacheados localmente para exibição, mas nunca a fonte de verdade (sempre revalidar ao conectar).

## Definition of Done (mobile/assinaturas, Fase 1-2)

- [ ] Decisão de estratégia de billing (web-first vs. IAP) documentada e aprovada pelo time antes de iniciar o módulo de billing.
- [ ] App publicável em build interno (TestFlight / Play Internal Testing) rodando login + registro de viagem offline-first.
- [ ] Modelo de `tenant`/`subscription` implementado no `core-api`, com pelo menos um canal de billing funcional.
- [ ] Tela de consentimento de localização no onboarding, revisada quanto à linguagem (clara, não escondida).
