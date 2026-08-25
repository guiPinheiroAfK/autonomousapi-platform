from datetime import datetime
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Query, status
from geoalchemy2 import Geography
from geoalchemy2.elements import WKTElement
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from ..aggregation import recalcular_road_readiness
from ..config import settings
from ..db import get_db
from ..driving_events import calcular_driving_events
from ..geocoding import NominatimGeocoder
from ..matching import encontrar_segmento_mais_proximo
from ..models import ChargingStation, ChargingStationStatus, RoadSegmentObservation, VehicleGpsPing
from ..quality_metrics import calcular_metricas_qualidade
from ..road_readiness_v2 import recalcular_road_readiness_v2
from ..routing import OsrmRoutingClient
from ..security import require_service_token
from ..sessionization import reconstruir_passagens

# Todas as rotas exigem token de serviço. Prefixo /internal deixa explícito que não é público.
router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(require_service_token)],
    tags=["internal"],
)


@router.get("/health")
def health() -> dict[str, str]:
    """Health interno consumido pelo core-api para compor o health agregado."""
    return {"status": "ok", "service": "geo-api"}


class GpsPingIn(BaseModel):
    vehicle_id: UUID
    recorded_at: datetime
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)
    speed: float | None = None
    heading: float | None = None
    accuracy: float | None = None


class GpsPingAccepted(BaseModel):
    id: UUID


@router.post("/gps/pings", status_code=status.HTTP_202_ACCEPTED)
def ingest_ping(ping: GpsPingIn, db: Session = Depends(get_db)) -> GpsPingAccepted:
    """
    Ingestão de ping de GPS + map matching (Fase 2, spec 02). Persiste o ping bruto
    (expurgado depois pelo job de retenção, ver app/retention.py) e, se cair perto o
    bastante de um `road_segment` conhecido, gera uma `road_segment_observation` —
    isso é síncrono e barato (uma consulta indexada), diferente da agregação do score,
    que o spec proíbe explicitamente de rodar em tempo real (fica para o job periódico).
    """
    segmento_id = encontrar_segmento_mais_proximo(db, ping.lat, ping.lon)
    row = VehicleGpsPing(
        vehicle_id=ping.vehicle_id,
        recorded_at=ping.recorded_at,
        lat=ping.lat,
        lon=ping.lon,
        speed=ping.speed,
        heading=ping.heading,
        accuracy=ping.accuracy,
        # Gravado no próprio ping pra sessionização (D1.1) reaproveitar o match sem
        # refazer a consulta espacial (ADR 0019, Decisão 1).
        road_segment_id=segmento_id,
        geom=WKTElement(f"POINT({ping.lon} {ping.lat})", srid=4326),
    )
    db.add(row)

    if segmento_id is not None:
        db.add(
            RoadSegmentObservation(
                id=uuid4(),
                road_segment_id=segmento_id,
                observed_at=ping.recorded_at,
                avg_speed_kmh=ping.speed,
            )
        )

    db.commit()
    db.refresh(row)
    return GpsPingAccepted(id=row.id)


class GpsPingBatchIn(BaseModel):
    pings: list[GpsPingIn]


class GpsPingBatchAccepted(BaseModel):
    accepted: int
    received: int


