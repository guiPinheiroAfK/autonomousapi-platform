from contextlib import asynccontextmanager

from fastapi import FastAPI

from .routers import internal
from .scheduler import iniciar_scheduler, parar_scheduler


@asynccontextmanager
async def lifespan(app: FastAPI):
    iniciar_scheduler()
    try:
        yield
    finally:
        parar_scheduler()


app = FastAPI(
    title="geo-api",
    version="0.1.0",
    description=(
        "Serviço geoespacial interno (GPS, rotas, prontidão viária). "
        "Não exposto à internet pública — só o core-api chama (spec 01)."
    ),
    lifespan=lifespan,
)

app.include_router(internal.router)


@app.get("/", tags=["meta"])
def root() -> dict[str, str]:
    """Liveness sem token, para orquestradores (não expõe nada sensível)."""
    return {"service": "geo-api", "status": "alive"}
