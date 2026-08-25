import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmDialogHost, deleteWithConfirm } from './confirm';

// deleteWithConfirm/confirmDialog dependem de <ConfirmDialogHost/> montado (padrão
// singleton via módulo, ver comentário em confirm.tsx) — por isso o teste é de
// integração (renderiza o host de verdade) em vez de mockar confirmDialog isoladamente.
vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { toast as sonnerToast } from 'sonner';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('deleteWithConfirm', () => {
  it('chama remove/onSuccess e mostra toast de sucesso quando o usuário confirma', async () => {
    const user = userEvent.setup();
    render(<ConfirmDialogHost />);

    const remove = vi.fn().mockResolvedValue(undefined);
    const onSuccess = vi.fn();

    void deleteWithConfirm({
      confirmMessage: 'Excluir este item?',
      remove,
      successMessage: 'Excluído com sucesso',
      fallbackErrorMessage: 'Falha ao excluir',
      onSuccess,
    });

    await screen.findByText('Excluir este item?');
    await user.click(screen.getByRole('button', { name: 'Excluir' }));

    await waitFor(() => expect(remove).toHaveBeenCalledTimes(1));
    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(sonnerToast.success).toHaveBeenCalledWith('Excluído com sucesso');
  });

  it('não chama remove/onSuccess quando o usuário cancela', async () => {
    const user = userEvent.setup();
    render(<ConfirmDialogHost />);

    const remove = vi.fn().mockResolvedValue(undefined);
    const onSuccess = vi.fn();

    void deleteWithConfirm({
      confirmMessage: 'Excluir este item?',
      remove,
      successMessage: 'Excluído com sucesso',
      fallbackErrorMessage: 'Falha ao excluir',
      onSuccess,
    });

    await screen.findByText('Excluir este item?');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByText('Excluir este item?')).not.toBeInTheDocument());
    expect(remove).not.toHaveBeenCalled();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('mostra toast de erro com a mensagem da exceção quando remove falha', async () => {
    const user = userEvent.setup();
    render(<ConfirmDialogHost />);

    const remove = vi.fn().mockRejectedValue(new Error('Erro do servidor'));
    const onSuccess = vi.fn();

    void deleteWithConfirm({
      confirmMessage: 'Excluir este item?',
      remove,
      successMessage: 'Excluído com sucesso',
      fallbackErrorMessage: 'Falha ao excluir',
      onSuccess,
    });

    await screen.findByText('Excluir este item?');
    await user.click(screen.getByRole('button', { name: 'Excluir' }));

    await waitFor(() => expect(sonnerToast.error).toHaveBeenCalledWith('Erro do servidor'));
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('usa a mensagem de fallback quando o erro não é um Error', async () => {
    const user = userEvent.setup();
    render(<ConfirmDialogHost />);

    const remove = vi.fn().mockRejectedValue('string qualquer');

    void deleteWithConfirm({
      confirmMessage: 'Excluir este item?',
      remove,
      successMessage: 'Excluído com sucesso',
      fallbackErrorMessage: 'Falha ao excluir',
      onSuccess: vi.fn(),
    });

    await screen.findByText('Excluir este item?');
    await user.click(screen.getByRole('button', { name: 'Excluir' }));

    await waitFor(() => expect(sonnerToast.error).toHaveBeenCalledWith('Falha ao excluir'));
  });
});
