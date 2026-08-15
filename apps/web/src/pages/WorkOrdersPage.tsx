import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Eye, Plus, Trash2 } from 'lucide-react';
import {
  coreApi,
  type DriverResponse,
  type VehicleResponse,
  type WorkOrderItemRequest,
  type WorkOrderRequest,
  type WorkOrderResponse,
} from '../api/client';
import { PRIORIDADE_OS_OPTIONS, STATUS_OS_OPTIONS, TIPO_OS_LABEL, TIPO_OS_OPTIONS } from '../lib/workOrderLabels';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeOS, StatusBadgePrioridade } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { Separator } from '../components/ui/separator';
import { formatBRL, formatDateBR } from '../lib/format';

const STATUS_LABEL: Record<string, string> = {
  ABERTA: 'Aberta',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  ATRASADA: 'Atrasada',
  CANCELADA: 'Cancelada',
};
const PRIORIDADE_LABEL: Record<string, string> = { BAIXA: 'Baixa', MEDIA: 'Média', ALTA: 'Alta' };

type ItemForm = WorkOrderItemRequest;

const EMPTY_FORM: WorkOrderRequest = {
  vehicleId: '',
  driverId: undefined,
  tipo: 'CORRETIVA',
  status: 'ABERTA',
  prioridade: 'MEDIA',
  descricaoProblema: '',
  observacoes: undefined,
  responsavelOficina: '',
  dataAbertura: new Date().toISOString().slice(0, 10),
  previsaoConclusao: new Date().toISOString().slice(0, 10),
  kmAbertura: 0,
  itens: [{ descricao: '', quantidade: 1, valorUnitario: 0 }],
};

