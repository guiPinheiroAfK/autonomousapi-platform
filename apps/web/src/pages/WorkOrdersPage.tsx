import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Eye, Plus, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type DriverResponse,
  type VehicleResponse,
  type WorkOrderItemRequest,
  type WorkOrderRequest,
  type WorkOrderResponse,
} from '../api/client';
import { PRIORIDADE_OS_OPTIONS, STATUS_OS_OPTIONS, TIPO_OS_OPTIONS } from '../lib/workOrderLabels';
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
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';
import { TableSkeleton } from '../components/shared/TableSkeleton';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';

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
  const { t } = useTranslation();
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
      .then((res) => setOrders(res.content))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.workOrders.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);
  useEffect(() => {
    // Paginado (spec de escala) — size grande cobre a frota inteira na maioria dos tenants,
    // já que esta tela usa a lista pra popular o filtro e o seletor de veículo da OS.
    coreApi.vehicles.list(0, 500).then((res) => setVehicles(res.content));
    coreApi.drivers.list().then((res) => setDrivers(res.content));
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
        toast.success(t('pages.workOrders.toasts.atualizada'));
      } else {
        await coreApi.workOrders.create(form);
        toast.success(t('pages.workOrders.toasts.criada'));
      }
      setModalOpen(false);
      refresh();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : t('pages.workOrders.toasts.falhaSalvar'));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    await deleteWithConfirm({
      confirmMessage: t('pages.workOrders.toasts.confirmarExcluir'),
      remove: () => coreApi.workOrders.remove(id),
      successMessage: t('pages.workOrders.toasts.excluida'),
      fallbackErrorMessage: t('pages.workOrders.toasts.falhaExcluir'),
      onSuccess: refresh,
    });
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.workOrders.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.workOrders.subtitulo')}</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> {t('pages.workOrders.novaOS')}
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
            placeholder={t('pages.workOrders.buscarPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          <Select value={status} onChange={(e) => setStatus(e.target.value)} className="w-44">
            <option value="todos">{t('pages.workOrders.todosOsStatus')}</option>
            {STATUS_OS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {t(`status.os.${s}`)}
              </option>
            ))}
          </Select>
          <Select value={tipo} onChange={(e) => setTipo(e.target.value)} className="w-40">
            <option value="todos">{t('pages.workOrders.todosOsTipos')}</option>
            {TIPO_OS_OPTIONS.map((opt) => (
              <option key={opt} value={opt}>
                {t(`status.tipoOS.${opt}`)}
              </option>
            ))}
          </Select>
          <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground">
            <span>{t('pages.workOrders.osContagem', { n: filtered.length })}</span>
            <Separator orientation="vertical" className="h-4" />
            <span className="font-data font-semibold text-foreground">{formatBRL(totalCusto)}</span>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.workOrders.todasAsOS')}</CardTitle>
        </CardHeader>
        {loading ? (
          <TableSkeleton rows={6} columns={10} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <Th>{t('pages.workOrders.tabela.os')}</Th>
                  <Th>{t('pages.workOrders.tabela.veiculo')}</Th>
                  <Th>{t('pages.workOrders.tabela.motorista')}</Th>
                  <Th>{t('pages.workOrders.tabela.tipo')}</Th>
                  <Th>{t('pages.workOrders.tabela.prioridade')}</Th>
                  <Th>{t('pages.workOrders.tabela.abertura')}</Th>
                  <Th>{t('pages.workOrders.tabela.previsao')}</Th>
                  <Th>{t('pages.workOrders.tabela.status')}</Th>
                  <Th className="text-right">{t('pages.workOrders.tabela.custo')}</Th>
                  <Th />
                </tr>
              </thead>
              <StaggerGroup as="tbody" className="divide-y divide-border">
                {filtered.map((os) => (
                  <StaggerItem as="tr" key={os.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5 font-data font-medium text-foreground">{os.numero}</td>
                    <td className="px-5 py-2.5">
                      <div className="flex items-center gap-2">
                        <PlacaBR placa={os.vehiclePlate ?? ''} size="sm" />
                        <span className="text-foreground">{os.vehicleName}</span>
                      </div>
                    </td>
                    <td className="px-5 py-2.5 text-muted-foreground">{os.driverName ?? '—'}</td>
                    <td className="px-5 py-2.5 text-muted-foreground">{os.tipo ? t(`status.tipoOS.${os.tipo}`) : '—'}</td>
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
                          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                        >
                          <Eye className="size-4" />
                        </button>
                        <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openEdit(os)}>
                          {t('pages.workOrders.editar')}
                        </Button>
                        <Button
                          variant="link"
                          size="sm"
                          className="h-auto p-0 text-destructive"
                          onClick={() => handleDelete(os.id!)}
                        >
                          {t('pages.workOrders.excluir')}
                        </Button>
                      </div>
                    </td>
                  </StaggerItem>
                ))}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={10} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      {t('pages.workOrders.nenhumaOSEncontrada')}
                    </td>
                  </tr>
                )}
              </StaggerGroup>
            </table>
          </div>
        )}
      </Card>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? t('pages.workOrders.editarOS') : t('pages.workOrders.novaOS')}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="vehicleId">{t('pages.workOrders.form.veiculo')}</Label>
              <Select
                id="vehicleId"
                value={form.vehicleId}
                onChange={(e) => setForm({ ...form, vehicleId: e.target.value })}
                required
              >
                <option value="" disabled>
                  {t('pages.workOrders.form.selecione')}
                </option>
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.plate} — {v.brand} {v.model}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="driverId">{t('pages.workOrders.form.motoristaOpcional')}</Label>
              <Select
                id="driverId"
                value={form.driverId ?? ''}
                onChange={(e) => setForm({ ...form, driverId: e.target.value || undefined })}
              >
                <option value="">{t('pages.workOrders.form.semMotorista')}</option>
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
              <Label htmlFor="tipo">{t('pages.workOrders.form.tipo')}</Label>
              <Select id="tipo" value={form.tipo} onChange={(e) => setForm({ ...form, tipo: e.target.value as WorkOrderRequest['tipo'] })}>
                {TIPO_OS_OPTIONS.map((opt) => (
                  <option key={opt} value={opt}>
                    {t(`status.tipoOS.${opt}`)}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="status">{t('pages.workOrders.form.status')}</Label>
              <Select
                id="status"
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as WorkOrderRequest['status'] })}
              >
                {STATUS_OS_OPTIONS.map((s) => (
                  <option key={s} value={s}>
                    {t(`status.os.${s}`)}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="prioridade">{t('pages.workOrders.form.prioridade')}</Label>
              <Select
                id="prioridade"
                value={form.prioridade}
                onChange={(e) => setForm({ ...form, prioridade: e.target.value as WorkOrderRequest['prioridade'] })}
              >
                {PRIORIDADE_OS_OPTIONS.map((p) => (
                  <option key={p} value={p}>
                    {t(`status.prioridade.${p}`)}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          <div>
            <Label htmlFor="descricaoProblema">{t('pages.workOrders.form.descricaoProblema')}</Label>
            <textarea
              id="descricaoProblema"
              value={form.descricaoProblema}
              onChange={(e) => setForm({ ...form, descricaoProblema: e.target.value })}
              required
              rows={2}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring sm:text-sm"
            />
          </div>

          <div>
            <Label htmlFor="observacoes">{t('pages.workOrders.form.observacoesOpcional')}</Label>
            <textarea
              id="observacoes"
              value={form.observacoes ?? ''}
              onChange={(e) => setForm({ ...form, observacoes: e.target.value || undefined })}
              rows={2}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring sm:text-sm"
            />
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <Label htmlFor="responsavelOficina">{t('pages.workOrders.form.oficina')}</Label>
              <Input
                id="responsavelOficina"
                value={form.responsavelOficina}
                onChange={(e) => setForm({ ...form, responsavelOficina: e.target.value })}
                required
              />
            </div>
            <div>
              <Label htmlFor="dataAbertura">{t('pages.workOrders.form.abertura')}</Label>
              <Input
                id="dataAbertura"
                type="date"
                value={form.dataAbertura}
                onChange={(e) => setForm({ ...form, dataAbertura: e.target.value })}
                required
              />
            </div>
            <div>
              <Label htmlFor="previsaoConclusao">{t('pages.workOrders.form.previsao')}</Label>
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
            <Label htmlFor="kmAbertura">{t('pages.workOrders.form.odometroAbertura')}</Label>
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
              <Label>{t('pages.workOrders.form.itens')}</Label>
              <Button type="button" variant="outline" size="sm" onClick={addItem}>
                <Plus /> {t('pages.workOrders.form.item')}
              </Button>
            </div>
            <div className="space-y-2">
              {form.itens.map((item, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input
                    placeholder={t('pages.workOrders.form.descricao')}
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
              {t('pages.workOrders.form.cancelar')}
            </Button>
            <Button type="submit" disabled={saving}>
              {saving
                ? t('pages.workOrders.form.salvando')
                : editingId
                  ? t('pages.workOrders.form.salvar')
                  : t('pages.workOrders.form.criarOS')}
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
                    {selected.tipo ? t(`status.tipoOS.${selected.tipo}`) : '—'} ·{' '}
                    {t('pages.workOrders.detalhe.abertaEm', {
                      data: selected.dataAbertura ? formatDateBR(selected.dataAbertura) : '—',
                    })}
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
                      {selected.driverName ?? t('pages.workOrders.detalhe.semMotorista')} ·{' '}
                      {selected.kmAbertura?.toLocaleString('pt-BR')} km
                    </p>
                  </div>
                </div>
                <StatusBadgePrioridade prioridade={selected.prioridade} />
              </div>

              <div>
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {t('pages.workOrders.detalhe.descricaoProblema')}
                </p>
                <p className="text-xs text-foreground">{selected.descricaoProblema}</p>
                {selected.observacoes && (
                  <p className="mt-1 text-xs italic text-muted-foreground">{selected.observacoes}</p>
                )}
              </div>

              <div>
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {t('pages.workOrders.detalhe.itensDaOS')}
                </p>
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-border text-muted-foreground">
                      <th className="py-1.5 text-left font-medium">{t('pages.workOrders.form.descricao')}</th>
                      <th className="py-1.5 text-right font-medium">{t('pages.workOrders.detalhe.qtd')}</th>
                      <th className="py-1.5 text-right font-medium">{t('pages.workOrders.detalhe.unitario')}</th>
                      <th className="py-1.5 text-right font-medium">{t('pages.workOrders.detalhe.subtotal')}</th>
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
