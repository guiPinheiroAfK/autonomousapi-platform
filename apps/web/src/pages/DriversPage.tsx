import { useEffect, useState, type FormEvent } from 'react';
import { coreApi, type DriverRequest, type DriverResponse } from '../api/client';

const STATUS_OPTIONS = ['ATIVO', 'INATIVO'] as const;

const EMPTY_FORM: DriverRequest = { name: '', cnh: '', phone: '', status: 'ATIVO' };

export function DriversPage() {
  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [form, setForm] = useState<DriverRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  function refresh() {
    coreApi.drivers
      .list()
      .then(setDrivers)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar motoristas'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      if (editingId) {
        await coreApi.drivers.update(editingId, form);
      } else {
        await coreApi.drivers.create(form);
      }
      setForm(EMPTY_FORM);
      setEditingId(null);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao salvar motorista');
    }
  }

  function startEdit(d: DriverResponse) {
    setEditingId(d.id!);
    setForm({
      name: d.name!,
      cnh: d.cnh!,
      phone: d.phone ?? '',
      status: d.status as DriverRequest['status'],
    });
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

  return (
    <section>
      <h2>Motoristas</h2>

      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
        <input
          placeholder="Nome"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />
        <input
          placeholder="CNH (11 dígitos)"
          value={form.cnh}
          onChange={(e) => setForm({ ...form, cnh: e.target.value })}
          pattern="\d{11}"
          title="11 dígitos numéricos"
          required
        />
        <input
          placeholder="Telefone"
          value={form.phone ?? ''}
          onChange={(e) => setForm({ ...form, phone: e.target.value })}
        />
        <select
          value={form.status}
          onChange={(e) => setForm({ ...form, status: e.target.value as DriverRequest['status'] })}
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
              <th>Nome</th>
              <th>CNH</th>
              <th>Telefone</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {drivers.map((d) => (
              <tr key={d.id} style={{ borderBottom: '1px solid #eee' }}>
                <td>{d.name}</td>
                <td>{d.cnh}</td>
                <td>{d.phone ?? '—'}</td>
                <td>{d.status}</td>
                <td style={{ display: 'flex', gap: 6 }}>
                  <button type="button" onClick={() => startEdit(d)}>
                    Editar
                  </button>
                  <button type="button" onClick={() => handleDelete(d.id!)}>
                    Excluir
                  </button>
                </td>
              </tr>
            ))}
            {drivers.length === 0 && (
              <tr>
                <td colSpan={5}>Nenhum motorista cadastrado ainda.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}
