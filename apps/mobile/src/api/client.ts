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

/** Envelope de paginação do core-api (PageResponse<T> no backend) — mesma forma usada
 *  no cliente web. GET /v1/vehicles e GET /v1/trips devolvem isso, não um array puro. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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

export interface ChargingStationItem {
  id: string;
  name: string | null;
  address: string | null;
  connectorType: string | null;
  powerKw: number | null;
  lat: number;
  lon: number;
  status: 'DISPONIVEL' | 'OCUPADO' | 'FORA_DE_SERVICO' | 'DESCONHECIDO';
}

export interface ChargingStationsResponse {
  providerAvailable: boolean;
  stations: ChargingStationItem[];
}

// Rota multi-parada designada ao motorista (spec 02/07, item 8) — mesma forma do
// RoutePlanResponse/RouteStopResponse do core-api, ver RoutePlansPage.tsx (web) pro
// mesmo consumo. categoria TRANSFER renderiza como cartão único; ROTA como lista.
export type RouteStopType = 'COLETA' | 'ENTREGA';
export type RoutePlanStatusValue = 'PLANEJADA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
export type RouteCategoriaValue = 'ROTA' | 'TRANSFER';

export interface RouteStopResponse {
  id: string;
  tipo: RouteStopType;
  label: string;
  lat: number;
  lon: number;
  collectionPointId: string | null;
  janelaInicio: string | null;
  janelaFim: string | null;
  ordemSugerida: number;
  ordemRealExecutada: number | null;
  concluidaEm: string | null;
}

export interface RoutePlanResponse {
  id: string;
  driverId: string | null;
  driverName: string | null;
  vehicleId: string | null;
  vehiclePlate: string | null;
  status: RoutePlanStatusValue;
  categoria: RouteCategoriaValue;
  dataExecucao: string;
  valor: number | null;
  custoEstimado: number | null;
  margemRealizada: number | null;
  createdAt: string;
  stops: RouteStopResponse[];
}

let authToken: string | null = null;

/** Chamado pelo App ao logar/restaurar sessão/deslogar — client sem depender de estado React. */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

let refreshTokenValue: string | null = null;
let onTokensRefreshed: ((accessToken: string, refreshToken: string) => void) | null = null;
let refreshInFlight: Promise<boolean> | null = null;

/** Chamado pelo App ao logar/restaurar sessão/deslogar — espelha `setAuthToken`. */
export function setRefreshToken(token: string | null): void {
  refreshTokenValue = token;
}

/** Chamado pelo App pra persistir o par novo de tokens no SecureStore sempre que o
 *  refresh silencioso (abaixo) renova a sessão sozinha, sem o motorista perceber. */
export function setTokensRefreshedHandler(
  fn: ((accessToken: string, refreshToken: string) => void) | null,
): void {
  onTokensRefreshed = fn;
}

/**
 * Refresh silencioso — mesmo padrão do client web (`apps/web/src/api/client.ts`). Sem
 * isso, access token de 15min expirando com o app em segundo plano (ou reaberto depois de
 * um tempo fechado) jogava pro login de novo mesmo com um refresh token de 30 dias válido
 * guardado à toa no SecureStore — achado nesta sessão (grava um dado que nunca era lido de
 * volta). `fetch` cru, não `request`: `request` chamando refresh chamando `request` de
 * novo é recursão sem necessidade, e o endpoint de refresh já é público (sem Bearer).
 *
 * Dedup via `refreshInFlight`: o backend ROTACIONA o refresh token a cada uso (revoga o
 * antigo, emite um novo) — duas chamadas tomando 401 ao mesmo tempo, cada uma tentando seu
 * próprio refresh, faria a segunda chegar com token já revogado pela primeira e falhar à
 * toa. Uma promise compartilhada garante um refresh só por N requisições simultâneas.
 */
async function refreshTokens(): Promise<boolean> {
  if (!refreshTokenValue) return false;
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${CORE_API_URL}/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: refreshTokenValue }),
        });
        if (!res.ok) return false;
        const data = (await res.json()) as TokenResponse;
        if (!data.accessToken || !data.refreshToken) return false;
        authToken = data.accessToken;
        refreshTokenValue = data.refreshToken;
        onTokensRefreshed?.(data.accessToken, data.refreshToken);
        return true;
      } catch {
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

async function request<T>(path: string, init?: RequestInit, isRetryAfterRefresh = false): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  const res = await fetch(`${CORE_API_URL}${path}`, { ...init, headers: { ...headers, ...init?.headers } });
  if (!res.ok) {
    if (res.status === 401 && !isRetryAfterRefresh && (await refreshTokens())) {
      return request<T>(path, init, true);
    }
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
    // /v1/vehicles é paginado no backend (PageResponse<VehicleResponse>) — corrigido aqui
    // (achado da auditoria de cleanup: o tipo antigo `VehicleResponse[]` não batia com a
    // resposta real, então `vehicles.map(...)` na tela de viagem quebraria em runtime
    // assim que a frota do tenant passasse a existir de verdade). Size alto cobre a frota
    // inteira numa request só — a mesma lógica do cliente web.
    list: () => request<PageResponse<VehicleResponse>>('/v1/vehicles?size=500'),
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
    /** Motorista-only: solicita cancelamento da rota ativa (ADR 0021) — nunca cancela
     *  sozinho, só avisa o gestor, que decide. */
    solicitarCancelamento: (conversationId: string) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/route-plan/solicitar-cancelamento`, {
        method: 'POST',
      }),
    /** Motorista-only: solicita passar a rota ativa pra outra pessoa (ADR 0021). */
    solicitarTroca: (conversationId: string) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/route-plan/solicitar-troca`, {
        method: 'POST',
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
    // Paginado (cleanup de performance) — o histórico de viagens cresce sem limite. A
    // viagem em andamento, se houver, está sempre na primeira página (ordenação por
    // started_at desc no backend garante isso).
    list: () => request<PageResponse<TripResponse>>('/v1/trips'),
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

  chargingStations: {
    list: () => request<ChargingStationsResponse>('/v1/charging-stations'),
  },

  routePlans: {
    /** Rota ativa (PLANEJADA ou EM_ANDAMENTO) do motorista do token — null se não houver. */
    active: () => request<RoutePlanResponse | null>('/v1/routes/plans/active'),
    completeStop: (stopId: string) =>
      request<RouteStopResponse>(`/v1/routes/plans/stops/${stopId}/complete`, { method: 'POST' }),
  },
};
