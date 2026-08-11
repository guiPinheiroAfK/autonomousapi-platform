from fastapi import Header, HTTPException, status

from .config import settings


def require_service_token(
    x_service_token: str | None = Header(default=None, alias="X-Service-Token"),
) -> None:
    """
    Exige o token de serviço em toda rota interna. O geo-api NÃO é exposto à internet
    pública; o único chamador legítimo é o core-api (spec 01), que envia este header.
    """
    if x_service_token != settings.service_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid or missing service token",
        )
