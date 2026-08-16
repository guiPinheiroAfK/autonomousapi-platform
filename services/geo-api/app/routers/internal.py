from datetime import datetime
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Query, status
from geoalchemy2 import Geography
from geoalchemy2.elements import WKTElement
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..aggregation import recalcular_road_readiness
from ..config import settings
from ..db import get_db
from ..driving_events import calcular_driving_events
from ..geocoding import NominatimGeocoder
from ..matching import encontrar_segmento_mais_proximo
from ..models import ChargingStation, ChargingStationStatus, RoadSegmentObservation, VehicleGpsPing
from ..routing import OsrmRoutingClient
from ..security import require_service_token

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
    row = VehicleGpsPing(
        vehicle_id=ping.vehicle_id,
        recorded_at=ping.recorded_at,
        lat=ping.lat,
        lon=ping.lon,
        speed=ping.speed,
        heading=ping.heading,
        accuracy=ping.accuracy,
        geom=WKTElement(f"POINT({ping.lon} {ping.lat})", srid=4326),
    )
    db.add(row)

    segmento_id = encontrar_segmento_mais_proximo(db, ping.lat, ping.lon)
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
