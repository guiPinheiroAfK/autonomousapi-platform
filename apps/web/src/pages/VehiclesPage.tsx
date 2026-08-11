import { useEffect, useState, type FormEvent } from 'react';
import { coreApi, type VehicleRequest, type VehicleResponse } from '../api/client';

const STATUS_OPTIONS = ['ATIVO', 'MANUTENCAO', 'INATIVO'] as const;

const EMPTY_FORM: VehicleRequest = {
  plate: '',
  brand: '',
  model: '',
  modelYear: undefined,
  odometerKm: 0,
  status: 'ATIVO',
};

interface Props {
  onViewCosts: (vehicleId: string, plate: string) => void;
}

export function VehiclesPage({ onViewCosts }: Props) {
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [form, setForm] = useState<VehicleRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  function refresh() {
    coreApi.vehicles
      .list()
      .then(setVehicles)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar veículos'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      if (editingId) {
        await coreApi.vehicles.update(editingId, form);
      } else {
        await coreApi.vehicles.create(form);
      }
      setForm(EMPTY_FORM);
      setEditingId(null);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao salvar veículo');
    }
  }

  function startEdit(v: VehicleResponse) {
    setEditingId(v.id!);
    setForm({
      plate: v.plate!,
      brand: v.brand!,
      model: v.model!,
      modelYear: v.modelYear,
      odometerKm: v.odometerKm!,
      status: v.status as VehicleRequest['status'],
    });
  }

  async function handleDelete(id: string) {
    if (!confirm('Excluir este veículo?')) return;
    try {
      await coreApi.vehicles.remove(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao excluir veículo');
    }
  }

  return (
    <section>
      <h2>Veículos</h2>

      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
        <input
          placeholder="Placa"
          value={form.plate}
          onChange={(e) => setForm({ ...form, plate: e.target.value })}
          required
        />
        <input
          placeholder="Marca"
          value={form.brand}
          onChange={(e) => setForm({ ...form, brand: e.target.value })}
          required
        />
        <input
          placeholder="Modelo"
          value={form.model}
          onChange={(e) => setForm({ ...form, model: e.target.value })}
          required
        />
        <input
          type="number"
          placeholder="Ano"
          value={form.modelYear ?? ''}
          onChange={(e) => setForm({ ...form, modelYear: e.target.value ? Number(e.target.value) : undefined })}
          style={{ width: 90 }}
        />
        <input
          type="number"
          placeholder="Odômetro (km)"
          value={form.odometerKm}
          onChange={(e) => setForm({ ...form, odometerKm: Number(e.target.value) })}
          min={0}
          required
          style={{ width: 130 }}
        />
        <select
          value={form.status}
          onChange={(e) => setForm({ ...form, status: e.target.value as VehicleRequest['status'] })}
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <button type="submit">{editingId ? 'Salvar' : 'Adicionar'}</button>
        {editingId && (
          <button
            type="button"
            onClick={() => {
              setEditingId(null);
              setForm(EMPTY_FORM);
            }}
          >
            Cancelar
          </button>
        )}
      </form>

      {error && <p style={{ color: '#c00' }}>{error}</p>}
      {loading ? (
        <p>Carregando...</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '1px solid #ccc' }}>
              <th>Placa</th>
              <th>Marca/Modelo</th>
              <th>Ano</th>
              <th>Odômetro</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {vehicles.map((v) => (
              <tr key={v.id} style={{ borderBottom: '1px solid #eee' }}>
                <td>{v.plate}</td>
                <td>
                  {v.brand} {v.model}
                </td>
                <td>{v.modelYear ?? '—'}</td>
                <td>{v.odometerKm} km</td>
                <td>{v.status}</td>
                <td style={{ display: 'flex', gap: 6 }}>
                  <button type="button" onClick={() => onViewCosts(v.id!, v.plate!)}>
                    Custos
                  </button>
                  <button type="button" onClick={() => startEdit(v)}>
                    Editar
                  </button>
                  <button type="button" onClick={() => handleDelete(v.id!)}>
                    Excluir
                  </button>
                </td>
              </tr>
            ))}
            {vehicles.length === 0 && (
              <tr>
                <td colSpan={6}>Nenhum veículo cadastrado ainda.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}
