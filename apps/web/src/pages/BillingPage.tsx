import { useEffect, useState } from 'react';
import { CheckCircle2, CreditCard } from 'lucide-react';
import { coreApi, type SubscriptionResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { formatDateTimeBR } from '../lib/format';

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Ativa',
  TRIALING: 'Em teste',
  PAST_DUE: 'Pagamento pendente',
  CANCELED: 'Cancelada',
  INCOMPLETE: 'Incompleta',
};

export function BillingPage() {
  const [subscription, setSubscription] = useState<SubscriptionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [checkingOut, setCheckingOut] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    coreApi.billing
      .subscription()
      .then(setSubscription)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar assinatura'))
      .finally(() => setLoading(false));
  }, []);

  async function handleCheckout() {
    setCheckingOut(true);
    setError('');
    try {
      const { checkoutUrl } = await coreApi.billing.checkout();
      window.location.href = checkoutUrl!;
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Billing ainda não está configurado neste ambiente (falta chave da Stripe).',
      );
    } finally {
      setCheckingOut(false);
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Assinatura</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">Plano e cobrança da sua frota</p>
      </div>

      <Card className="max-w-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-1.5">
            <CreditCard className="size-4 text-primary" />
            Plano atual
          </CardTitle>
        </CardHeader>
        <div className="px-5 pb-5">
          {loading ? (
            <p className="text-xs text-muted-foreground">Carregando...</p>
          ) : subscription?.hasSubscription ? (
            <div className="space-y-3">
              <div className="flex items-center gap-2 rounded-md border border-status-success-bg bg-status-success-bg px-3 py-2 text-status-success">
                <CheckCircle2 className="size-4" />
                <span className="text-sm font-medium">
                  Assinatura {STATUS_LABEL[subscription.status ?? ''] ?? subscription.status}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-3 text-xs">
                <div>
                  <p className="text-muted-foreground">Canal</p>
                  <p className="font-medium text-foreground">{subscription.billingSource}</p>
                </div>
                {subscription.currentPeriodEnd && (
                  <div>
                    <p className="text-muted-foreground">Renova em</p>
                    <p className="font-medium text-foreground">{formatDateTimeBR(subscription.currentPeriodEnd)}</p>
                  </div>
                )}
                {subscription.status === 'TRIALING' && subscription.trialEndsAt && (
                  <div>
                    <p className="text-muted-foreground">Trial até</p>
                    <p className="font-medium text-foreground">{formatDateTimeBR(subscription.trialEndsAt)}</p>
                  </div>
                )}
              </div>
              {subscription.status === 'TRIALING' ? (
                <div className="space-y-2">
                  <p className="text-[11px] text-muted-foreground">
                    Depois do trial, cadastrar veículo/motorista novo exige assinatura ativa — o que já está
                    cadastrado continua acessível.
                  </p>
                  <Button size="sm" onClick={handleCheckout} disabled={checkingOut}>
                    {checkingOut ? 'Abrindo checkout...' : 'Assinar agora'}
                  </Button>
                  {error && <p className="text-xs text-status-danger">{error}</p>}
                </div>
              ) : (
                <p className="text-[11px] text-muted-foreground">
                  Gerenciamento de forma de pagamento e cancelamento fica no portal da Stripe (em breve, link direto
                  aqui).
                </p>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-xs text-muted-foreground">
                Você ainda não tem uma assinatura ativa. O plano é cobrado por veículo ativo na frota.
              </p>
              <Button onClick={handleCheckout} disabled={checkingOut}>
                {checkingOut ? 'Abrindo checkout...' : 'Assinar agora'}
              </Button>
              {error && <p className="text-xs text-status-danger">{error}</p>}
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
