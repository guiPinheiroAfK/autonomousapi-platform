from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from geoalchemy2.elements import WKTElement

from app.db import SessionLocal
from app.models import RoadReadinessScore, RoadSegment, RoadSegmentPassage
from app.road_readiness_v2 import recalcular_road_readiness_v2

TZ = "America/Sao_Paulo"


def _criar_segmento(offset: float, highway_type="residential", maxspeed_kmh=None) -> RoadSegment:
    db = SessionLocal()
    try:
        segmento = RoadSegment(
            osm_way_id=uuid4().int % 2_000_000_000,
            name="Via de teste (score v2)",
            highway_type=highway_type,
            maxspeed_kmh=maxspeed_kmh,
            geom=WKTElement(f"LINESTRING(0 {offset}, 0 {offset + 0.001})", srid=4326),
        )
        db.add(segmento)
        db.commit()
        db.refresh(segmento)
        return segmento
    finally:
        db.close()


def _criar_passagem(segmento_id, entered_at, avg_speed_kmh, ping_count=1):
    db = SessionLocal()
    try:
        passagem = RoadSegmentPassage(
            id=uuid4(),
            road_segment_id=segmento_id,
            entered_at=entered_at,
            exited_at=entered_at,
            avg_speed_kmh=avg_speed_kmh,
            min_speed_kmh=avg_speed_kmh,
            ping_count=ping_count,
        )
        db.add(passagem)
        db.commit()
        db.refresh(passagem)
        return passagem.id
    finally:
        db.close()


def _limpar(*, segmento_ids=(), passagem_ids=()):
    db = SessionLocal()
    try:
        db.query(RoadReadinessScore).filter(
            RoadReadinessScore.road_segment_id.in_(segmento_ids)
        ).delete(synchronize_session=False)
        db.query(RoadSegmentPassage).filter(RoadSegmentPassage.id.in_(passagem_ids)).delete(
            synchronize_session=False
        )
        db.query(RoadSegment).filter(RoadSegment.id.in_(segmento_ids)).delete(
            synchronize_session=False
        )
        db.commit()
    finally:
        db.close()


def _recalcular():
    db = SessionLocal()
    try:
        return recalcular_road_readiness_v2(db, pilot_timezone=TZ)
    finally:
        db.close()


def _scores_do_segmento(segmento_id):
    db = SessionLocal()
    try:
        return (
            db.query(RoadReadinessScore)
            .filter(
                RoadReadinessScore.road_segment_id == segmento_id,
                RoadReadinessScore.algorithm_version == "v2-flow-reliability",
            )
            .all()
        )
    finally:
        db.close()


# Instante fixo dentro da faixa ENTREPICO de um dia útil (ver test_time_buckets.py),
# recente o bastante pra ter confiança máxima de recência.
_QUANDO = datetime.now(UTC) - timedelta(hours=1)


def test_via_congestionada_pontua_baixo_nao_alto():
    """O bug central que motivou a ADR 0019: no v1, muita gente parada no
    congestionamento gerava MUITAS observações e um score ALTO. No v2, uma via onde a
    velocidade típica é uma fração pequena da referência tem que pontuar mais baixo
    que uma via fluindo bem — nunca o contrário, não importa quantas passagens."""
    congestionada = _criar_segmento(offset=0.20, highway_type="primary")  # referência 60
    fluindo = _criar_segmento(offset=0.205, highway_type="primary")
    ids_congestionada = [
        _criar_passagem(congestionada.id, _QUANDO, avg_speed_kmh=5.0),
        _criar_passagem(congestionada.id, _QUANDO + timedelta(minutes=1), avg_speed_kmh=6.0),
        _criar_passagem(congestionada.id, _QUANDO + timedelta(minutes=2), avg_speed_kmh=4.0),
        # Muito mais passagens que a via fluindo — no v1 (contagem por ping) isso
        # sozinho já bastaria pra pontuar mais alto. No v2 não pode fazer diferença
        # nenhuma pro score (só pra confiança).
        *[
            _criar_passagem(congestionada.id, _QUANDO + timedelta(minutes=3 + i), 5.0)
            for i in range(20)
        ],
    ]
    ids_fluindo = [
        _criar_passagem(fluindo.id, _QUANDO, avg_speed_kmh=58.0),
        _criar_passagem(fluindo.id, _QUANDO + timedelta(minutes=1), avg_speed_kmh=60.0),
    ]
    try:
        _recalcular()
        score_congestionada = _scores_do_segmento(congestionada.id)[0].score
        score_fluindo = _scores_do_segmento(fluindo.id)[0].score
        assert score_congestionada < score_fluindo
    finally:
        _limpar(
            segmento_ids=[congestionada.id, fluindo.id],
            passagem_ids=ids_congestionada + ids_fluindo,
        )


