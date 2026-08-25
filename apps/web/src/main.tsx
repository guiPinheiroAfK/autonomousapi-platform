import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { AuthProvider } from './auth/AuthContext';
import { ThemeProvider } from './lib/theme';
import './i18n';
import './index.css';

const rootEl = document.getElementById('root');
if (!rootEl) throw new Error('Elemento #root não encontrado no index.html');

createRoot(rootEl).render(
  <StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
);
