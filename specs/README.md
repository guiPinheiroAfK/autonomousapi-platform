# AutonomousAPI — Spec-Driven Development

Este diretório é a fonte de verdade do produto para quem for implementar (humano ou agente).
Antes de escrever código, leia os arquivos nesta ordem:

| # | Arquivo | Conteúdo |
|---|---|---|
| 00 | `00-visao-geral.md` | Problema, proposta de valor, princípios de produto, o que NÃO fazer ainda |
| 01 | `01-arquitetura.md` | Componentes do sistema, stack, fronteiras de serviço, contratos |
| 02 | `02-dados-mapas-rotas.md` | Modelo de dados, pipeline de coleta de GPS/vídeo, índice de prontidão viária, estratégia de mapas/rotas |
| 03 | `03-mobile-e-assinaturas.md` | App Android/iOS, modelo de assinatura, billing, políticas de loja |
| 04 | `04-repositorio-e-git-workflow.md` | Estrutura de repositório, branches, commits, CI/CD, code owners |
| 05 | `05-roadmap-fases.md` | Fases de entrega (a "escada de crescimento"), critério de saída de cada fase |
| 06 | `06-parcerias-e-dados-futuros.md` | Recarga elétrica, condição do veículo (FIPE + sinistro), avaliação privada de motorista, receita por afiliados |
| 07 | `07-app-motorista.md` | Front + back do app do motorista: veículo atual, CNH, OS read-only, ocorrência, notificações, histórico de viagens, mini-chat gestor↔motorista |
| 08 | `08-decisoes-tecnicas-pendentes.md` | Mini-ADRs: tela de veículo (dialog→rota), fonte de dado de manutenção por modelo, status do catálogo de afiliados, ícone por tipo de veículo |

## Como usar isso com o Claude Code

1. Abra o repositório vazio (ou recém-criado, ver `04-repositorio-e-git-workflow.md`).
2. Copie esta pasta `specs/` inteira para a raiz do repo.
3. Peça para o Claude Code ler `specs/00-visao-geral.md` até `specs/05-roadmap-fases.md`, nessa ordem, antes de gerar qualquer código.
4. Trabalhe **uma fase por vez** (ver `05-roadmap-fases.md`) — não peça para implementar tudo de uma vez.
5. Cada spec tem uma seção "Definition of Done" — use isso como checklist de PR.

## Princípio geral

Fundamentos primeiro, features depois. Como o produto vai virar app de loja + assinatura + fonte de dado geoespacial licenciado, decisões de auth, schema de dados, billing e estrutura de repo tomadas errado na Fase 1 são caras de desfazer na Fase 3. Por isso este spec é mais rígido em arquitetura e dados do que em UI.
