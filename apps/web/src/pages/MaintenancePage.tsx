import { useEffect, useMemo, useState } from 'react';
import { HandCoins, PackageSearch, Wrench } from 'lucide-react';
import { coreApi, type VehicleResponse } from '../api/client';
import { Badge } from '../components/ui/badge';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Select } from '../components/ui/select';
import { StatCard } from '../components/shared/StatCard';
import { PlacaBR } from '../components/shared/PlacaBR';
import { formatBRL, formatDateBR } from '../lib/format';

/** Peças/oficina são campos só-visuais (não existem no backend ainda) — atribuídos
 *  deterministicamente a partir do id do lançamento, só pra enriquecer a tela. */
const PECAS_POOL = [
  ['Óleo de motor', 'Filtro de óleo'],
  ['Pastilha de freio'],
  ['Amortecedor'],
  ['Correia dentada', 'Tensionador'],
  ['Bateria'],
  ['Filtro de ar'],
  [],
];
const OFICINA_POOL = ['Oficina Central RotaCerta', 'Bosch Service Cascavel', 'Moto Center RotaCerta'];

function pick<T>(pool: T[], key: string): T {
  let hash = 0;
  for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
  return pool[hash % pool.length];
}

interface Row {
  id: string;
  data: string;
  placa: string;
  veiculo: string;
  descricao: string;
  pecas: string[];
  oficina: string;
  custo: number;
}

export function MaintenancePage() {
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [veiculoFiltro, setVeiculoFiltro] = useState('todos');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Duas requisições fixas, independente do tamanho da frota. Antes era 1 + uma por
    // veículo: o backend agora devolve os custos já com os dados do veículo embutidos.
    Promise.all([coreApi.vehicles.list(), coreApi.expenses.fleetList('MANUTENCAO')])
      .then(([vehicleList, costs]) => {
        setVehicles(vehicleList);
        setRows(
          costs.map((c) => ({
            id: c.id!,
            data: c.data!,
            placa: c.plate!,
            veiculo: `${c.brand} ${c.model}`,
            descricao: c.descricao ?? '',
            pecas: pick(PECAS_POOL, c.id!),
            oficina: pick(OFICINA_POOL, c.vehicleId!),
            custo: Number(c.valor),
          })),
        );
      })
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(
    () => (veiculoFiltro === 'todos' ? rows : rows.filter((r) => r.placa === veiculoFiltro)),
    [rows, veiculoFiltro],
  );

  const totalPeriodo = filtered.reduce((sum, r) => sum + r.custo, 0);
  const custoPecas = filtered.reduce((sum, r) => sum + r.custo * 0.65, 0);
  const custoMaoDeObra = totalPeriodo - custoPecas;

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Manutenção</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">Histórico de manutenções de toda a frota</p>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label="Manutenções registradas" value={filtered.length} icon={Wrench} />
        <StatCard label="Custo em peças" value={formatBRL(custoPecas)} tone="warning" icon={PackageSearch} />
        <StatCard label="Custo em mão de obra" value={formatBRL(custoMaoDeObra)} tone="success" icon={HandCoins} />
      </div>

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Select value={veiculoFiltro} onChange={(e) => setVeiculoFiltro(e.target.value)} className="w-56">
            <option value="todos">Todos os veículos</option>
            {vehicles.map((v) => (
              <option key={v.id} value={v.plate}>
                {v.plate} · {v.brand} {v.model}
              </option>
            ))}
          </Select>
          <div className="ml-auto text-xs text-muted-foreground">
            Total do período: <span className="font-data font-semibold text-foreground">{formatBRL(totalPeriodo)}</span>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Registros de manutenção</CardTitle>
        </CardHeader>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Data
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Veículo
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Descrição
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Peças trocadas
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Oficina
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Custo total
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map((r) => (
                  <tr key={r.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(r.data)}</td>
                    <td className="px-5 py-2.5">
                      <div className="flex items-center gap-2">
                        <PlacaBR placa={r.placa} size="sm" />
                        <span className="text-foreground">{r.veiculo}</span>
                      </div>
                    </td>
                    <td className="max-w-[220px] px-5 py-2.5 text-muted-foreground">{r.descricao}</td>
                    <td className="px-5 py-2.5">
                      {r.pecas.length === 0 ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {r.pecas.map((p) => (
                            <Badge key={p} variant="outline">
                              {p}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </td>
                    <td className="px-5 py-2.5 text-muted-foreground">{r.oficina}</td>
                    <td className="px-5 py-2.5 text-right font-data font-semibold text-foreground">
                      {formatBRL(r.custo)}
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Nenhuma manutenção registrada.
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
