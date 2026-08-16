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

Cosmético, sem dependência de API — usa o campo `tipo` que o veículo já tem (carro, moto, van, etc.) para trocar o ícone/SVG exibido na lista e no detalhe. Baixo esforço, fica de backlog para quando fizer sentido priorizar — não bloqueia nada.

## Definition of Done

- [x] Rota `/frota/:id` no ar substituindo o dialog, com breadcrumb, botão voltar e acesso direto por link funcionando (PR #44).
- [x] Decisão de dado de manutenção (item 2) também registrada como ADR no repositório real — [`docs/adr/0017-manutencao-por-modelo-manual.md`](../docs/adr/0017-manutencao-por-modelo-manual.md).
- [x] Ícone por tipo de veículo implementado (PR #49).
