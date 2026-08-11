import type { components } from '@autonomousapi/shared-types/core-api';

/**
 * Client HTTP único do web. REGRA DE OURO (spec 01): o frontend fala SÓ com o
 * core-api. Não existe, e não deve existir, um client apontando para o geo-api
 * ou para qualquer serviço interno — auth, billing e rate limit vivem no core-api.
 *
 * Todas as chamadas passam por `/api`, que o Vite (dev) e o reverse proxy (prod)
 * roteiam para o core-api. Tipos de request/response vêm de @autonomousapi/shared-types,
 * gerados a partir do OpenAPI do core-api (ADR 0003) — nunca digitados à mão aqui.
 */
const BASE = '/api';

type Schemas = components['schemas'];
export type VehicleResponse = Schemas['VehicleResponse'];
export type VehicleRequest = Schemas['VehicleRequest'];
export type DriverResponse = Schemas['DriverResponse'];
export type DriverRequest = Schemas['DriverRequest'];
export type VehicleCostEntryResponse = Schemas['VehicleCostEntryResponse'];
export type VehicleCostEntryRequest = Schemas['VehicleCostEntryRequest'];
export type VehicleCostSummaryResponse = Schemas['VehicleCostSummaryResponse'];
export type TokenResponse = Schemas['TokenResponse'];
export type UserResponse = Schemas['UserResponse'];
export type LoginRequest = Schemas['LoginRequest'];
export type SignupRequest = Schemas['SignupRequest'];
export type ApiError = { code: string; message: string };

let authToken: string | null = null;

/** Chamado pelo AuthContext ao logar/deslogar — mantém o client sem depender de React. */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  const res = await fetch(`${BASE}${path}`, { ...init, headers: { ...headers, ...init?.headers } });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as ApiError | null;
    throw new Error(body?.message ?? `core-api ${res.status} em ${path}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const coreApi = {
  health: () => request<{ status: string; services?: Record<string, string> }>('/v1/health'),

  auth: {
    login: (body: LoginRequest) =>
      request<TokenResponse>('/v1/auth/login', { method: 'POST', body: JSON.stringify(body) }),
    signup: (body: SignupRequest) =>
      request<TokenResponse>('/v1/auth/signup', { method: 'POST', body: JSON.stringify(body) }),
    me: () => request<UserResponse>('/v1/auth/me'),
  },

  vehicles: {
    list: () => request<VehicleResponse[]>('/v1/vehicles'),
    create: (body: VehicleRequest) =>
      request<VehicleResponse>('/v1/vehicles', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: VehicleRequest) =>
      request<VehicleResponse>(`/v1/vehicles/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/vehicles/${id}`, { method: 'DELETE' }),
  },

  drivers: {
    list: () => request<DriverResponse[]>('/v1/drivers'),
    create: (body: DriverRequest) =>
      request<DriverResponse>('/v1/drivers', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: DriverRequest) =>
      request<DriverResponse>(`/v1/drivers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/drivers/${id}`, { method: 'DELETE' }),
  },

  vehicleCosts: {
    list: (vehicleId: string) =>
      request<VehicleCostEntryResponse[]>(`/v1/vehicles/${vehicleId}/costs`),
    add: (vehicleId: string, body: VehicleCostEntryRequest) =>
      request<VehicleCostEntryResponse>(`/v1/vehicles/${vehicleId}/costs`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    remove: (vehicleId: string, costId: string) =>
      request<void>(`/v1/vehicles/${vehicleId}/costs/${costId}`, { method: 'DELETE' }),
    summary: (vehicleId: string) =>
      request<VehicleCostSummaryResponse>(`/v1/vehicles/${vehicleId}/cost-summary`),
  },
};
