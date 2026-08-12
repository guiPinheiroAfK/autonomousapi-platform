# 02 — Dados, Mapas e Rotas

Este é o núcleo estratégico do produto: mapas melhores, para uso futuro em rotas e prontidão viária, construídos a partir do dado que a própria operação de frota gera. Pensar nisso desde já evita reescrever o pipeline inteiro quando o produto crescer.

## Camadas de dado

1. **Dado de base (open data)** — OpenStreetMap (malha viária) + Mapillary (imagens de rua já existentes). Ponto de partida, cobertura conhecida, sem custo de coleta.
2. **Dado próprio de operação (o diferencial)** — GPS e, quando disponível, vídeo captados pelos veículos da frota-piloto durante a operação normal. É o dado que nenhum concorrente genérico tem.
3. **Dado derivado (índice de prontidão viária)** — processamento dos dois anteriores em um score por trecho de via.

## Modelo de dados — entidades centrais (schema `geo`)

- `vehicle_gps_ping`: veículo (referência por ID), timestamp, lat/lon, velocidade, heading, precisão do sinal.
- `trip`: agregação de pings em uma viagem — veículo, motorista (referência por ID), início, fim, distância, custo calculado.
- `road_segment`: trecho de via, geometria (PostGIS `LINESTRING`), origem OpenStreetMap (`osm_way_id`).
- `road_segment_observation`: uma passagem observada por um veículo sobre um `road_segment` — velocidade média, eventos (frenagem brusca, se disponível via sensor), timestamp.
- `road_readiness_score`: agregado por `road_segment` — score, quantidade de observações, última atualização, versão do algoritmo que gerou o score.
- `street_media` (opcional, Fase 2+): referência a vídeo/imagem capturado, vinculado a um trecho e timestamp — armazenamento em object storage (não no banco), banco guarda só metadado e URL.

Regra: **nunca calcular `road_readiness_score` em tempo real na requisição.** É um job assíncrono (batch ou stream) que atualiza `road_segment_observation` → agrega em `road_readiness_score`. A API pública sempre lê o valor já agregado.

## Pipeline de coleta (map matching)

1. App mobile do motorista envia pings de GPS em intervalo configurável (bateria vs. precisão é trade-off a decidir na Fase 1 — começar com intervalo conservador, ex. 10-15s, e ping adicional em evento de mudança de direção).
2. `geo-api` recebe o ping, faz *map matching* (associa lat/lon ao `road_segment` mais próximo, usando lib madura — ex. `osrm-match` ou equivalente — não escrever isso do zero).
3. Ping vira `road_segment_observation`.
4. Job periódico recalcula `road_readiness_score` para trechos com observações novas.

## Estratégia de roteamento (rotas)

- Fase 1-2: usar OSRM ou GraphHopper (open source) rodando sobre extrato do OpenStreetMap do Brasil, sem lógica própria de otimização.
- Fase 3+: usar `road_readiness_score` como peso adicional no grafo de roteamento (evitar/preferir trechos conforme o score), não como motor de roteamento inteiro — continuar usando o motor open source como base.
- VRP (otimização de múltiplas rotas/veículos, útil para "gestão de frota") é problema separado de roteamento ponto-a-ponto — tratar como feature de Fase 2, usando heurística (ex. algoritmos tipo Clarke-Wright ou biblioteca como Google OR-Tools), nunca busca exaustiva.

## Roteamento com múltiplos pontos (coleta e entrega)

Além do roteamento ponto-a-ponto (rota de A para B), o produto precisa suportar o caso de logística real de frota: **N pontos de coleta e 1 ponto de entrega**, ou o inverso (1 coleta e N entregas). Isso é o caso de uso natural das "empresas de entrega" já identificadas como cliente no pitch (slide 5) — vale a pena tratar como diferencial de venda para esse segmento, não só como feature técnica.

Isso é uma variação do **VRP (Vehicle Routing Problem)** restrita a um único veículo por rota (o caso mais simples do VRP, às vezes chamado de "TSP com múltiplos pontos e destino fixo"). Continua sendo NP-difícil — a mesma regra do restante deste documento se aplica: **nunca força bruta, sempre heurística de biblioteca madura.**