export function WorkOrdersPage() {
  const [orders, setOrders] = useState<WorkOrderResponse[]>([]);
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string | 'todos'>('todos');
  const [tipo, setTipo] = useState<string | 'todos'>('todos');
  const [selected, setSelected] = useState<WorkOrderResponse | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<WorkOrderRequest>(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  function refresh() {
    setLoading(true);
    coreApi.workOrders
      .list()
      .then(setOrders)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar ordens de serviço'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);
  useEffect(() => {
    coreApi.vehicles.list().then(setVehicles);
    coreApi.drivers.list().then(setDrivers);
  }, []);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return orders.filter((os) => {
      if (status !== 'todos' && os.status !== status) return false;
      if (tipo !== 'todos' && os.tipo !== tipo) return false;
      if (term) {
        const haystack = `${os.numero ?? ''} ${os.vehiclePlate ?? ''} ${os.vehicleName ?? ''}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });
  }, [orders, search, status, tipo]);

  const totalCusto = filtered.reduce((sum, os) => sum + Number(os.custoTotal ?? 0), 0);

  function openCreate() {
    setEditingId(null);
    setForm({ ...EMPTY_FORM, vehicleId: vehicles[0]?.id ?? '' });
    setFormError('');
    setModalOpen(true);
  }

  function openEdit(os: WorkOrderResponse) {
    setEditingId(os.id!);
    setForm({
      vehicleId: os.vehicleId!,
      driverId: os.driverId ?? undefined,
      tipo: os.tipo!,
      status: os.status!,
      prioridade: os.prioridade!,
      descricaoProblema: os.descricaoProblema!,
      observacoes: os.observacoes ?? undefined,
      responsavelOficina: os.responsavelOficina!,
      dataAbertura: os.dataAbertura!,
      previsaoConclusao: os.previsaoConclusao!,
      kmAbertura: os.kmAbertura!,
      itens: (os.itens ?? []).map((i) => ({
        descricao: i.descricao!,
        quantidade: i.quantidade!,
        valorUnitario: i.valorUnitario!,
      })),
    });
    setFormError('');
    setModalOpen(true);
  }

  function updateItem(index: number, patch: Partial<ItemForm>) {
    setForm((f) => ({
      ...f,
      itens: f.itens.map((it, i) => (i === index ? { ...it, ...patch } : it)),
    }));
  }

  function addItem() {
    setForm((f) => ({ ...f, itens: [...f.itens, { descricao: '', quantidade: 1, valorUnitario: 0 }] }));
  }

  function removeItem(index: number) {
    setForm((f) => ({ ...f, itens: f.itens.filter((_, i) => i !== index) }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError('');
    setSaving(true);
    try {
      if (editingId) {
        await coreApi.workOrders.update(editingId, form);
      } else {
        await coreApi.workOrders.create(form);
      }
      setModalOpen(false);
      refresh();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Falha ao salvar ordem de serviço');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Excluir esta ordem de serviço?')) return;
    try {
      await coreApi.workOrders.remove(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao excluir ordem de serviço');
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Ordens de Serviço</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">Acompanhamento das OSs da oficina</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> Nova OS
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Input
            placeholder="Buscar por número, placa ou modelo..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          <Select value={status} onChange={(e) => setStatus(e.target.value)} className="w-44">
            <option value="todos">Todos os status</option>
            {STATUS_OS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </Select>
          <Select value={tipo} onChange={(e) => setTipo(e.target.value)} className="w-40">
            <option value="todos">Todos os tipos</option>
            {TIPO_OS_OPTIONS.map((t) => (
              <option key={t} value={t}>
                {TIPO_OS_LABEL[t]}
              </option>
            ))}
          </Select>
          <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground">
            <span>{filtered.length} OS</span>
            <Separator orientation="vertical" className="h-4" />
            <span className="font-data font-semibold text-foreground">{formatBRL(totalCusto)}</span>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Todas as ordens de serviço</CardTitle>
        </CardHeader>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <Th>OS</Th>
                  <Th>Veículo</Th>
                  <Th>Motorista</Th>
                  <Th>Tipo</Th>
                  <Th>Prioridade</Th>
                  <Th>Abertura</Th>
                  <Th>Previsão</Th>
                  <Th>Status</Th>
                  <Th className="text-right">Custo</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map((os) => (
                  <tr key={os.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5 font-data font-medium text-foreground">{os.numero}</td>
                    <td className="px-5 py-2.5">
                      <div className="flex items-center gap-2">
                        <PlacaBR placa={os.vehiclePlate ?? ''} size="sm" />
                        <span className="text-foreground">{os.vehicleName}</span>
                      </div>
                    </td>
                    <td className="px-5 py-2.5 text-muted-foreground">{os.driverName ?? '—'}</td>
                    <td className="px-5 py-2.5 text-muted-foreground">{TIPO_OS_LABEL[os.tipo ?? ''] ?? os.tipo}</td>
                    <td className="px-5 py-2.5">
                      <StatusBadgePrioridade prioridade={os.prioridade} />
                    </td>
                    <td className="px-5 py-2.5 font-data text-muted-foreground">
                      {os.dataAbertura ? formatDateBR(os.dataAbertura) : '—'}
                    </td>
                    <td className="px-5 py-2.5 font-data text-muted-foreground">
                      {os.previsaoConclusao ? formatDateBR(os.previsaoConclusao) : '—'}
                    </td>
                    <td className="px-5 py-2.5">
                      <StatusBadgeOS status={os.status} />
                    </td>
                    <td className="px-5 py-2.5 text-right font-data font-semibold text-foreground">
                      {formatBRL(Number(os.custoTotal ?? 0))}
                    </td>
                    <td className="px-5 py-2.5">
                      <div className="flex items-center gap-3">
                        <button
                          type="button"
                          onClick={() => setSelected(os)}
                          className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                        >
                          <Eye className="size-4" />
                        </button>
                        <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openEdit(os)}>
                          Editar
                        </Button>
                        <Button
                          variant="link"
                          size="sm"
                          className="h-auto p-0 text-destructive"
                          onClick={() => handleDelete(os.id!)}
                        >
                          Excluir
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={10} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Nenhuma OS encontrada.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editingId ? 'Editar OS' : 'Nova OS'}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="vehicleId">Veículo</Label>
              <Select
                id="vehicleId"
                value={form.vehicleId}
                onChange={(e) => setForm({ ...form, vehicleId: e.target.value })}
                required
              >
                <option value="" disabled>
                  Selecione...
                </option>
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.plate} — {v.brand} {v.model}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="driverId">Motorista (opcional)</Label>
              <Select
                id="driverId"
                value={form.driverId ?? ''}
                onChange={(e) => setForm({ ...form, driverId: e.target.value || undefined })}
              >
                <option value="">Sem motorista</option>
                {drivers.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <Label htmlFor="tipo">Tipo</Label>
              <Select id="tipo" value={form.tipo} onChange={(e) => setForm({ ...form, tipo: e.target.value as WorkOrderRequest['tipo'] })}>
                {TIPO_OS_OPTIONS.map((t) => (
                  <option key={t} value={t}>
                    {TIPO_OS_LABEL[t]}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="status">Status</Label>
              <Select
                id="status"
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as WorkOrderRequest['status'] })}
              >
                {STATUS_OS_OPTIONS.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABEL[s]}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="prioridade">Prioridade</Label>
              <Select
                id="prioridade"
                value={form.prioridade}
                onChange={(e) => setForm({ ...form, prioridade: e.target.value as WorkOrderRequest['prioridade'] })}
              >
                {PRIORIDADE_OS_OPTIONS.map((p) => (
                  <option key={p} value={p}>
                    {PRIORIDADE_LABEL[p]}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          <div>
            <Label htmlFor="descricaoProblema">Descrição do problema</Label>
            <textarea
              id="descricaoProblema"
              value={form.descricaoProblema}
              onChange={(e) => setForm({ ...form, descricaoProblema: e.target.value })}
              required
              rows={2}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
            />
          </div>

          <div>
            <Label htmlFor="observacoes">Observações (opcional)</Label>
            <textarea
              id="observacoes"
              value={form.observacoes ?? ''}
              onChange={(e) => setForm({ ...form, observacoes: e.target.value || undefined })}
              rows={2}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
            />
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <Label htmlFor="responsavelOficina">Oficina</Label>
              <Input
                id="responsavelOficina"
                value={form.responsavelOficina}
                onChange={(e) => setForm({ ...form, responsavelOficina: e.target.value })}
                required
              />
            </div>
            <div>
              <Label htmlFor="dataAbertura">Abertura</Label>
              <Input
                id="dataAbertura"
                type="date"
                value={form.dataAbertura}
                onChange={(e) => setForm({ ...form, dataAbertura: e.target.value })}
                required
              />
            </div>
            <div>
              <Label htmlFor="previsaoConclusao">Previsão</Label>
              <Input
                id="previsaoConclusao"
                type="date"
                value={form.previsaoConclusao}
                onChange={(e) => setForm({ ...form, previsaoConclusao: e.target.value })}
                required
              />
            </div>
          </div>

          <div>
            <Label htmlFor="kmAbertura">Odômetro na abertura (km)</Label>
            <Input
              id="kmAbertura"
              type="number"
              min={0}
              value={form.kmAbertura}
              onChange={(e) => setForm({ ...form, kmAbertura: Number(e.target.value) })}
              required
            />
          </div>

          <div className="border-t border-border pt-4">
            <div className="mb-2 flex items-center justify-between">
              <Label>Itens</Label>
              <Button type="button" variant="outline" size="sm" onClick={addItem}>
                <Plus /> Item
              </Button>
            </div>
            <div className="space-y-2">
              {form.itens.map((item, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input
                    placeholder="Descrição"
                    value={item.descricao}
                    onChange={(e) => updateItem(i, { descricao: e.target.value })}
                    required
                    className="flex-1"
                  />
                  <Input
                    type="number"
                    min={1}
                    value={item.quantidade}
                    onChange={(e) => updateItem(i, { quantidade: Number(e.target.value) })}
                    className="w-16"
                    required
                  />
                  <Input
                    type="number"
                    min={0}
                    step="0.01"
                    value={item.valorUnitario}
                    onChange={(e) => updateItem(i, { valorUnitario: Number(e.target.value) })}
                    className="w-28"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => removeItem(i)}
                    disabled={form.itens.length === 1}
                    className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-destructive disabled:opacity-30"
                  >
                    <Trash2 className="size-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {formError && <p className="text-xs text-status-danger">{formError}</p>}

          <div className="flex justify-end gap-3 border-t border-border pt-4">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Salvando...' : editingId ? 'Salvar' : 'Criar OS'}
            </Button>
          </div>
        </form>
      </Modal>

      <Dialog open={selected != null} onOpenChange={(open) => !open && setSelected(null)}>
        {selected && (
          <DialogContent className="max-w-xl">
            <DialogHeader>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <DialogTitle className="font-data text-base">{selected.numero}</DialogTitle>
                  <DialogDescription>
                    {TIPO_OS_LABEL[selected.tipo ?? ''] ?? selected.tipo} · Aberta em{' '}
                    {selected.dataAbertura ? formatDateBR(selected.dataAbertura) : '—'}
                  </DialogDescription>
                </div>
                <StatusBadgeOS status={selected.status} />
              </div>
            </DialogHeader>

            <div className="space-y-4 p-5">
              <div className="flex items-center justify-between rounded-md border border-border p-3">
                <div className="flex items-center gap-2">
                  <PlacaBR placa={selected.vehiclePlate ?? ''} size="sm" />
                  <div>
                    <p className="text-xs font-medium text-foreground">{selected.vehicleName}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {selected.driverName ?? 'Sem motorista'} · {selected.kmAbertura?.toLocaleString('pt-BR')} km
                    </p>
                  </div>
                </div>
                <StatusBadgePrioridade prioridade={selected.prioridade} />
              </div>

              <div>
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Descrição do problema
                </p>
                <p className="text-xs text-foreground">{selected.descricaoProblema}</p>
                {selected.observacoes && (
                  <p className="mt-1 text-xs italic text-muted-foreground">{selected.observacoes}</p>
                )}
              </div>

              <div>
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Itens da OS
                </p>
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-border text-muted-foreground">
                      <th className="py-1.5 text-left font-medium">Descrição</th>
                      <th className="py-1.5 text-right font-medium">Qtd</th>
                      <th className="py-1.5 text-right font-medium">Unitário</th>
                      <th className="py-1.5 text-right font-medium">Subtotal</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {(selected.itens ?? []).map((item, i) => (
                      <tr key={i}>
                        <td className="py-1.5 text-foreground">{item.descricao}</td>
                        <td className="py-1.5 text-right font-data text-muted-foreground">{item.quantidade}</td>
                        <td className="py-1.5 text-right font-data text-muted-foreground">
                          {formatBRL(Number(item.valorUnitario ?? 0))}
                        </td>
                        <td className="py-1.5 text-right font-data text-foreground">
                          {formatBRL(Number(item.subtotal ?? 0))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="mt-2 flex items-center justify-between border-t border-border pt-2">
                  <span className="text-xs text-muted-foreground">{selected.responsavelOficina}</span>
                  <span className="font-data text-sm font-bold text-foreground">
                    {formatBRL(Number(selected.custoTotal ?? 0))}
                  </span>
                </div>
              </div>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </div>
  );
}

function Th({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <th
      className={`px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground ${className ?? ''}`}
    >
      {children}
    </th>
  );
}
