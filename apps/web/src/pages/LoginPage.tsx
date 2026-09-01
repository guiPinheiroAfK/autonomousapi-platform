import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import type { TenantChoiceResponse } from '../api/client';
import {
  AuthLayout,
  BotaoPublico,
  CampoPublico,
  ErroPublico,
} from '../components/layout/AuthLayout';
import { GoogleSignInButton, isGoogleSignInEnabled } from '../components/shared/GoogleSignInButton';

interface Props {
  onGoToSignup: () => void;
  onVoltarParaHome: () => void;
  onGoToForgotPassword: () => void;
}

export function LoginPage({ onGoToSignup, onVoltarParaHome, onGoToForgotPassword }: Props) {
  const { t } = useTranslation();
  const { login, selectTenant, loginWithGoogle } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  // V34: preenchido só no caso raro de a mesma senha bater em mais de uma conta do e-mail
  // (tenants diferentes) — o login não loga direto, pede pra escolher qual empresa.
  const [tenantChoice, setTenantChoice] = useState<TenantChoiceResponse | null>(null);

  const papelLabel: Record<string, string> = {
    GESTOR_FROTA: t('pages.team.papel.gestor'),
    DESPACHANTE: t('pages.team.papel.despachante'),
    VISUALIZADOR: t('pages.team.papel.visualizador'),
  };

  /**
   * Dispara o download do chunk do Dashboard (mesmo specifier do `lazy()` em App.tsx —
   * bundler dedupe por módulo resolvido, não duplica) em paralelo com a chamada de login,
   * não depois dela. Sem isso, o usuário via duas esperas em sequência: a rede do login, e
   * só depois o download do chunk (Suspense fallback) — pedido explícito do Guilherme pra
   * a transição pro painel parecer instantânea assim que o login responde. Se o login falhar,
   * o chunk baixado não faz mal nenhum — já fica em cache pra um próximo login bem-sucedido.
   */
  function prefetchDashboard() {
    import('./DashboardPage');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    prefetchDashboard();
    try {
      const choice = await login({ email, password });
      if (choice) setTenantChoice(choice);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('auth.login.falha'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSelectTenant(tenantId: string) {
    if (!tenantChoice) return;
    setError('');
    setSubmitting(true);
    try {
      await selectTenant(tenantChoice.pendingToken!, tenantId);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('auth.login.falha'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGoogleCredential(idToken: string) {
    setError('');
    setSubmitting(true);
    prefetchDashboard();
    try {
      await loginWithGoogle(idToken);
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
      {/* V34: e-mail com conta em mais de uma empresa e a mesma senha nas duas — pede pra
          escolher em vez de entrar numa arbitrariamente. Substitui o formulário normal,
          não some (o e-mail/senha já foram validados, só falta escolher o tenant). */}
      {tenantChoice ? (
        <div className="mt-8 space-y-3">
          <p className="text-[14px] text-[var(--tinta-suave)]">{t('auth.login.escolherEmpresa')}</p>
          {tenantChoice.tenants?.map((opcao) => (
            <BotaoPublico
              key={opcao.tenantId}
              type="button"
              disabled={submitting}
              onClick={() => handleSelectTenant(opcao.tenantId!)}
            >
              {opcao.tenantName} — {papelLabel[opcao.role!] ?? opcao.role}
            </BotaoPublico>
          ))}
          {error && <ErroPublico>{error}</ErroPublico>}
          <button
            type="button"
            onClick={() => setTenantChoice(null)}
            className="link-sublinhado text-[13px] text-[var(--tinta-suave)]"
          >
            {t('auth.login.voltarParaLogin')}
          </button>
        </div>
      ) : (
      <>
      {/* O bloco inteiro some sem VITE_GOOGLE_CLIENT_ID configurado — sem isso, sobraria
          um divisor "ou" solto sem nenhum botão acima dele. */}
      {isGoogleSignInEnabled && (
        <>
          <div className="mt-8">
            <GoogleSignInButton onCredential={handleGoogleCredential} />
          </div>
          <div className="my-6 flex items-center gap-3 text-[12px] text-[var(--tinta-suave)]">
            <div className="h-px flex-1 bg-[var(--linha)]" />
            {t('auth.ou')}
            <div className="h-px flex-1 bg-[var(--linha)]" />
          </div>
        </>
      )}

      <form onSubmit={handleSubmit} className={isGoogleSignInEnabled ? 'space-y-4' : 'mt-8 space-y-4'}>
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
      </>
      )}
    </AuthLayout>
  );
}
