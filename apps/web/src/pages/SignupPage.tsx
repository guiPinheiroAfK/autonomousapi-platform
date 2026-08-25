import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { coreApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import {
  AuthLayout,
  BotaoPublico,
  CampoPublico,
  ErroPublico,
} from '../components/layout/AuthLayout';

interface Props {
  onGoToLogin: () => void;
  onVoltarParaHome: () => void;
}

export function SignupPage({ onGoToLogin, onVoltarParaHome }: Props) {
  const { t } = useTranslation();
  const { signup } = useAuth();
  const [tenantName, setTenantName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  // ADR 0011: signup não loga mais — só troca a tela pra "confirme seu e-mail".
  const [emailPendente, setEmailPendente] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const resp = await signup({ email, password, tenantName });
      setEmailPendente(resp.email ?? email);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('auth.signup.falha'));
    } finally {
      setSubmitting(false);
    }
  }

  if (emailPendente) {
    return (
      <ConfirmeSeuEmail
        email={emailPendente}
        onVoltarParaHome={onVoltarParaHome}
        onGoToLogin={onGoToLogin}
      />
    );
  }

  return (
    <AuthLayout
      titulo={t('auth.signup.titulo')}
      chamada={
        <>
          {t('auth.signup.chamadaPre')}
          <em className="italic">{t('auth.signup.chamadaEm')}</em>
          {t('auth.signup.chamadaPos')}
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">{t('auth.signup.descricao')}</p>

      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="tenantName"
          rotulo={t('auth.signup.nomeFrota')}
          autoComplete="organization"
          placeholder="RotaCerta Entregas"
          value={tenantName}
          onChange={(e) => setTenantName(e.target.value)}
          required
        />
        <CampoPublico
          id="email"
          rotulo={t('auth.signup.email')}
          type="email"
          autoComplete="email"
          placeholder="voce@suafrota.com.br"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <CampoPublico
          id="password"
          rotulo={t('auth.signup.senha')}
          type="password"
          autoComplete="new-password"
          placeholder={t('auth.signup.minimoCaracteres')}
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        {error && <ErroPublico>{error}</ErroPublico>}

        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? t('auth.signup.criandoConta') : t('auth.signup.criarConta')}
          </BotaoPublico>
        </div>
      </form>

      <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
        {t('auth.signup.jaTemConta')}{' '}
        <button type="button" onClick={onGoToLogin} className="link-sublinhado text-[var(--tinta)]">
          {t('auth.signup.entrar')}
        </button>
      </p>
    </AuthLayout>
  );
}

function ConfirmeSeuEmail({
  email,
  onVoltarParaHome,
  onGoToLogin,
}: {
  email: string;
  onVoltarParaHome: () => void;
  onGoToLogin: () => void;
}) {
  const { t } = useTranslation();
  const [reenviando, setReenviando] = useState(false);
  const [reenviado, setReenviado] = useState(false);

  async function reenviar() {
    setReenviando(true);
    try {
      await coreApi.auth.resendVerification({ email });
      setReenviado(true);
    } finally {
      setReenviando(false);
    }
  }

  return (
    <AuthLayout
      titulo={t('auth.confirmeEmail.titulo')}
      chamada={
        <>
          {t('auth.confirmeEmail.chamadaPre')}
          <em className="italic">{t('auth.confirmeEmail.chamadaEm')}</em>
          {t('auth.confirmeEmail.chamadaPos')}
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
        {t('auth.confirmeEmail.descricaoPre')}
        <span className="text-[var(--tinta)]">{email}</span>
        {t('auth.confirmeEmail.descricaoPos')}
      </p>

      <div className="mt-8 space-y-3">
        <BotaoPublico type="button" onClick={reenviar} disabled={reenviando || reenviado}>
          {reenviado
            ? t('auth.confirmeEmail.linkReenviado')
            : reenviando
              ? t('auth.confirmeEmail.reenviando')
              : t('auth.confirmeEmail.reenviarEmail')}
        </BotaoPublico>
      </div>

      <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
        {t('auth.confirmeEmail.jaConfirmou')}{' '}
        <button type="button" onClick={onGoToLogin} className="link-sublinhado text-[var(--tinta)]">
          {t('auth.confirmeEmail.entrar')}
        </button>
      </p>
    </AuthLayout>
  );
}
