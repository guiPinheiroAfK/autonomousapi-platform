import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { flushSync } from 'react-dom';

type Theme = 'light' | 'dark';

interface ThemeContextValue {
  theme: Theme;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);
const STORAGE_KEY = 'autonomousapi-theme';

function getInitialTheme(): Theme {
  const saved = window.localStorage.getItem(STORAGE_KEY);
  if (saved === 'light' || saved === 'dark') return saved;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(getInitialTheme);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    window.localStorage.setItem(STORAGE_KEY, theme);
  }, [theme]);

  /**
   * O esmaecido na troca de tema não pode vir de `transition` em cor no body/html: já
   * tentamos (ver comentário em index.css) e a cor computada travava no valor anterior,
   * porque a transição fica na MESMA camada que carrega as variáveis (--background/
   * --foreground mudam instantâneo, a propriedade que lê var() não anima direito atrás
   * disso). View Transitions resolve isso numa camada de verdade diferente: tira um
   * "print" do antes/depois e faz o crossfade entre as duas imagens, sem nunca animar a
   * cor em si. Sem suporte no browser (Firefox/Safari ainda não têm), cai de volta pra
   * troca instantânea — nunca quebra, só não anima.
   *
   * `flushSync` é obrigatório aqui: `setTheme` sozinho é uma atualização assíncrona do
   * React (batching), mas o callback de `startViewTransition` precisa que o DOM já esteja
   * no estado novo de forma SÍNCRONA antes de devolver — senão o browser tenta capturar o
   * "depois" antes do React ter repintado a classe `.dark`.
   *
   * `.catch` silencioso de propósito: a Promise rejeita com `InvalidStateError` quando o
   * documento não está visível (aba em segundo plano, `document.hidden`) — a especificação
   * aborta a transição nesse caso, não tem nada pra fazer crossfade de uma página escondida.
   * O tema já trocou certo pelo `flushSync` de qualquer forma; a transição é só efeito
   * visual, nunca pode virar erro não tratado no console.
   */
  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    if (document.startViewTransition) {
      document.startViewTransition(() => flushSync(() => setTheme(next))).ready.catch(() => {});
    } else {
      setTheme(next);
    }
  }

  return <ThemeContext.Provider value={{ theme, toggleTheme }}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme precisa estar dentro de <ThemeProvider>');
  return ctx;
}
