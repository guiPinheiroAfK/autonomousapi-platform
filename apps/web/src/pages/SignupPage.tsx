import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';

interface Props {
  onGoToLogin: () => void;
}

export function SignupPage({ onGoToLogin }: Props) {
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
    <main style={{ maxWidth: 360, margin: '4rem auto', fontFamily: 'system-ui, sans-serif' }}>
      <h1>AutonomousAPI</h1>
      <h2 style={{ fontWeight: 400, color: '#555' }}>Cadastrar minha frota</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <input
          placeholder="Nome da frota/empresa"
          value={tenantName}
          onChange={(e) => setTenantName(e.target.value)}
          required
        />
        <input
          type="email"
          placeholder="Seu e-mail"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Senha (mín. 8 caracteres)"
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <p style={{ color: '#c00' }}>{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Criando...' : 'Criar conta'}
        </button>
      </form>
      <p style={{ marginTop: 16 }}>
        Já tem conta?{' '}
        <button type="button" onClick={onGoToLogin} style={{ all: 'unset', cursor: 'pointer', color: '#06c' }}>
          Entrar
        </button>
      </p>
    </main>
  );
}
