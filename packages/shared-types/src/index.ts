/**
 * Tipos compartilhados entre web e mobile.
 *
 * Os tipos do core-api são GERADOS a partir do seu OpenAPI (ADR 0003) — não edite
 * `src/generated/core-api.ts` à mão. Rode `npm run gen:types` na raiz sempre que o
 * DTO de um endpoint mudar no core-api.
 */

export type * from './generated/core-api';

/**
 * Perfis de usuário (spec 01). O DTO expõe `role` como String (via enum.name() no
 * Java), então o OpenAPI gerado não capta os valores possíveis — mantido manual
 * aqui até o core-api anotar o campo com o tipo enum real.
 */
export type UserRole = 'GESTOR_FROTA' | 'MOTORISTA' | 'ADMIN' | 'PARCEIRO_API';
