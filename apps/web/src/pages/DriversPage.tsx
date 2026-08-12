import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Plus } from 'lucide-react';
import { coreApi, type DriverRequest, type DriverResponse } from '../api/client';
import { StatusBadgeMotorista } from '../components/shared/StatusBadge';
import { Avatar, AvatarFallback } from '../components/ui/avatar';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { cn } from '../lib/utils';
import { diasAteVencer, iniciais } from '../lib/format';

const STATUS_OPTIONS = ['ATIVO', 'INATIVO'] as const;

const EMPTY_FORM: DriverRequest = { name: '', cnh: '', phone: '', status: 'ATIVO', cnhValidade: undefined };

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

  function refresh() {
    coreApi.drivers
      .list()
      .then(setDrivers)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar motoristas'))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

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
                        <div className="flex gap-4">
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
    </div>
  );
}
