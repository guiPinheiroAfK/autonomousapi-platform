import { useEffect, useState } from 'react';
import { AlertTriangle, ClipboardList, Truck, Users, Wrench } from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  coreApi,
  type DriverLicenseAlertResponse,
  type DriverResponse,
  type MonthlyCostResponse,
  type VehicleMaintenanceAlertResponse,
  type VehicleResponse,
} from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatCard } from '../components/shared/StatCard';
import { cn } from '../lib/utils';
import { monthLabel } from '../lib/format';

const VEHICLE_STATUS_COLOR: Record<string, string> = {
  ATIVO: 'var(--color-status-success)',
  MANUTENCAO: 'var(--color-status-warning)',
  INATIVO: 'var(--color-status-neutral)',
};
const VEHICLE_STATUS_LABEL: Record<string, string> = {
  ATIVO: 'Ativo',
  MANUTENCAO: 'Em manutenção',
  INATIVO: 'Inativo',
};

interface Props {
  onViewVehicles: () => void;
}

export function DashboardPage({ onViewVehicles }: Props) {
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [maintenanceAlerts, setMaintenanceAlerts] = useState<VehicleMaintenanceAlertResponse[]>([]);
  const [licenseAlerts, setLicenseAlerts] = useState<DriverLicenseAlertResponse[]>([]);
  const [costTrend, setCostTrend] = useState<MonthlyCostResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      coreApi.vehicles.list(),
      coreApi.drivers.list(),
      coreApi.vehicles.maintenanceDue(),
      coreApi.drivers.licenseExpiring(),
      coreApi.vehicles.costTrend(),
    ])
      .then(([v, d, m, l, t]) => {
        setVehicles(v);
        setDrivers(d);
        setMaintenanceAlerts(m);
        setLicenseAlerts(l);
        setCostTrend(t);
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
        <h2 className="font-display text-lg font-semibold text-foreground">Dashboard</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">Visão geral da frota</p>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Veículos" value={vehicles.length} hint="Total da frota" icon={Truck} />
        <StatCard label="Em Operação" value={ativos} tone="success" hint="Veículos ativos" icon={ClipboardList} />
        <StatCard label="Em Manutenção" value={manutencao} tone="warning" hint="Fora de operação" icon={Wrench} />
        <StatCard label="Motoristas" value={drivers.length} hint="Cadastrados" icon={Users} />
      </div>

      {!loading && (
        <div className="mb-5 grid grid-cols-1 gap-3 lg:grid-cols-[2fr_1fr]">
          <Card>
            <CardHeader>
              <CardTitle>Custo de manutenção — últimos 6 meses</CardTitle>
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
                      'Custo',
                    ]}
                  />
                  <Bar dataKey="total" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Veículos por status</CardTitle>
            </CardHeader>
            {statusData.length === 0 ? (
              <p className="p-8 text-center text-xs text-muted-foreground">Sem veículos cadastrados.</p>
            ) : (
              <>
                <div className="h-40 px-2">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={statusData}
                        dataKey="count"
                        nameKey="status"
                        innerRadius={45}
                        outerRadius={70}
                        paddingAngle={2}
                        strokeWidth={0}
                      >
                        {statusData.map((d) => (
                          <Cell key={d.status} fill={VEHICLE_STATUS_COLOR[d.status]} />
                        ))}
                      </Pie>
                      <Tooltip
                        contentStyle={{
                          background: 'var(--color-card)',
                          border: '1px solid var(--color-border)',
                          borderRadius: 8,
                          fontSize: 12,
                        }}
                        formatter={(v, _n, entry) => [
                          Number(v),
                          VEHICLE_STATUS_LABEL[(entry.payload as { status: string }).status],
                        ]}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1 px-5 pb-4 text-[11px] text-muted-foreground">
                  {statusData.map((d) => (
                    <li key={d.status} className="flex items-center gap-1.5">
                      <span
                        className="size-1.5 rounded-full"
                        style={{ background: VEHICLE_STATUS_COLOR[d.status] }}
                      />
                      {VEHICLE_STATUS_LABEL[d.status]} ({d.count})
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
              Alertas de manutenção e CNH
            </CardTitle>
          </CardHeader>
          <div className="grid grid-cols-1 gap-4 px-5 pb-5 sm:grid-cols-2">
            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                Manutenção de veículos
              </p>
              {maintenanceAlerts.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum alerta.</p>
              ) : (
                <ul className="space-y-2">
                  {maintenanceAlerts.map((a) => (
                    <AlertRow
                      key={a.vehicleId}
                      label={`${a.plate} — ${a.brand} ${a.model}`}
                      diasRestantes={a.diasRestantes ?? null}
                      detalhe={
                        a.kmRestante != null
                          ? `${a.kmRestante} km restantes`
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
                CNH de motoristas
              </p>
              {licenseAlerts.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum alerta.</p>
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
          <CardTitle>Veículos recentes</CardTitle>
          <button type="button" onClick={onViewVehicles} className="text-xs font-medium text-primary hover:underline">
            Ver todos →
          </button>
        </CardHeader>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Placa
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Marca/Modelo
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Odômetro
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Status
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
                      Nenhum veículo cadastrado ainda.
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
        {diasRestantes == null ? '—' : vencido ? `${Math.abs(diasRestantes)}d vencido` : `em ${diasRestantes}d`}
      </span>
    </li>
  );
}
