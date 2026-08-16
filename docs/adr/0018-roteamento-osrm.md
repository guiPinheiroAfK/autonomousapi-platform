# ADR 0018 — Roteamento com OSRM sobre extrato da área do piloto

**Status:** aceito
**Data:** 2026-08-16
**Spec:** `specs/02-dados-mapas-rotas.md` (Estratégia de roteamento), `specs/05-roadmap-fases.md` (Fase 2)

## Contexto

O spec 02 fecha a Fase 2 com "roteamento básico via OSRM ou GraphHopper, sem lógica
própria de otimização". O item ficou aberto por ser o único da fase que exige
infraestrutura nova: um motor de roteamento e um grafo pré-processado.

## Decisões

### OSRM, não GraphHopper

Empate técnico para o que precisamos agora (os dois roteiam sobre OSM, os dois são open
source maduros). O desempate é o passo seguinte do próprio spec: a Fase 3 pede
`road_readiness_score` como **peso adicional no grafo**. O OSRM, no modo MLD, separa
"extrair o grafo" de "customizar os pesos" — `osrm-customize` recalcula custo de aresta
sem reextrair nada. É exatamente a operação que a Fase 3 vai repetir a cada agregação de
score.

### MLD, não CH

Contraction Hierarchies responde consulta mais rápido, mas qualquer mudança de peso
obriga a recontrair o grafo inteiro. Isso mataria a Fase 3. MLD custa alguns
milissegundos a mais por consulta e permite recustomização barata — troca óbvia no nosso
caso, e irrelevante no volume atual.

### Extrato via Overpass (OSM XML), não .pbf da Geofabrik

O `.pbf` de São Paulo passa de meio giga e leva dezenas de minutos de pré-processamento.
Impor isso a quem clonou o repositório para ver o painel funcionando é custo alto demais
para o benefício. A Overpass devolve OSM XML válido — que o `osrm-extract` lê nativamente
— e a área do piloto sai em ~6 MB e ~30s de pipeline inteiro.

Consequência aceita: a área é pequena por construção. Trocar por um `.pbf` regional
quando o piloto real for definido é mudança de um script, não de arquitetura.

### `PILOTO_BBOX` é fonte única, compartilhada com o map matching

`app/pilot_area.py` é importado tanto pelo import de `road_segment` quanto pelo preparo
do grafo. Divergir as duas áreas seria um bug silencioso: o produto devolveria rotas por
trechos sobre os quais não existe observação nenhuma, e o peso por score da Fase 3 não
teria dado ali.

### Raio máximo de snap de 1 km

Sem limite, o OSRM "gruda" qualquer coordenada na via mais próxima do grafo e responde
`code: Ok`. Na prática: pedir rota para Londres devolvia, com sucesso aparente, uma rota
de 7 km terminando numa rua do Brás — resposta silenciosamente errada, que é pior que um
erro. Com `radiuses=1000`, o OSRM responde `NoSegment` e a tela diz que o ponto está fora
da área coberta.

Cuidado relacionado: o OSRM devolve **HTTP 400** para `NoSegment`/`NoRoute`. Tratar 4xx
como exceção faria o usuário ler "motor indisponível" quando o motor está perfeito e o
problema é o ponto que ele escolheu. O cliente lê o campo `code` do corpo em vez de
confiar no status.

### Serviço atrás do profile `routing`, com degradação explícita

`docker compose up` não sobe o OSRM: sem grafo preparado o processo morre em loop, e
preparar o grafo é passo manual que exige rede. Com o motor fora do ar, o geo-api responde
`available: false` com motivo legível — mesmo padrão já usado para Stripe, SMTP, push e
provedor de recarga.

### Geocodificação via Nominatim

Não é pedida por nome no spec, mas "roteamento exposto no web/mobile" não se sustenta se a
única entrada for lat/lon. Nominatim é o geocoder do próprio OSM — endereço encontrado
nele é endereço que o grafo sabe rotear. Busca limitada à bbox do piloto (`bounded=1`), o
que também elimina a classe de erro "digitei endereço de outro estado".

Sem chave, mas com política de uso a respeitar (volume baixo, User-Agent identificável).
A URL é configurável para trocar por instância própria quando o volume justificar.

## Consequências

- Fecha o último item aberto do DoD de dados/mapas da Fase 2.
- A Fase 3 (peso por `road_readiness_score`) entra via `osrm-customize`, sem trocar de
  motor nem reimplementar roteamento — que é o que o spec 02 determina.
- O roteamento multi-parada (`route_plan`/`route_stop` + OR-Tools) continua aberto, e agora
  destravado: ele depende da matriz de distância que este motor passa a fornecer.

## Reavaliar quando

- A área do piloto real for definida e for grande demais para a Overpass — aí entra `.pbf`
  regional e o preparo do grafo vira etapa de build/deploy, não script de dev.
- O volume de geocodificação passar do que a instância pública do Nominatim comporta.