@router.post("/gps/pings/batch", status_code=status.HTTP_202_ACCEPTED)
def ingest_ping_batch(batch: GpsPingBatchIn, db: Session = Depends(get_db)) -> GpsPingBatchAccepted:
    """
    Versão em lote de `ingest_ping` (ADR 0019, pré-requisito A) — uma transação para o
    lote inteiro em vez de uma chamada HTTP por ping (era o caminho antes: o app manda
    o lote pro core-api, mas `TripService#submitPings` chamava `POST /gps/pings` uma
    vez por ping).

    Contrato com a fila offline do app (ver ADR 0019, "Anexo — contrato da ingestão em
    lote", A1/A2), não mudar sem reler aquilo:

    - `INSERT ... ON CONFLICT DO NOTHING` na chave natural (vehicle_id, recorded_at,
      migration 0004) — ping duplicado (reenvio de lote parcial) não vira erro nem
      linha nova, só é ignorado.
    - `accepted` sempre conta o lote inteiro quando a transação teve sucesso, nunca só
      as linhas de fato inseridas — duplicata é sucesso do ponto de vista do cliente
      (A1). Só uma falha real de transação (exceção) resulta em resposta de erro, e aí
      o app reenvia o lote inteiro (idempotente por construção, graças ao ON CONFLICT).
    - Observação só é gerada para o ping que a query `RETURNING` de fato devolveu — ou
      seja, só para quem foi inserido agora. Ping deduplicado não gera observação de
      novo (A2): a linha já existia, então a observação dela (se algum dia existiu)
      também já foi gravada na primeira vez.

    Matching roda ANTES do insert, pra todo ping do lote (inclusive os que acabam sendo
    conflito e descartados pelo `ON CONFLICT` — barato, é só uma consulta indexada a
    mais por duplicata). É o que permite gravar `road_segment_id` na mesma linha do
    ping (ADR 0019, Decisão 1) sem uma segunda ida ao banco pós-insert.
    """
    if not batch.pings:
        return GpsPingBatchAccepted(accepted=0, received=0)

    valores = [
        {
            "id": uuid4(),
            "vehicle_id": p.vehicle_id,
            "recorded_at": p.recorded_at,
            "lat": p.lat,
            "lon": p.lon,
            "speed": p.speed,
            "heading": p.heading,
            "accuracy": p.accuracy,
            "road_segment_id": encontrar_segmento_mais_proximo(db, p.lat, p.lon),
            "geom": WKTElement(f"POINT({p.lon} {p.lat})", srid=4326),
        }
        for p in batch.pings
    ]
    stmt = (
        pg_insert(VehicleGpsPing.__table__)
        .values(valores)
        .on_conflict_do_nothing(
            index_elements=[VehicleGpsPing.vehicle_id, VehicleGpsPing.recorded_at]
        )
        .returning(VehicleGpsPing.recorded_at, VehicleGpsPing.speed, VehicleGpsPing.road_segment_id)
    )
    inseridos = db.execute(stmt).all()

    for observed_at, speed, segmento_id in inseridos:
        if segmento_id is not None:
            db.add(
                RoadSegmentObservation(
                    id=uuid4(),
                    road_segment_id=segmento_id,
                    observed_at=observed_at,
                    avg_speed_kmh=speed,
                )
            )

    db.commit()
    return GpsPingBatchAccepted(accepted=len(batch.pings), received=len(batch.pings))


class RoadReadinessRecalculated(BaseModel):
    segments_updated: int


@router.post("/road-readiness/recalculate")
def recalculate_road_readiness(db: Session = Depends(get_db)) -> RoadReadinessRecalculated:
    """
    Dispara o job de agregação sob demanda (o scheduler já roda isso periodicamente,
    ver app/scheduler.py) — útil para operação manual e para testes determinísticos,
    sem depender do timing do agendador em background.
    """
    return RoadReadinessRecalculated(segments_updated=recalcular_road_readiness(db))


class PassagesReconstructed(BaseModel):
    passages_created: int


@router.post("/road-segment-passages/reconstruct")
def reconstruct_road_segment_passages(db: Session = Depends(get_db)) -> PassagesReconstructed:
    """
    Dispara a sessionização sob demanda (ADR 0019, Decisão 1) — o scheduler já roda
    isso periodicamente (ver app/scheduler.py); útil para operação manual e para
    testes determinísticos, mesmo padrão de `/road-readiness/recalculate`.
    """
    passagens = reconstruir_passagens(
        db,
        gap_max_minutes=settings.passage_gap_max_minutes,
        rebuild_window_hours=settings.passage_rebuild_window_hours,
    )
    return PassagesReconstructed(passages_created=passagens)


class RoadReadinessV2Recalculated(BaseModel):
    cells_updated: int


