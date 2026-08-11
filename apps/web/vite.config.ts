import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Web fala SÓ com core-api (spec 01). Em dev, proxya /api para o core-api local,
// evitando CORS e mantendo o mesmo caminho relativo que será usado em produção.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_CORE_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
        // O core-api não conhece o prefixo /api (ele expõe /v1/...) — sem isso,
        // toda chamada cai fora dos matchers públicos do SecurityConfig e vira 401.
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
});
