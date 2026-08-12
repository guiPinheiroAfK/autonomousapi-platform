# mobile

App do motorista (React Native + Expo). Abrir no **WebStorm** (é JS/TS).
Fala **só com o core-api** (spec 01) — nunca com o geo-api direto.

## Rodar

```bash
npm install            # na raiz do monorepo (uma vez)
npm run start --workspace @autonomousapi/mobile   # abre o Expo Dev Tools
```

Depois use o app **Expo Go** (Android/iOS) lendo o QR code, ou um emulador
(`npm run android` / `npm run ios` no workspace). Precisa do backend no ar
(`infra/docker-compose.yml`, com `CORE_PROFILES=demo` pra ter usuário demo) para
o login funcionar.

> Rodar em device/emulador precisa da toolchain mobile (Expo Go, Android Studio
> ou Xcode) — não coberto pelo CI, que só faz typecheck.

## Estrutura

| Arquivo | Papel |
|---|---|
| `index.ts` | Entry (registerRootComponent — monorepo-safe) |
| `App.tsx` | Orquestra consentimento → sessão → login → tela do motorista |
| `src/api/client.ts` | Client único do core-api (login, me, veículos, viagens) |
| `src/auth/tokenStorage.ts` | Tokens no Keychain/Keystore (expo-secure-store) |
| `src/onboarding/consent.ts` | Flag de consentimento de localização (AsyncStorage) |
| `src/screens/` | Login, consentimento, viagem (motorista), bloqueio (não-motorista) |
| `src/offline/pingQueue.ts` | Fila offline-first de pings de GPS, persistida em AsyncStorage |
| `metro.config.js` | Metro ciente do monorepo |

## Registro de viagem (spec 03)

Fluxo real, ponta a ponta contra o core-api (`/v1/trips`, que por sua vez encaminha
pro geo-api — verificado manualmente com um usuário `MOTORISTA` real, ping chegou em
`geo.vehicle_gps_ping`):

1. Login → `GET /v1/auth/me` decide a tela: só contas com role `MOTORISTA` veem a tela
   de viagem (`TripScreen`); as demais veem uma tela de bloqueio.
2. Motorista escolhe o veículo e inicia a viagem (`POST /v1/trips`) — o backend rejeita
   uma segunda viagem simultânea (409).
3. Enquanto a viagem está em andamento, `expo-location` observa a posição
   (`watchPositionAsync`, foreground) e cada leitura vai pra fila local
   (`pingQueue.ts`, AsyncStorage — sobrevive a reinício do app).
4. "Sincronizar" envia a fila em ordem pro core-api (`POST /v1/trips/{id}/pings`); ping
   que falhar continua na fila pra nova tentativa, nunca é descartado.
5. "Finalizar viagem" para a captura de localização e chama `POST /v1/trips/{id}/stop`.

**O que falta pra ficar 100% pronto pra loja** (não coberto aqui — precisa de device/
emulador real e da toolchain nativa, que este ambiente não tem):
- Captura de localização *verdadeiramente* em background (hoje é `watchPositionAsync`
  em foreground; rastreio contínuo com app minimizado precisa de
  `Location.startLocationUpdatesAsync` + `expo-task-manager`, não testável sem device).
- `ios.bundleIdentifier` / `android.package` (só necessários pra build de loja, não pro
  fluxo via Expo Go).
- Fluxo de convite de motorista (hoje só existe cadastro público como `GESTOR_FROTA` — a
  criação de conta `MOTORISTA` ainda não tem endpoint próprio).

## Verificação feita

- `npm run typecheck` — limpo
- `npx expo export` — Metro empacota todos os módulos sem erro de import/resolução (o
  único erro que aparece nesse ambiente é a falta do binário nativo `hermesc`, que não
  existe neste container headless e não afeta o fluxo via Expo Go)
- Fluxo completo testado via `curl` contra o backend real (login motorista → iniciar
  viagem → ping → confirmar no Postgres do geo-api → finalizar → listar)
