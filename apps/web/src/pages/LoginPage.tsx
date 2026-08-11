import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';

interface Props {
  onGoToSignup: () => void;
}

export function LoginPage({ onGoToSignup }: Props) {
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
      setError(err instanceof Error ? err.message : 'Falha no login');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main style={{ maxWidth: 360, margin: '4rem auto', fontFamily: 'system-ui, sans-serif' }}>
      <h1>AutonomousAPI</h1>
      <h2 style={{ fontWeight: 400, color: '#555' }}>Entrar</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <input
          type="email"
          placeholder="E-mail"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Senha"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <p style={{ color: '#c00' }}>{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
      <p style={{ marginTop: 16 }}>
        Ainda não tem conta?{' '}
        <button type="button" onClick={onGoToSignup} style={{ all: 'unset', cursor: 'pointer', color: '#06c' }}>
          Cadastrar minha frota
        </button>
      </p>
    </main>
  );
}
