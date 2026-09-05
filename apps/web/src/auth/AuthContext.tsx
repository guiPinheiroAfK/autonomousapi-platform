import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import {
  coreApi,
  setAuthToken,
  setUnauthorizedHandler,
  type LoginRequest,
  type SignupRequest,
  type SignupResponse,
  type TenantChoiceResponse,
  type TokenResponse,
  type UserResponse,
} from '../api/client';

const STORAGE_KEY = 'autonomousapi.accessToken';

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

  // Registrado antes do efeito de restauração de sessão abaixo, para que um 401 já
  // disparado durante a própria restauração (token salvo expirado) seja coberto.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    const storedAccess = localStorage.getItem(STORAGE_KEY);
    if (!storedAccess) {
      setLoading(false);
      return;
    }
    setAuthToken(storedAccess);
    // Sem refresh silencioso (de propósito — a pessoa deve ser deslogada quando o access
    // token de 15min expira): se o token salvo já expirou, este `.me()` toma 401 e
    // `onUnauthorizedHandler` (registrado acima) já limpa a sessão sozinho.
    coreApi.auth
      .me()
      .then(setUser)
      .catch(() => {
        clearStoredTokens();
        setAuthToken(null);
      })
      .finally(() => setLoading(false));
  }, []);

  function persistTokens(accessToken: string) {
    localStorage.setItem(STORAGE_KEY, accessToken);
  }

  function clearStoredTokens() {
    localStorage.removeItem(STORAGE_KEY);
  }

  async function afterAuth(tokens: TokenResponse) {
    persistTokens(tokens.accessToken!);
    setAuthToken(tokens.accessToken!);
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

/** Módulos com permissão configurável por usuário (ADR 0025) — espelha o enum do backend. */
export type ModuloPermissao =
  | 'FROTA'
  | 'ORDENS_SERVICO'
  | 'MOTORISTAS'
  | 'MENSAGENS'
  | 'ROTAS'
  | 'CUSTOS'
  | 'RELATORIOS'
  | 'PARCEIROS'
  | 'RECARGA';

/**
 * ADR 0025 — "esta pessoa pode ver/escrever neste módulo?". Só esconde o que ia dar 403:
 * quem decide de verdade é o `@PreAuthorize` do backend, a partir das permissões que vêm
 * dentro do próprio JWT. Aqui a lista chega por `/v1/auth/me`.
 */
export function usePode(modulo: ModuloPermissao, acao: 'VER' | 'ESCREVER'): boolean {
  const { user } = useAuth();
  return (user?.permissions ?? []).includes(`${modulo}_${acao}`);
}
