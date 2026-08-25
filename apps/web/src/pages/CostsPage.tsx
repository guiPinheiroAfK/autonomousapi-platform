import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Download, Plus, Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  coreApi,
  type BudgetRequest,
  type BudgetResponse,
  type CategoryTotal,
  type ExpenseCategory,
  type ExpenseEntryRequest,
  type FleetExpenseEntryResponse,
  type RoutePlanResponse,
  type VehicleResponse,
} from '../api/client';
import { StatusBadgeCusto } from '../components/shared/StatusBadge';
import { StatCard } from '../components/shared/StatCard';
import { PlacaBR } from '../components/shared/PlacaBR';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import { cn } from '../lib/utils';
import { formatBRL, formatDateBR } from '../lib/format';
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';
import { TableSkeleton } from '../components/shared/TableSkeleton';

const CATEGORY_OPTIONS: ExpenseCategory[] = [
  'COMBUSTIVEL',
  'MANUTENCAO',
  'SEGURO',
  'IPVA',
  'MULTA',
  'PEDAGIO',
  'LAVAGEM',
  'OUTRO',
];
const CATEGORY_COLOR: Record<ExpenseCategory, string> = {
  COMBUSTIVEL: 'var(--color-status-info)',
  MANUTENCAO: 'var(--color-status-warning)',
  SEGURO: 'var(--color-status-success)',
  IPVA: 'var(--color-status-neutral)',
  MULTA: 'var(--color-status-danger)',
  PEDAGIO: 'var(--color-primary)',
  LAVAGEM: 'var(--color-status-info)',
  OUTRO: 'var(--color-status-neutral)',
};

