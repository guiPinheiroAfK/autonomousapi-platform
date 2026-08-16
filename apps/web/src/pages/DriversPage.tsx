import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Car, Eye, Mail, Plus, Star } from 'lucide-react';
import {
  coreApi,
  type DriverAssignmentResponse,
  type DriverRatingResponse,
  type DriverRatingSummaryResponse,
  type DriverRequest,
  type DriverResponse,
  type VehicleResponse,
} from '../api/client';
import { StatusBadgeMotorista } from '../components/shared/StatusBadge';
import { Avatar, AvatarFallback } from '../components/ui/avatar';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { cn } from '../lib/utils';
import { diasAteVencer, formatDateBR, iniciais } from '../lib/format';

const STATUS_OPTIONS = ['ATIVO', 'INATIVO'] as const;

const EMPTY_FORM: DriverRequest = {
  name: '',
  cnh: '',
  phone: '',
  status: 'ATIVO',
  cnhValidade: undefined,
  email: undefined,
};

/** Categoria da CNH é campo só-visual (não existe no backend ainda) — atribuída
 *  deterministicamente a partir do próprio número da CNH. */
const CNH_CATEGORIAS = ['B', 'D', 'E'];
function categoriaCnh(cnh: string): string {
  let hash = 0;
  for (let i = 0; i < cnh.length; i++) hash = (hash * 31 + cnh.charCodeAt(i)) >>> 0;
  return CNH_CATEGORIAS[hash % CNH_CATEGORIAS.length];
}

