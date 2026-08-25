import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import pt from './locales/pt.json';
import en from './locales/en.json';
import es from './locales/es.json';

export const SUPPORTED_LANGUAGES = ['pt', 'en', 'es'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/**
 * Detecção automática (navigator.language) com persistência em localStorage assim que o
 * usuário troca manualmente — a landing não tem seletor de idioma de propósito (pedido:
 * não poluir a página pública), então o único jeito de alguém ver outro idioma lá é o
 * navegador já pedir esse idioma. O seletor manual (só dentro do painel autenticado)
 * grava em localStorage, que passa a valer também na próxima visita à landing.
 *
 * `load: 'languageOnly'` reduz qualquer variante regional (en-US, es-AR, pt-BR) pro
 * código base (en, es, pt) antes de procurar recurso — sem isso, `supportedLngs` só
 * casa com o código exato, e a alternativa óbvia (`nonExplicitSupportedLngs: true`)
 * tem um bug nesta versão do i18next: `resolvedLanguage` reporta o idioma certo, mas a
 * busca de chave (`t()`/`exists()`) usa outro código internamente e nunca acha nada —
 * toda tradução cai no fallback de mostrar a chave crua. Achado batendo `t()` contra
 * `getResourceBundle()` isolado num teste Node, fora do React, pra eliminar HMR/browser
 * como causa antes de comparar as duas opções lado a lado.
 */
i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      pt: { translation: pt },
      en: { translation: en },
      es: { translation: es },
    },
    fallbackLng: 'pt',
    load: 'languageOnly',
    ns: 'translation',
    defaultNS: 'translation',
    supportedLngs: SUPPORTED_LANGUAGES,
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'autonomousapi.idioma',
      caches: ['localStorage'],
    },
    interpolation: { escapeValue: false },
  });

export default i18n;
