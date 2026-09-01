import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import {
  coreApi,
  setAuthToken,
  setRefreshToken,
  setTokensRefreshedHandler,
  setUnauthorizedHandler,
  type LoginRequest,
  type SignupRequest,
  type SignupResponse,
  type TenantChoiceResponse,
  type TokenResponse,
  type UserResponse,
} from '../api/client';

const STORAGE_KEY = 'autonomousapi.accessToken';
const REFRESH_STORAGE_KEY = 'autonomousapi.refreshToken';

interface AuthState {
  user: UserResponse | null;
  loading: boolean;
  /** V34: devolve `null` quando já loga direto (caso comum); devolve a escolha de tenant
   *  quando a senha bate em mais de uma conta do e-mail — completar com `selectTenant`. */
  login: (body: LoginRequest) => Promise<TenantChoiceResponse | null>;
  selectTenant: (pendingToken: string, tenantId: string) => Promise<void>;
  /** Login OU cadastro via Google, na mesma chamada — o backend decide. */
  loginWithGoogle: (idToken: string) => Promise<void>;
  /** Não loga automaticamente (ADR 0011) — devolve a mensagem de "confirme seu e-mail". */
  signup: (body: SignupRequest) => Promise<SignupResponse>;
  /** Habilita a conta e já loga — o clique no link é a prova de posse do e-mail. */
  verifyEmail: (token: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Painel interno de gestão de frota: guardar o access token no localStorage é uma
 * troca aceitável para um MVP admin (sem cookies httpOnly ainda) — reavaliar se o
 * painel expor uma superfície pública maior.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // Registrado antes do efeito de restauração de sessão abaixo, para que um 401
  // já disparado durante a própria restauração (token salvo expirado, e o refresh
  // silencioso também falhou — ex. os 30 dias do refresh token já passaram) seja coberto.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    // Toda vez que o client renova os tokens sozinho (access token expirado no meio de
    // uma navegação, refresh token de 30 dias ainda válido), persiste o par novo — sem
    // isso, o refresh silencioso funcionaria na aba aberta mas o próximo F5 voltaria a
    // usar o access token velho já expirado do localStorage.
    setTokensRefreshedHandler(persistTokens);
    return () => {
      setUnauthorizedHandler(null);
      setTokensRefreshedHandler(null);
    };
  }, []);

  useEffect(() => {
    const storedAccess = localStorage.getItem(STORAGE_KEY);
    const storedRefresh = localStorage.getItem(REFRESH_STORAGE_KEY);
    if (!storedAccess || !storedRefresh) {
      setLoading(false);
      return;
    }
    setAuthToken(storedAccess);
    setRefreshToken(storedRefresh);
    // Access token de 15min quase sempre já expirou entre uma visita e outra — o refresh
    // silencioso dentro de `request` (client.ts) cobre isso sozinho no primeiro 401 que
    // este `.me()` tomar, sem precisar de nenhum código especial aqui pra esse caso.
    coreApi.auth
      .me()
      .then(setUser)
      .catch(() => {
        clearStoredTokens();
        setAuthToken(null);
        setRefreshToken(null);
      })
      .finally(() => setLoading(false));
  }, []);

  function persistTokens(accessToken: string, refreshToken: string) {
    localStorage.setItem(STORAGE_KEY, accessToken);
    localStorage.setItem(REFRESH_STORAGE_KEY, refreshToken);
  }

  function clearStoredTokens() {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(REFRESH_STORAGE_KEY);
  }

  async function afterAuth(tokens: TokenResponse) {
    persistTokens(tokens.accessToken!, tokens.refreshToken!);
    setAuthToken(tokens.accessToken!);
    setRefreshToken(tokens.refreshToken!);
    setUser(await coreApi.auth.me());
  }

  async function login(body: LoginRequest): Promise<TenantChoiceResponse | null> {
    const result = await coreApi.auth.login(body);
    if (result.tokens) {
      await afterAuth(result.tokens);
      return null;
    }
    return result.tenantChoice ?? null;
  }

  async function selectTenant(pendingToken: string, tenantId: string) {
    await afterAuth(await coreApi.auth.selectTenant({ pendingToken, tenantId }));
  }

  async function loginWithGoogle(idToken: string) {
    await afterAuth(await coreApi.auth.google(idToken));
  }

  async function signup(body: SignupRequest) {
    return coreApi.auth.signup(body);
  }

  async function verifyEmail(token: string) {
    await afterAuth(await coreApi.auth.verifyEmail({ token }));
  }

  function logout() {
    clearStoredTokens();
    setAuthToken(null);
    setRefreshToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, selectTenant, loginWithGoogle, signup, verifyEmail, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>');
  return ctx;
}

/**
 * Fora do fluxo de rota, criar/editar/excluir (frota, motorista, custo, OS, manutenção,
 * etc.) é Gestor-only — Despachante/Visualizador só leem. O backend já bloqueia essas
 * escritas com 403 pra quem não é `GESTOR_FROTA`/`ADMIN`; isso só esconde o botão que ia
 * dar erro (spec 15, fast-follow).
 */
export function usePodeEscrever(): boolean {
  const { user } = useAuth();
  return user?.role === 'GESTOR_FROTA' || user?.role === 'ADMIN';
}