@router.post("/road-readiness/recalculate-v2")
def recalculate_road_readiness_v2(db: Session = Depends(get_db)) -> RoadReadinessV2Recalculated:
    """
    Dispara o score v2 sob demanda (ADR 0019, Decisões 2/3) — mesmo padrão de
    `/road-readiness/recalculate` (v1). Lê de `road_segment_passage`, não de
    `road_segment_observation`; rodar a sessionização antes (endpoint acima) se a
    janela de passagens ainda não tiver sido reconstruída.
    """
    celulas = recalcular_road_readiness_v2(db, pilot_timezone=settings.pilot_timezone)
    return RoadReadinessV2Recalculated(cells_updated=celulas)


class QualityMetrics(BaseModel):
    total_segments: int
    segments_with_score: int
    coverage_ratio: float | None
    total_cells: int
    average_confidence: float | None
    low_confidence_cell_ratio: float | None
    average_observations_per_cell: float | None
    total_pings: int
    late_ping_ratio: float | None


@router.get("/road-readiness/quality-metrics")
def road_readiness_quality_metrics(db: Session = Depends(get_db)) -> QualityMetrics:
    """
    Métricas de qualidade do score (ADR 0019, passo 6 — DoD da Fase 3): cobertura,
    distribuição de confiança, densidade de observação e taxa de ping atrasado. Ver
    app/quality_metrics.py para o porquê de cada uma. Read-only, computado sob
    demanda — não é um job agendado (não há necessidade: barato de calcular, e não
    existe consumidor automatizado ainda que precise de valor sempre fresco em
    background).
    """
    metricas = calcular_metricas_qualidade(
        db, rebuild_window_hours=settings.passage_rebuild_window_hours
    )
    return QualityMetrics(**metricas)


class ChargingStationItem(BaseModel):
    id: UUID
    name: str | None
    address: str | None
    connector_type: str | None
    power_kw: float | None
    lat: float
    lon: float
    status: str


class ChargingStationsResponse(BaseModel):
    provider_available: bool
    stations: list[ChargingStationItem]


@router.get("/charging-stations")
def listar_estacoes_recarga(
    lat: float | None = Query(default=None, ge=-90, le=90),
    lon: float | None = Query(default=None, ge=-180, le=180),
    radius_km: float = Query(default=20.0, gt=0),
    db: Session = Depends(get_db),
) -> ChargingStationsResponse:
    """
    Lista estações com o status mais recente de cada uma (spec 06, item 1). RNF011: uma
    estação sem status observado recentemente (provedor nunca sincronizou, ou está fora
    do ar há tempo) aparece com status "DESCONHECIDO" em vez de ser omitida ou quebrar a
    resposta — a tela do usuário nunca vê erro por causa disso.
    """
    query = select(ChargingStation)
    if lat is not None and lon is not None:
        ponto = func.ST_SetSRID(func.ST_MakePoint(lon, lat), 4326)
        query = query.where(
            func.ST_DWithin(
                func.cast(ChargingStation.geom, Geography),
                func.cast(ponto, Geography),
                radius_km * 1000,
            )
        )
    estacoes = db.execute(query).scalars().all()

    itens: list[ChargingStationItem] = []
    for estacao in estacoes:
        ultimo_status = db.execute(
            select(ChargingStationStatus)
            .where(ChargingStationStatus.station_id == estacao.id)
            .order_by(ChargingStationStatus.observed_at.desc())
            .limit(1)
        ).scalar_one_or_none()
        itens.append(
            ChargingStationItem(
                id=estacao.id,
                name=estacao.name,
                address=estacao.address,
                connector_type=estacao.connector_type,
                power_kw=estacao.power_kw,
                lat=estacao.lat,
                lon=estacao.lon,
                status=ultimo_status.status if ultimo_status else "DESCONHECIDO",
            )
        )

    return ChargingStationsResponse(
        provider_available=bool(settings.open_charge_map_api_key), stations=itens
    )


class DrivingEventsResponse(BaseModel):
    ping_count: int
    hard_braking_count: int
    overspeed_count: int


