from fastapi import FastAPI

from .routers import internal

app = FastAPI(
    title="geo-api",
    version="0.1.0",
    description=(
        "Serviço geoespacial interno (GPS, rotas, prontidão viária). "
        "Não exposto à internet pública — só o core-api chama (spec 01)."
    ),
)

app.include_router(internal.router)


@app.get("/", tags=["meta"])
def root() -> dict[str, str]:
    """Liveness sem token, para orquestradores (não expõe nada sensível)."""
    return {"service": "geo-api", "status": "alive"}
