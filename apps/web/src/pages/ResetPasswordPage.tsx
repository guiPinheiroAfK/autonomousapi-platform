import { useState, type FormEvent } from 'react';
import { coreApi } from '../api/client';
import { AuthLayout, BotaoPublico, CampoPublico, ErroPublico } from '../components/layout/AuthLayout';

interface Props {
  token: string;
  onGoToLogin: () => void;
  onVoltarParaHome: () => void;
}

/** Chega pelo link do e-mail (ADR 0012): App.tsx lê ?token= de /redefinir-senha. */
export function ResetPasswordPage({ token, onGoToLogin, onVoltarParaHome }: Props) {
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sucesso, setSucesso] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await coreApi.auth.resetPassword({ token, newPassword });
      setSucesso(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível redefinir a senha.');
    } finally {
      setSubmitting(false);
    }
  }

  if (sucesso) {
    return (
      <AuthLayout titulo="Senha redefinida" chamada={<>Pronto, <em className="italic">já pode entrar</em>.</>} onVoltar={onVoltarParaHome}>
        <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
          Sua senha foi trocada e qualquer sessão antiga foi encerrada, por segurança.
        </p>
        <div className="mt-8">
          <BotaoPublico type="button" onClick={onGoToLogin}>
            Ir para o login
          </BotaoPublico>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout titulo="Escolha uma senha nova" chamada={<>Uma senha, <em className="italic">de novo</em>.</>} onVoltar={onVoltarParaHome}>
      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="newPassword"
          rotulo="Nova senha"
          type="password"
          autoComplete="new-password"
          placeholder="mínimo de 8 caracteres"
          minLength={8}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
        />
        {error && <ErroPublico>{error}</ErroPublico>}
        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? 'Salvando...' : 'Redefinir senha'}
          </BotaoPublico>
        </div>
      </form>
    </AuthLayout>
  );
}
