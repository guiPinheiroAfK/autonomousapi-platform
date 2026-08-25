import { toast as sonnerToast } from 'sonner';

/**
 * Wrapper fino sobre o sonner — nunca importar `sonner` direto numa página. Além de manter
 * o visual (cores do design system, via classNames no <Toaster/> em App.tsx) consistente
 * num lugar só, dá pra trocar de lib de toast no futuro sem tocar em cada página.
 */
export const toast = {
  success: (message: string) => sonnerToast.success(message),
  error: (message: string) => sonnerToast.error(message),
};
