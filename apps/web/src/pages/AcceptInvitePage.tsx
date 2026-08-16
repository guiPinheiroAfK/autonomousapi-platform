import { useState, type FormEvent } from 'react';
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
      setError(err instanceof Error ? err.message : 'Não foi possível aceitar o convite.');
    } finally {
      setSubmitting(false);
    }
  }

  if (sucesso) {
    return (
      <AuthLayout titulo="Acesso liberado" chamada={<>Bem-vindo, <em className="italic">já pode entrar</em>.</>} onVoltar={onVoltarParaHome}>
        <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
          Sua senha foi definida. Entre no app da AutonomousAPI com o seu e-mail e a senha que
          você acabou de escolher.
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
    <AuthLayout titulo="Defina sua senha" chamada={<>Seu gestor te <em className="italic">convidou</em>.</>} onVoltar={onVoltarParaHome}>
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
        Escolha uma senha pra acessar o app da AutonomousAPI com o seu e-mail.
      </p>
      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="password"
          rotulo="Senha"
          type="password"
          autoComplete="new-password"
          placeholder="mínimo de 8 caracteres"
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <ErroPublico>{error}</ErroPublico>}
        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? 'Salvando...' : 'Definir senha'}
          </BotaoPublico>
        </div>
      </form>
    </AuthLayout>
  );
}
