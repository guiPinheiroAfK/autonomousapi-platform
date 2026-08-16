import { useEffect, useState } from 'react';
import { Check, MapPin, Route as RouteIcon } from 'lucide-react';
import { coreApi, type RoutePlanResponse } from '../api/client';
import { StatusBadgeRotaPlan } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { cn } from '../lib/utils';

const TIPO_LABEL: Record<string, string> = { COLETA: 'Coleta', ENTREGA: 'Entrega' };

/** Rota do dia do motorista (spec 07 item 8) — paradas na ordem definida pelo gestor,
 *  com um único botão por parada: marcar concluída. Nada de reordenar ou editar. */
export function DriverRoutePage() {
  const [route, setRoute] = useState<RoutePlanResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [completingId, setCompletingId] = useState<string | null>(null);
  const [error, setError] = useState('');

  function refresh() {
    coreApi.routePlans
      .active()
      .then(setRoute)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar rota'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  async function concluir(stopId: string) {
    setCompletingId(stopId);
    setError('');
    try {
      await coreApi.routePlans.completeStop(stopId);
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao concluir parada');
    } finally {
      setCompletingId(null);
    }
  }

  if (loading) return <p className="p-5 text-xs text-muted-foreground">Carregando...</p>;

  if (!route) {
    return (
      <div className="p-5">
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <RouteIcon className="size-6 text-muted-foreground/60" />
            <p>Nenhuma rota atribuída no momento.</p>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Minha rota</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{route.stops?.length ?? 0} parada(s)</p>
        </div>
        <StatusBadgeRotaPlan status={route.status} />
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

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
                  <p className="text-[11px] text-muted-foreground">{TIPO_LABEL[s.tipo ?? ''] ?? s.tipo}</p>
                </div>
                {!concluida && (
                  <Button size="sm" variant="secondary" onClick={() => concluir(s.id!)} disabled={completingId === s.id!}>
                    {completingId === s.id ? 'Marcando...' : 'Concluir'}
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
