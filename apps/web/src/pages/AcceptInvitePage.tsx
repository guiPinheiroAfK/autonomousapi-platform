import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { coreApi } from '../api/client';
import { AuthLayout, BotaoPublico, CampoPublico, ErroPublico } from '../components/layout/AuthLayout';

interface Props {
  token: string;
  onGoToLogin: () => void;
  onVoltarParaHome: () => void;
}

/**
 * Chega pelo link do e-mail de convite (ADR 0013): App.tsx lê ?token= de /aceitar-convite.
 * O clique no link já é a prova de posse — a conta nasce habilitada aqui, sem passo extra
 * de confirmação (mesmo raciocínio do verifyEmail).
 */
export function AcceptInvitePage({ token, onGoToLogin, onVoltarParaHome }: Props) {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sucesso, setSucesso] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await coreApi.auth.acceptInvite({ token, password });
      setSucesso(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('auth.acceptInvite.falha'));
    } finally {
      setSubmitting(false);
    }
  }

  if (sucesso) {
    return (
      <AuthLayout
        titulo={t('auth.acceptInvite.acessoLiberado')}
        chamada={
          <>
            {t('auth.acceptInvite.bemVindoPre')}
            <em className="italic">{t('auth.acceptInvite.bemVindoEm')}</em>
            {t('auth.acceptInvite.bemVindoPos')}
          </>
        }
        onVoltar={onVoltarParaHome}
      >
        <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">{t('auth.acceptInvite.senhaDefinida')}</p>
        <div className="mt-8">
          <BotaoPublico type="button" onClick={onGoToLogin}>
            {t('auth.acceptInvite.irParaLogin')}
          </BotaoPublico>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      titulo={t('auth.acceptInvite.defineSuaSenha')}
      chamada={
        <>
          {t('auth.acceptInvite.gestorConvidouPre')}
          <em className="italic">{t('auth.acceptInvite.gestorConvidouEm')}</em>
          {t('auth.acceptInvite.gestorConvidouPos')}
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">{t('auth.acceptInvite.escolhaSenha')}</p>
      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="password"
          rotulo={t('auth.acceptInvite.senha')}
          type="password"
          autoComplete="new-password"
          placeholder={t('auth.acceptInvite.minimoCaracteres')}
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <ErroPublico>{error}</ErroPublico>}
        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? t('auth.acceptInvite.salvando') : t('auth.acceptInvite.definirSenha')}
          </BotaoPublico>
        </div>
      </form>
    </AuthLayout>
  );
}
