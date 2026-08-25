import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import {
  AuthLayout,
  BotaoPublico,
  CampoPublico,
  ErroPublico,
} from '../components/layout/AuthLayout';

interface Props {
  onGoToSignup: () => void;
  onVoltarParaHome: () => void;
  onGoToForgotPassword: () => void;
}

export function LoginPage({ onGoToSignup, onVoltarParaHome, onGoToForgotPassword }: Props) {
  const { t } = useTranslation();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login({ email, password });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('auth.login.falha'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      titulo={t('auth.login.titulo')}
      chamada={
        <>
          {t('auth.login.chamada1')}
          <em className="italic">{t('auth.login.chamada2')}</em>
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="email"
          rotulo={t('auth.login.email')}
          type="email"
          autoComplete="email"
          placeholder="voce@suafrota.com.br"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <CampoPublico
          id="password"
          rotulo={t('auth.login.senha')}
          type="password"
          autoComplete="current-password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        {error && <ErroPublico>{error}</ErroPublico>}

        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? t('auth.login.entrando') : t('auth.login.entrar')}
          </BotaoPublico>
        </div>
      </form>

      <p className="mt-4 text-[13px] text-[var(--tinta-suave)]">
        <button type="button" onClick={onGoToForgotPassword} className="link-sublinhado text-[var(--tinta)]">
          {t('auth.login.esqueceuSenha')}
        </button>
      </p>

      <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
        {t('auth.login.semConta')}{' '}
        <button type="button" onClick={onGoToSignup} className="link-sublinhado text-[var(--tinta)]">
          {t('auth.login.cadastrarMinhaFrota')}
        </button>
      </p>
    </AuthLayout>
  );
}
