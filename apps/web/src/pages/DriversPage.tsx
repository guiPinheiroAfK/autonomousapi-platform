import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Car, Copy, Eye, Mail, Plus, Star } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type DriverAssignmentResponse,
  type DriverRatingResponse,
  type DriverRatingSummaryResponse,
  type DriverRequest,
  type DriverResponse,
  type VehicleResponse,
} from '../api/client';
import { usePode } from '../auth/AuthContext';
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
import { maskCnh, maskTelefoneBR } from '../lib/masks';
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';
import { TableSkeleton } from '../components/shared/TableSkeleton';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';

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
  const { t } = useTranslation();
  const podeEscrever = usePode('MOTORISTAS', 'ESCREVER');
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
  // Sem domínio de e-mail verificado ainda, o link volta na própria resposta — o gestor
  // copia e manda por fora (WhatsApp etc.) em vez de depender só do e-mail chegar.
  const [inviteLinkUrl, setInviteLinkUrl] = useState('');
  const [notifyBody, setNotifyBody] = useState('');
  const [notifySending, setNotifySending] = useState(false);
  const [notifySent, setNotifySent] = useState(false);
  const [notifyError, setNotifyError] = useState('');

  function refresh() {
    coreApi.drivers
      .list()
      .then((res) => setDrivers(res.content))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.drivers.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);
  useEffect(() => {
    // Paginado (spec de escala) — size grande cobre a frota inteira na maioria dos tenants,
    // já que essa tela usa a lista pra popular o seletor de veículo na designação.
    coreApi.vehicles.list(0, 500).then((res) => setVehicles(res.content));
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
        toast.success(t('pages.drivers.toasts.atualizado'));
      } else {
        await coreApi.drivers.create(form);
        toast.success(t('pages.drivers.toasts.cadastrado'));
      }
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaSalvar'));
    }
  }

  async function handleDelete(id: string) {
    await deleteWithConfirm({
      confirmMessage: t('pages.drivers.toasts.confirmarExcluir'),
      remove: () => coreApi.drivers.remove(id),
      successMessage: t('pages.drivers.toasts.excluido'),
      fallbackErrorMessage: t('pages.drivers.toasts.falhaExcluir'),
      onSuccess: refresh,
    });
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
    coreApi.driverRatings.list(driverId).then((res) => setDetailRatings(res.content));
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
      setRatingError(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaAvaliacao'));
    } finally {
      setRatingSaving(false);
    }
  }

  async function handleRemoveRating(ratingId: string) {
    if (!detail) return;
    const driverId = detail.id!;
    await deleteWithConfirm({
      confirmMessage: t('pages.drivers.detalhe.confirmarExcluirAvaliacao'),
      remove: () => coreApi.driverRatings.remove(driverId, ratingId),
      successMessage: t('pages.drivers.toasts.avaliacaoExcluida'),
      fallbackErrorMessage: t('pages.drivers.toasts.falhaExcluirAvaliacao'),
      onSuccess: () => refreshRatings(driverId),
    });
  }

  async function handleInvite() {
    if (!detail) return;
    setInviteSending(true);
    setInviteError('');
    setInviteSent('');
    setInviteLinkUrl('');
    try {
      const resp = await coreApi.drivers.invite(detail.id!);
      setInviteSent(t('pages.drivers.toasts.conviteEnviado', { email: resp.email }));
      setInviteLinkUrl(resp.linkUrl ?? '');
      refresh();
    } catch (err) {
      setInviteError(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaConvite'));
    } finally {
      setInviteSending(false);
    }
  }

  async function copiarLinkConviteMotorista() {
    if (!inviteLinkUrl) return;
    try {
      await navigator.clipboard.writeText(inviteLinkUrl);
      toast.success(t('pages.drivers.toasts.linkCopiado'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaCopiarLink'));
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
      setAssignError(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaDesignar'));
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
      setAssignError(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaEncerrar'));
    }
  }

  async function handleNotify(e: FormEvent) {
    e.preventDefault();
    if (!detail || !notifyBody.trim()) return;
    setNotifySending(true);
    setNotifyError('');
    setNotifySent(false);
    try {
      await coreApi.drivers.notify(detail.id!, { title: t('pages.drivers.toasts.avisoDoGestor'), body: notifyBody });
      setNotifyBody('');
      setNotifySent(true);
    } catch (err) {
      setNotifyError(err instanceof Error ? err.message : t('pages.drivers.toasts.falhaAviso'));
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
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.drivers.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.drivers.subtitulo', { n: drivers.length })}</p>
        </div>
        {podeEscrever && (
          <Button onClick={openCreate}>
            <Plus /> {t('pages.drivers.novoMotorista')}
          </Button>
        )}
      </div>

      {error && !modalOpen && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Input
            placeholder={t('pages.drivers.buscarPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          <Select
            value={statusFiltro}
            onChange={(e) => setStatusFiltro(e.target.value as (typeof STATUS_OPTIONS)[number] | 'todos')}
            className="w-44"
          >
            <option value="todos">{t('pages.drivers.todosOsStatus')}</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {t(`status.motorista.${s}`)}
              </option>
            ))}
          </Select>
          <span className="ml-auto text-xs text-muted-foreground">{t('pages.drivers.motoristasContagem', { n: filtered.length })}</span>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.drivers.todosOsMotoristas')}</CardTitle>
        </CardHeader>
        <div className="overflow-x-auto">
          {loading ? (
            <TableSkeleton rows={6} columns={6} />
          ) : (
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.drivers.tabela.motorista')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.drivers.tabela.cnh')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.drivers.tabela.validade')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.drivers.tabela.telefone')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.drivers.tabela.status')}
                  </th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <StaggerGroup as="tbody" className="divide-y divide-border">
                {filtered.map((d) => {
                  const dias = d.cnhValidade ? diasAteVencer(d.cnhValidade) : null;
                  return (
                    <StaggerItem as="tr" key={d.id} className="hover:bg-muted/50">
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
                          {t('pages.drivers.categoriaAbrev', { cat: categoriaCnh(d.cnh!) })}
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
                                {dias < 0 ? t('pages.drivers.vencida') : t('pages.drivers.venceEmDias', { n: dias })}
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
                            className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                          >
                            <Eye className="size-4" />
                          </button>
                          {podeEscrever && (
                            <>
                              <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openEdit(d)}>
                                {t('pages.drivers.editar')}
                              </Button>
                              <Button
                                variant="link"
                                size="sm"
                                className="h-auto p-0 text-destructive"
                                onClick={() => handleDelete(d.id!)}
                              >
                                {t('pages.drivers.excluir')}
                              </Button>
                            </>
                          )}
                        </div>
                      </td>
                    </StaggerItem>
                  );
                })}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      {t('pages.drivers.nenhumMotoristaEncontrado')}
                    </td>
                  </tr>
                )}
              </StaggerGroup>
            </table>
          )}
        </div>
      </Card>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? t('pages.drivers.editarMotorista') : t('pages.drivers.novoMotorista')}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="name">{t('pages.drivers.form.nome')}</Label>
            <Input id="name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </div>
          <div>
            <Label htmlFor="cnh">{t('pages.drivers.form.cnh')}</Label>
            <Input
              id="cnh"
              inputMode="numeric"
              value={form.cnh}
              onChange={(e) => setForm({ ...form, cnh: maskCnh(e.target.value) })}
              pattern="\d{11}"
              title={t('pages.drivers.form.cnh11Digitos')}
              required
            />
          </div>
          <div>
            <Label htmlFor="phone">{t('pages.drivers.form.telefone')}</Label>
            <Input
              id="phone"
              inputMode="tel"
              value={maskTelefoneBR(form.phone ?? '')}
              onChange={(e) => setForm({ ...form, phone: maskTelefoneBR(e.target.value) })}
            />
          </div>
          <div>
            <Label htmlFor="email">{t('pages.drivers.form.email')}</Label>
            <Input
              id="email"
              type="email"
              value={form.email ?? ''}
              onChange={(e) => setForm({ ...form, email: e.target.value || undefined })}
            />
          </div>
          <div>
            <Label htmlFor="status">{t('pages.drivers.form.status')}</Label>
            <Select
              id="status"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as DriverRequest['status'] })}
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {t(`status.motorista.${s}`)}
                </option>
              ))}
            </Select>
          </div>
          <div className="border-t border-border pt-4">
            <Label htmlFor="cnhValidade">{t('pages.drivers.form.validadeCnh')}</Label>
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
              {t('pages.drivers.form.cancelar')}
            </Button>
            <Button type="submit">{editingId ? t('pages.drivers.form.salvar') : t('pages.drivers.form.adicionar')}</Button>
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
                  <DialogDescription>{t('pages.drivers.detalhe.avaliacaoSoGestor')}</DialogDescription>
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
                        {detail.hasLogin
                          ? t('pages.drivers.detalhe.acessoAppAtivo')
                          : detail.email
                            ? t('pages.drivers.detalhe.convitePendente')
                            : t('pages.drivers.detalhe.semEmailCadastrado')}
                      </p>
                      <p className="text-[11px] text-muted-foreground">
                        {detail.email ?? t('pages.drivers.detalhe.cadastreEmailConvidar')}
                      </p>
                    </div>
                  </div>
                  {podeEscrever && !detail.hasLogin && detail.email && (
                    <Button type="button" size="sm" variant="outline" onClick={handleInvite} disabled={inviteSending}>
                      {inviteSending ? t('pages.drivers.detalhe.enviando') : t('pages.drivers.detalhe.convidar')}
                    </Button>
                  )}
                </div>
                {inviteSent && <p className="mt-2 text-[11px] text-status-success">{inviteSent}</p>}
                {inviteLinkUrl && (
                  <button
                    type="button"
                    onClick={copiarLinkConviteMotorista}
                    className="mt-1 flex items-center gap-1 text-[11px] text-muted-foreground hover:text-foreground"
                  >
                    <Copy className="size-3" /> {t('pages.drivers.detalhe.copiarLinkConvite')}
                  </button>
                )}
                {inviteError && <p className="mt-2 text-[11px] text-status-danger">{inviteError}</p>}

                {podeEscrever && detail.hasLogin && (
                  <form onSubmit={handleNotify} className="mt-3 flex items-center gap-2 border-t border-border pt-3">
                    <Input
                      placeholder={t('pages.drivers.detalhe.avisoRapidoPlaceholder')}
                      value={notifyBody}
                      onChange={(e) => setNotifyBody(e.target.value)}
                      className="flex-1"
                    />
                    <Button type="submit" size="sm" variant="outline" disabled={!notifyBody.trim() || notifySending}>
                      {notifySending ? t('pages.drivers.detalhe.enviando') : t('pages.drivers.detalhe.enviar')}
                    </Button>
                  </form>
                )}
                {notifySent && <p className="mt-2 text-[11px] text-status-success">{t('pages.drivers.detalhe.avisoEnviado')}</p>}
                {notifyError && <p className="mt-2 text-[11px] text-status-danger">{notifyError}</p>}
              </div>

              <div className="rounded-md border border-border p-3">
                <div className="mb-2 flex items-center gap-2.5">
                  <Car className="size-4 text-muted-foreground" />
                  <p className="text-xs font-medium text-foreground">{t('pages.drivers.detalhe.veiculoDesignado')}</p>
                </div>
                {detailAssignment ? (
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-data text-xs text-foreground">
                      {detailAssignment.plate} — {detailAssignment.brand} {detailAssignment.model}
                    </span>
                    {podeEscrever && (
                      <Button type="button" size="sm" variant="ghost" className="text-destructive" onClick={handleEndAssignment}>
                        {t('pages.drivers.detalhe.encerrar')}
                      </Button>
                    )}
                  </div>
                ) : (
                  podeEscrever && (
                    <form onSubmit={handleAssign} className="flex items-center gap-2">
                      <Select
                        value={assignVehicleId}
                        onChange={(e) => setAssignVehicleId(e.target.value)}
                        className="flex-1"
                      >
                        <option value="">{t('pages.drivers.detalhe.selecioneVeiculo')}</option>
                        {vehicles.map((v) => (
                          <option key={v.id} value={v.id}>
                            {v.plate} — {v.brand} {v.model}
                          </option>
                        ))}
                      </Select>
                      <Button type="submit" size="sm" disabled={!assignVehicleId || assignSaving}>
                        {assignSaving ? t('pages.drivers.detalhe.designando') : t('pages.drivers.detalhe.designar')}
                      </Button>
                    </form>
                  )
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
                    {t('pages.drivers.detalhe.avaliacoesContagem', { n: detailSummary?.totalAvaliacoes ?? 0 })}
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
                      {podeEscrever && (
                        <button
                          type="button"
                          onClick={() => handleRemoveRating(r.id!)}
                          className="mt-1.5 text-[11px] font-medium text-status-danger hover:underline"
                        >
                          {t('pages.drivers.detalhe.excluirAvaliacao')}
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}

              {podeEscrever && (
              <form onSubmit={handleAddRating} className="space-y-2 border-t border-border pt-4">
                <Label htmlFor="novaNota">{t('pages.drivers.detalhe.novaAvaliacao')}</Label>
                <Select id="novaNota" value={novaNota} onChange={(e) => setNovaNota(Number(e.target.value))}>
                  {[5, 4, 3, 2, 1].map((n) => (
                    <option key={n} value={n}>
                      {n} — {'★'.repeat(n)}
                      {'☆'.repeat(5 - n)}
                    </option>
                  ))}
                </Select>
                <Input
                  placeholder={t('pages.drivers.detalhe.comentarioOpcional')}
                  value={novoComentario}
                  onChange={(e) => setNovoComentario(e.target.value)}
                />
                {ratingError && <p className="text-xs text-status-danger">{ratingError}</p>}
                <Button type="submit" size="sm" className="w-full" disabled={ratingSaving}>
                  {ratingSaving ? t('pages.drivers.detalhe.salvando') : t('pages.drivers.detalhe.registrarAvaliacao')}
                </Button>
              </form>
              )}
            </div>
          </DialogContent>
        )}
      </Dialog>
    </div>
  );
}
