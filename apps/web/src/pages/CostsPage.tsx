import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Download, Plus, Wallet } from 'lucide-react';
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
const CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  COMBUSTIVEL: 'Combustível',
  MANUTENCAO: 'Manutenção',
  SEGURO: 'Seguro',
  IPVA: 'IPVA',
  MULTA: 'Multa',
  PEDAGIO: 'Pedágio',
  LAVAGEM: 'Lavagem',
  OUTRO: 'Outro',
};
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
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);

  useEffect(() => {
    coreApi.vehicles.list().then(setVehicles);
  }, []);

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Custos</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Despesas categorizadas, orçamento e rentabilidade de transfers
        </p>
      </div>

      <Tabs defaultValue="visao-geral">
        <TabsList>
          <TabsTrigger value="visao-geral">Visão geral</TabsTrigger>
          <TabsTrigger value="despesas">Despesas</TabsTrigger>
          <TabsTrigger value="orcamento">Orçamento</TabsTrigger>
          <TabsTrigger value="rentabilidade">Rentabilidade de transfers</TabsTrigger>
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

  const total = totals.reduce((sum, t) => sum + Number(t.total ?? 0), 0);
  const maior = [...totals].sort((a, b) => Number(b.total ?? 0) - Number(a.total ?? 0))[0];
  const chartData = totals.map((t) => ({
    categoria: CATEGORY_LABEL[t.categoria as ExpenseCategory] ?? t.categoria,
    total: Number(t.total ?? 0),
    fill: CATEGORY_COLOR[t.categoria as ExpenseCategory] ?? 'var(--color-primary)',
  }));

  async function handleExport() {
    setExporting(true);
    try {
      await coreApi.reports.exportCostsCsv();
    } finally {
      setExporting(false);
    }
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div>
          <Label htmlFor="from">De</Label>
          <Input id="from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="to">Até</Label>
          <Input id="to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <Button variant="outline" onClick={handleExport} disabled={exporting} className="ml-auto">
          <Download /> {exporting ? 'Exportando...' : 'Exportar CSV'}
        </Button>
      </div>

      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatCard label="Total no período" value={formatBRL(total)} icon={Wallet} />
        <StatCard
          label="Maior categoria"
          value={maior ? CATEGORY_LABEL[maior.categoria as ExpenseCategory] : '—'}
          hint={maior ? formatBRL(Number(maior.total ?? 0)) : undefined}
          tone="warning"
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Despesas por categoria</CardTitle>
        </CardHeader>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
        ) : chartData.length === 0 ? (
          <p className="p-8 text-center text-xs text-muted-foreground">Nenhuma despesa no período.</p>
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

function DespesasTab({ vehicles }: { vehicles: VehicleResponse[] }) {
  const [entries, setEntries] = useState<FleetExpenseEntryResponse[]>([]);
  const [categoriaFiltro, setCategoriaFiltro] = useState<ExpenseCategory | 'todas'>('todas');
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<ExpenseEntryRequest>(EMPTY_EXPENSE_FORM);
  const [error, setError] = useState('');

  function refresh() {
    setLoading(true);
    coreApi.expenses
      .fleetList(categoriaFiltro === 'todas' ? undefined : categoriaFiltro)
      .then(setEntries)
      .finally(() => setLoading(false));
  }

  useEffect(refresh, [categoriaFiltro]);

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
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao lançar despesa');
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Excluir esta despesa?')) return;
    await coreApi.expenses.removeFleet(id);
    refresh();
  }

  const categoriaCombustivel = form.categoria === 'COMBUSTIVEL';

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Select
          value={categoriaFiltro}
          onChange={(e) => setCategoriaFiltro(e.target.value as ExpenseCategory | 'todas')}
          className="w-52"
        >
          <option value="todas">Todas as categorias</option>
          {CATEGORY_OPTIONS.map((c) => (
            <option key={c} value={c}>
              {CATEGORY_LABEL[c]}
            </option>
          ))}
        </Select>
        <Button onClick={openCreate} className="ml-auto">
          <Plus /> Nova despesa
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Despesas</CardTitle>
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
                    Categoria
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Valor
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Descrição
                  </th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {entries.map((e) => (
                  <tr key={e.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(e.data!)}</td>
                    <td className="px-5 py-2.5">
                      {e.plate ? <PlacaBR placa={e.plate} size="sm" /> : <span className="text-muted-foreground">Frota</span>}
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
                        Excluir
                      </Button>
                    </td>
                  </tr>
                ))}
                {entries.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Nenhuma despesa lançada ainda.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova despesa">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="veiculo">Veículo (opcional — em branco = despesa de frota)</Label>
            <Select
              id="veiculo"
              value={form.vehicleId ?? ''}
              onChange={(e) => setForm({ ...form, vehicleId: e.target.value || undefined })}
            >
              <option value="">Despesa de frota (sem veículo)</option>
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.plate} · {v.brand} {v.model}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="categoria">Categoria</Label>
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
                  {CATEGORY_LABEL[c]}
                </option>
              ))}
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="valor">Valor (R$)</Label>
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
              <Label htmlFor="data">Data</Label>
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
                <Label htmlFor="litrosOuKwh">Litros (ou kWh)</Label>
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
                <Label htmlFor="odometro">Odômetro (km)</Label>
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
            <Label htmlFor="descricao">Descrição (opcional)</Label>
            <Input
              id="descricao"
              value={form.descricao ?? ''}
              onChange={(e) => setForm({ ...form, descricao: e.target.value })}
            />
          </div>

          {error && <p className="text-xs text-status-danger">{error}</p>}

          <div className="flex justify-end gap-3 border-t border-border pt-4">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit">Lançar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

