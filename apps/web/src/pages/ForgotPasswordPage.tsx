import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { coreApi } from '../api/client';
import { AuthLayout, BotaoPublico, CampoPublico } from '../components/layout/AuthLayout';

interface Props {
  onVoltarParaLogin: () => void;
  onVoltarParaHome: () => void;
}

export function ForgotPasswordPage({ onVoltarParaLogin, onVoltarParaHome }: Props) {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [enviado, setEnviado] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await coreApi.auth.forgotPassword({ email });
    } finally {
      // Sempre mostra a mesma confirmação, e-mail existindo ou não (o backend já é
      // silencioso quanto a isso — o front não pode reintroduzir o vazamento aqui).
      setSubmitting(false);
      setEnviado(true);
    }
  }

  if (enviado) {
    return (
      <AuthLayout titulo={t('auth.forgotPassword.verifiqueEmail')} chamada={<>{t('auth.forgotPassword.quaseLa')}</>} onVoltar={onVoltarParaHome}>
        <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
          {t('auth.forgotPassword.seEmailTiverContaPre')}
          <span className="text-[var(--tinta)]">{email}</span>
          {t('auth.forgotPassword.seEmailTiverContaPos')}
        </p>
        <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
          <button type="button" onClick={onVoltarParaLogin} className="link-sublinhado text-[var(--tinta)]">
            {t('auth.forgotPassword.voltarParaLogin')}
          </button>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      titulo={t('auth.forgotPassword.esqueceuSenha')}
      chamada={
        <>
          {t('auth.forgotPassword.semProblemaPre')}
          <em className="italic">{t('auth.forgotPassword.semProblemaEm')}</em>
          {t('auth.forgotPassword.semProblemaPos')}
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">{t('auth.forgotPassword.informeEmail')}</p>

      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="email"
          rotulo={t('auth.forgotPassword.email')}
          type="email"
          autoComplete="email"
          placeholder="voce@suafrota.com.br"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? t('auth.forgotPassword.enviando') : t('auth.forgotPassword.enviarLink')}
          </BotaoPublico>
        </div>
      </form>

      <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
        <button type="button" onClick={onVoltarParaLogin} className="link-sublinhado text-[var(--tinta)]">
          {t('auth.forgotPassword.voltarParaLogin')}
        </button>
      </p>
    </AuthLayout>
  );
}
