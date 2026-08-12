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
export type VehicleMaintenanceAlertResponse = Schemas['VehicleMaintenanceAlertResponse'];
export type MonthlyCostResponse = Schemas['MonthlyCostResponse'];
export type SubscriptionResponse = Schemas['SubscriptionResponse'];
export type CheckoutSessionResponse = Schemas['CheckoutSessionResponse'];
export type DriverLicenseAlertResponse = Schemas['DriverLicenseAlertResponse'];
export type TokenResponse = Schemas['TokenResponse'];
export type UserResponse = Schemas['UserResponse'];
export type LoginRequest = Schemas['LoginRequest'];
export type SignupRequest = Schemas['SignupRequest'];
export type ApiError = { code: string; message: string };

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

/** Chamado pelo AuthContext ao logar/deslogar — mantém o client sem depender de React. */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

/**
 * Chamado pelo AuthContext para reagir a 401 (token expirado/inválido) limpando a
 * sessão automaticamente. O access token dura 15min (app.jwt.access-ttl-minutes no
 * core-api) e o front ainda não implementa refresh silencioso — sem isso, uma tela
 * ficava com erro vermelho em vez de simplesmente voltar pro login.
 */
export function setUnauthorizedHandler(fn: (() => void) | null): void {
  onUnauthorized = fn;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  const res = await fetch(`${BASE}${path}`, { ...init, headers: { ...headers, ...init?.headers } });

  if (!res.ok) {
    if (res.status === 401) onUnauthorized?.();
    const body = (await res.json().catch(() => null)) as ApiError | null;
    throw new Error(body?.message ?? `core-api ${res.status} em ${path}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** Baixa um arquivo autenticado (ex.: CSV de relatório) disparando o download no navegador. */
async function downloadFile(path: string, filename: string): Promise<void> {
  const headers: Record<string, string> = {};
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  const res = await fetch(`${BASE}${path}`, { headers });
  if (!res.ok) {
    if (res.status === 401) onUnauthorized?.();
    throw new Error(`core-api ${res.status} em ${path}`);
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
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
    maintenanceDue: () => request<VehicleMaintenanceAlertResponse[]>('/v1/vehicles/maintenance-due'),
    costTrend: () => request<MonthlyCostResponse[]>('/v1/vehicles/cost-trend'),
  },

  drivers: {
    list: () => request<DriverResponse[]>('/v1/drivers'),
    create: (body: DriverRequest) =>
      request<DriverResponse>('/v1/drivers', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: DriverRequest) =>
      request<DriverResponse>(`/v1/drivers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/drivers/${id}`, { method: 'DELETE' }),
    licenseExpiring: () => request<DriverLicenseAlertResponse[]>('/v1/drivers/license-expiring'),
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

  reports: {
    exportCostsCsv: () => downloadFile('/v1/reports/costs.csv', 'relatorio-custos.csv'),
  },

  billing: {
    subscription: () => request<SubscriptionResponse>('/v1/billing/subscription'),
    checkout: () => request<CheckoutSessionResponse>('/v1/billing/checkout', { method: 'POST' }),
  },
};
