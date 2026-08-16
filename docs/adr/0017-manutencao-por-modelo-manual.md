# ADR 0017 — Dado de manutenção por modelo fica manual

**Status:** aceito
**Data:** 2026-08-16

## Contexto

"Próx. preventiva" e "Preventiva por km" (`vehicle.proxima_manutencao_data`,
`vehicle.proxima_manutencao_km`) são preenchidos manualmente pelo gestor no cadastro do
veículo. A pergunta recorrente é se dá pra automatizar isso a partir de marca/modelo —
ex. inferir automaticamente o intervalo de troca de correia dentada do Fiat Strada 2023.

## Decisão

**Manter manual.** Não existe hoje API pública/gratuita confiável no Brasil com esse dado
por modelo. As opções reais avaliadas:

- **(a) Provedor pago de dados técnicos por modelo** (do tipo usado por
  concessionárias/oficinas) — custo recorrente, alta precisão.
- **(b) Tabela curada internamente por categoria de veículo** (ex. "moto: revisão a cada
  3.000km", "van: a cada 10.000km") — mais barata, bem menos precisa (ignora
  variação por modelo/motorização dentro da mesma categoria).

Nenhuma das duas se paga no volume atual de frotas. A FIPE (spec 06, item 2) resolve valor
de mercado, não intervalo de manutenção — são fontes de dado diferentes, não dá pra reusar
a mesma integração.

Isso **não é um gap esquecido**, é escolha consciente até existir sinal real de que o
produto precisa de mais automação.

## Reavaliar quando

- A base de clientes crescer o suficiente para justificar o custo recorrente da opção (a).
- O time tiver uma janela para curar a tabela por categoria da opção (b) como projeto à
  parte, com confiança de que a perda de precisão por modelo é aceitável.

Nenhuma das duas é bloqueadora de nada hoje.
