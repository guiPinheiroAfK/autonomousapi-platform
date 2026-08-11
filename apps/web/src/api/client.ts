/**
 * Client HTTP único do web. REGRA DE OURO (spec 01): o frontend fala SÓ com o
 * core-api. Não existe, e não deve existir, um client apontando para o geo-api
 * ou para qualquer serviço interno — auth, billing e rate limit vivem no core-api.
 *
 * Todas as chamadas passam por `/api`, que o Vite (dev) e o reverse proxy (prod)
 * roteiam para o core-api. Quando os tipos forem gerados a partir do OpenAPI do
 * core-api (ver packages/shared-types, ADR 0003), as respostas passam a ser tipadas.
 */
const BASE = '/api';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    throw new Error(`core-api ${res.status} em ${path}`);
  }
  return (await res.json()) as T;
}

export const coreApi = {
  /** Health agregado do core-api (que internamente checa o geo-api). */
  health: () => request<{ status: string; services?: Record<string, string> }>('/v1/health'),
};
