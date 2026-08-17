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

**Estado real (`RoutePlanService.suggestOrder`):** implementado com matriz de distância real via OSRM `/table` (`geo-api POST /internal/v1/table`) + solver VRP (Google OR-Tools, `OrToolsRouteOptimizer`), agrupando coleta antes de entrega — sempre revisada pelo gestor antes de confirmar (nunca aplicada sem revisão humana). Nearest-neighbor por haversine (linha reta) da v1 continua no código só como fallback de dois níveis: se o `/table` cair ou devolver um par sem ligação viária (`RouteMatrixService`, com log de telemetria, nunca silencioso) e, no limite, se o próprio OR-Tools não achar solução.

Modelo de dados adicional (schema `core`, não `geo` — são entidades operacionais/de tenant, não dado geoespacial agregado):
- `collection_point`: cadastro reutilizável de ponto de coleta/entrega — nome, endereço, lat/lon, tenant, ativo (bool), **janela de horário padrão** (`janela_inicio`/`janela_fim`, opcional), `posicao_ajustada` (bool — true quando o gestor corrige a posição depois da geocodificação automática). Existe porque, na prática, a mesma empresa cliente costuma coletar/entregar sempre nos mesmos endereços (depósito, filial, cliente recorrente) — sem esse cadastro, o gestor teria que digitar o mesmo endereço toda rota nova.
- `route_plan`: gestor/tenant que criou, veículo/motorista designado, `data_execucao` (obrigatória, validada contra data passada), status (`PLANEJADA`/`EM_ANDAMENTO`/`CONCLUIDA`, derivado automaticamente do estado das paradas — nunca escrito manualmente), `categoria` (`ROTA` | `TRANSFER` — ver seção própria abaixo), `valor` (opcional).
- `route_stop`: pertence a um `route_plan`, tipo (coleta/entrega), referência a um `collection_point` (quando o ponto é cadastrado, e nesse caso o servidor resolve label/lat/lon do cadastro, nunca confia no que o cliente mandou) **ou** um endereço avulso, janela de horário própria (pré-preenchida a partir do `collection_point` quando ele tiver janela padrão, sempre editável nessa instância), ordem sugerida, ordem real executada.

Implementado: tela "Pontos de Coleta" no web (`CollectionPointsPage.tsx`) — CRUD simples, geocodificação via Nominatim.

### Travas de validação (implementadas em `RoutePlanService.create`, não só no front)

- [x] `route_plan` não pode ser criado com `data_execucao` no passado.
- [x] `janela_fim` de uma parada não pode ser `<=` `janela_inicio`, quando as duas estiverem preenchidas.
- [ ] (Melhoria opcional, não obrigatória) Alertar sem bloquear se `data_execucao` for hoje e `janela_fim` de alguma parada já tiver passado — não implementado.

### Rota como "transfer" — categoria com valor embutido

`route_plan.categoria`: `ROTA` (padrão, multi-parada) ou `TRANSFER` (exatamente 2 paradas — origem/COLETA e destino/ENTREGA — validado no backend). `route_plan.valor` é opcional, editável pelo gestor. Do lado do motorista, `TRANSFER` renderiza como cartão único (origem → destino, valor, botão "iniciar"/"concluir") em vez da lista numerada de paradas — implementado em `DriverHomePage.tsx` e `DriverRoutePage.tsx`.

### Distância real (OSRM `/table`) + OR-Tools — implementado

Os dois passos que a spec pedia juntos (não implementar um "solver" do zero, e não usar distância em linha reta) estão no ar:

1. **Matriz de distância/tempo real via OSRM `/table`** — `geo-api` expõe `POST /internal/v1/table` (mesmo padrão de degradação do `/route`: sempre 200, `available=false` com motivo legível quando o motor está fora do ar ou algum par de pontos não tem ligação viária), consumida por `GeoApiClient.distanceMatrix`.
2. **OR-Tools (VRP)** sobre essa matriz — `OrToolsRouteOptimizer` resolve um TSP de caminho aberto (nó fantasma de custo zero como truque para não forçar volta ao ponto de partida), substituindo o nearest-neighbor greedy como estratégia primária.