@router.get("/driving-events")
def driving_events(
    vehicle_id: UUID,
    de: datetime = Query(alias="from"),
    ate: datetime = Query(alias="to"),
    db: Session = Depends(get_db),
) -> DrivingEventsResponse:
    """
    Componentes de avaliação automática de motorista (spec 06, item 3): frenagem brusca
    e excesso de velocidade calculados a partir do ping de GPS da viagem. Chamado só
    pelo job diário do core-api (DriverAutoRatingJob) — nunca em tempo real na tela.
    """
    eventos = calcular_driving_events(db, vehicle_id, de, ate)
    return DrivingEventsResponse(
        ping_count=eventos.ping_count,
        hard_braking_count=eventos.hard_braking_count,
        overspeed_count=eventos.overspeed_count,
    )


class RouteStepOut(BaseModel):
    instruction_type: str
    modifier: str | None
    name: str | None
    distance_m: float
    duration_s: float


class RouteResponse(BaseModel):
    available: bool
    distance_m: float | None = None
    duration_s: float | None = None
    geometry: list[list[float]] = []
    steps: list[RouteStepOut] = []
    unavailable_reason: str | None = None


@router.get("/route")
def rota(
    from_lat: float = Query(ge=-90, le=90),
    from_lon: float = Query(ge=-180, le=180),
    to_lat: float = Query(ge=-90, le=90),
    to_lon: float = Query(ge=-180, le=180),
) -> RouteResponse:
    """
    Rota ponto-a-ponto (spec 02, Fase 1-2 do roteamento). Sempre 200: motor fora do ar ou
    par de pontos sem rota devolvem `available=false` com motivo legível, em vez de 5xx —
    a diferença entre "não configurado", "caiu" e "não existe rota aqui" importa pra quem
    lê a tela, e um 503 genérico apaga essa diferença.
    """
    cliente = OsrmRoutingClient(settings.osrm_url)
    resultado = cliente.rota(from_lat, from_lon, to_lat, to_lon)
    return RouteResponse(
        available=resultado.available,
        distance_m=resultado.distance_m,
        duration_s=resultado.duration_s,
        geometry=resultado.geometry,
        steps=[
            RouteStepOut(
                instruction_type=p.instruction_type,
                modifier=p.modifier,
                name=p.name,
                distance_m=p.distance_m,
                duration_s=p.duration_s,
            )
            for p in resultado.steps
        ],
        unavailable_reason=resultado.unavailable_reason,
    )


class TablePoint(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)


class TableRequest(BaseModel):
    # Lista, não par de coordenadas: a ordem enviada aqui é a mesma ordem usada nos índices
    # de `distances_m`/`durations_s` da resposta — quem chama precisa manter essa ordem para
    # mapear a matriz de volta às paradas originais.
    points: list[TablePoint]


class TableResponse(BaseModel):
    available: bool
    distances_m: list[list[float | None]] = []
    durations_s: list[list[float | None]] = []
    unavailable_reason: str | None = None


@router.post("/table")
def table(body: TableRequest) -> TableResponse:
    """
    Matriz de distância/duração real entre N pontos (spec 02, "Evolução pendente"),
    consumida pelo solver VRP do core-api. Mesmo contrato de degradação do `/route`: motor
    fora do ar, ponto fora da área ou mais pontos que o teto viram `available=false` com
    motivo legível, sempre 200 — nunca 5xx por causa de um serviço de infraestrutura externo.
    """
    cliente = OsrmRoutingClient(settings.osrm_url)
    resultado = cliente.table([(p.lat, p.lon) for p in body.points])
    return TableResponse(
        available=resultado.available,
        distances_m=resultado.distances_m,
        durations_s=resultado.durations_s,
        unavailable_reason=resultado.unavailable_reason,
    )


class PlaceOut(BaseModel):
    display_name: str
    lat: float
    lon: float


@router.get("/geocode")
def geocode(q: str = Query(min_length=3)) -> list[PlaceOut]:
    """
    Endereço -> coordenada, restrito à área do piloto (spec 02). Lista vazia é resposta
    válida ("não achei aqui"), não erro — o front distingue isso de falha por já ter
    recebido 200.
    """
    geocoder = NominatimGeocoder(settings.nominatim_url)
    return [
        PlaceOut(display_name=lugar.display_name, lat=lugar.lat, lon=lugar.lon)
        for lugar in geocoder.buscar(q)
    ]
