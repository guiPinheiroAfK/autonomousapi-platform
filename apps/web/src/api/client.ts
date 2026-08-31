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
export type ExpenseEntryResponse = Schemas['ExpenseEntryResponse'];
export type ExpenseEntryRequest = Schemas['ExpenseEntryRequest'];
export type ExpenseSummaryResponse = Schemas['ExpenseSummaryResponse'];
export type FleetExpenseEntryResponse = Schemas['FleetExpenseEntryResponse'];
export type CategoryTotal = Schemas['CategoryTotal'];
export type ExpenseCategory =
  | 'COMBUSTIVEL'
  | 'MANUTENCAO'
  | 'SEGURO'
  | 'IPVA'
  | 'MULTA'
  | 'PEDAGIO'
  | 'LAVAGEM'
  | 'OUTRO';
export type BudgetRequest = Schemas['BudgetRequest'];
export type BudgetResponse = Schemas['BudgetResponse'];
export type VehicleMaintenanceAlertResponse = Schemas['VehicleMaintenanceAlertResponse'];
export type MonthlyCostResponse = Schemas['MonthlyCostResponse'];
export type SubscriptionResponse = Schemas['SubscriptionResponse'];
export type CheckoutSessionResponse = Schemas['CheckoutSessionResponse'];
export type BillingPortalSessionResponse = Schemas['BillingPortalSessionResponse'];
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
export type ChargingStationItem = Schemas['ChargingStationItem'];
export type ChargingStationsResponse = Schemas['ChargingStationsResponse'];
export type RouteResponse = Schemas['RouteResponse'];
export type RouteStep = Schemas['RouteStep'];
export type PlaceResponse = Schemas['PlaceResponse'];
export type ChatConversationResponse = Schemas['ChatConversationResponse'];
export type ChatMessageResponse = Schemas['ChatMessageResponse'];
export type ChatReactionResponse = Schemas['ChatReactionResponse'];
export type CreateConversationRequest = Schemas['CreateConversationRequest'];
export type SendMessageRequest = Schemas['SendMessageRequest'];
export type SendRoutePlanRequest = Schemas['SendRoutePlanRequest'];
export type SyncCursorRequest = Schemas['SyncCursorRequest'];
export type AcceptInviteRequest = Schemas['AcceptInviteRequest'];
export type StopInput = Schemas['StopInput'];
export type RoutePlanResponse = Schemas['RoutePlanResponse'];
export type RouteStopResponse = Schemas['RouteStopResponse'];
export type SuggestOrderRequest = Schemas['SuggestOrderRequest'];
export type CreateRoutePlanRequest = Schemas['CreateRoutePlanRequest'];
export type AssignDriverRequest = Schemas['AssignDriverRequest'];
export type RouteCategoria = 'ROTA' | 'TRANSFER';
export type CollectionPointRequest = Schemas['CollectionPointRequest'];
export type CollectionPointResponse = Schemas['CollectionPointResponse'];
export type PassengerRequest = Schemas['PassengerRequest'];
export type PassengerResponse = Schemas['PassengerResponse'];
export type TeamRole = Schemas['CreateTeamInviteRequest']['role'];
export type CreateTeamInviteRequest = Schemas['CreateTeamInviteRequest'];
export type ChangeTeamRoleRequest = Schemas['ChangeTeamRoleRequest'];
export type TeamMemberResponse = Schemas['TeamMemberResponse'];
export type TeamInviteResponse = Schemas['TeamInviteResponse'];
export type TeamOverviewResponse = Schemas['TeamOverviewResponse'];
export type DriverProfileResponse = Schemas['DriverProfileResponse'];
export type TripResponse = Schemas['TripResponse'];
export type NotificationResponse = Schemas['NotificationResponse'];
export type ApiError = { code: string; message: string };