As três salvaguardas exigidas junto dessa troca:
- **Fallback com log/telemetria** — `RouteMatrixService` cai para a heurística haversine (v1) se o OSRM `/table` estiver indisponível ou devolver qualquer par sem ligação viária, sempre com `log.warn`, nunca silencioso; se mesmo assim o OR-Tools não achar solução, cai para o nearest-neighbor greedy como último recurso.
- **Teto de paradas por `route_plan`** — 30 (`RoutePlanService.TETO_PARADAS_ROTA`, espelhado em `geo-api` como `TETO_PONTOS_MATRIZ`), validado tanto em `suggestOrder` quanto em `create`.
- **Cache da matriz por conjunto de pontos** — `RouteMatrixService` mantém a matriz em memória por 5 minutos por conjunto de coordenadas, evitando recalcular a cada ajuste de "sugerir ordem" na mesma tela (mesmo padrão single-instance de `TypingIndicatorService`, Redis como evolução natural se precisar de múltiplas instâncias).

## Vídeo e visão computacional

- Tratado como **linha de pesquisa paralela**, não dependência do MVP nem da Fase 2.
- Dois problemas teóricos reconhecidos desde o pitch: calibração de câmera (pixel → distância real) e viés de domínio de modelos pré-treinados no trânsito misto brasileiro.
- Antes de qualquer investimento de engenharia em visão computacional, validar com um piloto pequeno e supervisionado (poucos veículos, poucas horas de vídeo) se o dado é sequer utilizável — não assumir que "vai funcionar" só porque funciona em datasets internacionais.

## Privacidade e LGPD (aplica-se a este módulo inteiro)

- Dado de GPS e vídeo é dado pessoal/sensível quando vinculável a um motorista identificável — anonimizar ou pseudonimizar antes de qualquer uso agregado (`road_readiness_score` não deve carregar referência a motorista/veículo específico).
- Definir e documentar tempo de retenção de `vehicle_gps_ping` bruto (não guardar indefinidamente — agregar e descartar o dado bruto após uma janela definida).
- Consentimento do motorista para coleta deve estar no fluxo de onboarding do app (ver `03-mobile-e-assinaturas.md`), não é opcional nem letra miúda.

## Definition of Done (dados/mapas, Fase 1-2)

- [x] Schema `geo` com as entidades acima, migrations versionadas. (`road_segment`, `road_segment_observation`, `road_readiness_score` — migration 0002)
- [x] Import de um extrato OSM (ex. região do piloto) rodando localmente. (`scripts/import_osm_pilot.py`, via Overpass API)
- [x] Pipeline de ingestão de GPS → `road_segment_observation` funcionando ponta a ponta com dado de pelo menos 1 veículo real ou simulado. (verificado com dado real importado)
- [x] Job de agregação para `road_readiness_score` rodando. (`app/aggregation.py`, agendado via `app/scheduler.py`)
- [x] Política de retenção/anonimização documentada e implementada. (ADR 0009; `app/retention.py`)
- [x] Roteamento básico via OSRM/GraphHopper. (OSRM em modo MLD sobre extrato da área do piloto — ADR 0018; `GET /v1/routes/preview` no core-api, tela "Rotas" no web.)
- [x] Cadastro de pontos de coleta reutilizáveis (`collection_point`), com geocodificação via Nominatim.
- [x] Rota com categoria `TRANSFER` (trajeto único com valor) além de `ROTA` (multi-parada).
- [x] **(Extensão Fase 2+) Roteamento multi-coleta/multi-entrega com solver existente (OR-Tools) e matriz de distância real (OSRM `/table`)** — ver seção "Distância real (OSRM `/table`) + OR-Tools" acima; heurística nearest-neighbor por linha reta da v1 preservada como fallback de dois níveis, nunca removida.
