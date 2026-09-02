import { useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type ExpenseEntryRequest, type ExpenseEntryResponse, type ExpenseSummaryResponse } from '../api/client';
import { usePode } from '../auth/AuthContext';
import { StatusBadgeCusto } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { StatCard } from '../components/shared/StatCard';
import { formatDateBR } from '../lib/format';
import { maskMoedaBR, parseMoedaBR } from '../lib/masks';
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';
import { TableSkeleton } from '../components/shared/TableSkeleton';

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
  const { t } = useTranslation();
  const podeEscrever = usePode('CUSTOS', 'ESCREVER');
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
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('pages.vehicleCosts.toasts.falhaCarregar')))
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
      toast.success(t('pages.vehicleCosts.toasts.lancado'));
      setModalOpen(false);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('pages.vehicleCosts.toasts.falhaLancar'));
    }
  }

  async function handleDelete(costId: string) {
    await deleteWithConfirm({
      confirmMessage: t('pages.vehicleCosts.toasts.confirmarExcluir'),
      remove: () => coreApi.expenses.remove(vehicleId, costId),
      successMessage: t('pages.vehicleCosts.toasts.excluido'),
      fallbackErrorMessage: t('pages.vehicleCosts.toasts.falhaExcluir'),
      onSuccess: refresh,
    });
  }

  const categoriaCombustivel = form.categoria === 'COMBUSTIVEL';

  return (
    <div className="p-5">
      <Button variant="ghost" size="sm" onClick={onBack} className="mb-4">
        <ArrowLeft /> {t('pages.vehicleCosts.voltarParaVeiculos')}
      </Button>

      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.vehicleCosts.custosDe', { placa: plate })}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.vehicleCosts.subtitulo')}</p>
        </div>
        {podeEscrever && (
          <Button onClick={openCreate}>
            <Plus /> {t('pages.vehicleCosts.lancarCusto')}
          </Button>
        )}
      </div>

      {summary && (
        <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <StatCard label={t('pages.vehicleCosts.totalGasto')} value={`R$ ${summary.totalValor?.toFixed(2)}`} />
          {/* Km rodado desde o cadastro é o número grande — é sobre ele que o custo por km
              é calculado. Odômetro total vai só como hint pequeno: contexto de canto, sem
              disputar espaço com o que de fato importa pra essa conta. */}
          <StatCard
            label={t('pages.vehicleCosts.kmRodadoDesdeCadastro')}
            value={`${summary.kmRodado} km`}
            hint={t('pages.vehicleCosts.odometroTotal', { km: summary.odometerKm })}
          />
          <StatCard
            label={t('pages.vehicleCosts.custoPorKm')}
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
          <CardTitle>{t('pages.vehicleCosts.lancamentos')}</CardTitle>
        </CardHeader>
        {loading ? (
          <TableSkeleton rows={5} columns={5} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicleCosts.tabela.data')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicleCosts.tabela.categoria')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicleCosts.tabela.valor')}
                  </th>
                  <th className="px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {t('pages.vehicleCosts.tabela.descricao')}
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
                      {podeEscrever && (
                        <Button
                          variant="link"
                          size="sm"
                          className="h-auto p-0 text-destructive"
                          onClick={() => handleDelete(c.id!)}
                        >
                          {t('pages.vehicleCosts.excluir')}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
                {entries.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      {t('pages.vehicleCosts.nenhumCusto')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={t('pages.vehicleCosts.lancarCusto')}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="categoria">{t('pages.vehicleCosts.form.categoria')}</Label>
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
                  {t(`status.custo.${c}`)}
                </option>
              ))}
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="valor">{t('pages.vehicleCosts.form.valorReais')}</Label>
              <Input
                id="valor"
                inputMode="decimal"
                value={form.valor ? maskMoedaBR(String(Math.round(form.valor * 100))) : ''}
                onChange={(e) => setForm({ ...form, valor: parseMoedaBR(maskMoedaBR(e.target.value)) })}
                required
              />
            </div>
            <div>
              <Label htmlFor="data">{t('pages.vehicleCosts.form.data')}</Label>
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
                <Label htmlFor="litrosOuKwh">{t('pages.vehicleCosts.form.litrosOuKwh')}</Label>
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
                <Label htmlFor="odometro">{t('pages.vehicleCosts.form.odometroKm')}</Label>
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
            <Label htmlFor="descricao">{t('pages.vehicleCosts.form.descricaoOpcional')}</Label>
            <Input
              id="descricao"
              value={form.descricao ?? ''}
              onChange={(e) => setForm({ ...form, descricao: e.target.value })}
            />
          </div>

          {error && <p className="text-xs text-status-danger">{error}</p>}

          <div className="flex justify-end gap-3 border-t border-border pt-4">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              {t('pages.vehicleCosts.form.cancelar')}
            </Button>
            <Button type="submit">{t('pages.vehicleCosts.form.lancar')}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