function primeiroDiaDoMes(): string {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-01`;
}
function hojeISO(): string {
  return new Date().toISOString().slice(0, 10);
}

const EMPTY_EXPENSE_FORM: ExpenseEntryRequest = {
  vehicleId: undefined,
  categoria: 'COMBUSTIVEL',
  valor: 0,
  descricao: '',
  data: hojeISO(),
};

const EMPTY_BUDGET_FORM: BudgetRequest = {
  vehicleId: undefined,
  categoria: undefined,
  valorLimite: 0,
};

/**
 * Aba "Custos" (spec 10) — despesas categorizadas, orçamento com alerta, rentabilidade de
 * transfers. Recorte diferente e complementar ao Relatórios (financeiro de ordem de
 * serviço): aqui é lançamento manual (combustível, seguro, multa etc.), lá é custo real de
 * OS — ver nota em ReportsPage.
 */
export function CostsPage() {
  const { t } = useTranslation();
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);

  useEffect(() => {
    // Paginado (spec de escala) — size grande cobre a frota inteira na maioria dos tenants,
    // já que esta tela usa a lista pra popular os seletores de veículo (despesa, orçamento).
    coreApi.vehicles.list(0, 500).then((res) => setVehicles(res.content));
  }, []);

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.costs.titulo')}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.costs.subtitulo')}</p>
      </div>

      <Tabs defaultValue="visao-geral">
        <TabsList>
          <TabsTrigger value="visao-geral">{t('pages.costs.tabs.visaoGeral')}</TabsTrigger>
          <TabsTrigger value="despesas">{t('pages.costs.tabs.despesas')}</TabsTrigger>
          <TabsTrigger value="orcamento">{t('pages.costs.tabs.orcamento')}</TabsTrigger>
          <TabsTrigger value="rentabilidade">{t('pages.costs.tabs.rentabilidade')}</TabsTrigger>
        </TabsList>

        <TabsContent value="visao-geral">
          <VisaoGeralTab />
        </TabsContent>
        <TabsContent value="despesas">
          <DespesasTab vehicles={vehicles} />
        </TabsContent>
        <TabsContent value="orcamento">
          <OrcamentoTab vehicles={vehicles} />
        </TabsContent>
        <TabsContent value="rentabilidade">
          <RentabilidadeTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function VisaoGeralTab() {
  const { t } = useTranslation();
  const [from, setFrom] = useState(primeiroDiaDoMes());
  const [to, setTo] = useState(hojeISO());
  const [totals, setTotals] = useState<CategoryTotal[]>([]);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    setLoading(true);
    coreApi.expenses
      .summaryByCategory(from, to)
      .then(setTotals)
      .finally(() => setLoading(false));
  }, [from, to]);

  const total = totals.reduce((sum, item) => sum + Number(item.total ?? 0), 0);
  const maior = [...totals].sort((a, b) => Number(b.total ?? 0) - Number(a.total ?? 0))[0];
  const chartData = totals.map((item) => ({
    categoria: t(`status.custo.${item.categoria}`, { defaultValue: item.categoria }),
    total: Number(item.total ?? 0),
    fill: CATEGORY_COLOR[item.categoria as ExpenseCategory] ?? 'var(--color-primary)',
  }));

  async function handleExport() {
    setExporting(true);
    try {
      await coreApi.reports.exportCostsCsv();
      toast.success(t('pages.costs.visaoGeral.csvExportado'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.costs.visaoGeral.falhaExportar'));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div>
          <Label htmlFor="from">{t('pages.costs.visaoGeral.de')}</Label>
          <Input id="from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="to">{t('pages.costs.visaoGeral.ate')}</Label>
          <Input id="to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <Button variant="outline" onClick={handleExport} disabled={exporting} className="ml-auto">
          <Download /> {exporting ? t('pages.costs.visaoGeral.exportando') : t('pages.costs.visaoGeral.exportarCsv')}
        </Button>
      </div>

      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatCard label={t('pages.costs.visaoGeral.totalNoPeriodo')} value={formatBRL(total)} icon={Wallet} />
        <StatCard
          label={t('pages.costs.visaoGeral.maiorCategoria')}
          value={maior ? t(`status.custo.${maior.categoria}`) : '—'}
          hint={maior ? formatBRL(Number(maior.total ?? 0)) : undefined}
          tone="warning"
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.costs.visaoGeral.despesasPorCategoria')}</CardTitle>
        </CardHeader>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>
        ) : chartData.length === 0 ? (
          <p className="p-8 text-center text-xs text-muted-foreground">{t('pages.costs.visaoGeral.nenhumaDespesaPeriodo')}</p>
        ) : (
          <div className="h-72 px-2 pb-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis
                  dataKey="categoria"
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
                  formatter={(v) => formatBRL(Number(v))}
                />
                <Bar dataKey="total" radius={[4, 4, 0, 0]}>
                  {chartData.map((d) => (
                    <Bar key={d.categoria} dataKey="total" fill={d.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </div>
  );
}

const EXPENSES_PAGE_SIZE = 20;

function DespesasTab({ vehicles }: { vehicles: VehicleResponse[] }) {
  const { t } = useTranslation();
  const [entries, setEntries] = useState<FleetExpenseEntryResponse[]>([]);
  const [categoriaFiltro, setCategoriaFiltro] = useState<ExpenseCategory | 'todas'>('todas');
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<ExpenseEntryRequest>(EMPTY_EXPENSE_FORM);
  const [error, setError] = useState('');

  function refresh() {
    setLoading(true);
    coreApi.expenses
      .fleetList(categoriaFiltro === 'todas' ? undefined : categoriaFiltro, page, EXPENSES_PAGE_SIZE)
      .then((res) => {
        setEntries(res.content);
        setTotalElements(res.totalElements);
        setTotalPages(res.totalPages);
      })
      .finally(() => setLoading(false));
  }

  useEffect(refresh, [categoriaFiltro, page]);

  function openCreate() {
    setForm(EMPTY_EXPENSE_FORM);
    setError('');
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await coreApi.expenses.createFleet(form);
      toast.success(t('pages.costs.despesas.toasts.lancada'));
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('pages.costs.despesas.toasts.falhaLancar'));
    }
  }

  async function handleDelete(id: string) {
    await deleteWithConfirm({
      confirmMessage: t('pages.costs.despesas.toasts.confirmarExcluir'),
      remove: () => coreApi.expenses.removeFleet(id),
      successMessage: t('pages.costs.despesas.toasts.excluida'),
      fallbackErrorMessage: t('pages.costs.despesas.toasts.falhaExcluir'),
      onSuccess: refresh,
    });
  }

  const categoriaCombustivel = form.categoria === 'COMBUSTIVEL';

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Select
          value={categoriaFiltro}
          onChange={(e) => {
            setCategoriaFiltro(e.target.value as ExpenseCategory | 'todas');
            setPage(0);
          }}
          className="w-52"
        >
          <option value="todas">{t('pages.costs.despesas.todasAsCategorias')}</option>
          {CATEGORY_OPTIONS.map((c) => (
            <option key={c} value={c}>
              {t(`status.custo.${c}`)}
            </option>
          ))}
        </Select>
        <Button onClick={openCreate} className="ml-auto">
          <Plus /> {t('pages.costs.despesas.novaDespesa')}
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.costs.despesas.despesasContagem', { n: totalElements })}</CardTitle>
        </CardHeader>
        {loading ? (
          <TableSkeleton rows={6} columns={6} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.despesas.tabela.data')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.despesas.tabela.veiculo')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.despesas.tabela.categoria')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.despesas.tabela.valor')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.despesas.tabela.descricao')}
                  </th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {entries.map((e) => (
                  <tr key={e.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(e.data!)}</td>
                    <td className="px-5 py-2.5">
                      {e.plate ? (
                        <PlacaBR placa={e.plate} size="sm" />
                      ) : (
                        <span className="text-muted-foreground">{t('pages.costs.despesas.frota')}</span>
                      )}
                    </td>
                    <td className="px-5 py-2.5">
                      <StatusBadgeCusto categoria={e.categoria} />
                    </td>
                    <td className="px-5 py-2.5 font-data font-medium text-foreground">{formatBRL(Number(e.valor))}</td>
                    <td className="px-5 py-2.5 text-muted-foreground">{e.descricao ?? '—'}</td>
                    <td className="px-5 py-2.5">
                      <Button
                        variant="link"
                        size="sm"
                        className="h-auto p-0 text-destructive"
                        onClick={() => handleDelete(e.id!)}
                      >
                        {t('pages.costs.despesas.excluir')}
                      </Button>
                    </td>
                  </tr>
                ))}
                {entries.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      {t('pages.costs.despesas.nenhumaDespesa')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-5 py-3">
            <span className="text-xs text-muted-foreground">
              {t('pages.costs.despesas.paginaXDeY', { atual: page + 1, total: totalPages })}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                {t('pages.costs.despesas.anterior')}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                {t('pages.costs.despesas.proxima')}
              </Button>
            </div>
          </div>
        )}
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={t('pages.costs.despesas.novaDespesa')}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="veiculo">{t('pages.costs.despesas.form.veiculoOpcional')}</Label>
            <Select
              id="veiculo"
              value={form.vehicleId ?? ''}
              onChange={(e) => setForm({ ...form, vehicleId: e.target.value || undefined })}
            >
              <option value="">{t('pages.costs.despesas.form.despesaDeFrota')}</option>
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.plate} · {v.brand} {v.model}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="categoria">{t('pages.costs.despesas.form.categoria')}</Label>
            <Select
              id="categoria"
              value={form.categoria}
              onChange={(e) =>
                setForm({
                  ...form,
                  categoria: e.target.value as ExpenseCategory,
                  litrosOuKwh: undefined,
                  odometro: undefined,
                })
              }
            >
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {t(`status.custo.${c}`)}
                </option>
              ))}
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="valor">{t('pages.costs.despesas.form.valorReais')}</Label>
              <Input
                id="valor"
                type="number"
                min={0.01}
                step="0.01"
                value={form.valor}
                onChange={(e) => setForm({ ...form, valor: Number(e.target.value) })}
                required
              />
            </div>
            <div>
              <Label htmlFor="data">{t('pages.costs.despesas.form.data')}</Label>
              <Input
                id="data"
                type="date"
                value={form.data}
                onChange={(e) => setForm({ ...form, data: e.target.value })}
                required
              />
            </div>
          </div>
          {categoriaCombustivel && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="litrosOuKwh">{t('pages.costs.despesas.form.litrosOuKwh')}</Label>
                <Input
                  id="litrosOuKwh"
                  type="number"
                  min={0}
                  step="0.001"
                  value={form.litrosOuKwh ?? ''}
                  onChange={(e) =>
                    setForm({ ...form, litrosOuKwh: e.target.value === '' ? undefined : Number(e.target.value) })
                  }
                />
              </div>
              <div>
                <Label htmlFor="odometro">{t('pages.costs.despesas.form.odometroKm')}</Label>
                <Input
                  id="odometro"
                  type="number"
                  min={0}
                  value={form.odometro ?? ''}
                  onChange={(e) =>
                    setForm({ ...form, odometro: e.target.value === '' ? undefined : Number(e.target.value) })
                  }
                />
              </div>
            </div>
          )}
          <div>
            <Label htmlFor="descricao">{t('pages.costs.despesas.form.descricaoOpcional')}</Label>
            <Input
              id="descricao"
              value={form.descricao ?? ''}
              onChange={(e) => setForm({ ...form, descricao: e.target.value })}
            />
          </div>

          {error && <p className="text-xs text-status-danger">{error}</p>}

          <div className="flex justify-end gap-3 border-t border-border pt-4">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              {t('pages.costs.despesas.form.cancelar')}
            </Button>
            <Button type="submit">{t('pages.costs.despesas.form.lancar')}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

function OrcamentoTab({ vehicles }: { vehicles: VehicleResponse[] }) {
  const { t } = useTranslation();
  const [budgets, setBudgets] = useState<BudgetResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<BudgetRequest>(EMPTY_BUDGET_FORM);
  const [error, setError] = useState('');

  function refresh() {
    setLoading(true);
    coreApi.budgets
      .list()
      .then(setBudgets)
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  function openCreate() {
    setForm(EMPTY_BUDGET_FORM);
    setError('');
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await coreApi.budgets.create(form);
      toast.success(t('pages.costs.orcamento.toasts.criado'));
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('pages.costs.orcamento.toasts.falhaCriar'));
    }
  }

  async function handleDelete(id: string) {
    await deleteWithConfirm({
      confirmMessage: t('pages.costs.orcamento.toasts.confirmarExcluir'),
      remove: () => coreApi.budgets.remove(id),
      successMessage: t('pages.costs.orcamento.toasts.excluido'),
      fallbackErrorMessage: t('pages.costs.orcamento.toasts.falhaExcluir'),
      onSuccess: refresh,
    });
  }

  function escopoLabel(b: BudgetResponse): string {
    const veiculo = b.vehicleId
      ? (vehicles.find((v) => v.id === b.vehicleId)?.plate ?? t('pages.costs.despesas.tabela.veiculo'))
      : t('pages.costs.orcamento.frotaInteira');
    const categoria = b.categoria ? t(`status.custo.${b.categoria}`) : t('pages.costs.orcamento.todasAsCategorias');
    return `${veiculo} · ${categoria}`;
  }

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <Button onClick={openCreate}>
          <Plus /> {t('pages.costs.orcamento.novoOrcamento')}
        </Button>
      </div>

      {loading ? (
        <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>
      ) : budgets.length === 0 ? (
        <Card>
          <p className="p-8 text-center text-xs text-muted-foreground">{t('pages.costs.orcamento.nenhumOrcamento')}</p>
        </Card>
      ) : (
        <div className="space-y-3">
          {budgets.map((b) => {
            const percentual = Number(b.percentualConsumido ?? 0);
            const estourado = percentual >= 100;
            const emAviso = percentual >= 80;
            return (
              <Card key={b.id} className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-foreground">{escopoLabel(b)}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {t('pages.costs.orcamento.deValor', {
                        consumido: formatBRL(Number(b.valorConsumido ?? 0)),
                        limite: formatBRL(Number(b.valorLimite ?? 0)),
                      })}{' '}
                      · {b.periodo === 'MENSAL' ? t('pages.costs.orcamento.mensal') : b.periodo}
                    </p>
                  </div>
                  <Button
                    variant="link"
                    size="sm"
                    className="h-auto shrink-0 p-0 text-destructive"
                    onClick={() => handleDelete(b.id!)}
                  >
                    {t('pages.costs.orcamento.excluir')}
                  </Button>
                </div>
                <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn(
                      'h-full rounded-full transition-all',
                      estourado ? 'bg-status-danger' : emAviso ? 'bg-status-warning' : 'bg-primary',
                    )}
                    style={{ width: `${Math.min(100, percentual)}%` }}
                  />
                </div>
                <p
                  className={cn(
                    'mt-1 text-[11px] font-medium',
                    estourado ? 'text-status-danger' : emAviso ? 'text-status-warning' : 'text-muted-foreground',
                  )}
                >
                  {t('pages.costs.orcamento.percentualConsumido', { p: percentual.toFixed(1) })}
                </p>
              </Card>
            );
          })}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={t('pages.costs.orcamento.novoOrcamento')}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="b-veiculo">{t('pages.costs.orcamento.form.escopo')}</Label>
            <Select
              id="b-veiculo"
              value={form.vehicleId ?? ''}
              onChange={(e) => setForm({ ...form, vehicleId: e.target.value || undefined })}
            >
              <option value="">{t('pages.costs.orcamento.frotaInteira')}</option>
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.plate} · {v.brand} {v.model}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="b-categoria">{t('pages.costs.orcamento.form.categoriaOpcional')}</Label>
            <Select
              id="b-categoria"
              value={form.categoria ?? ''}
              onChange={(e) => setForm({ ...form, categoria: (e.target.value || undefined) as ExpenseCategory })}
            >
              <option value="">{t('pages.costs.orcamento.todasAsCategorias')}</option>
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {t(`status.custo.${c}`)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="valorLimite">{t('pages.costs.orcamento.form.valorLimiteMensal')}</Label>
            <Input
              id="valorLimite"
              type="number"
              min={0.01}
              step="0.01"
              value={form.valorLimite}
              onChange={(e) => setForm({ ...form, valorLimite: Number(e.target.value) })}
              required
            />
          </div>

          {error && <p className="text-xs text-status-danger">{error}</p>}

          <div className="flex justify-end gap-3 border-t border-border pt-4">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              {t('pages.costs.orcamento.form.cancelar')}
            </Button>
            <Button type="submit">{t('pages.costs.orcamento.form.criar')}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

function RentabilidadeTab() {
  const { t } = useTranslation();
  const [plans, setPlans] = useState<RoutePlanResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // size grande: esta aba agrega TRANSFER concluído de todo o histórico pra
    // relatório de receita — paginação padrão (100) truncaria o número mostrado,
    // não só "carregaria menos rápido" (mesmo motivo do teto no backend ter subido
    // pra 500, ver RoutePlanController.MAX_PAGE_SIZE).
    coreApi.routePlans
      .list(0, 500)
      .then((res) => setPlans(res.content))
      .finally(() => setLoading(false));
  }, []);

  const transfers = useMemo(
    () => plans.filter((p) => p.categoria === 'TRANSFER' && p.status === 'CONCLUIDA'),
    [plans],
  );

  const totalValor = transfers.reduce((sum, p) => sum + Number(p.valor ?? 0), 0);
  const totalCusto = transfers.reduce((sum, p) => sum + Number(p.custoEstimado ?? 0), 0);
  const totalMargem = transfers.reduce((sum, p) => sum + Number(p.margemRealizada ?? 0), 0);

  if (loading) {
    return <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>;
  }

  return (
    <div>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label={t('pages.costs.rentabilidade.valorCombinadoTotal')} value={formatBRL(totalValor)} />
        <StatCard label={t('pages.costs.rentabilidade.custoEstimadoTotal')} value={formatBRL(totalCusto)} tone="warning" />
        <StatCard
          label={t('pages.costs.rentabilidade.margemRealizadaTotal')}
          value={formatBRL(totalMargem)}
          tone={totalMargem >= 0 ? 'success' : 'danger'}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.costs.rentabilidade.transfersConcluidos')}</CardTitle>
        </CardHeader>
        {transfers.length === 0 ? (
          <p className="p-8 text-center text-xs text-muted-foreground">{t('pages.costs.rentabilidade.nenhumTransfer')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.rentabilidade.tabela.motorista')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.rentabilidade.tabela.data')}
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.rentabilidade.tabela.valor')}
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.rentabilidade.tabela.custoEstimado')}
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.costs.rentabilidade.tabela.margem')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {transfers.map((p) => {
                  const margem = p.margemRealizada != null ? Number(p.margemRealizada) : null;
                  return (
                    <tr key={p.id} className="hover:bg-muted/50">
                      <td className="px-5 py-2.5 text-foreground">{p.driverName ?? '—'}</td>
                      <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(p.dataExecucao!)}</td>
                      <td className="px-5 py-2.5 text-right font-data text-foreground">
                        {p.valor != null ? formatBRL(Number(p.valor)) : '—'}
                      </td>
                      <td className="px-5 py-2.5 text-right font-data text-muted-foreground">
                        {p.custoEstimado != null ? formatBRL(Number(p.custoEstimado)) : '—'}
                      </td>
                      <td
                        className={cn(
                          'px-5 py-2.5 text-right font-data font-semibold',
                          margem == null
                            ? 'text-muted-foreground'
                            : margem >= 0
                              ? 'text-status-success'
                              : 'text-status-danger',
                        )}
                      >
                        {margem != null ? formatBRL(margem) : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
