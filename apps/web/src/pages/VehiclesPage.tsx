import { useEffect, useRef, useState, type FormEvent } from 'react';
import { CircleOff, Eye, Plus, Truck, Wrench } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type VehicleRequest, type VehicleResponse } from '../api/client';
import { usePode } from '../auth/AuthContext';
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { VehicleTypeIcon } from '../components/shared/VehicleTypeIcon';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { StatCard } from '../components/shared/StatCard';
import { TableSkeleton } from '../components/shared/TableSkeleton';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';

const STATUS_OPTIONS = ['ATIVO', 'MANUTENCAO', 'INATIVO'] as const;
const TIPO_OPTIONS = ['CARRO', 'MOTO', 'VAN', 'CAMINHAO', 'ONIBUS'] as const;

const EMPTY_FORM: VehicleRequest = {
  plate: '',
  brand: '',
  model: '',
  modelYear: undefined,
  odometerKm: 0,
  status: 'ATIVO',
  tipo: undefined,
  proximaManutencaoData: undefined,
  proximaManutencaoKm: undefined,
};

interface Props {
  onViewCosts: (vehicleId: string, plate: string) => void;
  onViewDetail: (vehicleId: string) => void;
}

const VEHICLES_PAGE_SIZE = 20;

export function VehiclesPage({ onViewCosts, onViewDetail }: Props) {
  const { t } = useTranslation();
  const podeEscrever = usePode('FROTA', 'ESCREVER');
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [form, setForm] = useState<VehicleRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const [search, setSearch] = useState('');
  const [searchDebounced, setSearchDebounced] = useState('');
  const [statusFiltro, setStatusFiltro] = useState<(typeof STATUS_OPTIONS)[number] | 'todos'>('todos');
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Debounce: sem isso, cada tecla digitada na busca dispararia uma request — a busca
  // agora é server-side (ver comentário abaixo de `total`), então precisa desse
  // amortecimento do jeito que o filtro em memória de antes nunca precisou. Reseta a
  // página junto (mesmo tick): página 3 de uma busca nova quase sempre não existe mais.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchDebounced(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Troca de filtro/página rápida pode fazer uma resposta antiga chegar DEPOIS de uma
  // mais nova (a ordem de chegada na rede não é garantida pela ordem de disparo) — sem
  // essa guarda, a tela ficaria mostrando o resultado do filtro anterior por cima do
  // atual. Mesmo padrão de "descartar resposta obsoleta" já usado em BuscaEndereco.tsx.
  const buscaAtual = useRef(0);

  function refresh() {
    const buscaId = ++buscaAtual.current;
    coreApi.vehicles
      .list(page, VEHICLES_PAGE_SIZE, searchDebounced || undefined, statusFiltro === 'todos' ? undefined : statusFiltro)
      .then((res) => {
        if (buscaId !== buscaAtual.current) return;
        setVehicles(res.content);
        setTotalElements(res.totalElements);
        setTotalPages(res.totalPages);
      })
      .catch((e: unknown) => {
        if (buscaId !== buscaAtual.current) return;
        setError(e instanceof Error ? e.message : t('pages.vehicles.toasts.falhaCarregar'));
      })
      .finally(() => {
        if (buscaId === buscaAtual.current) setLoading(false);
      });
  }

  useEffect(refresh, [page, searchDebounced, statusFiltro]);

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
      tipo: v.tipo as VehicleRequest['tipo'],
      proximaManutencaoData: v.proximaManutencaoData,
      proximaManutencaoKm: v.proximaManutencaoKm,
    });
    setError('');
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      if (editingId) {
        await coreApi.vehicles.update(editingId, form);
        toast.success(t('pages.vehicles.toasts.atualizado'));
      } else {
        await coreApi.vehicles.create(form);
        toast.success(t('pages.vehicles.toasts.cadastrado'));
      }
      setModalOpen(false);
      refresh();
    } catch (err) {
      // Erro de salvar fica dentro do modal (não vira toast): o formulário continua aberto
      // com os dados preenchidos, e o motivo da falha precisa ficar visível bem ali, perto
      // do botão que a pessoa vai clicar de novo — sumir com um toast faria ela reler tudo.
      setError(err instanceof Error ? err.message : t('pages.vehicles.toasts.falhaSalvar'));
    }
  }

  async function handleDelete(id: string) {
    await deleteWithConfirm({
      confirmMessage: t('pages.vehicles.toasts.confirmarExcluir'),
      remove: () => coreApi.vehicles.remove(id),
      successMessage: t('pages.vehicles.toasts.excluido'),
      fallbackErrorMessage: t('pages.vehicles.toasts.falhaExcluir'),
      onSuccess: refresh,
    });
  }

  // Frota paginada (spec de escala) — busca/status agora filtram no servidor (ver
  // coreApi.vehicles.list), então totalElements já reflete o filtro ativo, não a frota
  // inteira sempre. Ativos/manutenção/inativos continuam só da página carregada: não há
  // endpoint de contagem por status ainda.
  const filtroAtivo = searchDebounced !== '' || statusFiltro !== 'todos';
  const total = totalElements;
  const ativos = vehicles.filter((v) => v.status === 'ATIVO').length;
  const manutencao = vehicles.filter((v) => v.status === 'MANUTENCAO').length;
  const inativos = vehicles.filter((v) => v.status === 'INATIVO').length;

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.vehicles.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.vehicles.subtitulo')}</p>
        </div>
        {podeEscrever && (
          <Button onClick={openCreate}>
            <Plus /> {t('pages.vehicles.novoVeiculo')}
          </Button>
        )}
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label={t('pages.vehicles.totalDeVeiculos')}
          value={total}
          hint={filtroAtivo ? t('pages.vehicles.nestaBusca') : t('pages.vehicles.todaAFrota')}
          icon={Truck}
        />
        <StatCard label={t('pages.vehicles.ativos')} value={ativos} tone="success" hint={t('pages.vehicles.nestaPagina')} icon={Truck} />
        <StatCard
          label={t('pages.vehicles.emManutencao')}
          value={manutencao}
          tone="warning"
          hint={t('pages.vehicles.nestaPagina')}
          icon={Wrench}
        />
        <StatCard label={t('pages.vehicles.inativos')} value={inativos} tone="danger" hint={t('pages.vehicles.nestaPagina')} icon={CircleOff} />
      </div>

      {error && !modalOpen && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Input
            placeholder={t('pages.vehicles.buscarPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          <Select
            value={statusFiltro}
            onChange={(e) => {
              setStatusFiltro(e.target.value as (typeof STATUS_OPTIONS)[number] | 'todos');
              setPage(0);
            }}
            className="w-44"
          >
            <option value="todos">{t('pages.vehicles.todosOsStatus')}</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {t(`status.veiculo.${s}`)}
              </option>
            ))}
          </Select>
          <span className="ml-auto text-xs text-muted-foreground">{t('pages.vehicles.veiculosContagem', { n: total })}</span>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.vehicles.todosOsVeiculos')}</CardTitle>
        </CardHeader>
        {loading ? (
          <TableSkeleton rows={8} columns={7} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicles.tabela.tipo')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicles.tabela.placa')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicles.tabela.marcaModelo')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicles.tabela.ano')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicles.tabela.odometro')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicles.tabela.status')}
                  </th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <StaggerGroup as="tbody" className="divide-y divide-border">
                {vehicles.map((v) => (
                  <StaggerItem as="tr" key={v.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5">
                      <div title={v.tipo ? t(`status.tipoVeiculo.${v.tipo}`) : t('pages.vehicles.tipoNaoInformado')}>
                        <VehicleTypeIcon tipo={v.tipo} />
                      </div>
                    </td>
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
                          onClick={() => onViewDetail(v.id!)}
                          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                        >
                          <Eye className="size-4" />
                        </button>
                        <Button variant="link" size="sm" className="h-auto p-0" onClick={() => onViewCosts(v.id!, v.plate!)}>
                          {t('pages.vehicles.custos')}
                        </Button>
                        {podeEscrever && (
                          <>
                            <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openEdit(v)}>
                              {t('pages.vehicles.editar')}
                            </Button>
                            <Button
                              variant="link"
                              size="sm"
                              className="h-auto p-0 text-destructive"
                              onClick={() => handleDelete(v.id!)}
                            >
                              {t('pages.vehicles.excluir')}
                            </Button>
                          </>
                        )}
                      </div>
                    </td>
                  </StaggerItem>
                ))}
                {vehicles.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      {t('pages.vehicles.nenhumVeiculoEncontrado')}
                    </td>
                  </tr>
                )}
              </StaggerGroup>
            </table>
          </div>
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-5 py-3">
            <span className="text-xs text-muted-foreground">
              {t('pages.vehicles.paginaXDeY', { atual: page + 1, total: totalPages })}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                {t('pages.vehicles.anterior')}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                {t('pages.vehicles.proxima')}
              </Button>
            </div>
          </div>
        )}
      </Card>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? t('pages.vehicles.editarVeiculo') : t('pages.vehicles.novoVeiculo')}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="plate">{t('pages.vehicles.form.placa')}</Label>
            <Input id="plate" value={form.plate} onChange={(e) => setForm({ ...form, plate: e.target.value })} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="brand">{t('pages.vehicles.form.marca')}</Label>
              <Input id="brand" value={form.brand} onChange={(e) => setForm({ ...form, brand: e.target.value })} required />
            </div>
            <div>
              <Label htmlFor="model">{t('pages.vehicles.form.modelo')}</Label>
              <Input id="model" value={form.model} onChange={(e) => setForm({ ...form, model: e.target.value })} required />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="modelYear">{t('pages.vehicles.form.ano')}</Label>
              <Input
                id="modelYear"
                type="number"
                value={form.modelYear ?? ''}
                onChange={(e) => setForm({ ...form, modelYear: e.target.value ? Number(e.target.value) : undefined })}
              />
            </div>
            <div>
              <Label htmlFor="odometerKm">{t('pages.vehicles.form.odometroKm')}</Label>
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
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="status">{t('pages.vehicles.form.status')}</Label>
              <Select
                id="status"
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as VehicleRequest['status'] })}
              >
                {STATUS_OPTIONS.map((s) => (
                  <option key={s} value={s}>
                    {t(`status.veiculo.${s}`)}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="tipo">{t('pages.vehicles.form.tipo')}</Label>
              <Select
                id="tipo"
                value={form.tipo ?? ''}
                onChange={(e) =>
                  setForm({ ...form, tipo: (e.target.value || undefined) as VehicleRequest['tipo'] })
                }
              >
                <option value="">{t('pages.vehicles.form.naoInformado')}</option>
                {TIPO_OPTIONS.map((tipo) => (
                  <option key={tipo} value={tipo}>
                    {t(`status.tipoVeiculo.${tipo}`)}
                  </option>
                ))}
              </Select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4 border-t border-border pt-4">
            <div>
              <Label htmlFor="proximaManutencaoData">{t('pages.vehicles.form.proximaManutencaoData')}</Label>
              <Input
                id="proximaManutencaoData"
                type="date"
                value={form.proximaManutencaoData ?? ''}
                onChange={(e) => setForm({ ...form, proximaManutencaoData: e.target.value || undefined })}
              />
            </div>
            <div>
              <Label htmlFor="proximaManutencaoKm">{t('pages.vehicles.form.proximaManutencaoKm')}</Label>
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
              {t('pages.vehicles.form.cancelar')}
            </Button>
            <Button type="submit">{editingId ? t('pages.vehicles.form.salvar') : t('pages.vehicles.form.adicionar')}</Button>
          </div>
        </form>
      </Modal>

    </div>
  );
}

