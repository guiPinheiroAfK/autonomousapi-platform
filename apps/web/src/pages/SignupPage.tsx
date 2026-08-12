import { useState, type FormEvent } from 'react';
import { Truck } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';

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
          <h2 className="mb-5 font-display text-base font-semibold text-foreground">Cadastrar minha frota</h2>

          {error && (
            <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <Label htmlFor="tenantName">Nome da frota/empresa</Label>
              <Input id="tenantName" value={tenantName} onChange={(e) => setTenantName(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="email">Seu e-mail</Label>
              <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="password">Senha (mín. 8 caracteres)</Label>
              <Input
                id="password"
                type="password"
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={submitting} className="w-full">
              {submitting ? 'Criando...' : 'Criar conta'}
            </Button>
          </form>

          <p className="mt-5 text-center text-xs text-muted-foreground">
            Já tem conta?{' '}
            <Button variant="link" type="button" onClick={onGoToLogin} className="h-auto p-0 text-xs">
              Entrar
            </Button>
          </p>
        </Card>
      </div>
    </div>
  );
}
