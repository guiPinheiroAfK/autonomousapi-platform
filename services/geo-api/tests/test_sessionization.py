from datetime import UTC, datetime, timedelta
from uuid import uuid4

from geoalchemy2.elements import WKTElement

from app.db import SessionLocal
from app.models import RoadSegment, RoadSegmentPassage, VehicleGpsPing
from app.sessionization import reconstruir_passagens

GAP_MAX_MINUTES = 5
REBUILD_WINDOW_HOURS = 72


def _criar_segmento(offset: float = 0.0) -> RoadSegment:
    db = SessionLocal()
    try:
        segmento = RoadSegment(
            osm_way_id=uuid4().int % 2_000_000_000,
            name="Via de teste (sessionização)",
            highway_type="residential",
            geom=WKTElement(f"LINESTRING(0 {offset}, 0 {offset + 0.001})", srid=4326),
        )
        db.add(segmento)
        db.commit()
        db.refresh(segmento)
        return segmento
    finally:
        db.close()


def _salvar_ping(vehicle_id, segmento_id, recorded_at, speed=None):
    db = SessionLocal()
    try:
        ping = VehicleGpsPing(
            vehicle_id=vehicle_id,
            recorded_at=recorded_at,
            lat=0.0,
            lon=0.0,
            speed=speed,
            road_segment_id=segmento_id,
            geom=WKTElement("POINT(0 0)", srid=4326),
        )
        db.add(ping)
        db.commit()
        db.refresh(ping)
        return ping.id
    finally:
        db.close()


def _limpar(*, segmento_ids=(), ping_ids=()):
    db = SessionLocal()
    try:
        db.query(RoadSegmentPassage).filter(
            RoadSegmentPassage.road_segment_id.in_(segmento_ids)
        ).delete(synchronize_session=False)
        db.query(VehicleGpsPing).filter(VehicleGpsPing.id.in_(ping_ids)).delete(
            synchronize_session=False
        )
        db.query(RoadSegment).filter(RoadSegment.id.in_(segmento_ids)).delete(
            synchronize_session=False
        )
        db.commit()
    finally:
        db.close()


def _reconstruir():
    db = SessionLocal()
    try:
        return reconstruir_passagens(
            db, gap_max_minutes=GAP_MAX_MINUTES, rebuild_window_hours=REBUILD_WINDOW_HOURS
        )
    finally:
        db.close()


def _passagens_do_segmento(segmento_id):
    db = SessionLocal()
    try:
        return (
            db.query(RoadSegmentPassage)
            .filter(RoadSegmentPassage.road_segment_id == segmento_id)
            .order_by(RoadSegmentPassage.entered_at)
            .all()
        )
    finally:
        db.close()


def test_pings_contiguos_do_mesmo_veiculo_e_segmento_viram_uma_so_passagem():
    segmento = _criar_segmento(offset=0.10)
    vehicle_id = uuid4()
    inicio = datetime.now(UTC) - timedelta(hours=1)
    ping_ids = [
        _salvar_ping(vehicle_id, segmento.id, inicio, 40.0),
        _salvar_ping(vehicle_id, segmento.id, inicio + timedelta(seconds=30), 42.0),
        _salvar_ping(vehicle_id, segmento.id, inicio + timedelta(minutes=1), 38.0),
    ]
    try:
        _reconstruir()
        passagens = _passagens_do_segmento(segmento.id)
        assert len(passagens) == 1
        p = passagens[0]
        assert p.ping_count == 3
        assert p.entered_at == inicio
        assert p.exited_at == inicio + timedelta(minutes=1)
        assert p.avg_speed_kmh == 40.0
        assert p.min_speed_kmh == 38.0
    finally:
        _limpar(segmento_ids=[segmento.id], ping_ids=ping_ids)


def test_gap_maior_que_limite_quebra_em_duas_passagens():
    """D1.1: intervalo maior que gap_max_minutes separa "atravessou a via" de "voltou
    horas depois" — mesmo veículo, mesmo segmento, duas passagens distintas."""
    segmento = _criar_segmento(offset=0.11)
    vehicle_id = uuid4()
    inicio = datetime.now(UTC) - timedelta(hours=2)
    ping_ids = [
        _salvar_ping(vehicle_id, segmento.id, inicio, 40.0),
        _salvar_ping(
            vehicle_id, segmento.id, inicio + timedelta(minutes=GAP_MAX_MINUTES + 1), 30.0
        ),
    ]
    try:
        _reconstruir()
        passagens = _passagens_do_segmento(segmento.id)
        assert len(passagens) == 2
        assert all(p.ping_count == 1 for p in passagens)
    finally:
        _limpar(segmento_ids=[segmento.id], ping_ids=ping_ids)


