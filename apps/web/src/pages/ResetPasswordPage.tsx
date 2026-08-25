import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { coreApi } from '../api/client';
import { AuthLayout, BotaoPublico, CampoPublico, ErroPublico } from '../components/layout/AuthLayout';

interface Props {
  token: string;
  onGoToLogin: () => void;
  onVoltarParaHome: () => void;
}

/** Chega pelo link do e-mail (ADR 0012): App.tsx lê ?token= de /redefinir-senha. */
export function ResetPasswordPage({ token, onGoToLogin, onVoltarParaHome }: Props) {
  const { t } = useTranslation();
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
      setError(err instanceof Error ? err.message : t('auth.resetPassword.falha'));
    } finally {
      setSubmitting(false);
    }
  }

  if (sucesso) {
    return (
      <AuthLayout
        titulo={t('auth.resetPassword.senhaRedefinida')}
        chamada={
          <>
            {t('auth.resetPassword.prontoPre')}
            <em className="italic">{t('auth.resetPassword.prontoEm')}</em>
            {t('auth.resetPassword.prontoPos')}
          </>
        }
        onVoltar={onVoltarParaHome}
      >
        <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">{t('auth.resetPassword.senhaTrocada')}</p>
        <div className="mt-8">
          <BotaoPublico type="button" onClick={onGoToLogin}>
            {t('auth.resetPassword.irParaLogin')}
          </BotaoPublico>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      titulo={t('auth.resetPassword.escolhaSenhaNova')}
      chamada={
        <>
          {t('auth.resetPassword.umaSenhaPre')}
          <em className="italic">{t('auth.resetPassword.umaSenhaEm')}</em>
          {t('auth.resetPassword.umaSenhaPos')}
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="newPassword"
          rotulo={t('auth.resetPassword.novaSenha')}
          type="password"
          autoComplete="new-password"
          placeholder={t('auth.resetPassword.minimoCaracteres')}
          minLength={8}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
        />
        {error && <ErroPublico>{error}</ErroPublico>}
        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? t('auth.resetPassword.salvando') : t('auth.resetPassword.redefinirSenha')}
          </BotaoPublico>
        </div>
      </form>
    </AuthLayout>
  );
}
