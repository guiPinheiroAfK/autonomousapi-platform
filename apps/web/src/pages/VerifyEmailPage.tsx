import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout, BotaoPublico, ErroPublico } from '../components/layout/AuthLayout';

interface Props {
  token: string;
  onVoltarParaHome: () => void;
  onGoToSignup: () => void;
}

/**
 * Chega aqui pelo link do e-mail (ADR 0011): App.tsx lê `?token=` da URL antes de montar
 * o resto do app e decide essa tela. Confirma sozinha ao montar — não tem formulário,
 * só estado de carregando/erro, porque a única entrada é o token que já veio na URL.
 */
export function VerifyEmailPage({ token, onVoltarParaHome, onGoToSignup }: Props) {
  const { t } = useTranslation();
  const { verifyEmail } = useAuth();
  const [status, setStatus] = useState<'confirmando' | 'erro'>('confirmando');
  const [error, setError] = useState('');

  useEffect(() => {
    verifyEmail(token).catch((err) => {
      setError(err instanceof Error ? err.message : t('auth.verifyEmail.falha'));
      setStatus('erro');
    });
  }, [token, verifyEmail, t]);

  if (status === 'confirmando') {
    return (
      <AuthLayout titulo={t('auth.verifyEmail.confirmandoTitulo')} chamada={<>{t('auth.verifyEmail.jaJaVoceEstaLa')}</>} onVoltar={onVoltarParaHome}>
        <p className="mt-3 text-[14px] text-[var(--tinta-suave)]">{t('auth.verifyEmail.umSegundo')}</p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      titulo={t('auth.verifyEmail.naoDeuCerto')}
      chamada={
        <>
          {t('auth.verifyEmail.linkPodeTerPre')}
          <em className="italic">{t('auth.verifyEmail.linkPodeTerEm')}</em>
          {t('auth.verifyEmail.linkPodeTerPos')}
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <ErroPublico>{error}</ErroPublico>
      <p className="mt-6 text-[14px] text-[var(--tinta-suave)]">
        {t('auth.verifyEmail.linksValemPre')}
        <button type="button" onClick={onGoToSignup} className="link-sublinhado text-[var(--tinta)]">
          {t('auth.verifyEmail.cadastreSeDeNovo')}
        </button>
        {t('auth.verifyEmail.linksValemPos')}
      </p>
      <div className="mt-8">
        <BotaoPublico type="button" onClick={onVoltarParaHome}>
          {t('auth.verifyEmail.voltarParaInicio')}
        </BotaoPublico>
      </div>
    </AuthLayout>
  );
}
