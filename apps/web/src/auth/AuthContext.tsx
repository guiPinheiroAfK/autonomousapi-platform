import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import {
  coreApi,
  setAuthToken,
  setUnauthorizedHandler,
  type LoginRequest,
  type SignupRequest,
  type SignupResponse,
  type UserResponse,
} from '../api/client';

const STORAGE_KEY = 'autonomousapi.accessToken';

interface AuthState {
  user: UserResponse | null;
  loading: boolean;
  login: (body: LoginRequest) => Promise<void>;
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
  // já disparado durante a própria restauração (token salvo expirado) seja coberto.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) {
      setLoading(false);
      return;
    }
    setAuthToken(stored);
    coreApi.auth
      .me()
      .then(setUser)
      .catch(() => {
        localStorage.removeItem(STORAGE_KEY);
        setAuthToken(null);
      })
      .finally(() => setLoading(false));
  }, []);

  async function afterAuth(accessToken: string) {
    localStorage.setItem(STORAGE_KEY, accessToken);
    setAuthToken(accessToken);
    setUser(await coreApi.auth.me());
  }

  async function login(body: LoginRequest) {
    const tokens = await coreApi.auth.login(body);
    await afterAuth(tokens.accessToken!);
  }

  async function signup(body: SignupRequest) {
    return coreApi.auth.signup(body);
  }

  async function verifyEmail(token: string) {
    const tokens = await coreApi.auth.verifyEmail({ token });
    await afterAuth(tokens.accessToken!);
  }

  function logout() {
    localStorage.removeItem(STORAGE_KEY);
    setAuthToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, verifyEmail, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>');
  return ctx;
}
