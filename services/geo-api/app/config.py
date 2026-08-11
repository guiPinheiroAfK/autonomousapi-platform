from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Config do geo-api. Variáveis com prefixo GEO_ (ex.: GEO_DB_URL, GEO_SERVICE_TOKEN)."""

    model_config = SettingsConfigDict(env_prefix="GEO_", env_file=".env", extra="ignore")

    # Banco compartilhado (Neon). O geo-api é dono EXCLUSIVO do schema `geo` (ADR 0004).
    db_url: str = "postgresql+psycopg://autonomousapi:autonomousapi@localhost:5432/autonomousapi"

    # Token de SERVIÇO (não token de usuário). Só o core-api chama o geo-api (spec 01).
    service_token: str = "dev-service-token-change-me"


settings = Settings()