/** Envelope de paginação — usado por endpoints de listagem grandes o bastante pra não
 *  devolver o resultado inteiro de uma vez (ex. /v1/vehicles, /v1/expenses). Definido à mão
 *  em vez de vir de shared-types porque o Springdoc gera um schema nomeado por tipo genérico
 *  (PageResponseVehicleResponse, PageResponseFleetExpenseEntryResponse, ...) — a forma é
 *  sempre a mesma, então um tipo genérico local evita depender do nome gerado por endpoint. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

/**
 * Chamado pelo AuthContext ao logar/deslogar E ao restaurar sessão de um reload (token que
 * já estava salvo no localStorage) — os três casos passam por aqui. Só invalida o cache
 * quando o token É DE VERDADE outro (login novo, logout, troca de usuário): comparar contra
 * `authToken` (variável em memória, sempre nula logo após um F5) sempre pareceria "sessão
 * nova" na restauração, esvaziando o cache a cada reload — exatamente o caso que ele existe
 * pra sobreviver. Por isso o "dono" anterior do cache fica marcado no próprio
 * `sessionStorage` (sobrevive ao F5 do mesmo jeito que o cache), não em memória.
 */
export function setAuthToken(token: string | null): void {
  authToken = token;
  try {
    const previousOwner = sessionStorage.getItem(CACHE_OWNER_KEY);
    if (previousOwner !== token) {
      clearListCache();
      if (token) sessionStorage.setItem(CACHE_OWNER_KEY, token);
    }
  } catch {
    // sessionStorage indisponível — sem cache persistente mesmo, nada a invalidar.
  }
}

/**
 * Cache curto pra listas que várias telas pedem de novo sem o dado ter mudado (ex.
 * `vehicles.list()`/`drivers.list()` abertos direto na página e de novo pra popular um
 * seletor no modal de outra tela). Em `sessionStorage`, não em memória: sobrevive a um F5
 * dentro da mesma aba (o caso mais comum de "recarreguei e ficou tudo lento de novo"), mas
 * nunca atravessa pra outra aba nem sobrevive ao fechar o navegador — sem o risco de
 * persistência longa demais que `localStorage` teria pra dado que já tem TTL curto por
 * natureza. TTL curto o bastante pra nunca mostrar dado visivelmente desatualizado, longo o
 * bastante pra cortar a maioria dos refetch redundantes numa mesma sessão de navegação.
 * Invalidação é por prefixo, chamada nos pontos de mutação abaixo.
 *
 * `sessionStorage` pode lançar (modo privado do Safari, quota estourada) — todo acesso é
 * best-effort: falha vira "sem cache" (sempre busca de novo), nunca quebra a tela.
 */
const LIST_CACHE_PREFIX = 'autonomousapi.listCache.';
const CACHE_OWNER_KEY = `${LIST_CACHE_PREFIX}__owner`;
const LIST_CACHE_TTL_MS = 30_000;
/** Catálogo global gerido pela AutonomousAPI, não pelo tenant — muda por intervenção
 *  manual no banco (ver specs/08), não por ação do usuário logado. TTL bem mais longo. */
const REFERENCE_DATA_TTL_MS = 60 * 60_000;
/** Dado editável pelo usuário (pontos de coleta) ou de provedor externo (pontos de
 *  recarga) — muda mais que o catálogo de afiliados, mas ainda bem menos que listas
 *  operacionais do dia a dia. */
const SLOW_CHANGING_TTL_MS = 5 * 60_000;

function readListCache<T>(key: string): T | undefined {
  try {
    const raw = sessionStorage.getItem(LIST_CACHE_PREFIX + key);
    if (!raw) return undefined;
    const { data, expiresAt } = JSON.parse(raw) as { data: T; expiresAt: number };
    if (expiresAt <= Date.now()) {
      sessionStorage.removeItem(LIST_CACHE_PREFIX + key);
      return undefined;
    }
    return data;
  } catch {
    return undefined;
  }
}

function writeListCache<T>(key: string, data: T, ttlMs: number): void {
  try {
    sessionStorage.setItem(LIST_CACHE_PREFIX + key, JSON.stringify({ data, expiresAt: Date.now() + ttlMs }));
  } catch {
    // sessionStorage indisponível — cache vira no-op, próxima chamada busca direto.
  }
}

/**
 * Requisição em voo compartilhada: se duas telas pedem a MESMA chave ao mesmo tempo (ex.
 * dois widgets que precisam de `vehicles.list()` no mesmo instante, antes da primeira
 * resposta voltar pra popular o cache), a segunda espera a primeira em vez de disparar
 * outra fetch idêntica. Só existe enquanto a promise não resolve — depois disso quem
 * decide servir da cache ou buscar de novo é o `readListCache`/TTL normal.
 */
