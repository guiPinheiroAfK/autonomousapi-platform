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
export type FleetCostEntryResponse = Schemas['FleetCostEntryResponse'];
export type SubscriptionResponse = Schemas['SubscriptionResponse'];
export type CheckoutSessionResponse = Schemas['CheckoutSessionResponse'];
export type DriverLicenseAlertResponse = Schemas['DriverLicenseAlertResponse'];
export type TokenResponse = Schemas['TokenResponse'];
export type UserResponse = Schemas['UserResponse'];
export type LoginRequest = Schemas['LoginRequest'];
export type SignupRequest = Schemas['SignupRequest'];
export type SignupResponse = Schemas['SignupResponse'];
export type VerifyEmailRequest = Schemas['VerifyEmailRequest'];
export type ResendVerificationRequest = Schemas['ResendVerificationRequest'];
export type ForgotPasswordRequest = Schemas['ForgotPasswordRequest'];
export type ResetPasswordRequest = Schemas['ResetPasswordRequest'];
export type WorkOrderRequest = Schemas['WorkOrderRequest'];
export type WorkOrderResponse = Schemas['WorkOrderResponse'];
export type WorkOrderItemRequest = Schemas['WorkOrderItemRequest'];
export type WorkOrderReportResponse = Schemas['WorkOrderReportResponse'];
export type DriverRatingRequest = Schemas['DriverRatingRequest'];
export type DriverRatingResponse = Schemas['DriverRatingResponse'];
export type DriverRatingSummaryResponse = Schemas['DriverRatingSummaryResponse'];
export type VehicleMarketValueRequest = Schemas['VehicleMarketValueRequest'];
export type VehicleMarketValueResponse = Schemas['VehicleMarketValueResponse'];
export type VehicleIncidentRequest = Schemas['VehicleIncidentRequest'];
export type VehicleIncidentResponse = Schemas['VehicleIncidentResponse'];
export type VehicleConditionScoreResponse = Schemas['VehicleConditionScoreResponse'];
export type AffiliatePartnerResponse = Schemas['AffiliatePartnerResponse'];
export type AffiliateClickResponse = Schemas['AffiliateClickResponse'];
export type DriverInviteResponse = Schemas['DriverInviteResponse'];
export type DriverAssignmentResponse = Schemas['DriverAssignmentResponse'];
export type AssignVehicleRequest = Schemas['AssignVehicleRequest'];
export type NotifyDriverRequest = Schemas['NotifyDriverRequest'];
export type ChatConversationResponse = Schemas['ChatConversationResponse'];
export type ChatMessageResponse = Schemas['ChatMessageResponse'];
export type CreateConversationRequest = Schemas['CreateConversationRequest'];
export type SendMessageRequest = Schemas['SendMessageRequest'];
export type SyncCursorRequest = Schemas['SyncCursorRequest'];
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
    // Não devolve tokens (ADR 0011): a conta nasce desabilitada até confirmar o e-mail.
    signup: (body: SignupRequest) =>
      request<SignupResponse>('/v1/auth/signup', { method: 'POST', body: JSON.stringify(body) }),
    verifyEmail: (body: VerifyEmailRequest) =>
      request<TokenResponse>('/v1/auth/verify-email', { method: 'POST', body: JSON.stringify(body) }),
    resendVerification: (body: ResendVerificationRequest) =>
      request<void>('/v1/auth/resend-verification', { method: 'POST', body: JSON.stringify(body) }),
    forgotPassword: (body: ForgotPasswordRequest) =>
      request<void>('/v1/auth/forgot-password', { method: 'POST', body: JSON.stringify(body) }),
    resetPassword: (body: ResetPasswordRequest) =>
      request<void>('/v1/auth/reset-password', { method: 'POST', body: JSON.stringify(body) }),
    me: () => request<UserResponse>('/v1/auth/me'),
  },

  vehicles: {
    list: () => request<VehicleResponse[]>('/v1/vehicles'),
    get: (id: string) => request<VehicleResponse>(`/v1/vehicles/${id}`),
    create: (body: VehicleRequest) =>
      request<VehicleResponse>('/v1/vehicles', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: VehicleRequest) =>
      request<VehicleResponse>(`/v1/vehicles/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/vehicles/${id}`, { method: 'DELETE' }),
    maintenanceDue: () => request<VehicleMaintenanceAlertResponse[]>('/v1/vehicles/maintenance-due'),
    costTrend: () => request<MonthlyCostResponse[]>('/v1/vehicles/cost-trend'),
    /** Custos da frota inteira em UMA requisição (evita 1+N por veículo). */
    fleetCosts: (category?: 'COMBUSTIVEL' | 'MANUTENCAO' | 'OUTRO') =>
      request<FleetCostEntryResponse[]>(
        `/v1/vehicles/costs${category ? `?category=${category}` : ''}`,
      ),
  },

  drivers: {
    list: () => request<DriverResponse[]>('/v1/drivers'),
    create: (body: DriverRequest) =>
      request<DriverResponse>('/v1/drivers', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: DriverRequest) =>
      request<DriverResponse>(`/v1/drivers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/drivers/${id}`, { method: 'DELETE' }),
    licenseExpiring: () => request<DriverLicenseAlertResponse[]>('/v1/drivers/license-expiring'),
    /** Envia o convite de acesso ao app (ADR 0013). Exige e-mail cadastrado no motorista. */
    invite: (id: string) => request<DriverInviteResponse>(`/v1/drivers/${id}/invite`, { method: 'POST' }),
    /** Designação de veículo (ADR 0014). */
    assign: (id: string, body: AssignVehicleRequest) =>
      request<DriverAssignmentResponse>(`/v1/drivers/${id}/assignment`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    endAssignment: (id: string) => request<void>(`/v1/drivers/${id}/assignment/end`, { method: 'POST' }),
    activeAssignment: (id: string) => request<DriverAssignmentResponse | null>(`/v1/drivers/${id}/assignment`),
    /** "Aviso do gestor" via push (ADR 0016). */
    notify: (id: string, body: NotifyDriverRequest) =>
      request<void>(`/v1/drivers/${id}/notify`, { method: 'POST', body: JSON.stringify(body) }),
  },

  /** Avaliação manual de motorista (spec 06) — todo endpoint é GESTOR_FROTA/ADMIN no core-api. */
  driverRatings: {
    list: (driverId: string) => request<DriverRatingResponse[]>(`/v1/drivers/${driverId}/ratings`),
    summary: (driverId: string) =>
      request<DriverRatingSummaryResponse>(`/v1/drivers/${driverId}/ratings/summary`),
    create: (driverId: string, body: DriverRatingRequest) =>
      request<DriverRatingResponse>(`/v1/drivers/${driverId}/ratings`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
  },

  /** Valor de mercado/FIPE (spec 06) — lançamento manual, ver ADR do backend. */
  vehicleMarketValue: {
    /** null quando o veículo ainda não tem nenhum valor lançado (204 sem corpo). */
    latest: (vehicleId: string) =>
      request<VehicleMarketValueResponse | null>(`/v1/vehicles/${vehicleId}/market-value`),
    record: (vehicleId: string, body: VehicleMarketValueRequest) =>
      request<VehicleMarketValueResponse>(`/v1/vehicles/${vehicleId}/market-value`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
  },

  /** Sinistro e condição do veículo (spec 06). */
  vehicleCondition: {
    score: (vehicleId: string) =>
      request<VehicleConditionScoreResponse>(`/v1/vehicles/${vehicleId}/condition-score`),
    incidents: (vehicleId: string) =>
      request<VehicleIncidentResponse[]>(`/v1/vehicles/${vehicleId}/incidents`),
    registerIncident: (vehicleId: string, body: VehicleIncidentRequest) =>
      request<VehicleIncidentResponse>(`/v1/vehicles/${vehicleId}/incidents`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
  },

  /** Afiliados (spec 06) — catálogo é global, gerido pela AutonomousAPI, não por tenant. */
  affiliates: {
    listPartners: () => request<AffiliatePartnerResponse[]>('/v1/affiliates/partners'),
    click: (partnerId: string, vehicleId?: string) =>
      request<AffiliateClickResponse>(`/v1/affiliates/partners/${partnerId}/click`, {
        method: 'POST',
        body: JSON.stringify({ vehicleId }),
      }),
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

  workOrders: {
    list: (vehicleId?: string) =>
      request<WorkOrderResponse[]>(`/v1/work-orders${vehicleId ? `?vehicleId=${vehicleId}` : ''}`),
    create: (body: WorkOrderRequest) =>
      request<WorkOrderResponse>('/v1/work-orders', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: WorkOrderRequest) =>
      request<WorkOrderResponse>(`/v1/work-orders/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/work-orders/${id}`, { method: 'DELETE' }),
  },

  reports: {
    exportCostsCsv: () => downloadFile('/v1/reports/costs.csv', 'relatorio-custos.csv'),
    maintenanceSummary: () => request<WorkOrderReportResponse>('/v1/reports/maintenance-summary'),
  },

  billing: {
    subscription: () => request<SubscriptionResponse>('/v1/billing/subscription'),
    checkout: () => request<CheckoutSessionResponse>('/v1/billing/checkout', { method: 'POST' }),
  },

  /** Mini-chat gestor↔motorista (ADR 0015). Aberto a gestor e motorista — isolamento é no backend. */
  chat: {
    listConversations: () => request<ChatConversationResponse[]>('/v1/chat/conversations'),
    createConversation: (body: CreateConversationRequest) =>
      request<ChatConversationResponse>('/v1/chat/conversations', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    listMessages: (conversationId: string) =>
      request<ChatMessageResponse[]>(`/v1/chat/conversations/${conversationId}/messages`),
    sendMessage: (conversationId: string, body: SendMessageRequest) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/messages`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    /** Gestor-only: confirma que o device já persistiu localmente até syncedAt (habilita a limpeza no servidor). */
    syncCursor: (body: SyncCursorRequest) =>
      request<void>('/v1/chat/sync-cursor', { method: 'POST', body: JSON.stringify(body) }),
  },
};
