import { useEffect, useState } from 'react';
import { ArrowRight, Car, Route as RouteIcon } from 'lucide-react';
import { coreApi, type DriverAssignmentResponse, type RoutePlanResponse } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatusBadgeRotaPlan } from '../components/shared/StatusBadge';

interface Props {
  onViewRoute: () => void;
}

/**
 * Home do motorista (spec 07) — substitui o dashboard analítico de frota pra esse papel.
 * Deliberadamente enxuta: só o que afeta o trabalho dele hoje (próprio veículo, rota do
 * dia), nada de gráficos ou dado de outros motoristas/veículos ("peça não precisa saber
 * como trabalhar, só deve trabalhar" — pedido explícito do usuário).
 */
export function DriverHomePage({ onViewRoute }: Props) {
  const { user } = useAuth();
  const [vehicle, setVehicle] = useState<DriverAssignmentResponse | null>(null);
  const [route, setRoute] = useState<RoutePlanResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([coreApi.me.vehicle(), coreApi.routePlans.active()])
      .then(([v, r]) => {
        setVehicle(v);
        setRoute(r);
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Olá{user?.email ? `, ${user.email}` : ''}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">O que você precisa saber hoje.</p>
      </div>

      {!loading && (
        <div className="grid gap-3 sm:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-1.5">
                <Car className="size-3.5" /> Seu veículo
              </CardTitle>
            </CardHeader>
            <div className="px-5 pb-4 text-sm text-foreground">
              {vehicle ? (
                <>
                  <p className="font-medium">{vehicle.plate}</p>
                  <p className="text-xs text-muted-foreground">
                    {vehicle.brand} {vehicle.model}
                  </p>
                </>
              ) : (
                <p className="text-xs text-muted-foreground">Nenhum veículo designado no momento.</p>
              )}
            </div>
          </Card>

          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle className="flex items-center gap-1.5">
                <RouteIcon className="size-3.5" /> Rota do dia
              </CardTitle>
              {route && <StatusBadgeRotaPlan status={route.status} />}
            </CardHeader>
            <div className="px-5 pb-4">
              {route ? (
                <>
                  <p className="text-sm text-foreground">{route.stops?.length ?? 0} parada(s)</p>
                  <Button size="sm" variant="secondary" className="mt-2" onClick={onViewRoute}>
                    Ver paradas <ArrowRight className="size-3.5" />
                  </Button>
                </>
              ) : (
                <p className="text-xs text-muted-foreground">Nenhuma rota atribuída no momento.</p>
              )}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
