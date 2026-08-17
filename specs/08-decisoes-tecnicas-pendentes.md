# 08 — Decisões Técnicas Pendentes (Front Web + Dados de Veículo)

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

## Definition of Done

- [x] Rota `/frota/:id` no ar substituindo o dialog, com breadcrumb, botão voltar e acesso direto por link funcionando (PR #44).
- [x] Decisão de dado de manutenção (item 2) também registrada como ADR no repositório real — [`docs/adr/0017-manutencao-por-modelo-manual.md`](../docs/adr/0017-manutencao-por-modelo-manual.md).
- [x] Ícone por tipo de veículo implementado (PR #49).
- [x] Aba "Pontos de Coleta" no ar, com CRUD básico e geocodificação de endereço.
- [x] Chat revisado visualmente para o mesmo padrão do dashboard.
