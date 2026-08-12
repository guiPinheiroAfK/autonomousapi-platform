import { CORE_API_URL } from '../config';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface UserResponse {
  id: string;
  tenantId: string;
  email: string;
  role: string;
}

export interface VehicleResponse {
  id: string;
  plate: string;
  brand: string;
  model: string;
  status: string;
}

export interface TripResponse {
  id: string;
  vehicleId: string;
  status: 'EM_ANDAMENTO' | 'FINALIZADA';
  startedAt: string;
  endedAt: string | null;
}

export interface SubmitPingRequest {
  recordedAt: string;
  lat: number;
  lon: number;
  speed?: number;
  heading?: number;
  accuracy?: number;
}

export interface SubmitPingBatchResponse {
  accepted: number;
  received: number;
}

let authToken: string | null = null;

/** Chamado pelo App ao logar/restaurar sessão/deslogar — client sem depender de estado React. */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  const res = await fetch(`${CORE_API_URL}${path}`, { ...init, headers: { ...headers, ...init?.headers } });
  if (!res.ok) {
    throw new Error(`core-api ${res.status} em ${path}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// Client único: mobile fala SÓ com o core-api (spec 01) — nunca com o geo-api direto.
// Tipos migram para @autonomousapi/shared-types quando fizer sentido compartilhar com o web
// (ADR 0003); por ora, mínimos e locais, só o que o app do motorista usa.
export const coreApi = {
  login: (email: string, password: string) =>
    request<TokenResponse>('/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  me: () => request<UserResponse>('/v1/auth/me'),

  vehicles: {
    list: () => request<VehicleResponse[]>('/v1/vehicles'),
  },

  trips: {
    start: (vehicleId: string) =>
      request<TripResponse>('/v1/trips', { method: 'POST', body: JSON.stringify({ vehicleId }) }),
    stop: (tripId: string) => request<TripResponse>(`/v1/trips/${tripId}/stop`, { method: 'POST' }),
    list: () => request<TripResponse[]>('/v1/trips'),
    /**
     * Manda um lote da fila em uma requisição; devolve quantos foram aceitos.
     * O app sempre envia em lote — o endpoint unitário do core-api (POST /pings) segue
     * existindo para outros consumidores, mas não é usado aqui.
     */
    submitPingBatch: (tripId: string, pings: SubmitPingRequest[]) =>
      request<SubmitPingBatchResponse>(`/v1/trips/${tripId}/pings/batch`, {
        method: 'POST',
        body: JSON.stringify({ pings }),
      }),
  },
};
