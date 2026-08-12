import { useState, type FormEvent } from 'react';
import { Truck } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';

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
    <div className="flex min-h-screen items-center justify-center bg-sidebar p-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex size-12 items-center justify-center rounded-lg bg-sidebar-accent text-[#1a1206] shadow-lg">
            <Truck className="size-6" />
          </div>
          <h1 className="font-display text-xl font-bold text-white">AutonomousAPI</h1>
          <p className="mt-1 text-sm text-sidebar-muted">Gestão de Frota</p>
        </div>

        <Card className="p-6">
          <h2 className="mb-5 font-display text-base font-semibold text-foreground">Entrar na conta</h2>

          {error && (
            <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <Label htmlFor="email">E-mail</Label>
              <Input
                id="email"
                type="email"
                placeholder="seu@email.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="password">Senha</Label>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={submitting} className="w-full">
              {submitting ? 'Entrando...' : 'Entrar'}
            </Button>
          </form>

          <p className="mt-5 text-center text-xs text-muted-foreground">
            Ainda não tem conta?{' '}
            <Button variant="link" type="button" onClick={onGoToSignup} className="h-auto p-0 text-xs">
              Cadastrar minha frota
            </Button>
          </p>
        </Card>
      </div>
    </div>
  );
}
