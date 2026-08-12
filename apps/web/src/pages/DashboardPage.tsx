import { useEffect, useState } from 'react';
import { coreApi, type DriverResponse, type VehicleResponse } from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatCard } from '../components/StatCard';

interface Props {
  onViewVehicles: () => void;
}

export function DashboardPage({ onViewVehicles }: Props) {
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([coreApi.vehicles.list(), coreApi.drivers.list()])
      .then(([v, d]) => {
        setVehicles(v);
        setDrivers(d);
      })
      .finally(() => setLoading(false));
  }, []);

  const ativos = vehicles.filter((v) => v.status === 'ATIVO').length;
  const manutencao = vehicles.filter((v) => v.status === 'MANUTENCAO').length;

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
