import { CORE_API_URL } from '../config';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${CORE_API_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    throw new Error(`core-api ${res.status} em ${path}`);
  }
  return (await res.json()) as T;
}

// Client único: mobile fala SÓ com o core-api. Tipos migram para @autonomousapi/shared-types
// quando o OpenAPI do core-api for gerado (ADR 0003).
export const coreApi = {
  login: (email: string, password: string) =>
    request<TokenResponse>('/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
};