def test_troca_de_segmento_fecha_a_passagem_mesmo_sem_gap_de_tempo():
    segmento_a = _criar_segmento(offset=0.12)
    segmento_b = _criar_segmento(offset=0.13)
    vehicle_id = uuid4()
    inicio = datetime.now(UTC) - timedelta(hours=1)
    ping_ids = [
        _salvar_ping(vehicle_id, segmento_a.id, inicio, 40.0),
        _salvar_ping(vehicle_id, segmento_b.id, inicio + timedelta(seconds=10), 40.0),
    ]
    try:
        _reconstruir()
        assert len(_passagens_do_segmento(segmento_a.id)) == 1
        assert len(_passagens_do_segmento(segmento_b.id)) == 1
    finally:
        _limpar(segmento_ids=[segmento_a.id, segmento_b.id], ping_ids=ping_ids)


def test_passagens_de_veiculos_diferentes_nao_se_misturam():
    segmento = _criar_segmento(offset=0.14)
    veiculo_1, veiculo_2 = uuid4(), uuid4()
    inicio = datetime.now(UTC) - timedelta(hours=1)
    ping_ids = [
        _salvar_ping(veiculo_1, segmento.id, inicio, 40.0),
        _salvar_ping(veiculo_2, segmento.id, inicio + timedelta(seconds=1), 50.0),
    ]
    try:
        _reconstruir()
        passagens = _passagens_do_segmento(segmento.id)
        assert len(passagens) == 2
        assert all(p.ping_count == 1 for p in passagens)
    finally:
        _limpar(segmento_ids=[segmento.id], ping_ids=ping_ids)


def test_ping_sem_segmento_nao_gera_passagem():
    vehicle_id = uuid4()
    ping_id = _salvar_ping(vehicle_id, None, datetime.now(UTC) - timedelta(hours=1), 40.0)
    try:
        antes = _reconstruir()
        assert antes >= 0  # não lança, e não conta esse ping em lugar nenhum
    finally:
        _limpar(ping_ids=[ping_id])


def test_passagem_com_ping_unico_nao_e_descartada_por_amostra_pequena():
    """D1.1: sem filtro de amostra mínima — 1 ping vira passagem válida (é o caso de
    quem atravessa o segmento rápido, exatamente o que o v1 penalizava por engano)."""
    segmento = _criar_segmento(offset=0.15)
    vehicle_id = uuid4()
    ping_id = _salvar_ping(vehicle_id, segmento.id, datetime.now(UTC) - timedelta(hours=1), 60.0)
    try:
        _reconstruir()
        passagens = _passagens_do_segmento(segmento.id)
        assert len(passagens) == 1
        assert passagens[0].ping_count == 1
    finally:
        _limpar(segmento_ids=[segmento.id], ping_ids=[ping_id])


def test_reconstrucao_e_idempotente():
    segmento = _criar_segmento(offset=0.16)
    vehicle_id = uuid4()
    inicio = datetime.now(UTC) - timedelta(hours=1)
    ping_ids = [_salvar_ping(vehicle_id, segmento.id, inicio, 40.0)]
    try:
        _reconstruir()
        primeira = _passagens_do_segmento(segmento.id)
        _reconstruir()
        segunda = _passagens_do_segmento(segmento.id)
        assert len(primeira) == len(segunda) == 1
        # Mesmo conteúdo, id pode mudar (linha recriada) — o que importa é não duplicar.
        assert primeira[0].entered_at == segunda[0].entered_at
        assert primeira[0].ping_count == segunda[0].ping_count
    finally:
        _limpar(segmento_ids=[segmento.id], ping_ids=ping_ids)


def test_passagem_que_comeca_antes_da_janela_nao_e_duplicada():
    """D1.2: a passagem inteira começou antes do limite da janela reconstruída — a
    margem de leitura permite reconhecê-la (pra não duplicar), mas ela não deve ser
    reinserida, porque uma rodada anterior já a teria gravado."""
    segmento = _criar_segmento(offset=0.17)
    vehicle_id = uuid4()
    # Muito antes da janela de 72h — mas dentro da margem de leitura de 1h antes do
    # limite, então os pings SÃO lidos, mas a passagem detectada começa antes do
    # limite e por isso não deve gerar uma linha nova.
    limite = datetime.now(UTC) - timedelta(hours=REBUILD_WINDOW_HOURS)
    inicio = limite - timedelta(minutes=30)
    ping_ids = [
        _salvar_ping(vehicle_id, segmento.id, inicio, 40.0),
        _salvar_ping(vehicle_id, segmento.id, inicio + timedelta(minutes=1), 40.0),
    ]
    try:
        criadas = _reconstruir()
        passagens = _passagens_do_segmento(segmento.id)
        assert len(passagens) == 0
        assert criadas == 0
    finally:
        _limpar(segmento_ids=[segmento.id], ping_ids=ping_ids)
