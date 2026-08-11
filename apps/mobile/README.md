# mobile

App do motorista/gestor (React Native + Expo). Abrir no **WebStorm** (é JS/TS).
Fala **só com o core-api** (spec 01).

## Rodar

```bash
npm install            # na raiz do monorepo (uma vez)
npm run start --workspace @autonomousapi/mobile   # abre o Expo Dev Tools
```

Depois use o app **Expo Go** (Android/iOS) lendo o QR code, ou um emulador
(`npm run android` / `npm run ios` no workspace). Precisa do backend no ar
(`infra/docker-compose.yml`) para o login funcionar.

> Rodar em device/emulador precisa da toolchain mobile (Expo Go, Android Studio
> ou Xcode) — não coberto pelo CI, que só faz typecheck.

## Estrutura

| Arquivo | Papel |
|---|---|
| `index.ts` | Entry (registerRootComponent — monorepo-safe) |
| `App.tsx` | Alterna entre Login e Viagem conforme autenticação |
| `src/api/client.ts` | Client único do core-api |
| `src/screens/` | Telas (Login, Trip) |
| `src/offline/pingQueue.ts` | Fila offline-first de pings de GPS (spec 03) |
| `metro.config.js` | Metro ciente do monorepo |

## Offline-first e localização

- A fila de pings (`pingQueue.ts`) é o esqueleto do requisito offline (spec 03):
  registra localmente e sincroniza ao reconectar. O backing store (AsyncStorage/SQLite)
  e a captura real via `expo-location` em background entram na Fase 1.
- As descrições de permissão de localização (inclusive background) já estão em `app.json`
  — preparadas para a revisão das lojas desde já (spec 03).
