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

    # Recarga elétrica (spec 06, item 1). Sem chave (padrão dev/demo) = provider
    # desabilitado, mecanismo pronto e testável sem credencial — mesmo padrão do
    # EmailSender/PushSender/Stripe no core-api (ver app/charging.py).
    open_charge_map_api_key: str = ""
    charging_sync_country_code: str = "BR"


settings = Settings()
