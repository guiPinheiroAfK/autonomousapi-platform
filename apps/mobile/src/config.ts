// O mobile fala SÓ com o core-api (spec 01) — nunca com o geo-api direto.
// EXPO_PUBLIC_* fica disponível no bundle; defina no .env ou no ambiente de build.
export const CORE_API_URL = process.env.EXPO_PUBLIC_CORE_API_URL ?? 'http://localhost:8080';
