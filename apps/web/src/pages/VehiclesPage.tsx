import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { CircleOff, Eye, Plus, Truck, Wrench } from 'lucide-react';
import {
  coreApi,
  type VehicleCostEntryResponse,
  type VehicleRequest,
  type VehicleResponse,
  type WorkOrderResponse,
} from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeOS, StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import { StatCard } from '../components/shared/StatCard';
import { formatBRL, formatDateBR } from '../lib/format';
import { TIPO_OS_LABEL } from '../lib/workOrderLabels';

const STATUS_OPTIONS = ['ATIVO', 'MANUTENCAO', 'INATIVO'] as const;

const EMPTY_FORM: VehicleRequest = {
  plate: '',
  brand: '',
  model: '',
  modelYear: undefined,
  odometerKm: 0,
  status: 'ATIVO',
  proximaManutencaoData: undefined,
  proximaManutencaoKm: undefined,
};

interface Props {
  onViewCosts: (vehicleId: string, plate: string) => void;
}

export function VehiclesPage({ onViewCosts }: Props) {
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [form, setForm] = useState<VehicleRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const [search, setSearch] = useState('');
  const [statusFiltro, setStatusFiltro] = useState<(typeof STATUS_OPTIONS)[number] | 'todos'>('todos');
  const [detail, setDetail] = useState<VehicleResponse | null>(null);
  const [detailCosts, setDetailCosts] = useState<VehicleCostEntryResponse[]>([]);
  const [detailOS, setDetailOS] = useState<WorkOrderResponse[]>([]);

  function refresh() {
    coreApi.vehicles
      .list()
      .then(setVehicles)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar veículos'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError('');
    setModalOpen(true);
  }

  function openEdit(v: VehicleResponse) {
    setEditingId(v.id!);
    setForm({
      plate: v.plate!,
      brand: v.brand!,
      model: v.model!,
      modelYear: v.modelYear,
      odometerKm: v.odometerKm!,
      status: v.status as VehicleRequest['status'],
      proximaManutencaoData: v.proximaManutencaoData,
      proximaManutencaoKm: v.proximaManutencaoKm,
    });
    setError('');
    setModalOpen(true);
  }

  function openDetail(v: VehicleResponse) {
    setDetail(v);
    setDetailCosts([]);
    setDetailOS([]);
    coreApi.vehicleCosts.list(v.id!).then(setDetailCosts);
    coreApi.workOrders.list(v.id!).then(setDetailOS);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      if (editingId) {
        await coreApi.vehicles.update(editingId, form);
      } else {
        await coreApi.vehicles.create(form);
      }
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao salvar veículo');
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Excluir este veículo?')) return;
    try {
      await coreApi.vehicles.remove(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao excluir veículo');
    }
  }

  const total = vehicles.length;
  const ativos = vehicles.filter((v) => v.status === 'ATIVO').length;
  const manutencao = vehicles.filter((v) => v.status === 'MANUTENCAO').length;
  const inativos = vehicles.filter((v) => v.status === 'INATIVO').length;

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return vehicles.filter((v) => {
      if (statusFiltro !== 'todos' && v.status !== statusFiltro) return false;
      if (term) {
        const haystack = `${v.plate} ${v.brand} ${v.model}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });
  }, [vehicles, search, statusFiltro]);

  const detailManutencao = detailCosts.filter((c) => c.category === 'MANUTENCAO');

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Frota</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">Veículos cadastrados</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> Novo Veículo
        </Button>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total de Veículos" value={total} hint="Toda a frota" icon={Truck} />
        <StatCard label="Ativos" value={ativos} tone="success" hint="Em operação" icon={Truck} />
        <StatCard label="Em Manutenção" value={manutencao} tone="warning" hint="Fora de operação" icon={Wrench} />
        <StatCard label="Inativos" value={inativos} tone="danger" hint="Desativados" icon={CircleOff} />
      </div>

      {error && !modalOpen && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Input
            placeholder="Buscar por placa, marca ou modelo..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          <Select
            value={statusFiltro}
            onChange={(e) => setStatusFiltro(e.target.value as (typeof STATUS_OPTIONS)[number] | 'todos')}
            className="w-44"
          >
            <option value="todos">Todos os status</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
          <span className="ml-auto text-xs text-muted-foreground">{filtered.length} veículo(s)</span>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Todos os veículos</CardTitle>
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
                    Ano
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Odômetro
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Status
                  </th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map((v) => (
                  <tr key={v.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5">
                      <PlacaBR placa={v.plate!} size="sm" />
                    </td>
                    <td className="px-5 py-2.5 font-medium text-foreground">
                      {v.brand} {v.model}
                    </td>
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{v.modelYear ?? '—'}</td>
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{v.odometerKm} km</td>
                    <td className="px-5 py-2.5">
                      <StatusBadgeVeiculo status={v.status} />
                    </td>
                    <td className="px-5 py-2.5">
                      <div className="flex items-center gap-4">
                        <button
                          type="button"
                          onClick={() => openDetail(v)}
                          className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                        >
                          <Eye className="size-4" />
                        </button>
                        <Button variant="link" size="sm" className="h-auto p-0" onClick={() => onViewCosts(v.id!, v.plate!)}>
                          Custos
                        </Button>
                        <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openEdit(v)}>
                          Editar
                        </Button>
                        <Button
                          variant="link"
                          size="sm"
                          className="h-auto p-0 text-destructive"
                          onClick={() => handleDelete(v.id!)}
                        >
                          Excluir
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Nenhum veículo encontrado.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editingId ? 'Editar Veículo' : 'Novo Veículo'}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="plate">Placa</Label>
            <Input id="plate" value={form.plate} onChange={(e) => setForm({ ...form, plate: e.target.value })} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="brand">Marca</Label>
              <Input id="brand" value={form.brand} onChange={(e) => setForm({ ...form, brand: e.target.value })} required />
            </div>
            <div>
              <Label htmlFor="model">Modelo</Label>
              <Input id="model" value={form.model} onChange={(e) => setForm({ ...form, model: e.target.value })} required />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="modelYear">Ano</Label>
              <Input
                id="modelYear"
                type="number"
                value={form.modelYear ?? ''}
                onChange={(e) => setForm({ ...form, modelYear: e.target.value ? Number(e.target.value) : undefined })}
              />
            </div>
            <div>
              <Label htmlFor="odometerKm">Odômetro (km)</Label>
              <Input
                id="odometerKm"
                type="number"
                min={0}
                value={form.odometerKm}
                onChange={(e) => setForm({ ...form, odometerKm: Number(e.target.value) })}
                required
              />
            </div>
          </div>
          <div>
            <Label htmlFor="status">Status</Label>
            <Select
              id="status"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as VehicleRequest['status'] })}
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4 border-t border-border pt-4">
            <div>
              <Label htmlFor="proximaManutencaoData">Próxima manutenção (data)</Label>
              <Input
                id="proximaManutencaoData"
                type="date"
                value={form.proximaManutencaoData ?? ''}
                onChange={(e) => setForm({ ...form, proximaManutencaoData: e.target.value || undefined })}
              />
            </div>
            <div>
              <Label htmlFor="proximaManutencaoKm">Próxima manutenção (km)</Label>
              <Input
                id="proximaManutencaoKm"
                type="number"
                min={0}
                value={form.proximaManutencaoKm ?? ''}
                onChange={(e) =>
                  setForm({ ...form, proximaManutencaoKm: e.target.value ? Number(e.target.value) : undefined })
                }
              />
            </div>
          </div>

          {error && <p className="text-xs text-status-danger">{error}</p>}

          <div className="flex justify-end gap-3 border-t border-border pt-4">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit">{editingId ? 'Salvar' : 'Adicionar'}</Button>
          </div>
        </form>
      </Modal>

      <Dialog open={detail != null} onOpenChange={(open) => !open && setDetail(null)}>
        {detail && (
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <PlacaBR placa={detail.plate!} />
                  <div>
                    <DialogTitle>{detail.model}</DialogTitle>
                    <DialogDescription>
                      {detail.brand} · {detail.modelYear ?? '—'}
                    </DialogDescription>
                  </div>
                </div>
                <StatusBadgeVeiculo status={detail.status} />
              </div>
            </DialogHeader>

            <div className="p-5">
              <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <InfoStat label="KM atual" value={`${detail.odometerKm?.toLocaleString('pt-BR')} km`} />
                <InfoStat
                  label="Próx. preventiva"
                  value={detail.proximaManutencaoData ? formatDateBR(detail.proximaManutencaoData) : '—'}
                />
                <InfoStat
                  label="Preventiva por km"
                  value={detail.proximaManutencaoKm ? `${detail.proximaManutencaoKm.toLocaleString('pt-BR')} km` : '—'}
                />
                <InfoStat
                  label="Gasto histórico"
                  value={formatBRL(detailCosts.reduce((sum, c) => sum + Number(c.amount ?? 0), 0))}
                />
              </div>

              <Tabs defaultValue="manutencao">
                <TabsList>
                  <TabsTrigger value="manutencao">Manutenção</TabsTrigger>
                  <TabsTrigger value="os">Ordens de Serviço</TabsTrigger>
                </TabsList>
                <TabsContent value="manutencao">
                  {detailManutencao.length === 0 ? (
                    <p className="py-6 text-center text-xs text-muted-foreground">Nenhum lançamento de manutenção.</p>
                  ) : (
                    <ul className="space-y-2">
                      {detailManutencao.map((c) => (
                        <li key={c.id} className="rounded-md border border-border p-3 text-xs">
                          <div className="flex items-center justify-between">
                            <span className="font-medium text-foreground">{c.description}</span>
                            <span className="font-data font-semibold text-foreground">
                              {formatBRL(Number(c.amount))}
                            </span>
                          </div>
                          <span className="text-[11px] text-muted-foreground">{formatDateBR(c.occurredAt!)}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </TabsContent>
                <TabsContent value="os">
                  {detailOS.length === 0 ? (
                    <p className="py-6 text-center text-xs text-muted-foreground">Nenhuma OS registrada ainda.</p>
                  ) : (
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-border text-muted-foreground">
                          <th className="py-1.5 text-left font-medium">OS</th>
                          <th className="py-1.5 text-left font-medium">Tipo</th>
                          <th className="py-1.5 text-left font-medium">Abertura</th>
                          <th className="py-1.5 text-left font-medium">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {detailOS.map((os) => (
                          <tr key={os.id}>
                            <td className="py-1.5 font-data text-foreground">{os.numero}</td>
                            <td className="py-1.5 text-muted-foreground">{TIPO_OS_LABEL[os.tipo ?? ''] ?? os.tipo}</td>
                            <td className="py-1.5 font-data text-muted-foreground">
                              {os.dataAbertura ? formatDateBR(os.dataAbertura) : '—'}
                            </td>
                            <td className="py-1.5">
                              <StatusBadgeOS status={os.status} />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </TabsContent>
              </Tabs>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </div>
  );
}

function InfoStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-border p-2.5">
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="mt-0.5 font-data text-sm font-semibold text-foreground">{value}</p>
    </div>
  );
}
