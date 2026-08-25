from app.osm_tags import parse_maxspeed_kmh


def test_numero_simples():
    assert parse_maxspeed_kmh("50") == 50.0


def test_numero_com_sufixo_kmh():
    assert parse_maxspeed_kmh("60 km/h") == 60.0


def test_mph_converte_para_kmh():
    assert parse_maxspeed_kmh("30 mph") == 48.3


def test_mph_maiuscula_e_sem_espaco():
    assert parse_maxspeed_kmh("30MPH") == 48.3


def test_palavra_chave_vira_none():
    assert parse_maxspeed_kmh("signals") is None
    assert parse_maxspeed_kmh("none") is None
    assert parse_maxspeed_kmh("walk") is None
    assert parse_maxspeed_kmh("variable") is None


def test_lista_de_valores_vira_none():
    assert parse_maxspeed_kmh("30;50") is None
    assert parse_maxspeed_kmh("50|60") is None


def test_default_por_pais_vira_none():
    assert parse_maxspeed_kmh("BR:urban") is None


def test_vazio_ou_ausente_vira_none():
    assert parse_maxspeed_kmh(None) is None
    assert parse_maxspeed_kmh("") is None


def test_espacos_extras_sao_ignorados():
    assert parse_maxspeed_kmh("  40  ") == 40.0
