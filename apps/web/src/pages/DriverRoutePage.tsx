import { useEffect, useState } from 'react';
import { Check, MapPin, Route as RouteIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type RoutePlanResponse } from '../api/client';
import { StatusBadgeRotaPlan } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { cn } from '../lib/utils';
import { toast } from '../lib/toast';

/** Rota do dia do motorista (spec 07 item 8) — paradas na ordem definida pelo gestor,
 *  com um único botão por parada: marcar concluída. Nada de reordenar ou editar.
 *  TRANSFER (trajeto único, spec 02) renderiza um cartão único em vez da lista numerada. */
export function DriverRoutePage() {
  const { t } = useTranslation();
  const [route, setRoute] = useState<RoutePlanResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [completingId, setCompletingId] = useState<string | null>(null);

  function refresh() {
    coreApi.routePlans
      .active()
      .then(setRoute)
      .catch((e: unknown) => toast.error(e instanceof Error ? e.message : t('pages.driverRoute.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  async function concluir(stopId: string) {
    setCompletingId(stopId);
    try {
      await coreApi.routePlans.completeStop(stopId);
      refresh();
      toast.success(t('pages.driverRoute.toasts.paradaConcluida'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : t('pages.driverRoute.toasts.falhaConcluir'));
    } finally {
      setCompletingId(null);
    }
  }

  if (loading) return <p className="p-5 text-xs text-muted-foreground">{t('common.carregando')}</p>;

  if (!route) {
    return (
      <div className="p-5">
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <RouteIcon className="size-6 text-muted-foreground/60" />
            <p>{t('pages.driverRoute.nenhumaRotaAtribuida')}</p>
          </div>
        </Card>
      </div>
    );
  }

  if (route.categoria === 'TRANSFER') {
    const origem = route.stops?.[0];
    const destino = route.stops?.[1];
    const proxima = !origem?.concluidaEm ? origem : !destino?.concluidaEm ? destino : null;
    return (
      <div className="p-5">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.driverRoute.transfer')}</h2>
          <StatusBadgeRotaPlan status={route.status} />
        </div>
        <Card>
          <div className="space-y-3 p-5">
            <div className={cn('flex items-center gap-2.5', origem?.concluidaEm && 'text-muted-foreground')}>
              <span className={cn('flex size-6 shrink-0 items-center justify-center rounded-full', origem?.concluidaEm ? 'bg-status-success-bg text-status-success' : 'bg-secondary')}>
                {origem?.concluidaEm ? <Check className="size-3.5" /> : <MapPin className="size-3.5" />}
              </span>
              <div>
                <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{t('pages.driverRoute.origem')}</p>
                <p className="text-sm text-foreground">{origem?.label}</p>
              </div>
            </div>
            <div className={cn('flex items-center gap-2.5', destino?.concluidaEm && 'text-muted-foreground')}>
              <span className={cn('flex size-6 shrink-0 items-center justify-center rounded-full', destino?.concluidaEm ? 'bg-status-success-bg text-status-success' : 'bg-secondary')}>
                {destino?.concluidaEm ? <Check className="size-3.5" /> : <MapPin className="size-3.5" />}
              </span>
              <div>
                <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{t('pages.driverRoute.destino')}</p>
                <p className="text-sm text-foreground">{destino?.label}</p>
              </div>
            </div>
            {route.valor != null && (
              <p className="text-xs text-muted-foreground">{t('pages.driverRoute.valorCombinado', { valor: route.valor.toFixed(2) })}</p>
            )}
            {proxima && (
              <Button
                size="sm"
                onClick={() => concluir(proxima.id!)}
                disabled={completingId === proxima.id}
              >
                {completingId === proxima.id
                  ? t('pages.driverRoute.marcando')
                  : proxima === origem
                    ? t('pages.driverRoute.iniciar')
                    : t('pages.driverRoute.concluir')}
              </Button>
            )}
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.driverRoute.minhaRota')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.driverRoute.paradaContagem', { n: route.stops?.length ?? 0 })}</p>
        </div>
        <StatusBadgeRotaPlan status={route.status} />
      </div>

      <Card>
        <ol className="divide-y divide-border">
          {(route.stops ?? []).map((s, i) => {
            const concluida = !!s.concluidaEm;
            return (
              <li key={s.id} className="flex items-center gap-3 px-4 py-3">
                <span
                  className={cn(
                    'flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold',
                    concluida ? 'bg-status-success-bg text-status-success' : 'bg-secondary text-muted-foreground',
                  )}
                >
                  {concluida ? <Check className="size-3.5" /> : i + 1}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-1 text-sm text-foreground">
                    <MapPin className="size-3.5 shrink-0 text-muted-foreground" />
                    <span className="truncate">{s.label}</span>
                  </p>
                  <p className="text-[11px] text-muted-foreground">
                    {s.tipo === 'COLETA' ? t('pages.routePlans.coleta') : t('pages.routePlans.entrega')}
                  </p>
                </div>
                {!concluida && (
                  <Button size="sm" variant="secondary" onClick={() => concluir(s.id!)} disabled={completingId === s.id!}>
                    {completingId === s.id ? t('pages.driverRoute.marcando') : t('pages.driverRoute.concluir')}
                  </Button>
                )}
              </li>
            );
          })}
        </ol>
      </Card>
    </div>
  );
}
