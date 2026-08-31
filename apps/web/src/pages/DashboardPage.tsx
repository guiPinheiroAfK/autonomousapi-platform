import { useEffect, useState } from 'react';
import { AlertTriangle, ClipboardList, Truck, Users, Wrench } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  coreApi,
  type DriverLicenseAlertResponse,
  type MonthlyCostResponse,
  type VehicleMaintenanceAlertResponse,
  type VehicleResponse,
} from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatCard } from '../components/shared/StatCard';
import { DonutChart } from '../components/shared/DonutChart';
import { StatCardsSkeleton } from '../components/shared/StatCardsSkeleton';
import { TableSkeleton } from '../components/shared/TableSkeleton';
import { cn } from '../lib/utils';
import { monthLabel } from '../lib/format';

const VEHICLE_STATUS_COLOR: Record<string, string> = {
  ATIVO: 'var(--color-status-success)',
  MANUTENCAO: 'var(--color-status-warning)',
  INATIVO: 'var(--color-status-neutral)',
};

interface Props {
  onViewVehicles: () => void;
}

export function DashboardPage({ onViewVehicles }: Props) {
  const { t } = useTranslation();
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [maintenanceAlerts, setMaintenanceAlerts] = useState<VehicleMaintenanceAlertResponse[]>([]);
  const [licenseAlerts, setLicenseAlerts] = useState<DriverLicenseAlertResponse[]>([]);
  const [costTrend, setCostTrend] = useState<MonthlyCostResponse[]>([]);
  const [totalVehicles, setTotalVehicles] = useState(0);
  const [totalDrivers, setTotalDrivers] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // vehicles.list e drivers.list são paginados (spec de escala) — size grande o
    // bastante pra cobrir frota/equipe inteiras na imensa maioria dos tenants nos
    // gráficos abaixo. O total exibido no card vem de totalElements (exato), não de
    // vehicles.length/drivers.length (só a página).
    //
    // allSettled, não all (spec 15, achado ao testar o papel Despachante): com Promise.all,
    // um único 403 (ex. drivers.list, gestor-only) derrubava a promise inteira e zerava
    // TODOS os cards, não só o widget que de fato não tinha permissão — o gestor via 20 mil
    // veículos, o mesmo tenant como Despachante via "0" em tudo. Cada card agora falha (ou
    // não) independente dos outros.
    Promise.allSettled([
      coreApi.vehicles.list(0, 500),
      coreApi.drivers.list(),
      coreApi.vehicles.maintenanceDue(),
      coreApi.drivers.licenseExpiring(),
      coreApi.vehicles.costTrend(),
    ])
      .then(([v, d, m, l, t]) => {
        if (v.status === 'fulfilled') {
          setVehicles(v.value.content);
          setTotalVehicles(v.value.totalElements);
        }
        if (d.status === 'fulfilled') setTotalDrivers(d.value.totalElements);
        if (m.status === 'fulfilled') setMaintenanceAlerts(m.value);
        if (l.status === 'fulfilled') setLicenseAlerts(l.value);
        if (t.status === 'fulfilled') setCostTrend(t.value);
      })
      .finally(() => setLoading(false));
  }, []);

  const ativos = vehicles.filter((v) => v.status === 'ATIVO').length;
  const manutencao = vehicles.filter((v) => v.status === 'MANUTENCAO').length;
  const totalAlertas = maintenanceAlerts.length + licenseAlerts.length;

  const chartData = costTrend.map((m) => ({ label: monthLabel(m.month!), total: m.total ?? 0 }));
  const statusData = (['ATIVO', 'MANUTENCAO', 'INATIVO'] as const)
    .map((status) => ({ status, count: vehicles.filter((v) => v.status === status).length }))
    .filter((d) => d.count > 0);

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.dashboard.titulo')}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.dashboard.subtitulo')}</p>
      </div>

      {loading ? (
        <StatCardsSkeleton />
      ) : (
        <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label={t('pages.dashboard.veiculos')} value={totalVehicles} hint={t('pages.dashboard.totalDaFrota')} icon={Truck} />
          <StatCard
            label={t('pages.dashboard.emOperacao')}
            value={ativos}
            tone="success"
            hint={t('pages.dashboard.veiculosAtivos')}
            icon={ClipboardList}
          />
          <StatCard
            label={t('pages.dashboard.emManutencao')}
            value={manutencao}
            tone="warning"
            hint={t('pages.dashboard.foraDeOperacao')}
            icon={Wrench}
          />
          <StatCard label={t('pages.dashboard.motoristas')} value={totalDrivers} hint={t('pages.dashboard.cadastrados')} icon={Users} />
        </div>
      )}

      {!loading && (
        <div className="mb-5 grid grid-cols-1 gap-3 lg:grid-cols-[2fr_1fr]">
          <Card>
            <CardHeader>
              <CardTitle>{t('pages.dashboard.custoTotal6Meses')}</CardTitle>
            </CardHeader>
            <div className="h-64 px-2 pb-4">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                  <XAxis
                    dataKey="label"
                    tick={{ fill: 'var(--color-muted-foreground)', fontSize: 11 }}
                    axisLine={{ stroke: 'var(--color-border)' }}
                    tickLine={false}
                  />
                  <YAxis
                    tick={{ fill: 'var(--color-muted-foreground)', fontSize: 11 }}
                    axisLine={false}
                    tickLine={false}
                    width={48}
                    tickFormatter={(v: number) => (v >= 1000 ? `R$${Math.round(v / 1000)}k` : `R$${v}`)}
                  />
                  <Tooltip
                    cursor={{ fill: 'var(--color-muted)' }}
                    contentStyle={{
                      background: 'var(--color-card)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 8,
                      fontSize: 12,
                    }}
                    formatter={(v) => [
                      Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
                      t('pages.dashboard.custo'),
                    ]}
                  />
                  <Bar dataKey="total" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('pages.dashboard.veiculosPorStatus')}</CardTitle>
            </CardHeader>
            {statusData.length === 0 ? (
              <p className="p-8 text-center text-xs text-muted-foreground">{t('pages.dashboard.semVeiculosCadastrados')}</p>
            ) : (
              <>
                <div className="flex justify-center py-4">
                  <DonutChart
                    segments={statusData.map((d) => ({ value: d.count, color: VEHICLE_STATUS_COLOR[d.status] }))}
                  />
                </div>
                <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1 px-5 pb-4 text-[11px] text-muted-foreground">
                  {statusData.map((d) => (
                    <li key={d.status} className="flex items-center gap-1.5">
                      <span
                        className="size-1.5 rounded-full"
                        style={{ background: VEHICLE_STATUS_COLOR[d.status] }}
                      />
                      {t(`status.veiculo.${d.status}`)} ({d.count})
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Card>
        </div>
      )}

      {!loading && totalAlertas > 0 && (
        <Card className="mb-5">
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <AlertTriangle className="size-4 text-status-warning" />
              {t('pages.dashboard.alertasManutencaoCnh')}
            </CardTitle>
          </CardHeader>
          <div className="grid grid-cols-1 gap-4 px-5 pb-5 sm:grid-cols-2">
            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                {t('pages.dashboard.manutencaoDeVeiculos')}
              </p>
              {maintenanceAlerts.length === 0 ? (
                <p className="text-xs text-muted-foreground">{t('common.nenhumAlerta')}</p>
              ) : (
                <ul className="space-y-2">
                  {maintenanceAlerts.map((a) => (
                    <AlertRow
                      key={a.vehicleId}
                      label={`${a.plate} — ${a.brand} ${a.model}`}
                      diasRestantes={a.diasRestantes ?? null}
                      detalhe={
                        a.kmRestante != null
                          ? t('common.kmRestantes', { n: a.kmRestante })
                          : a.diasRestantes != null
                            ? `${a.proximaManutencaoData}`
                            : ''
                      }
                    />
                  ))}
                </ul>
              )}
            </div>
            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                {t('pages.dashboard.cnhDeMotoristas')}
              </p>
              {licenseAlerts.length === 0 ? (
                <p className="text-xs text-muted-foreground">{t('common.nenhumAlerta')}</p>
              ) : (
                <ul className="space-y-2">
                  {licenseAlerts.map((a) => (
                    <AlertRow
                      key={a.driverId}
                      label={a.name!}
                      diasRestantes={a.diasRestantes ?? null}
                      detalhe={a.cnhValidade!}
                    />
                  ))}
                </ul>
              )}
            </div>
          </div>
        </Card>
      )}

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>{t('pages.dashboard.veiculosRecentes')}</CardTitle>
          <button type="button" onClick={onViewVehicles} className="text-xs font-medium text-primary hover:underline">
            {t('common.verTodos')}
          </button>
        </CardHeader>
        {loading ? (
          <TableSkeleton rows={5} columns={4} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.dashboard.tabela.placa')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.dashboard.tabela.marcaModelo')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.dashboard.tabela.odometro')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.dashboard.tabela.status')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {vehicles.slice(0, 10).map((v) => (
                  <tr key={v.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5">
                      <PlacaBR placa={v.plate!} size="sm" />
                    </td>
                    <td className="px-5 py-2.5 font-medium text-foreground">
                      {v.brand} {v.model}
                    </td>
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{v.odometerKm} km</td>
                    <td className="px-5 py-2.5">
                      <StatusBadgeVeiculo status={v.status} />
                    </td>
                  </tr>
                ))}
                {vehicles.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      {t('pages.dashboard.nenhumVeiculoCadastrado')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

/** diasRestantes negativo = já venceu (tom danger); dentro do prazo = warning. */
function AlertRow({
  label,
  diasRestantes,
  detalhe,
}: {
  label: string;
  diasRestantes: number | null;
  detalhe: string;
}) {
  const { t } = useTranslation();
  const vencido = diasRestantes != null && diasRestantes < 0;
  return (
    <li className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2">
      <div className="min-w-0">
        <p className="truncate text-xs font-medium text-foreground">{label}</p>
        <p className="text-[11px] text-muted-foreground">{detalhe}</p>
      </div>
      <span
        className={cn(
          'shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium',
          vencido ? 'bg-status-danger-bg text-status-danger' : 'bg-status-warning-bg text-status-warning',
        )}
      >
        {diasRestantes == null
          ? '—'
          : vencido
            ? t('common.diasVencido', { n: Math.abs(diasRestantes) })
            : t('common.emDiasCurto', { n: diasRestantes })}
      </span>
    </li>
  );
}
