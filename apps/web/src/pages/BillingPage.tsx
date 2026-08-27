import { useEffect, useState } from 'react';
import { CheckCircle2, CreditCard } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type SubscriptionResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { formatDateTimeBR } from '../lib/format';
import { toast } from '../lib/toast';

export function BillingPage() {
  const { t } = useTranslation();
  const [subscription, setSubscription] = useState<SubscriptionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [checkingOut, setCheckingOut] = useState(false);
  const [openingPortal, setOpeningPortal] = useState(false);

  useEffect(() => {
    coreApi.billing
      .subscription()
      .then(setSubscription)
      .catch((e: unknown) => toast.error(e instanceof Error ? e.message : t('pages.billing.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
    // Só busca uma vez; `t` mudar de idioma não deve re-disparar o fetch (achado da
    // auditoria de cleanup — antes [t] causava refetch a cada troca de idioma).
  }, []);

  async function handleCheckout() {
    setCheckingOut(true);
    try {
      const { checkoutUrl } = await coreApi.billing.checkout();
      window.location.href = checkoutUrl!;
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.billing.toasts.falhaCheckout'));
    } finally {
      setCheckingOut(false);
    }
  }

  async function handleOpenPortal() {
    setOpeningPortal(true);
    try {
      const { portalUrl } = await coreApi.billing.portal();
      window.location.href = portalUrl!;
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.billing.toasts.falhaPortal'));
    } finally {
      setOpeningPortal(false);
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.billing.titulo')}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.billing.subtitulo')}</p>
      </div>

      <Card className="max-w-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-1.5">
            <CreditCard className="size-4 text-primary" />
            {t('pages.billing.planoAtual')}
          </CardTitle>
        </CardHeader>
        <div className="px-5 pb-5">
          {loading ? (
            <p className="text-xs text-muted-foreground">{t('common.carregando')}</p>
          ) : subscription?.hasSubscription ? (
            <div className="space-y-3">
              <div className="flex items-center gap-2 rounded-md border border-status-success-bg bg-status-success-bg px-3 py-2 text-status-success">
                <CheckCircle2 className="size-4" />
                <span className="text-sm font-medium">
                  {t('pages.billing.assinaturaStatus', {
                    status: subscription.status ? t(`pages.billing.status.${subscription.status}`) : subscription.status,
                  })}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-3 text-xs">
                <div>
                  <p className="text-muted-foreground">{t('pages.billing.canal')}</p>
                  <p className="font-medium text-foreground">{subscription.billingSource}</p>
                </div>
                {subscription.currentPeriodEnd && (
                  <div>
                    <p className="text-muted-foreground">{t('pages.billing.renovaEm')}</p>
                    <p className="font-medium text-foreground">{formatDateTimeBR(subscription.currentPeriodEnd)}</p>
                  </div>
                )}
                {subscription.status === 'TRIALING' && subscription.trialEndsAt && (
                  <div>
                    <p className="text-muted-foreground">{t('pages.billing.trialAte')}</p>
                    <p className="font-medium text-foreground">{formatDateTimeBR(subscription.trialEndsAt)}</p>
                  </div>
                )}
              </div>
              {subscription.status === 'TRIALING' ? (
                <div className="space-y-2">
                  <p className="text-[11px] text-muted-foreground">{t('pages.billing.avisoTrial')}</p>
                  <Button size="sm" onClick={handleCheckout} disabled={checkingOut}>
                    {checkingOut ? t('pages.billing.abrindoCheckout') : t('pages.billing.assinarAgora')}
                  </Button>
                </div>
              ) : (
                <Button variant="outline" size="sm" onClick={handleOpenPortal} disabled={openingPortal}>
                  {openingPortal ? t('pages.billing.abrindoPortal') : t('pages.billing.gerenciarAssinatura')}
                </Button>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-xs text-muted-foreground">{t('pages.billing.semAssinaturaAtiva')}</p>
              <Button onClick={handleCheckout} disabled={checkingOut}>
                {checkingOut ? t('pages.billing.abrindoCheckout') : t('pages.billing.assinarAgora')}
              </Button>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