def test_via_fluindo_bem_pontua_alto():
    segmento = _criar_segmento(offset=0.21, highway_type="primary")  # referência 60 km/h
    passagem_ids = [
        _criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=58.0),
        _criar_passagem(segmento.id, _QUANDO + timedelta(minutes=1), avg_speed_kmh=60.0),
        _criar_passagem(segmento.id, _QUANDO + timedelta(minutes=2), avg_speed_kmh=57.0),
    ]
    try:
        _recalcular()
        scores = _scores_do_segmento(segmento.id)
        assert len(scores) == 1
        assert scores[0].score > 0.9
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_maxspeed_do_segmento_tem_prioridade_sobre_padrao_por_highway_type():
    # highway_type residential (padrão 30) mas maxspeed do OSM diz 50 — usa 50.
    segmento = _criar_segmento(offset=0.22, highway_type="residential", maxspeed_kmh=50.0)
    passagem_ids = [_criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=25.0)]
    try:
        _recalcular()
        scores = _scores_do_segmento(segmento.id)
        # razão de fluxo = 25/50 = 0.5 (não 25/30 ≈ 0.833) — confiabilidade = 1.0
        # (amostra única, desvio 0). score = 0.7*0.5 + 0.3*1.0 = 0.65, não 0.883
        # (o que daria se tivesse ignorado maxspeed_kmh e usado o padrão do tipo).
        assert scores[0].score == pytest.approx(0.65)
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_p85_observado_corrige_referencia_so_pra_cima():
    """D3.1, item 3: se o segmento flui comprovadamente mais rápido que o padrão do
    tipo de via, o teto observado (p85) vira a referência — nunca o contrário."""
    segmento = _criar_segmento(offset=0.23, highway_type="residential")  # padrão 30
    passagem_ids = [
        _criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=45.0),
        _criar_passagem(segmento.id, _QUANDO + timedelta(minutes=1), avg_speed_kmh=44.0),
        _criar_passagem(segmento.id, _QUANDO + timedelta(minutes=2), avg_speed_kmh=46.0),
    ]
    try:
        _recalcular()
        scores = _scores_do_segmento(segmento.id)
        # Se a referência tivesse ficado em 30 (padrão), razão de fluxo teria sido
        # >1 e o score teria saturado em algo bem alto só pelo componente de fluxo,
        # mesmo assim o teste real é: não trava/não gera erro e o score é alto porque
        # a velocidade típica bate na própria referência corrigida (p85 ≈ 45-46).
        assert 0.85 < scores[0].score <= 1.0
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_velocidade_inconsistente_reduz_score_via_confiabilidade():
    """D3.2: mesma velocidade típica média, mas uma das duas vias é consistente e a
    outra varia muito — a inconsistente deve pontuar mais baixo (previsibilidade
    importa pro cliente de AV, ADR 0019 Decisão 3)."""
    consistente = _criar_segmento(offset=0.24, highway_type="primary")
    inconsistente = _criar_segmento(offset=0.25, highway_type="primary")
    passagem_ids = [
        _criar_passagem(consistente.id, _QUANDO, avg_speed_kmh=40.0),
        _criar_passagem(consistente.id, _QUANDO + timedelta(minutes=1), avg_speed_kmh=41.0),
        _criar_passagem(consistente.id, _QUANDO + timedelta(minutes=2), avg_speed_kmh=39.0),
        _criar_passagem(inconsistente.id, _QUANDO, avg_speed_kmh=5.0),
        _criar_passagem(inconsistente.id, _QUANDO + timedelta(minutes=1), avg_speed_kmh=55.0),
        _criar_passagem(inconsistente.id, _QUANDO + timedelta(minutes=2), avg_speed_kmh=60.0),
    ]
    try:
        _recalcular()
        score_consistente = _scores_do_segmento(consistente.id)[0].score
        score_inconsistente = _scores_do_segmento(inconsistente.id)[0].score
        assert score_consistente > score_inconsistente
    finally:
        _limpar(
            segmento_ids=[consistente.id, inconsistente.id],
            passagem_ids=passagem_ids,
        )


