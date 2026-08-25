import { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, Car, Check, MapPin, MessageCircle, Route as RouteIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type ChatConversationResponse,
  type DriverAssignmentResponse,
  type DriverProfileResponse,
  type RoutePlanResponse,
  type WorkOrderResponse,
} from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatusBadgeRotaPlan } from '../components/shared/StatusBadge';
import { cn } from '../lib/utils';
import { diasAteVencer } from '../lib/format';
import { getChatLastSeenAt } from '../lib/chatDb';

/** CNH some do alerta se faltar mais que isso — mesmo horizonte usado no alerta de frota
 *  do gestor (DriversPage), pra não ter dois critérios diferentes de "vencendo". */
const DIAS_ALERTA_CNH = 30;

interface Props {
  onViewRoute: () => void;
  onOpenChat: () => void;
}

/**
 * Home "Hoje" do motorista (spec 07) — substitui o dashboard analítico de frota pra esse
 * papel. Só o que exige atenção agora: próxima parada da rota ativa, alertas resumidos
 * (CNH, OS pendente), badge de chat não lido. Nada de gráfico, nada de dado de outro
 * motorista/veículo ("peça não precisa saber como trabalhar, só deve trabalhar" — pedido
 * explícito do usuário). Veículo completo, CNH, OS e histórico moram em "Mais".
 */
export function DriverHomePage({ onViewRoute, onOpenChat }: Props) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [vehicle, setVehicle] = useState<DriverAssignmentResponse | null>(null);
  const [route, setRoute] = useState<RoutePlanResponse | null>(null);
  const [profile, setProfile] = useState<DriverProfileResponse | null>(null);
  const [workOrders, setWorkOrders] = useState<WorkOrderResponse[]>([]);
  const [conversations, setConversations] = useState<ChatConversationResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      coreApi.me.vehicle(),
      coreApi.routePlans.active(),
      coreApi.me.profile(),
      coreApi.me.vehicleWorkOrders(),
      coreApi.chat.listConversations(),
    ])
      .then(([v, r, p, wo, convs]) => {
        setVehicle(v);
        setRoute(r);
        setProfile(p);
        setWorkOrders(wo);
        setConversations(convs);
      })
      .finally(() => setLoading(false));
  }, []);

  const diasCnh = profile?.cnhValidade ? diasAteVencer(profile.cnhValidade) : null;
  const cnhAlerta = diasCnh != null && diasCnh <= DIAS_ALERTA_CNH;
  const osPendentes = workOrders.filter((w) => w.status !== 'CONCLUIDA' && w.status !== 'CANCELADA').length;
  const temAlerta = cnhAlerta || osPendentes > 0;

  const ultimaMensagemEm = conversations.reduce<string | null>(
    (max, c) => (c.lastMessageAt && (!max || c.lastMessageAt > max) ? c.lastMessageAt : max),
    null,
  );
  const naoLida = !!ultimaMensagemEm && ultimaMensagemEm > (getChatLastSeenAt() ?? '');

  const proximaParada = route?.stops?.find((s) => !s.concluidaEm);

  if (loading) return <p className="p-5 text-xs text-muted-foreground">{t('common.carregando')}</p>;

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">
          {t('pages.driverHome.ola', { email: user?.email ? `, ${user.email}` : '' })}
        </h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.driverHome.oQueVoceSaberHoje')}</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Card className="sm:col-span-2">
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-1.5">
              <RouteIcon className="size-3.5" /> {t('pages.driverHome.rotaDeHoje')}
            </CardTitle>
            {route && <StatusBadgeRotaPlan status={route.status} />}
          </CardHeader>
          <div className="px-5 pb-4">
            {!route ? (
              <p className="text-xs text-muted-foreground">{t('pages.driverHome.nenhumaRotaHoje')}</p>
            ) : route.categoria === 'TRANSFER' ? (
              <CartaoTransfer route={route} onViewRoute={onViewRoute} />
            ) : (
              <div className="space-y-2">
                {proximaParada ? (
                  <div className="rounded-md border border-border bg-secondary/40 p-3">
                    <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{t('pages.driverHome.proximaParada')}</p>
                    <p className="mt-0.5 flex items-center gap-1.5 text-sm text-foreground">
                      <MapPin className="size-3.5 shrink-0" /> {proximaParada.label}
                    </p>
                  </div>
                ) : (
                  <p className="flex items-center gap-1.5 text-sm text-status-success">
                    <Check className="size-3.5" /> {t('pages.driverHome.todasParadasConcluidas')}
                  </p>
                )}
                <Button size="sm" variant="secondary" onClick={onViewRoute}>
                  {t('pages.driverHome.verRotaCompleta', { n: route.stops?.length ?? 0 })} <ArrowRight className="size-3.5" />
                </Button>
              </div>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <Car className="size-3.5" /> {t('pages.driverHome.seuVeiculo')}
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
              <p className="text-xs text-muted-foreground">{t('pages.driverHome.nenhumVeiculoDesignado')}</p>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <AlertTriangle className="size-3.5" /> {t('pages.driverHome.alertas')}
            </CardTitle>
          </CardHeader>
          <div className="space-y-1.5 px-5 pb-4 text-xs">
            {!temAlerta ? (
              <p className="text-muted-foreground">{t('pages.driverHome.tudoEmDia')}</p>
            ) : (
              <>
                {cnhAlerta && (
                  <p className={cn('flex items-center gap-1.5', diasCnh! < 0 ? 'text-status-danger' : 'text-status-warning')}>
                    <AlertTriangle className="size-3.5 shrink-0" />
                    {diasCnh! < 0 ? t('pages.driverHome.cnhVencida') : t('pages.driverHome.cnhVenceEmDias', { n: diasCnh })}
                  </p>
                )}
                {osPendentes > 0 && (
                  <p className="flex items-center gap-1.5 text-status-warning">
                    <AlertTriangle className="size-3.5 shrink-0" /> {t('pages.driverHome.osPendentes', { n: osPendentes })}
                  </p>
                )}
              </>
            )}
          </div>
        </Card>

        <button type="button" onClick={onOpenChat} className="text-left sm:col-span-2">
          <Card className="transition-colors hover:bg-muted/50">
            <div className="flex items-center justify-between px-5 py-4">
              <p className="flex items-center gap-1.5 text-sm text-foreground">
                <MessageCircle className="size-3.5" /> {t('pages.driverHome.mensagens')}
              </p>
              {naoLida && <span className="size-2 rounded-full bg-status-danger" />}
            </div>
          </Card>
        </button>
      </div>
    </div>
  );
}

function CartaoTransfer({ route, onViewRoute }: { route: RoutePlanResponse; onViewRoute: () => void }) {
  const { t } = useTranslation();
  const origem = route.stops?.[0];
  const destino = route.stops?.[1];
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-sm text-foreground">
        <MapPin className="size-3.5 shrink-0 text-status-success" />
        <span className="truncate">{origem?.label}</span>
      </div>
      <div className="flex items-center gap-2 text-sm text-foreground">
        <MapPin className="size-3.5 shrink-0 text-status-danger" />
        <span className="truncate">{destino?.label}</span>
      </div>
      {route.valor != null && (
        <p className="text-xs text-muted-foreground">{t('pages.driverHome.valorCombinado', { valor: route.valor.toFixed(2) })}</p>
      )}
      <Button size="sm" variant="secondary" onClick={onViewRoute}>
        {!origem?.concluidaEm ? t('pages.driverHome.iniciar') : t('pages.driverHome.concluir')} <ArrowRight className="size-3.5" />
      </Button>
    </div>
  );
}
