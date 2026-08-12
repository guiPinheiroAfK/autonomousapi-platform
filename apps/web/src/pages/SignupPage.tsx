import { useState, type FormEvent } from 'react';
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
  const { signup } = useAuth();
  const [tenantName, setTenantName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await signup({ email, password, tenantName });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha no cadastro');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      titulo="Cadastrar minha frota"
      chamada={
        <>
          Comece pelos veículos que <em className="italic">já estão</em> na rua.
        </>
      }
      onVoltar={onVoltarParaHome}
    >
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
        Sem instalação e sem equipamento novo no carro. Você cadastra a frota e o painel
        começa a se preencher.
      </p>

      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="tenantName"
          rotulo="Nome da frota ou empresa"
          autoComplete="organization"
          placeholder="RotaCerta Entregas"
          value={tenantName}
          onChange={(e) => setTenantName(e.target.value)}
          required
        />
        <CampoPublico
          id="email"
          rotulo="E-mail"
          type="email"
          autoComplete="email"
          placeholder="voce@suafrota.com.br"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
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
            {submitting ? 'Criando conta...' : 'Criar conta'}
          </BotaoPublico>
        </div>
      </form>

      <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
        Já tem conta?{' '}
        <button type="button" onClick={onGoToLogin} className="link-sublinhado text-[var(--tinta)]">
          Entrar
        </button>
      </p>
    </AuthLayout>
  );
}