export function DriversPage() {
  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [form, setForm] = useState<DriverRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const [search, setSearch] = useState('');
  const [statusFiltro, setStatusFiltro] = useState<(typeof STATUS_OPTIONS)[number] | 'todos'>('todos');

  const [detail, setDetail] = useState<DriverResponse | null>(null);
  const [detailRatings, setDetailRatings] = useState<DriverRatingResponse[]>([]);
  const [detailSummary, setDetailSummary] = useState<DriverRatingSummaryResponse | null>(null);
  const [novaNota, setNovaNota] = useState(5);
  const [novoComentario, setNovoComentario] = useState('');
  const [ratingSaving, setRatingSaving] = useState(false);
  const [ratingError, setRatingError] = useState('');

  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [detailAssignment, setDetailAssignment] = useState<DriverAssignmentResponse | null>(null);
  const [assignVehicleId, setAssignVehicleId] = useState('');
  const [assignSaving, setAssignSaving] = useState(false);
  const [assignError, setAssignError] = useState('');
  const [inviteSending, setInviteSending] = useState(false);
  const [inviteSent, setInviteSent] = useState('');
  const [inviteError, setInviteError] = useState('');
  const [notifyBody, setNotifyBody] = useState('');
  const [notifySending, setNotifySending] = useState(false);
  const [notifySent, setNotifySent] = useState(false);
  const [notifyError, setNotifyError] = useState('');

  function refresh() {
    coreApi.drivers
      .list()
      .then(setDrivers)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar motoristas'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);
  useEffect(() => {
    coreApi.vehicles.list().then(setVehicles);
  }, []);

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError('');
    setModalOpen(true);
  }

  function openEdit(d: DriverResponse) {
    setEditingId(d.id!);
    setForm({
      name: d.name!,
      cnh: d.cnh!,
      phone: d.phone ?? '',
      status: d.status as DriverRequest['status'],
      cnhValidade: d.cnhValidade,
      email: d.email ?? undefined,
    });
    setError('');
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      if (editingId) {
        await coreApi.drivers.update(editingId, form);
      } else {
        await coreApi.drivers.create(form);
      }
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao salvar motorista');
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Excluir este motorista?')) return;
    try {
      await coreApi.drivers.remove(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao excluir motorista');
    }
  }

  function openDetail(d: DriverResponse) {
    setDetail(d);
    setDetailRatings([]);
    setDetailSummary(null);
    setNovaNota(5);
    setNovoComentario('');
    setRatingError('');
    setDetailAssignment(null);
    setAssignVehicleId('');
    setAssignError('');
    setInviteSent('');
    setInviteError('');
    setNotifyBody('');
    setNotifySent(false);
    setNotifyError('');
    refreshRatings(d.id!);
    refreshAssignment(d.id!);
  }

  function refreshRatings(driverId: string) {
    coreApi.driverRatings.list(driverId).then(setDetailRatings);
    coreApi.driverRatings.summary(driverId).then(setDetailSummary);
  }

  function refreshAssignment(driverId: string) {
    coreApi.drivers.activeAssignment(driverId).then(setDetailAssignment);
  }

  async function handleAddRating(e: FormEvent) {
    e.preventDefault();
    if (!detail) return;
    setRatingSaving(true);
    setRatingError('');
    try {
      await coreApi.driverRatings.create(detail.id!, { nota: novaNota, comentario: novoComentario || undefined });
      setNovoComentario('');
      refreshRatings(detail.id!);
    } catch (err) {
      setRatingError(err instanceof Error ? err.message : 'Falha ao registrar avaliação');
    } finally {
      setRatingSaving(false);
    }
  }

  async function handleInvite() {
    if (!detail) return;
    setInviteSending(true);
    setInviteError('');
    setInviteSent('');
    try {
      const resp = await coreApi.drivers.invite(detail.id!);
      setInviteSent(`Convite enviado para ${resp.email}.`);
      refresh();
    } catch (err) {
      setInviteError(err instanceof Error ? err.message : 'Falha ao enviar convite');
    } finally {
      setInviteSending(false);
    }
  }

  async function handleAssign(e: FormEvent) {
    e.preventDefault();
    if (!detail || !assignVehicleId) return;
    setAssignSaving(true);
    setAssignError('');
    try {
      const assignment = await coreApi.drivers.assign(detail.id!, { vehicleId: assignVehicleId });
      setDetailAssignment(assignment);
      setAssignVehicleId('');
    } catch (err) {
      setAssignError(err instanceof Error ? err.message : 'Falha ao designar veículo');
    } finally {
      setAssignSaving(false);
    }
  }

  async function handleEndAssignment() {
    if (!detail) return;
    try {
      await coreApi.drivers.endAssignment(detail.id!);
      setDetailAssignment(null);
    } catch (err) {
      setAssignError(err instanceof Error ? err.message : 'Falha ao encerrar designação');
    }
  }

  async function handleNotify(e: FormEvent) {
    e.preventDefault();
    if (!detail || !notifyBody.trim()) return;
    setNotifySending(true);
    setNotifyError('');
    setNotifySent(false);
    try {
      await coreApi.drivers.notify(detail.id!, { title: 'Aviso do gestor', body: notifyBody });
      setNotifyBody('');
      setNotifySent(true);
    } catch (err) {
      setNotifyError(err instanceof Error ? err.message : 'Falha ao enviar aviso');
    } finally {
      setNotifySending(false);
    }
  }

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return drivers.filter((d) => {
      if (statusFiltro !== 'todos' && d.status !== statusFiltro) return false;
      if (term) {
        const haystack = `${d.name} ${d.cnh}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });
  }, [drivers, search, statusFiltro]);

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Motoristas</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{drivers.length} motoristas cadastrados</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> Novo Motorista
        </Button>
      </div>

      {error && !modalOpen && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Input
            placeholder="Buscar por nome ou CNH..."
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
          <span className="ml-auto text-xs text-muted-foreground">{filtered.length} motorista(s)</span>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Todos os motoristas</CardTitle>
        </CardHeader>
        <div className="overflow-x-auto">
          {loading ? (
            <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
          ) : (
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Motorista
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    CNH
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Validade
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Telefone
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Status
                  </th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map((d) => {
                  const dias = d.cnhValidade ? diasAteVencer(d.cnhValidade) : null;
                  return (
                    <tr key={d.id} className="hover:bg-muted/50">
                      <td className="px-5 py-2.5">
                        <div className="flex items-center gap-2.5">
                          <Avatar className="size-7">
                            <AvatarFallback>{iniciais(d.name!)}</AvatarFallback>
                          </Avatar>
                          <span className="font-medium text-foreground">{d.name}</span>
                        </div>
                      </td>
                      <td className="px-5 py-2.5">
                        <Badge variant="outline" className="font-data">
                          Cat. {categoriaCnh(d.cnh!)}
                        </Badge>
                      </td>
                      <td className="px-5 py-2.5">
                        {d.cnhValidade ? (
                          <div>
                            <span className="font-data text-muted-foreground">
                              {d.cnhValidade.split('-').reverse().join('/')}
                            </span>
                            {dias != null && dias <= 45 && (
                              <span className={cn('ml-2 text-[11px]', dias < 0 ? 'text-status-danger' : 'text-status-warning')}>
                                {dias < 0 ? 'vencida' : `vence em ${dias}d`}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="px-5 py-2.5 font-data text-muted-foreground">{d.phone ?? '—'}</td>
                      <td className="px-5 py-2.5">
                        <StatusBadgeMotorista status={d.status} />
                      </td>
                      <td className="px-5 py-2.5">
                        <div className="flex items-center gap-4">
                          <button
                            type="button"
                            onClick={() => openDetail(d)}
                            className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                          >
                            <Eye className="size-4" />
                          </button>
                          <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openEdit(d)}>
                            Editar
                          </Button>
                          <Button
                            variant="link"
                            size="sm"
                            className="h-auto p-0 text-destructive"
                            onClick={() => handleDelete(d.id!)}
                          >
                            Excluir
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Nenhum motorista encontrado.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </Card>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? 'Editar Motorista' : 'Novo Motorista'}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="name">Nome</Label>
            <Input id="name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </div>
          <div>
            <Label htmlFor="cnh">CNH (11 dígitos)</Label>
            <Input
              id="cnh"
              value={form.cnh}
              onChange={(e) => setForm({ ...form, cnh: e.target.value })}
              pattern="\d{11}"
              title="11 dígitos numéricos"
              required
            />
          </div>
          <div>
            <Label htmlFor="phone">Telefone</Label>
            <Input id="phone" value={form.phone ?? ''} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div>
            <Label htmlFor="email">E-mail (para acesso ao app)</Label>
            <Input
              id="email"
              type="email"
              value={form.email ?? ''}
              onChange={(e) => setForm({ ...form, email: e.target.value || undefined })}
            />
          </div>
          <div>
            <Label htmlFor="status">Status</Label>
            <Select
              id="status"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as DriverRequest['status'] })}
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </div>
          <div className="border-t border-border pt-4">
            <Label htmlFor="cnhValidade">Validade da CNH</Label>
            <Input
              id="cnhValidade"
              type="date"
              value={form.cnhValidade ?? ''}
              onChange={(e) => setForm({ ...form, cnhValidade: e.target.value || undefined })}
            />
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
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <div className="flex items-center gap-3">
                <Avatar className="size-9">
                  <AvatarFallback>{iniciais(detail.name!)}</AvatarFallback>
                </Avatar>
                <div>
                  <DialogTitle>{detail.name}</DialogTitle>
                  <DialogDescription>Avaliação (visível só para o gestor)</DialogDescription>
                </div>
              </div>
            </DialogHeader>

            <div className="space-y-4 p-5">
              <div className="rounded-md border border-border p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5">
                    <Mail className="size-4 text-muted-foreground" />
                    <div>
                      <p className="text-xs font-medium text-foreground">
                        {detail.hasLogin ? 'Acesso ao app ativo' : detail.email ? 'Convite pendente' : 'Sem e-mail cadastrado'}
                      </p>
                      <p className="text-[11px] text-muted-foreground">{detail.email ?? 'Cadastre um e-mail para convidar'}</p>
                    </div>
                  </div>
                  {!detail.hasLogin && detail.email && (
                    <Button type="button" size="sm" variant="outline" onClick={handleInvite} disabled={inviteSending}>
                      {inviteSending ? 'Enviando...' : 'Convidar'}
                    </Button>
                  )}
                </div>
                {inviteSent && <p className="mt-2 text-[11px] text-status-success">{inviteSent}</p>}
                {inviteError && <p className="mt-2 text-[11px] text-status-danger">{inviteError}</p>}

                {detail.hasLogin && (
                  <form onSubmit={handleNotify} className="mt-3 flex items-center gap-2 border-t border-border pt-3">
                    <Input
                      placeholder="Mandar um aviso rápido pelo app..."
                      value={notifyBody}
                      onChange={(e) => setNotifyBody(e.target.value)}
                      className="flex-1"
                    />
                    <Button type="submit" size="sm" variant="outline" disabled={!notifyBody.trim() || notifySending}>
                      {notifySending ? 'Enviando...' : 'Enviar'}
                    </Button>
                  </form>
                )}
                {notifySent && <p className="mt-2 text-[11px] text-status-success">Aviso enviado.</p>}
                {notifyError && <p className="mt-2 text-[11px] text-status-danger">{notifyError}</p>}
              </div>

              <div className="rounded-md border border-border p-3">
                <div className="mb-2 flex items-center gap-2.5">
                  <Car className="size-4 text-muted-foreground" />
                  <p className="text-xs font-medium text-foreground">Veículo designado</p>
                </div>
                {detailAssignment ? (
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-data text-xs text-foreground">
                      {detailAssignment.plate} — {detailAssignment.brand} {detailAssignment.model}
                    </span>
                    <Button type="button" size="sm" variant="ghost" className="text-destructive" onClick={handleEndAssignment}>
                      Encerrar
                    </Button>
                  </div>
                ) : (
                  <form onSubmit={handleAssign} className="flex items-center gap-2">
                    <Select
                      value={assignVehicleId}
                      onChange={(e) => setAssignVehicleId(e.target.value)}
                      className="flex-1"
                    >
                      <option value="">Selecione um veículo...</option>
                      {vehicles.map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.plate} — {v.brand} {v.model}
                        </option>
                      ))}
                    </Select>
                    <Button type="submit" size="sm" disabled={!assignVehicleId || assignSaving}>
                      {assignSaving ? 'Designando...' : 'Designar'}
                    </Button>
                  </form>
                )}
                {assignError && <p className="mt-2 text-[11px] text-status-danger">{assignError}</p>}
              </div>

              <div className="flex items-center gap-3 rounded-md border border-border p-3">
                <Star className="size-4 fill-status-warning text-status-warning" />
                <div>
                  <p className="font-data text-sm font-semibold text-foreground">
                    {detailSummary?.notaMedia != null ? Number(detailSummary.notaMedia).toFixed(1) : '—'} / 5
                  </p>
                  <p className="text-[11px] text-muted-foreground">
                    {detailSummary?.totalAvaliacoes ?? 0} avaliação(ões)
                  </p>
                </div>
              </div>

              {detailRatings.length > 0 && (
                <ul className="max-h-48 space-y-2 overflow-y-auto">
                  {detailRatings.map((r) => (
                    <li key={r.id} className="rounded-md border border-border p-3 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="flex items-center gap-1 font-data font-semibold text-foreground">
                          <Star className="size-3 fill-status-warning text-status-warning" /> {r.nota}
                        </span>
                        <span className="text-[11px] text-muted-foreground">
                          {r.createdAt ? formatDateBR(r.createdAt.slice(0, 10)) : '—'}
                        </span>
                      </div>
                      {r.comentario && <p className="mt-1 text-foreground">{r.comentario}</p>}
                    </li>
                  ))}
                </ul>
              )}

              <form onSubmit={handleAddRating} className="space-y-2 border-t border-border pt-4">
                <Label htmlFor="novaNota">Nova avaliação</Label>
                <Select id="novaNota" value={novaNota} onChange={(e) => setNovaNota(Number(e.target.value))}>
                  {[5, 4, 3, 2, 1].map((n) => (
                    <option key={n} value={n}>
                      {n} — {'★'.repeat(n)}
                      {'☆'.repeat(5 - n)}
                    </option>
                  ))}
                </Select>
                <Input
                  placeholder="Comentário (opcional)"
                  value={novoComentario}
                  onChange={(e) => setNovoComentario(e.target.value)}
                />
                {ratingError && <p className="text-xs text-status-danger">{ratingError}</p>}
                <Button type="submit" size="sm" className="w-full" disabled={ratingSaving}>
                  {ratingSaving ? 'Salvando...' : 'Registrar avaliação'}
                </Button>
              </form>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </div>
  );
}