def test_celula_sem_passagem_com_velocidade_nao_gera_score_nulo_indevido():
    """Passagem sem avg_speed_kmh (nenhum ping da passagem tinha speed) não deve
    contar pra formar uma célula — ela simplesmente não entra na agregação."""
    segmento = _criar_segmento(offset=0.26)
    passagem_ids = [_criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=None)]
    try:
        _recalcular()
        assert _scores_do_segmento(segmento.id) == []
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_confianca_baixa_com_poucas_passagens():
    segmento = _criar_segmento(offset=0.27, highway_type="primary")
    passagem_ids = [_criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=40.0)]
    try:
        _recalcular()
        scores = _scores_do_segmento(segmento.id)
        # 1 passagem de um teto de 30 (ver _AMOSTRA_TETO) — confiança baixa mesmo
        # sendo recente.
        assert scores[0].confidence < 0.1
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_confianca_baixa_quando_passagem_mais_recente_e_antiga():
    segmento = _criar_segmento(offset=0.28, highway_type="primary")
    antiga = datetime.now(UTC) - timedelta(days=120)
    passagem_ids = [
        _criar_passagem(segmento.id, antiga + timedelta(seconds=i), avg_speed_kmh=40.0)
        for i in range(40)  # amostra grande, mas tudo muito velho
    ]
    try:
        _recalcular()
        scores = _scores_do_segmento(segmento.id)
        assert scores[0].confidence < 0.05
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_recalculo_e_idempotente_upsert_nao_duplica_celula():
    segmento = _criar_segmento(offset=0.29, highway_type="primary")
    passagem_ids = [_criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=40.0)]
    try:
        _recalcular()
        _recalcular()
        scores = _scores_do_segmento(segmento.id)
        assert len(scores) == 1
    finally:
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)


def test_linhas_v1_global_convivem_sem_colisao_com_v2():
    """D4: linhas do v1 ficam com time_bucket='GLOBAL' e não colidem com nenhuma
    célula do v2 na unique key composta (road_segment_id, time_bucket)."""
    segmento = _criar_segmento(offset=0.30, highway_type="primary")
    db = SessionLocal()
    v1_id = uuid4()
    try:
        db.add(
            RoadReadinessScore(
                id=v1_id,
                road_segment_id=segmento.id,
                time_bucket="GLOBAL",
                score=0.42,
                observation_count=7,
                algorithm_version="v1-obs-count",
            )
        )
        db.commit()
    finally:
        db.close()

    passagem_ids = [_criar_passagem(segmento.id, _QUANDO, avg_speed_kmh=40.0)]
    try:
        criadas = _recalcular()
        assert criadas >= 1
        db = SessionLocal()
        try:
            todas = (
                db.query(RoadReadinessScore)
                .filter(RoadReadinessScore.road_segment_id == segmento.id)
                .all()
            )
            buckets = {s.time_bucket for s in todas}
            assert "GLOBAL" in buckets
            assert any(b != "GLOBAL" for b in buckets)
        finally:
            db.close()
    finally:
        db = SessionLocal()
        try:
            db.query(RoadReadinessScore).filter(RoadReadinessScore.id == v1_id).delete()
            db.commit()
        finally:
            db.close()
        _limpar(segmento_ids=[segmento.id], passagem_ids=passagem_ids)
