import { useEffect, useState, type FormEvent } from 'react';
import {
  coreApi,
  type VehicleCostEntryRequest,
  type VehicleCostEntryResponse,
  type VehicleCostSummaryResponse,
} from '../api/client';

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

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await coreApi.vehicleCosts.add(vehicleId, form);
      setForm(EMPTY_FORM);
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
    <section>
      <button type="button" onClick={onBack} style={{ marginBottom: 12 }}>
        ← Voltar para Veículos
      </button>
      <h2>Custos — {plate}</h2>

      {summary && (
        <p>
          Total: <strong>R$ {summary.totalCost?.toFixed(2)}</strong> · Odômetro:{' '}
          <strong>{summary.odometerKm} km</strong> · Custo/km:{' '}
          <strong>{summary.costPerKm != null ? `R$ ${summary.costPerKm.toFixed(2)}` : '—'}</strong>
        </p>
      )}

      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
        <select
          value={form.category}
          onChange={(e) => setForm({ ...form, category: e.target.value as VehicleCostEntryRequest['category'] })}
        >
          {CATEGORY_OPTIONS.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <input
          type="number"
          placeholder="Valor (R$)"
          value={form.amount}
          onChange={(e) => setForm({ ...form, amount: Number(e.target.value) })}
          min={0.01}
          step="0.01"
          required
          style={{ width: 120 }}
        />
        <input
          type="date"
          value={form.occurredAt}
          onChange={(e) => setForm({ ...form, occurredAt: e.target.value })}
          required
        />
        <input
          placeholder="Descrição (opcional)"
          value={form.description ?? ''}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
        <button type="submit">Lançar</button>
      </form>

      {error && <p style={{ color: '#c00' }}>{error}</p>}
      {loading ? (
        <p>Carregando...</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '1px solid #ccc' }}>
              <th>Data</th>
              <th>Categoria</th>
              <th>Valor</th>
              <th>Descrição</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {entries.map((c) => (
              <tr key={c.id} style={{ borderBottom: '1px solid #eee' }}>
                <td>{c.occurredAt}</td>
                <td>{c.category}</td>
                <td>R$ {c.amount?.toFixed(2)}</td>
                <td>{c.description ?? '—'}</td>
                <td>
                  <button type="button" onClick={() => handleDelete(c.id!)}>
                    Excluir
                  </button>
                </td>
              </tr>
            ))}
            {entries.length === 0 && (
              <tr>
                <td colSpan={5}>Nenhum custo lançado ainda.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}