const inFlightRequests = new Map<string, Promise<unknown>>();

function cachedGet<T>(key: string, fetcher: () => Promise<T>, ttlMs: number = LIST_CACHE_TTL_MS): Promise<T> {
  const cached = readListCache<T>(key);
  if (cached !== undefined) return Promise.resolve(cached);

  const inFlight = inFlightRequests.get(key);
  if (inFlight) return inFlight as Promise<T>;

  const promise = fetcher()
    .then((data) => {
      writeListCache(key, data, ttlMs);
      return data;
    })
    .finally(() => {
      inFlightRequests.delete(key);
    });
  inFlightRequests.set(key, promise);
  return promise;
}

function keysMatching(prefix: string): string[] {
  try {
    const matches: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k?.startsWith(prefix)) matches.push(k);
    }
    return matches;
  } catch {
    return [];
  }
}

function invalidateListCache(prefix: string): void {
  keysMatching(LIST_CACHE_PREFIX + prefix).forEach((k) => {
    try {
      sessionStorage.removeItem(k);
    } catch {
      // idem — melhor esforço
    }
  });
}

function clearListCache(): void {
  invalidateListCache('');
}

/** Chamado pelo AuthContext para reagir a 401 quando nem o refresh silencioso (abaixo)
 *  consegue segurar a sessão — aí sim limpa tudo e volta pro login. */
export function setUnauthorizedHandler(fn: (() => void) | null): void {
  onUnauthorized = fn;
}

let refreshTokenValue: string | null = null;
let onTokensRefreshed: ((accessToken: string, refreshToken: string) => void) | null = null;
let refreshInFlight: Promise<boolean> | null = null;

/** Chamado pelo AuthContext ao logar/deslogar/restaurar sessão — espelha `setAuthToken`. */
export function setRefreshToken(token: string | null): void {
  refreshTokenValue = token;
}

/** Chamado pelo AuthContext pra persistir o par novo de tokens no localStorage sempre que
 *  o refresh silencioso (abaixo) renova a sessão sozinho, sem o usuário perceber. */
export function setTokensRefreshedHandler(
  fn: ((accessToken: string, refreshToken: string) => void) | null,
): void {
  onTokensRefreshed = fn;
}

/**
 * Refresh silencioso (ADR pendente de registrar — achado nesta sessão): o access token dura
 * só 15min (`app.jwt.access-ttl-minutes`), e sem isso qualquer tela aberta por mais tempo, ou
 * reaberta depois de um tempo fechada, caía direto pro login — "enrolando" pra logar de novo
 * mesmo com um refresh token de 30 dias válido guardado à toa. Usa `fetch` cru, não `request`:
 * `request` chamando refresh chamando `request` de novo é recursão sem necessidade, e o
 * endpoint de refresh já é público (não manda Bearer).
 *
 * Dedup via `refreshInFlight`: o backend ROTACIONA o refresh token a cada uso (revoga o
 * antigo, emite um novo) — se duas requisições tomassem 401 ao mesmo tempo e cada uma tentasse
 * seu próprio refresh, a segunda chegaria com um token já revogado pela primeira e falharia à
 * toa. Uma promise compartilhada garante que N requisições simultâneas rendem UM refresh só.
 */
