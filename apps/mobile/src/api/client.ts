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

// Perfil e veículo do próprio motorista (spec 07, /v1/me/*). Deliberadamente sem
// nota/avaliação — DriverProfileResponse não tem esse campo no backend, não há o que
// vazar aqui (spec 06/07: motorista nunca vê a própria avaliação).
export interface DriverProfileResponse {
  id: string;
  name: string;
  cnh: string;
  cnhValidade: string | null;
  phone: string | null;
}

export interface MyVehicleResponse {
  id: string;
  driverId: string;
  vehicleId: string;
  plate: string;
  brand: string;
  model: string;
  startedAt: string;
}

export interface WorkOrderResponse {
  id: string;
  numero: string;
  tipo: string;
  status: string;
  prioridade: string;
  descricaoProblema: string;
  dataAbertura: string;
  previsaoConclusao: string | null;
}

export interface VehicleIncidentRequest {
  data: string;
  severidade: 'LEVE' | 'MODERADA' | 'GRAVE';
  descricao?: string;
  custoReparo?: number;
}

export interface VehicleIncidentResponse {
  id: string;
  vehicleId: string;
  data: string;
  severidade: string;
  descricao: string | null;
  custoReparo: number | null;
}

// Mini-chat gestor↔motorista (spec 07, ADR 0015). O app do motorista não confirma
// sync-cursor — isso é gestor-only, o motorista não retém histórico longo local.
export interface ChatConversationResponse {
  id: string;
  driverId: string;
  driverName: string;
  vehicleId: string | null;
  vehiclePlate: string | null;
  createdAt: string;
}

export interface ChatMessageResponse {
  id: string;
  conversationId: string;
  senderUserId: string;
  body: string;
  sentAt: string;
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
  // Corpo vazio não é só 204: um controller que devolve null (ex. "sem designação
  // ativa") também sai como 200 com Content-Length 0 — res.json() quebraria nesse
  // caso. Ler como texto primeiro cobre os dois de uma vez.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
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

  // Superfície do próprio motorista (spec 07, /v1/me/*) — tudo aqui é escopado pelo
  // token, nunca por um id que o app manda (ADR 0013).
  my: {
    profile: () => request<DriverProfileResponse>('/v1/me/profile'),
    /** Designação ativa — null se o motorista não tem veículo designado no momento. */
    vehicle: () => request<MyVehicleResponse | null>('/v1/me/vehicle'),
    workOrders: () => request<WorkOrderResponse[]>('/v1/me/vehicle/work-orders'),
    /** O vehicleId nunca é informado aqui — vem da designação ativa, resolvida no servidor. */
    reportIncident: (body: VehicleIncidentRequest) =>
      request<VehicleIncidentResponse>('/v1/me/incidents', { method: 'POST', body: JSON.stringify(body) }),
  },

  chat: {
    listConversations: () => request<ChatConversationResponse[]>('/v1/chat/conversations'),
    listMessages: (conversationId: string) =>
      request<ChatMessageResponse[]>(`/v1/chat/conversations/${conversationId}/messages`),
    sendMessage: (conversationId: string, body: string) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ body }),
      }),
  },

  push: {
    registerDevice: (token: string, plataforma: 'ANDROID' | 'IOS') =>
      request<void>('/v1/push/devices', { method: 'POST', body: JSON.stringify({ token, plataforma }) }),
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
