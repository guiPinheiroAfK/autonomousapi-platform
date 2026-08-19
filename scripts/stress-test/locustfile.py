"""
Teste de carga do core-api (spec-driven, ADR/specs em docs/ e specs/ do monorepo).

Por que o login não é uma task normal: /v1/auth/login tem rate limit (10
tentativas/60s por e-mail+IP, LoginRateLimitGuard) — se cada usuário simulado do
Locust logasse na task loop, a maioria tomaria 429 e o teste mediria o rate
limiter, não os endpoints que a gente quer olhar. Em vez disso, um único login é
feito uma vez em @events.test_start (before todo usuário simulado arrancar) e o
token é reusado por todos — replica o uso real: poucos gestores logados,
gerando muita requisição de leitura/escrita depois de autenticados.

Uso:
  cd scripts/stress-test
  .venv/Scripts/python -m locust -f locustfile.py --host http://localhost:8080

  Depois abra http://localhost:8089 pra configurar usuários simulados/spawn rate,
  ou rode headless:
  .venv/Scripts/python -m locust -f locustfile.py --host http://localhost:8080 \
    --headless -u 50 -r 5 -t 3m --csv=resultado
"""

import random
from datetime import date, timedelta

from locust import HttpUser, between, events, task

GESTOR_EMAIL = "gestor.e2e@teste.local"
GESTOR_PASSWORD = "senha12345"

_shared_token: str | None = None
_shared_vehicle_ids: list[str] = []


@events.test_start.add_listener
def _login_once(environment, **kwargs):
    """Um login só, fora do loop de task de qualquer usuário simulado (ver docstring)."""
    global _shared_token, _shared_vehicle_ids
    import requests

    base_url = environment.host or "http://localhost:8080"
    login = requests.post(
        f"{base_url}/v1/auth/login",
        json={"email": GESTOR_EMAIL, "password": GESTOR_PASSWORD},
        timeout=10,
    )
    login.raise_for_status()
    _shared_token = login.json()["accessToken"]

    # /v1/vehicles agora pagina (spec de escala) e devolve um envelope PageResponse
    # ({content, page, size, totalElements, totalPages}), não mais uma lista crua — size=500
    # aqui é só pra montar um pool de IDs razoável pro teste, nada a ver com o size=20 que o
    # front usa de verdade.
    vehicles = requests.get(
        f"{base_url}/v1/vehicles",
        params={"page": 0, "size": 500},
        headers={"Authorization": f"Bearer {_shared_token}"},
        timeout=30,
    )
    vehicles.raise_for_status()
    _shared_vehicle_ids = [v["id"] for v in vehicles.json()["content"]]
    print(f"==> Login ok. {len(_shared_vehicle_ids)} veículos carregados pro pool de IDs.")


class GestorUser(HttpUser):
    """Simula um gestor de frota logado navegando pelo painel — leitura pesa mais que
    escrita porque é isso que o uso real faz (poucos lançamentos de despesa pra cada
    tela aberta de dashboard/veículo/relatório)."""

    wait_time = between(0.5, 2.5)

    def on_start(self):
        if _shared_token is None:
            raise RuntimeError("Login inicial (test_start) não rodou — veja _login_once.")
        self.client.headers.update({"Authorization": f"Bearer {_shared_token}"})

    def _random_vehicle_id(self) -> str | None:
        return random.choice(_shared_vehicle_ids) if _shared_vehicle_ids else None

    @task(3)
    def list_vehicles(self):
        # Paginado (size=20 default, igual ao front) desde a correção de escala — antes
        # devolvia a frota inteira; comparar este run com um anterior ao fix mede o efeito
        # real da paginação sob a mesma carga.
        self.client.get("/v1/vehicles", name="/v1/vehicles [list]")

    @task(2)
    def vehicle_detail(self):
        vid = self._random_vehicle_id()
        if vid:
            self.client.get(f"/v1/vehicles/{vid}", name="/v1/vehicles/:id")

    @task(4)
    def cost_summary(self):
        # Endpoint que ganhou o cálculo de custoPorKm sobre km rodado desde o cadastro
        # (migration V24) — prioridade alta aqui de propósito.
        vid = self._random_vehicle_id()
        if vid:
            self.client.get(f"/v1/vehicles/{vid}/cost-summary", name="/v1/vehicles/:id/cost-summary")

    @task(2)
    def fleet_expenses(self):
        # Também paginado agora (era o segundo maior gargalo antes do fix, com 10k+
        # linhas devolvidas de uma vez).
        self.client.get("/v1/expenses", name="/v1/expenses [list]")

    @task(1)
    def expenses_summary(self):
        to = date.today()
        frm = to - timedelta(days=90)
        self.client.get(
            f"/v1/expenses/summary?from={frm.isoformat()}&to={to.isoformat()}",
            name="/v1/expenses/summary",
        )

    @task(1)
    def cost_trend(self):
        # Cacheado 60s por tenant (CacheConfig.CACHE_COST_TREND) — sob carga concorrente
        # dá pra ver o cache absorvendo a maior parte das chamadas.
        self.client.get("/v1/vehicles/cost-trend", name="/v1/vehicles/cost-trend")

    @task(1)
    def maintenance_summary(self):
        self.client.get("/v1/reports/maintenance-summary", name="/v1/reports/maintenance-summary")

    @task(1)
    def create_expense(self):
        vid = self._random_vehicle_id()
        if not vid:
            return
        payload = {
            "categoria": random.choice(["MANUTENCAO", "PEDAGIO", "OUTRO"]),
            "valor": round(random.uniform(20, 400), 2),
            "descricao": "Despesa gerada pelo teste de carga",
            "data": date.today().isoformat(),
        }
        self.client.post(
            f"/v1/vehicles/{vid}/costs",
            json=payload,
            name="/v1/vehicles/:id/costs [create]",
        )