async function refreshTokens(): Promise<boolean> {
  if (!refreshTokenValue) return false;
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${BASE}/v1/auth/refresh`, {
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

  const res = await fetch(`${BASE}${path}`, { ...init, headers: { ...headers, ...init?.headers } });

  if (!res.ok) {
    if (res.status === 401 && !isRetryAfterRefresh && (await refreshTokens())) {
      return request<T>(path, init, true);
    }
    if (res.status === 401) onUnauthorized?.();
    const body = (await res.json().catch(() => null)) as ApiError | null;
    throw new Error(body?.message ?? `core-api ${res.status} em ${path}`);
  }
  // Corpo vazio não é só 204: um controller que devolve null (ex. GET .../assignment
  // "sem designação ativa") também sai como 200 com Content-Length 0 — res.json()
  // quebraria nesse caso. Ler como texto primeiro cobre os dois de uma vez.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
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
    /** Login ou cadastro via Google, na mesma chamada — o backend decide (e-mail já
     *  cadastrado vira login, novo vira conta nova já habilitada). */
    google: (idToken: string) =>
      request<TokenResponse>('/v1/auth/google', { method: 'POST', body: JSON.stringify({ idToken }) }),
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
    /** Aceite do convite de motorista (ADR 0013) — cria o login MOTORISTA e define a senha. */
    acceptInvite: (body: AcceptInviteRequest) =>
      request<void>('/v1/auth/accept-invite', { method: 'POST', body: JSON.stringify(body) }),
    /** Aceite do convite de equipe (spec 15) — cria o login no papel definido no convite. */
    acceptTeamInvite: (body: AcceptInviteRequest) =>
      request<void>('/v1/auth/accept-team-invite', { method: 'POST', body: JSON.stringify(body) }),
    me: () => request<UserResponse>('/v1/auth/me'),
  },

  vehicles: {
    /** Paginado (size máx. 500 no backend) — a frota inteira não vem mais numa request só,
     *  ver nota de escala no load test (2000 veículos, p99 ~68ms com pico de 330ms sem isso).
     *  `search`/`status` viram filtro no servidor: com a listagem paginada, filtrar em
     *  memória (como antes) "escondia" veículo que caísse fora da página carregada. */
    list: (page = 0, size = 20, search?: string, status?: string) => {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (search) params.set('search', search);
      if (status) params.set('status', status);
      const query = params.toString();
      return cachedGet(`vehicles:${query}`, () =>
        request<PageResponse<VehicleResponse>>(`/v1/vehicles?${query}`),
      );
    },
    get: (id: string) => request<VehicleResponse>(`/v1/vehicles/${id}`),
    create: (body: VehicleRequest) =>
      request<VehicleResponse>('/v1/vehicles', { method: 'POST', body: JSON.stringify(body) }).then((r) => {
        invalidateListCache('vehicles:');
        return r;
      }),
    update: (id: string, body: VehicleRequest) =>
      request<VehicleResponse>(`/v1/vehicles/${id}`, { method: 'PUT', body: JSON.stringify(body) }).then((r) => {
        invalidateListCache('vehicles:');
        return r;
      }),
    remove: (id: string) =>
      request<void>(`/v1/vehicles/${id}`, { method: 'DELETE' }).then((r) => {
        invalidateListCache('vehicles:');
        return r;
      }),
    maintenanceDue: () => request<VehicleMaintenanceAlertResponse[]>('/v1/vehicles/maintenance-due'),
    costTrend: () => request<MonthlyCostResponse[]>('/v1/vehicles/cost-trend'),
  },

  drivers: {
    /** Paginado (cleanup de performance). `size` alto cobre a equipe inteira na
     *  imensa maioria dos tenants numa request só, mesmo padrão de `vehicles.list`. */
    list: (page = 0, size = 200) =>
      cachedGet(`drivers:${page}:${size}`, () =>
        request<PageResponse<DriverResponse>>(`/v1/drivers?page=${page}&size=${size}`),
      ),
    create: (body: DriverRequest) =>
      request<DriverResponse>('/v1/drivers', { method: 'POST', body: JSON.stringify(body) }).then((r) => {
        invalidateListCache('drivers:');
        return r;
      }),
    update: (id: string, body: DriverRequest) =>
      request<DriverResponse>(`/v1/drivers/${id}`, { method: 'PUT', body: JSON.stringify(body) }).then((r) => {
        invalidateListCache('drivers:');
        return r;
      }),
    remove: (id: string) =>
      request<void>(`/v1/drivers/${id}`, { method: 'DELETE' }).then((r) => {
        invalidateListCache('drivers:');
        return r;
      }),
    licenseExpiring: () => request<DriverLicenseAlertResponse[]>('/v1/drivers/license-expiring'),
    /** Envia o convite de acesso ao app (ADR 0013). Exige e-mail cadastrado no motorista. */
    invite: (id: string) =>
      request<DriverInviteResponse>(`/v1/drivers/${id}/invite`, { method: 'POST' }).then((r) => {
        invalidateListCache('drivers:');
        return r;
      }),
    /** Designação de veículo (ADR 0014). */
    assign: (id: string, body: AssignVehicleRequest) =>
      request<DriverAssignmentResponse>(`/v1/drivers/${id}/assignment`, {
        method: 'POST',
        body: JSON.stringify(body),
      }).then((r) => {
        invalidateListCache('drivers:');
        return r;
      }),
    endAssignment: (id: string) =>
      request<void>(`/v1/drivers/${id}/assignment/end`, { method: 'POST' }).then((r) => {
        invalidateListCache('drivers:');
        return r;
      }),
    activeAssignment: (id: string) => request<DriverAssignmentResponse | null>(`/v1/drivers/${id}/assignment`),
    /** "Aviso do gestor" via push (ADR 0016). */
    notify: (id: string, body: NotifyDriverRequest) =>
      request<void>(`/v1/drivers/${id}/notify`, { method: 'POST', body: JSON.stringify(body) }),
  },

  /** Avaliação manual de motorista (spec 06) — todo endpoint é GESTOR_FROTA/ADMIN no core-api. */
  driverRatings: {
    /** Paginado (cleanup de performance) — histórico de avaliações de um motorista
     *  cresce ao longo do tempo, não faz sentido trazer tudo de uma vez. */
    list: (driverId: string, page = 0, size = 20) =>
      request<PageResponse<DriverRatingResponse>>(
        `/v1/drivers/${driverId}/ratings?page=${page}&size=${size}`,
      ),
    summary: (driverId: string) =>
      request<DriverRatingSummaryResponse>(`/v1/drivers/${driverId}/ratings/summary`),
    create: (driverId: string, body: DriverRatingRequest) =>
      request<DriverRatingResponse>(`/v1/drivers/${driverId}/ratings`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    /** Corrige um lançamento errado — o resumo (nota média) é recalculado no backend. */
    remove: (driverId: string, ratingId: string) =>
      request<void>(`/v1/drivers/${driverId}/ratings/${ratingId}`, { method: 'DELETE' }),
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
    /** Corrige um lançamento errado — o score de condição é recalculado no backend. */
    removeIncident: (vehicleId: string, incidentId: string) =>
      request<void>(`/v1/vehicles/${vehicleId}/incidents/${incidentId}`, { method: 'DELETE' }),
  },

  /** Afiliados (spec 06) — catálogo é global, gerido pela AutonomousAPI, não por tenant. */
  affiliates: {
    listPartners: () =>
      cachedGet(
        'affiliates:partners',
        () => request<AffiliatePartnerResponse[]>('/v1/affiliates/partners'),
        REFERENCE_DATA_TTL_MS,
      ),
    click: (partnerId: string, vehicleId?: string) =>
      request<AffiliateClickResponse>(`/v1/affiliates/partners/${partnerId}/click`, {
        method: 'POST',
        body: JSON.stringify({ vehicleId }),
      }),
  },

  /** Roteamento ponto-a-ponto e busca de endereço (spec 02) — ver geo-api/OSRM.
   *  {@code vehicleId} (spec 09) anexa custoEstimado/valorSugerido à resposta quando o
   *  veículo tem consumo e o tenant tem preço de referência cadastrados; nunca falha o
   *  preview por falta deles, só deixa os dois campos null. */
  routes: {
    preview: (fromLat: number, fromLon: number, toLat: number, toLon: number, vehicleId?: string) =>
      request<RouteResponse>(
        `/v1/routes/preview?fromLat=${fromLat}&fromLon=${fromLon}&toLat=${toLat}&toLon=${toLon}` +
          (vehicleId ? `&vehicleId=${vehicleId}` : ''),
      ),
  },

  places: {
    search: (q: string) => request<PlaceResponse[]>(`/v1/places/search?q=${encodeURIComponent(q)}`),
  },

  /** Recarga elétrica (spec 06, item 1) — agregado de provedor externo, ver geo-api. */
  chargingStations: {
    list: (params?: { lat?: number; lon?: number; radiusKm?: number }) => {
      const query = new URLSearchParams();
      if (params?.lat != null) query.set('lat', String(params.lat));
      if (params?.lon != null) query.set('lon', String(params.lon));
      if (params?.radiusKm != null) query.set('radiusKm', String(params.radiusKm));
      const qs = query.toString();
      return cachedGet(
        `chargingStations:${qs}`,
        () => request<ChargingStationsResponse>(`/v1/charging-stations${qs ? `?${qs}` : ''}`),
        SLOW_CHANGING_TTL_MS,
      );
    },
  },

  /** Despesas categorizadas (spec 10) — evolução do antigo "custo por veículo" (spec 05).
   *  Duas famílias de rota: escopada por veículo (uso já existente, ex. VehicleCostsPage)
   *  e fleet-wide (nova, aba "Custos" — despesa pode não ter veículo, ex. seguro corporativo). */
  expenses: {
    list: (vehicleId: string) =>
      request<ExpenseEntryResponse[]>(`/v1/vehicles/${vehicleId}/costs`),
    add: (vehicleId: string, body: ExpenseEntryRequest) =>
      request<ExpenseEntryResponse>(`/v1/vehicles/${vehicleId}/costs`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    remove: (vehicleId: string, costId: string) =>
      request<void>(`/v1/vehicles/${vehicleId}/costs/${costId}`, { method: 'DELETE' }),
    summary: (vehicleId: string) =>
      request<ExpenseSummaryResponse>(`/v1/vehicles/${vehicleId}/cost-summary`),
    /** Fleet-wide, com os dados do veículo já resolvidos (null = despesa de frota, sem veículo).
     *  Paginado (size máx. 500 no backend) — 10 mil lançamentos era o segundo maior gargalo
     *  no load test (p99 ~140ms, picos de 330-370ms). */
    fleetList: (categoria?: ExpenseCategory, page = 0, size = 20) => {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (categoria) params.set('categoria', categoria);
      return request<PageResponse<FleetExpenseEntryResponse>>(`/v1/expenses?${params.toString()}`);
    },
    createFleet: (body: ExpenseEntryRequest) =>
      request<ExpenseEntryResponse>('/v1/expenses', { method: 'POST', body: JSON.stringify(body) }),
    removeFleet: (id: string) => request<void>(`/v1/expenses/${id}`, { method: 'DELETE' }),
    /** Soma por categoria no intervalo — alimenta a aba "Custos" → Visão geral. */
    summaryByCategory: (from: string, to: string) =>
      request<CategoryTotal[]>(`/v1/expenses/summary?from=${from}&to=${to}`),
  },

  /** Orçamento com alerta de estouro (spec 10, item 2) — gestor-only. */
  budgets: {
    list: () => request<BudgetResponse[]>('/v1/budgets'),
    create: (body: BudgetRequest) =>
      request<BudgetResponse>('/v1/budgets', { method: 'POST', body: JSON.stringify(body) }),
    remove: (id: string) => request<void>(`/v1/budgets/${id}`, { method: 'DELETE' }),
  },

  workOrders: {
    /** Paginado (cleanup de performance) — o histórico de OS do tenant cresce sem limite
     *  ao longo dos anos. `size` no teto do backend (100) cobre o volume típico numa
     *  request só, como já feito em outras listas grandes (ver nota em `vehicles.list`). */
    list: (vehicleId?: string, page = 0, size = 100) => {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (vehicleId) params.set('vehicleId', vehicleId);
      return request<PageResponse<WorkOrderResponse>>(`/v1/work-orders?${params.toString()}`);
    },
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
    /** Portal hospedado pela Stripe (cancelar, trocar cartão, baixar nota) — só existe
     *  depois de um checkout real (backend rejeita se a assinatura ainda não tem
     *  stripeCustomerId, ex. quem está só no trial). */
    portal: () => request<BillingPortalSessionResponse>('/v1/billing/portal', { method: 'POST' }),
  },

  /** Superfície do app do motorista (spec 07) — escopado ao token, motorista-only no backend. */
  me: {
    profile: () => request<DriverProfileResponse>('/v1/me/profile'),
    /** Designação ativa (null se não houver veículo designado no momento). */
    vehicle: () => request<DriverAssignmentResponse | null>('/v1/me/vehicle'),
    vehicleWorkOrders: () => request<WorkOrderResponse[]>('/v1/me/vehicle/work-orders'),
    trips: () => request<TripResponse[]>('/v1/me/trips'),
    reportIncident: (body: VehicleIncidentRequest) =>
      request<VehicleIncidentResponse>('/v1/me/incidents', { method: 'POST', body: JSON.stringify(body) }),
  },

  /** Pontos de coleta/entrega reutilizáveis (spec 08 item 5) — gestor-only. */
  collectionPoints: {
    /** {@code all=true} devolve inclusive inativos (tela de cadastro); por padrão só ativos. */
    list: (all = false) =>
      cachedGet(
        `collectionPoints:${all}`,
        () => request<CollectionPointResponse[]>(`/v1/collection-points${all ? '?all=true' : ''}`),
        SLOW_CHANGING_TTL_MS,
      ),
    create: (body: CollectionPointRequest) =>
      request<CollectionPointResponse>('/v1/collection-points', { method: 'POST', body: JSON.stringify(body) }).then(
        (r) => {
          invalidateListCache('collectionPoints:');
          return r;
        },
      ),
    update: (id: string, body: CollectionPointRequest) =>
      request<CollectionPointResponse>(`/v1/collection-points/${id}`, { method: 'PUT', body: JSON.stringify(body) }).then(
        (r) => {
          invalidateListCache('collectionPoints:');
          return r;
        },
      ),
    ativar: (id: string) =>
      request<CollectionPointResponse>(`/v1/collection-points/${id}/ativar`, { method: 'POST' }).then((r) => {
        invalidateListCache('collectionPoints:');
        return r;
      }),
    desativar: (id: string) =>
      request<CollectionPointResponse>(`/v1/collection-points/${id}/desativar`, { method: 'POST' }).then((r) => {
        invalidateListCache('collectionPoints:');
        return r;
      }),
  },

  /** Cadastro reutilizável de passageiro/cliente final (spec 14). */
  passengers: {
    list: () => cachedGet('passengers:', () => request<PassengerResponse[]>('/v1/passengers'), SLOW_CHANGING_TTL_MS),
    create: (body: PassengerRequest) =>
      request<PassengerResponse>('/v1/passengers', { method: 'POST', body: JSON.stringify(body) }).then((r) => {
        invalidateListCache('passengers:');
        return r;
      }),
    update: (id: string, body: PassengerRequest) =>
      request<PassengerResponse>(`/v1/passengers/${id}`, { method: 'PUT', body: JSON.stringify(body) }).then((r) => {
        invalidateListCache('passengers:');
        return r;
      }),
    delete: (id: string) =>
      request<void>(`/v1/passengers/${id}`, { method: 'DELETE' }).then((r) => {
        invalidateListCache('passengers:');
        return r;
      }),
  },

  /** Equipe e permissões (spec 15) — Gestor-only, backend também recusa. */
  team: {
    overview: () => request<TeamOverviewResponse>('/v1/team'),
    invite: (body: CreateTeamInviteRequest) =>
      request<TeamInviteResponse>('/v1/team/invite', { method: 'POST', body: JSON.stringify(body) }),
    changeRole: (userId: string, body: ChangeTeamRoleRequest) =>
      request<TeamMemberResponse>(`/v1/team/${userId}/role`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (userId: string) => request<void>(`/v1/team/${userId}`, { method: 'DELETE' }),
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
    /** Só o autor, só TEXTO, só enquanto `stillOnServer` (janela de retenção do servidor). */
    editMessage: (conversationId: string, messageId: string, body: { body: string }) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/messages/${messageId}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    /** Apagar pra todo mundo (sem "apagar só pra mim") — mesmas guardas de editMessage. */
    deleteMessage: (conversationId: string, messageId: string) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/messages/${messageId}`, {
        method: 'DELETE',
      }),
    forwardMessage: (conversationId: string, messageId: string, body: { targetConversationId: string }) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/messages/${messageId}/forward`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    /** Upsert — substitui a reação anterior desta pessoa nesta mensagem, se houver. */
    react: (conversationId: string, messageId: string, body: { emoji: string }) =>
      request<ChatReactionResponse[]>(`/v1/chat/conversations/${conversationId}/messages/${messageId}/reaction`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    removeReaction: (conversationId: string, messageId: string) =>
      request<ChatReactionResponse[]>(`/v1/chat/conversations/${conversationId}/messages/${messageId}/reaction`, {
        method: 'DELETE',
      }),
    /** Gestor-only: confirma que o device já persistiu localmente até syncedAt (habilita a limpeza no servidor). */
    syncCursor: (body: SyncCursorRequest) =>
      request<void>('/v1/chat/sync-cursor', { method: 'POST', body: JSON.stringify(body) }),
    /** Marca como lidas as mensagens do outro lado — chamar ao abrir/revisitar a conversa. */
    markAsRead: (conversationId: string) =>
      request<void>(`/v1/chat/conversations/${conversationId}/read`, { method: 'POST' }),
    /** Ping de "estou digitando" — efêmero, expira sozinho em alguns segundos no servidor. */
    typing: (conversationId: string) =>
      request<void>(`/v1/chat/conversations/${conversationId}/typing`, { method: 'POST' }),
    isOtherTyping: (conversationId: string) =>
      request<boolean>(`/v1/chat/conversations/${conversationId}/typing`),
    /** Gestor-only: anexa uma rota já cadastrada à conversa (spec 07 item 8). */
    sendRoutePlan: (conversationId: string, body: SendRoutePlanRequest) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/route-plan`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    /** Gestor-only: cancela rota pelo chat (ADR 0021) — único caminho que cancela rota já
     *  EM_ANDAMENTO; a tela de Rotas só cancela PLANEJADA. */
    cancelRoutePlan: (conversationId: string, body: SendRoutePlanRequest) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/route-plan/cancel`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    /** Gestor-only: reatribui a rota ao motorista desta conversa (ADR 0021) — chamado na
     *  conversa do NOVO motorista, geralmente em resposta a uma solicitação de troca. */
    trocaMotorista: (conversationId: string, body: SendRoutePlanRequest) =>
      request<ChatMessageResponse>(`/v1/chat/conversations/${conversationId}/route-plan/troca`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
  },

  /** Rota multi-parada (spec 02, spec 07 item 8). suggestOrder é stateless — a ordem
   *  sugerida é sempre revisada pelo gestor antes de create persistir. */
  routePlans: {
    suggestOrder: (body: SuggestOrderRequest) =>
      request<StopInput[]>('/v1/routes/plans/suggest-order', { method: 'POST', body: JSON.stringify(body) }),
    create: (body: CreateRoutePlanRequest) =>
      request<RoutePlanResponse>('/v1/routes/plans', { method: 'POST', body: JSON.stringify(body) }),
    /** Paginado (cleanup de performance). `size` alto cobre o volume típico de rotas
     *  numa request só, mesmo padrão de `vehicles.list`. */
    list: (page = 0, size = 100) =>
      request<PageResponse<RoutePlanResponse>>(`/v1/routes/plans?page=${page}&size=${size}`),
    assign: (id: string, body: AssignDriverRequest) =>
      request<RoutePlanResponse>(`/v1/routes/plans/${id}/assign`, { method: 'POST', body: JSON.stringify(body) }),
    /** Cancelamento direto — só funciona pra PLANEJADA (ADR 0021). Rota já EM_ANDAMENTO
     *  cancela só pelo chat, ver coreApi.chat.cancelRoutePlan. */
    cancel: (id: string) => request<RoutePlanResponse>(`/v1/routes/plans/${id}/cancel`, { method: 'POST' }),
    /** Motorista-only: rota ativa do próprio token (null se não houver). */
    active: () => request<RoutePlanResponse | null>('/v1/routes/plans/active'),
    /** Motorista-only: marca uma parada da própria rota ativa como concluída. */
    completeStop: (stopId: string) =>
      request<RouteStopResponse>(`/v1/routes/plans/stops/${stopId}/complete`, { method: 'POST' }),
  },

  /** Sino do topbar + tela "ver todas". Sem filtro de role — qualquer usuário autenticado
   *  só enxerga as próprias notificações (escopadas por userId no backend). */
  notifications: {
    list: (page = 0, size = 20) =>
      request<PageResponse<NotificationResponse>>(`/v1/notifications?page=${page}&size=${size}`),
    unreadCount: () => request<{ count: number }>('/v1/notifications/unread-count'),
    markRead: (id: string) => request<void>(`/v1/notifications/${id}/read`, { method: 'POST' }),
    markAllRead: () => request<void>('/v1/notifications/read-all', { method: 'POST' }),
  },
};