Abordagem recomendada:
1. Modelar a entrada como uma lista de paradas com tipo (`coleta` ou `entrega`) e, quando existir, janela de horário (ex. "coletar entre 8h-10h").
2. Usar um solver de roteamento existente (ex. Google OR-Tools, que tem suporte nativo a VRP com múltiplas paradas e janelas de tempo) por cima da matriz de distância/tempo calculada pelo motor de roteamento (OSRM/GraphHopper) — não implementar o solver do zero.
3. Resultado: sequência ordenada de paradas + rota entre elas, devolvida ao app do motorista já na ordem otimizada (o motorista não decide a sequência manualmente).
4. Fase 3+: usar `road_readiness_score` também como peso nesse cálculo, do mesmo jeito que no roteamento simples.

Modelo de dados adicional (schema `geo`):
- `route_plan`: gestor/tenant que criou, veículo/motorista designado, status (planejada, em andamento, concluída).
- `route_stop`: pertence a um `route_plan`, tipo (coleta/entrega), localização, janela de horário (opcional), ordem sugerida pelo solver, ordem real executada (para depois comparar planejado vs. realizado).

Isso é uma extensão da Fase 2 (roteamento básico), não da Fase 1 — depende do motor de roteamento já estar funcionando ponto-a-ponto antes de generalizar para múltiplas paradas.

## Vídeo e visão computacional

- Tratado como **linha de pesquisa paralela**, não dependência do MVP nem da Fase 2.
- Dois problemas teóricos reconhecidos desde o pitch: calibração de câmera (pixel → distância real) e viés de domínio de modelos pré-treinados no trânsito misto brasileiro.
- Antes de qualquer investimento de engenharia em visão computacional, validar com um piloto pequeno e supervisionado (poucos veículos, poucas horas de vídeo) se o dado é sequer utilizável — não assumir que "vai funcionar" só porque funciona em datasets internacionais.

## Privacidade e LGPD (aplica-se a este módulo inteiro)

- Dado de GPS e vídeo é dado pessoal/sensível quando vinculável a um motorista identificável — anonimizar ou pseudonimizar antes de qualquer uso agregado (`road_readiness_score` não deve carregar referência a motorista/veículo específico).
- Definir e documentar tempo de retenção de `vehicle_gps_ping` bruto (não guardar indefinidamente — agregar e descartar o dado bruto após uma janela definida).
- Consentimento do motorista para coleta deve estar no fluxo de onboarding do app (ver `03-mobile-e-assinaturas.md`), não é opcional nem letra miúda.

## Definition of Done (dados/mapas, Fase 1-2)

- [x] Schema `geo` com as entidades acima, migrations versionadas. (`road_segment`,
      `road_segment_observation`, `road_readiness_score` — migration 0002; `route_plan`/
      `route_stop` ficam para quando o roteamento multi-parada entrar, ver item abaixo)
- [x] Import de um extrato OSM (ex. região do piloto) rodando localmente.
      (`scripts/import_osm_pilot.py`, via Overpass API — bbox de exemplo: Av. Paulista/SP)
- [x] Pipeline de ingestão de GPS → `road_segment_observation` funcionando ponta a ponta com dado de pelo menos 1 veículo real ou simulado.
      (verificado com dado real importado — Rua Pamplona/SP)
- [x] Job de agregação para `road_readiness_score` rodando (mesmo que score seja simples, ex. contagem de observações).
      (`app/aggregation.py`, v1 = contagem normalizada, agendado via `app/scheduler.py`)
- [x] Política de retenção/anonimização documentada e implementada, não só planejada.
      (ADR 0009; `app/retention.py`, roda 1x/dia)
- [ ] Roteamento básico via OSRM/GraphHopper — **não feito nesta rodada**, deliberadamente:
      é infraestrutura própria (servidor OSRM + grafo de roteamento processado), separada
      do que este DoD original já cobria; abrir como item dedicado quando for a vez.
- [ ] (Extensão Fase 2+) Roteamento multi-coleta/multi-entrega funcionando com solver existente (ex. OR-Tools), retornando sequência otimizada de paradas.
      (depende do item anterior)
