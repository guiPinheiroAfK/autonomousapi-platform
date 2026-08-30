import { useEffect, useRef } from 'react';

interface GoogleCredentialResponse {
  credential: string;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: GoogleCredentialResponse) => void;
          }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;

/** Pras telas de login/cadastro decidirem sozinhas se mostram o divisor "ou" — sem
 *  isso, sobraria um divisor solto sem nenhum botão do Google acima dele. */
export const isGoogleSignInEnabled = Boolean(CLIENT_ID);

let scriptLoadPromise: Promise<void> | null = null;

/** Carrega o script do Google Identity Services uma única vez, mesmo se o botão
 *  aparecer em mais de uma tela (login e cadastro) — reaproveita a mesma promise em
 *  vez de injetar a tag de novo a cada montagem. */
function loadGisScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!scriptLoadPromise) {
    scriptLoadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Falha ao carregar o script do Google Identity Services.'));
      document.head.appendChild(script);
    });
  }
  return scriptLoadPromise;
}

interface Props {
  /** Chamado com o ID token assinado pelo Google — quem usa decide se é login ou
   *  cadastro (o backend também decide sozinho, ver AuthService#googleAuth). */
  onCredential: (idToken: string) => void;
}

/**
 * Botão "Continuar com o Google" — some inteiro se `VITE_GOOGLE_CLIENT_ID` não estiver
 * configurado (mesmo padrão de degradação do Stripe/Resend no backend: sem chave, o
 * recurso não aparece, nunca quebra). Usa o botão nativo do Google Identity Services
 * (`renderButton`), não um botão nosso — evita reimplementar acessibilidade/estados de
 * hover/loading que o próprio Google já resolve, e é o formato que o Google recomenda.
 */
export function GoogleSignInButton({ onCredential }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);

  // Só inicializa uma vez por montagem — não depende de `onCredential` mudar de
  // identidade entre renders (mesmo padrão de "só busca uma vez" já usado no resto do
  // app pra evitar re-disparo à toa).
  useEffect(() => {
    if (!CLIENT_ID || !containerRef.current) return;
    let cancelado = false;
    loadGisScript().then(() => {
      if (cancelado || !window.google || !containerRef.current) return;
      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: (response) => onCredential(response.credential),
      });
      window.google.accounts.id.renderButton(containerRef.current, {
        theme: 'outline',
        size: 'large',
        width: 320,
        text: 'continue_with',
      });
    });
    return () => {
      cancelado = true;
    };
  }, []);

  if (!CLIENT_ID) return null;

  return <div ref={containerRef} className="flex justify-center" />;
}
