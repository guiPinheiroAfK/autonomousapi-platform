import { useEffect, useState } from 'react';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../components/ui/alert-dialog';
import { Button } from '../components/ui/button';
import { toast } from './toast';

/**
 * Substitui window.confirm() por um dialog estilizado — o nativo (a) destoa do resto do
 * app (visual do SO, não do design system) e (b) trava chamada CDP em teste automatizado
 * (dialog síncrono do browser bloqueia o event loop até alguém clicar, e ferramenta de
 * automação não consegue "ver" nem responder um confirm() nativo).
 *
 * Padrão singleton (módulo, não hook): a maioria das chamadas (`handleDelete`) já mora
 * fora de componente React, dentro de um `async function` de página — precisa ser uma
 * função chamável de qualquer lugar, igual ao toast(). Só um <ConfirmDialogHost/>, montado
 * uma vez em App.tsx, resolve a Promise quando a pessoa decide.
 */
interface ConfirmState {
  message: string;
  confirmLabel: string;
  destructive: boolean;
  resolve: (value: boolean) => void;
}

let listener: ((state: ConfirmState | null) => void) | null = null;

export function confirmDialog(
  message: string,
  options?: { confirmLabel?: string; destructive?: boolean },
): Promise<boolean> {
  return new Promise((resolve) => {
    listener?.({
      message,
      confirmLabel: options?.confirmLabel ?? 'Excluir',
      destructive: options?.destructive ?? true,
      resolve,
    });
  });
}

/**
 * "confirmar → excluir → toast → refresh" repetido quase linha a linha em toda página com
 * CRUD (achado da auditoria de cleanup) — junta o padrão num só lugar em vez de reescrevê-lo
 * em cada `handleDelete`.
 */
export async function deleteWithConfirm(opts: {
  confirmMessage: string;
  remove: () => Promise<unknown>;
  successMessage: string;
  fallbackErrorMessage: string;
  onSuccess: () => void;
}): Promise<void> {
  if (!(await confirmDialog(opts.confirmMessage))) return;
  try {
    await opts.remove();
    toast.success(opts.successMessage);
    opts.onSuccess();
  } catch (err) {
    toast.error(err instanceof Error ? err.message : opts.fallbackErrorMessage);
  }
}

export function ConfirmDialogHost() {
  const [state, setState] = useState<ConfirmState | null>(null);

  useEffect(() => {
    listener = setState;
    return () => {
      listener = null;
    };
  }, []);

  function close(result: boolean) {
    state?.resolve(result);
    setState(null);
  }

  return (
    <AlertDialog open={state != null} onOpenChange={(open) => !open && close(false)}>
      {state && (
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Confirmar ação</AlertDialogTitle>
            <AlertDialogDescription>{state.message}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel asChild>
              <Button variant="ghost" onClick={() => close(false)}>
                Cancelar
              </Button>
            </AlertDialogCancel>
            <AlertDialogAction asChild>
              <Button variant={state.destructive ? 'destructive' : 'default'} onClick={() => close(true)}>
                {state.confirmLabel}
              </Button>
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      )}
    </AlertDialog>
  );
}
