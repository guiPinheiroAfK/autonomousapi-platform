from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Config do geo-api. Variáveis com prefixo GEO_ (ex.: GEO_DB_URL, GEO_SERVICE_TOKEN)."""

    model_config = SettingsConfigDict(env_prefix="GEO_", env_file=".env", extra="ignore")

    # Banco compartilhado (Neon). O geo-api é dono EXCLUSIVO do schema `geo` (ADR 0004).
    db_url: str = "postgresql+psycopg://autonomousapi:autonomousapi@localhost:5432/autonomousapi"

    # Token de SERVIÇO (não token de usuário). Só o core-api chama o geo-api (spec 01).
    service_token: str = "dev-service-token-change-me"

    # Retenção de GPS bruto (spec 02 + ADR 0009). 30 dias é ponto de partida documentado
    # na ADR, não um número regulatório — o agregado anônimo (road_segment_observation)
    # sobrevive à purga porque não carrega essa referência desde a origem.
    gps_retention_days: int = 30

    # Intervalo do job de agregação de road_readiness (spec 02: "job periódico").
    road_readiness_recalc_interval_minutes: int = 5

    # Sessionização de pings em passagens (ADR 0019, Decisão 1). Corta a passagem
    # quando o intervalo entre pings consecutivos do mesmo veículo/segmento excede
    # isso — separa "atravessou a via" de "voltou à mesma via horas depois" (D1.1).
    passage_gap_max_minutes: int = 5

    # Janela reconstruída a cada rodada do job (D1.2: apagar-e-reconstruir, não dá pra
    # marcar ping como "processado" sem ferir a anonimização estrutural da ADR 0009).
    # Ping mais atrasado que isso (fila offline do app) nunca vira passagem — vira
    # métrica de qualidade da Fase 3, não falha silenciosa.
    passage_rebuild_window_hours: int = 72

    # Intervalo do job de sessionização — passagem não é dado de tempo real como o
    # score (D1.2: "de hora em hora basta").
    passage_recalc_interval_minutes: int = 60

    # Fuso da área do piloto (app/pilot_area.py) — "pico da manhã" só significa algo
    # depois de converter de UTC pro fuso local (ADR 0019, D2.1).
    pilot_timezone: str = "America/Sao_Paulo"

    # Recarga elétrica (spec 06, item 1). Sem chave (padrão dev/demo) = provider
    # desabilitado, mecanismo pronto e testável sem credencial — mesmo padrão do
    # EmailSender/PushSender/Stripe no core-api (ver app/charging.py).
    open_charge_map_api_key: str = ""
    charging_sync_country_code: str = "BR"

    # Motor de roteamento (spec 02). Vazio = roteamento desligado, respondendo
    # "indisponível" com motivo legível em vez de erro. O grafo é preparado por
    # scripts/prepare_osrm_graph.py e servido pelo profile `routing` do compose.
    osrm_url: str = ""

    # Geocodificação (endereço -> coordenada) para o roteamento ser usável por gente.
    # Instância pública do Nominatim por padrão: sem chave, mas com política de uso
    # (volume baixo, UA identificável — ver app/geocoding.py). Trocar por instância
    # própria quando o volume justificar.
    nominatim_url: str = "https://nominatim.openstreetmap.org"


settings = Settings()
