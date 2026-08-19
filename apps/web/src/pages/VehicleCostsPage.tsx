import { useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, Plus } from 'lucide-react';
import { coreApi, type ExpenseEntryRequest, type ExpenseEntryResponse, type ExpenseSummaryResponse } from '../api/client';
import { StatusBadgeCusto } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { StatCard } from '../components/shared/StatCard';
import { formatDateBR } from '../lib/format';

const CATEGORY_OPTIONS = [
  'COMBUSTIVEL',
  'MANUTENCAO',
  'SEGURO',
  'IPVA',
  'MULTA',
  'PEDAGIO',
  'LAVAGEM',
  'OUTRO',
] as const;

const EMPTY_FORM: ExpenseEntryRequest = {
  categoria: 'COMBUSTIVEL',
  valor: 0,
  descricao: '',
  data: new Date().toISOString().slice(0, 10),
};

interface Props {
  vehicleId: string;
  onBack: () => void;
}

export function VehicleCostsPage({ vehicleId, onBack }: Props) {
  // Busca a placa em vez de receber por prop: essa tela agora tem URL própria
  // (/frota/:id/custos), então precisa se sustentar sozinha num F5 ou link direto —
  // o vehicleId da URL é o único dado garantido, o resto (placa) o front tem que buscar.
  const [plate, setPlate] = useState('');
  const [entries, setEntries] = useState<ExpenseEntryResponse[]>([]);
  const [summary, setSummary] = useState<ExpenseSummaryResponse | null>(null);
  const [form, setForm] = useState<ExpenseEntryRequest>(EMPTY_FORM);
  const [modalOpen, setModalOpen] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  function refresh() {
    Promise.all([coreApi.vehicles.get(vehicleId), coreApi.expenses.list(vehicleId), coreApi.expenses.summary(vehicleId)])
      .then(([v, e, s]) => {
        setPlate(v.plate ?? '');
        setEntries(e);
        setSummary(s);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Erro ao carregar custos'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, [vehicleId]);

  function openCreate() {
    setForm(EMPTY_FORM);
    setError('');
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await coreApi.expenses.add(vehicleId, form);
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao lançar custo');
    }
  }

  async function handleDelete(costId: string) {
    if (!confirm('Excluir este lançamento?')) return;
    try {
      await coreApi.expenses.remove(vehicleId, costId);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao excluir lançamento');
    }
  }

  const categoriaCombustivel = form.categoria === 'COMBUSTIVEL';

  return (
    <div className="p-5">
      <Button variant="ghost" size="sm" onClick={onBack} className="mb-4">
        <ArrowLeft /> Voltar para Veículos
      </Button>

      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Custos — {plate}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">Lançamentos de combustível, manutenção e outros</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> Lançar Custo
        </Button>
      </div>

      {summary && (
        <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <StatCard label="Total Gasto" value={`R$ ${summary.totalValor?.toFixed(2)}`} />
          {/* Km rodado desde o cadastro é o número grande — é sobre ele que o custo por km
              é calculado. Odômetro total vai só como hint pequeno: contexto de canto, sem
              disputar espaço com o que de fato importa pra essa conta. */}
          <StatCard
            label="Km Rodado (desde o cadastro)"
            value={`${summary.kmRodado} km`}
            hint={`Odômetro total: ${summary.odometerKm} km`}
          />
          <StatCard
            label="Custo por Km"
            value={summary.custoPorKm != null ? `R$ ${summary.custoPorKm.toFixed(2)}` : '—'}
            tone="success"
          />
        </div>
      )}

      {error && !modalOpen && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Lançamentos</CardTitle>
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
                {entries.map((c) => (
                  <tr key={c.id} className="hover:bg-muted/50">
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(c.data!)}</td>
                    <td className="px-5 py-2.5">
                      <StatusBadgeCusto categoria={c.categoria} />
                    </td>
                    <td className="px-5 py-2.5 font-data font-medium text-foreground">R$ {c.valor?.toFixed(2)}</td>
                    <td className="px-5 py-2.5 text-muted-foreground">{c.descricao ?? '—'}</td>
                    <td className="px-5 py-2.5">
                      <Button
                        variant="link"
                        size="sm"
                        className="h-auto p-0 text-destructive"
                        onClick={() => handleDelete(c.id!)}
                      >
                        Excluir
                      </Button>
                    </td>
                  </tr>
                ))}
                {entries.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Nenhum custo lançado ainda.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Lançar Custo">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="categoria">Categoria</Label>
            <Select
              id="categoria"
              value={form.categoria}
              onChange={(e) =>
                setForm({
                  ...form,
                  categoria: e.target.value as ExpenseEntryRequest['categoria'],
                  litrosOuKwh: undefined,
                  odometro: undefined,
                })
              }
            >
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {c}
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
