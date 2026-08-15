import { useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, Plus } from 'lucide-react';
import {
  coreApi,
  type VehicleCostEntryRequest,
  type VehicleCostEntryResponse,
  type VehicleCostSummaryResponse,
} from '../api/client';
import { StatusBadgeCusto } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { StatCard } from '../components/shared/StatCard';

const CATEGORY_OPTIONS = ['COMBUSTIVEL', 'MANUTENCAO', 'OUTRO'] as const;

const EMPTY_FORM: VehicleCostEntryRequest = {
  category: 'COMBUSTIVEL',
  amount: 0,
  description: '',
  occurredAt: new Date().toISOString().slice(0, 10),
};

interface Props {
  vehicleId: string;
  plate: string;
  onBack: () => void;
}

export function VehicleCostsPage({ vehicleId, plate, onBack }: Props) {
  const [entries, setEntries] = useState<VehicleCostEntryResponse[]>([]);
  const [summary, setSummary] = useState<VehicleCostSummaryResponse | null>(null);
  const [form, setForm] = useState<VehicleCostEntryRequest>(EMPTY_FORM);
  const [modalOpen, setModalOpen] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  function refresh() {
    Promise.all([coreApi.vehicleCosts.list(vehicleId), coreApi.vehicleCosts.summary(vehicleId)])
      .then(([e, s]) => {
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
      await coreApi.vehicleCosts.add(vehicleId, form);
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao lançar custo');
    }
  }

  async function handleDelete(costId: string) {
    if (!confirm('Excluir este lançamento?')) return;
    try {
      await coreApi.vehicleCosts.remove(vehicleId, costId);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao excluir lançamento');
    }
  }

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
          <StatCard label="Total Gasto" value={`R$ ${summary.totalCost?.toFixed(2)}`} />
          <StatCard label="Odômetro" value={`${summary.odometerKm} km`} />
          <StatCard
            label="Custo por Km"
            value={summary.costPerKm != null ? `R$ ${summary.costPerKm.toFixed(2)}` : '—'}
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
                    <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(c.occurredAt!)}</td>
                    <td className="px-5 py-2.5">
                      <StatusBadgeCusto categoria={c.category} />
                    </td>
                    <td className="px-5 py-2.5 font-data font-medium text-foreground">R$ {c.amount?.toFixed(2)}</td>
                    <td className="px-5 py-2.5 text-muted-foreground">{c.description ?? '—'}</td>
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
            <Label htmlFor="category">Categoria</Label>
            <Select
              id="category"
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value as VehicleCostEntryRequest['category'] })}
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
              <Label htmlFor="amount">Valor (R$)</Label>
              <Input
                id="amount"
                type="number"
                min={0.01}
                step="0.01"
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: Number(e.target.value) })}
                required
              />
            </div>
            <div>
              <Label htmlFor="occurredAt">Data</Label>
              <Input
                id="occurredAt"
                type="date"
                value={form.occurredAt}
                onChange={(e) => setForm({ ...form, occurredAt: e.target.value })}
                required
              />
            </div>
          </div>
          <div>
            <Label htmlFor="description">Descrição (opcional)</Label>
            <Input
              id="description"
              value={form.description ?? ''}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
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

/** Formata "YYYY-MM-DD" (LocalDate, sem timezone) como "DD/MM/YYYY" via string,
 *  sem passar por Date — evita deslocar o dia pra quem está a oeste de UTC. */
function formatDateBR(isoDate: string): string {
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}
