/**
 * Tipos compartilhados entre web e mobile.
 *
 * Os tipos do core-api são GERADOS a partir do seu OpenAPI (ADR 0003) — não edite
 * `src/generated/core-api.ts` à mão. Rode `npm run gen:types` na raiz depois que o
 * core-api existir (Checkpoint C) e expuser o `/v3/api-docs`.
 *
 * Até lá, este arquivo só reexporta tipos manuais mínimos e serve de ponto de entrada.
 */

// export type * from './generated/core-api'; // habilitar após a primeira geração

/** Perfis de usuário do core-api (spec 01). Fonte de verdade migra para o gerado. */
export type UserRole = 'gestor_frota' | 'motorista' | 'admin' | 'parceiro_api';

export {};
