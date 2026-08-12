import { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import {
  coreApi,
  type DriverLicenseAlertResponse,
  type DriverResponse,
  type VehicleMaintenanceAlertResponse,
  type VehicleResponse,
} from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatCard } from '../components/StatCard';
import { cn } from '../lib/utils';

interface Props {
  onViewVehicles: () => void;
}

export function DashboardPage({ onViewVehicles }: Props) {
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [maintenanceAlerts, setMaintenanceAlerts] = useState<VehicleMaintenanceAlertResponse[]>([]);
  const [licenseAlerts, setLicenseAlerts] = useState<DriverLicenseAlertResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      coreApi.vehicles.list(),
      coreApi.drivers.list(),
      coreApi.vehicles.maintenanceDue(),
      coreApi.drivers.licenseExpiring(),
    ])
      .then(([v, d, m, l]) => {
        setVehicles(v);
        setDrivers(d);
        setMaintenanceAlerts(m);
        setLicenseAlerts(l);
      })
      .finally(() => setLoading(false));
  }, []);

  const ativos = vehicles.filter((v) => v.status === 'ATIVO').length;
  const manutencao = vehicles.filter((v) => v.status === 'MANUTENCAO').length;
  const totalAlertas = maintenanceAlerts.length + licenseAlerts.length;

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Dashboard</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">Visão geral da frota</p>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Veículos" value={vehicles.length} hint="Total da frota" />
        <StatCard label="Em Operação" value={ativos} tone="success" hint="Veículos ativos" />
        <StatCard label="Em Manutenção" value={manutencao} tone="warning" hint="Fora de operação" />
        <StatCard label="Motoristas" value={drivers.length} hint="Cadastrados" />
      </div>

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