function OrcamentoTab({ vehicles }: { vehicles: VehicleResponse[] }) {
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
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao criar orçamento');
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Excluir este orçamento?')) return;
    await coreApi.budgets.remove(id);
    refresh();
  }

  function escopoLabel(b: BudgetResponse): string {
    const veiculo = b.vehicleId ? vehicles.find((v) => v.id === b.vehicleId)?.plate ?? 'Veículo' : 'Frota inteira';
    const categoria = b.categoria ? CATEGORY_LABEL[b.categoria as ExpenseCategory] : 'Todas as categorias';
    return `${veiculo} · ${categoria}`;
  }

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <Button onClick={openCreate}>
          <Plus /> Novo orçamento
        </Button>
      </div>

      {loading ? (
        <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
      ) : budgets.length === 0 ? (
        <Card>
          <p className="p-8 text-center text-xs text-muted-foreground">Nenhum orçamento configurado ainda.</p>
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
                      {formatBRL(Number(b.valorConsumido ?? 0))} de {formatBRL(Number(b.valorLimite ?? 0))} ·{' '}
                      {b.periodo === 'MENSAL' ? 'mensal' : b.periodo}
                    </p>
                  </div>
                  <Button
                    variant="link"
                    size="sm"
                    className="h-auto shrink-0 p-0 text-destructive"
                    onClick={() => handleDelete(b.id!)}
                  >
                    Excluir
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
                  {percentual.toFixed(1)}% consumido
                </p>
              </Card>
            );
          })}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo orçamento">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="b-veiculo">Escopo</Label>
            <Select
              id="b-veiculo"
              value={form.vehicleId ?? ''}
              onChange={(e) => setForm({ ...form, vehicleId: e.target.value || undefined })}
            >
              <option value="">Frota inteira</option>
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.plate} · {v.brand} {v.model}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="b-categoria">Categoria (opcional)</Label>
            <Select
              id="b-categoria"
              value={form.categoria ?? ''}
              onChange={(e) => setForm({ ...form, categoria: (e.target.value || undefined) as ExpenseCategory })}
            >
              <option value="">Todas as categorias</option>
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABEL[c]}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="valorLimite">Valor limite mensal (R$)</Label>
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
              Cancelar
            </Button>
            <Button type="submit">Criar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

function RentabilidadeTab() {
  const [plans, setPlans] = useState<RoutePlanResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    coreApi.routePlans
      .list()
      .then(setPlans)
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
    return <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>;
  }

  return (
    <div>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label="Valor combinado (total)" value={formatBRL(totalValor)} />
        <StatCard label="Custo estimado (total)" value={formatBRL(totalCusto)} tone="warning" />
        <StatCard
          label="Margem realizada (total)"
          value={formatBRL(totalMargem)}
          tone={totalMargem >= 0 ? 'success' : 'danger'}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Transfers concluídos</CardTitle>
        </CardHeader>
        {transfers.length === 0 ? (
          <p className="p-8 text-center text-xs text-muted-foreground">
            Nenhum transfer concluído ainda — a margem realizada só aparece depois que a rota é finalizada.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Motorista
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Data
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Valor
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Custo estimado
                  </th>
                  <th className="px-5 py-2.5 text-right text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Margem
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
