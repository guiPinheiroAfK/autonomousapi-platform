import { useEffect, useState, type FormEvent } from 'react';
import { ChevronLeft, Gauge, ShieldAlert } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type VehicleConditionScoreResponse,
  type ExpenseEntryResponse,
  type VehicleIncidentRequest,
  type VehicleIncidentResponse,
  type VehicleMarketValueRequest,
  type VehicleMarketValueResponse,
  type VehicleResponse,
  type WorkOrderResponse,
} from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeOS, StatusBadgeSeveridade, StatusBadgeVeiculo } from '../components/shared/StatusBadge';
import { VehicleTypeIcon } from '../components/shared/VehicleTypeIcon';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import { formatBRL, formatDateBR } from '../lib/format';
import { toast } from '../lib/toast';

interface Props {
  vehicleId: string;
  onBack: () => void;
}

/**
 * Rota própria (/frota/:id) no lugar do antigo dialog — spec 08 item 1. Mesmo conteúdo
 * (4 abas), agora com espaço pra crescer e deep-link direto pro veículo funcionando.
 */
export function VehicleDetailPage({ vehicleId, onBack }: Props) {
  const { t } = useTranslation();
  const [vehicle, setVehicle] = useState<VehicleResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  const [costs, setCosts] = useState<ExpenseEntryResponse[]>([]);
  const [os, setOs] = useState<WorkOrderResponse[]>([]);
  const [fipe, setFipe] = useState<VehicleMarketValueResponse | null>(null);
  const [score, setScore] = useState<VehicleConditionScoreResponse | null>(null);
  const [incidents, setIncidents] = useState<VehicleIncidentResponse[]>([]);

  const [fipeForm, setFipeForm] = useState({ valorFipe: '', dataReferencia: '', codigoFipe: '' });
  const [fipeSaving, setFipeSaving] = useState(false);
  const [incidentForm, setIncidentForm] = useState<VehicleIncidentRequest>({
    data: '',
    severidade: 'LEVE',
    descricao: '',
    custoReparo: undefined,
  });
  const [incidentSaving, setIncidentSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    setNotFound(false);
    coreApi.vehicles
      .get(vehicleId)
      .then((v) => {
        setVehicle(v);
        setFipeForm({ valorFipe: '', dataReferencia: new Date().toISOString().slice(0, 10), codigoFipe: '' });
        setIncidentForm({
          data: new Date().toISOString().slice(0, 10),
          severidade: 'LEVE',
          descricao: '',
          custoReparo: undefined,
        });
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));

    coreApi.expenses.list(vehicleId).then(setCosts);
    coreApi.workOrders.list(vehicleId).then((res) => setOs(res.content));
    coreApi.vehicleMarketValue.latest(vehicleId).then(setFipe);
    coreApi.vehicleCondition.score(vehicleId).then(setScore);
    coreApi.vehicleCondition.incidents(vehicleId).then(setIncidents);
  }, [vehicleId]);

  function refreshCondition() {
    coreApi.vehicleCondition.score(vehicleId).then(setScore);
    coreApi.vehicleCondition.incidents(vehicleId).then(setIncidents);
  }

  async function handleAddFipe(e: FormEvent) {
    e.preventDefault();
    setFipeSaving(true);
    try {
      const body: VehicleMarketValueRequest = {
        valorFipe: Number(fipeForm.valorFipe),
        dataReferencia: fipeForm.dataReferencia,
        codigoFipe: fipeForm.codigoFipe || undefined,
      };
      const saved = await coreApi.vehicleMarketValue.record(vehicleId, body);
      setFipe(saved);
      toast.success(t('pages.vehicleDetail.toasts.fipeLancado'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.vehicleDetail.toasts.falhaFipe'));
    } finally {
      setFipeSaving(false);
    }
  }

  async function handleAddIncident(e: FormEvent) {
    e.preventDefault();
    setIncidentSaving(true);
    try {
      await coreApi.vehicleCondition.registerIncident(vehicleId, incidentForm);
      setIncidentForm({ data: new Date().toISOString().slice(0, 10), severidade: 'LEVE', descricao: '', custoReparo: undefined });
      refreshCondition();
      toast.success(t('pages.vehicleDetail.toasts.sinistroRegistrado'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.vehicleDetail.toasts.falhaSinistro'));
    } finally {
      setIncidentSaving(false);
    }
  }

  if (loading) {
    return <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>;
  }

  if (notFound || !vehicle) {
    return (
      <div className="p-5">
        <Breadcrumb onBack={onBack} />
        <p className="mt-8 text-center text-sm text-muted-foreground">{t('pages.vehicleDetail.veiculoNaoEncontrado')}</p>
      </div>
    );
  }

  const manutencao = costs.filter((c) => c.categoria === 'MANUTENCAO');

  return (
    <div className="p-5">
      <Breadcrumb onBack={onBack} placa={vehicle.plate} modelo={vehicle.model} />

      <div className="mb-5 flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className="flex size-9 shrink-0 items-center justify-center rounded-md border border-border bg-secondary"
            title={vehicle.tipo ? t(`status.tipoVeiculo.${vehicle.tipo}`) : t('pages.vehicles.tipoNaoInformado')}
          >
            <VehicleTypeIcon tipo={vehicle.tipo} className="size-[18px] text-foreground" />
          </div>
          <PlacaBR placa={vehicle.plate!} />
          <div>
            <h2 className="font-display text-lg font-semibold text-foreground">{vehicle.model}</h2>
            <p className="text-xs text-muted-foreground">
              {vehicle.brand} · {vehicle.modelYear ?? '—'}
              {vehicle.tipo ? ` · ${t(`status.tipoVeiculo.${vehicle.tipo}`)}` : ''}
            </p>
          </div>
        </div>
        <StatusBadgeVeiculo status={vehicle.status} />
      </div>

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <InfoStat label={t('pages.vehicleDetail.kmAtual')} value={`${vehicle.odometerKm?.toLocaleString('pt-BR')} km`} />
        <InfoStat
          label={t('pages.vehicleDetail.proximaPreventiva')}
          value={vehicle.proximaManutencaoData ? formatDateBR(vehicle.proximaManutencaoData) : '—'}
        />
        <InfoStat
          label={t('pages.vehicleDetail.preventivaPorKm')}
          value={vehicle.proximaManutencaoKm ? `${vehicle.proximaManutencaoKm.toLocaleString('pt-BR')} km` : '—'}
        />
        <InfoStat
          label={t('pages.vehicleDetail.gastoHistorico')}
          value={formatBRL(costs.reduce((sum, c) => sum + Number(c.valor ?? 0), 0))}
        />
      </div>

      <Tabs defaultValue="manutencao">
        <TabsList>
          <TabsTrigger value="manutencao">{t('pages.vehicleDetail.tabs.manutencao')}</TabsTrigger>
          <TabsTrigger value="os">{t('pages.vehicleDetail.tabs.os')}</TabsTrigger>
          <TabsTrigger value="fipe">{t('pages.vehicleDetail.tabs.fipe')}</TabsTrigger>
          <TabsTrigger value="sinistros">{t('pages.vehicleDetail.tabs.sinistros')}</TabsTrigger>
        </TabsList>

        <TabsContent value="manutencao">
          {manutencao.length === 0 ? (
            <p className="py-6 text-center text-xs text-muted-foreground">{t('pages.vehicleDetail.nenhumLancamentoManutencao')}</p>
          ) : (
            <ul className="space-y-2">
              {manutencao.map((c) => (
                <li key={c.id} className="rounded-md border border-border p-3 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-foreground">{c.descricao}</span>
                    <span className="font-data font-semibold text-foreground">{formatBRL(Number(c.valor))}</span>
                  </div>
                  <span className="text-[11px] text-muted-foreground">{formatDateBR(c.data!)}</span>
                </li>
              ))}
            </ul>
          )}
        </TabsContent>

        <TabsContent value="os">
          {os.length === 0 ? (
            <p className="py-6 text-center text-xs text-muted-foreground">{t('pages.vehicleDetail.nenhumaOS')}</p>
          ) : (
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-border text-muted-foreground">
                  <th className="py-1.5 text-left font-medium">{t('pages.vehicleDetail.osTabela.os')}</th>
                  <th className="py-1.5 text-left font-medium">{t('pages.vehicleDetail.osTabela.tipo')}</th>
                  <th className="py-1.5 text-left font-medium">{t('pages.vehicleDetail.osTabela.abertura')}</th>
                  <th className="py-1.5 text-left font-medium">{t('pages.vehicleDetail.osTabela.status')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {os.map((o) => (
                  <tr key={o.id}>
                    <td className="py-1.5 font-data text-foreground">{o.numero}</td>
                    <td className="py-1.5 text-muted-foreground">{o.tipo ? t(`status.tipoOS.${o.tipo}`) : '—'}</td>
                    <td className="py-1.5 font-data text-muted-foreground">
                      {o.dataAbertura ? formatDateBR(o.dataAbertura) : '—'}
                    </td>
                    <td className="py-1.5">
                      <StatusBadgeOS status={o.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TabsContent>

        <TabsContent value="fipe">
          <div className="space-y-4">
            {fipe ? (
              <div className="flex items-center justify-between rounded-md border border-border p-3">
                <div className="flex items-center gap-2.5">
                  <Gauge className="size-4 text-primary" />
                  <div>
                    <p className="font-data text-sm font-semibold text-foreground">
                      {formatBRL(Number(fipe.valorFipe ?? 0))}
                    </p>
                    <p className="text-[11px] text-muted-foreground">
                      {t('pages.vehicleDetail.referencia', {
                        data: fipe.dataReferencia ? formatDateBR(fipe.dataReferencia) : '—',
                      })}
                      {fipe.codigoFipe ? t('pages.vehicleDetail.codigoAbrev', { codigo: fipe.codigoFipe }) : ''}
                    </p>
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">{t('pages.vehicleDetail.nenhumFipe')}</p>
            )}

            <form onSubmit={handleAddFipe} className="grid grid-cols-3 gap-2 border-t border-border pt-4">
              <Input
                type="number"
                min={0}
                step="0.01"
                placeholder={t('pages.vehicleDetail.valorReais')}
                value={fipeForm.valorFipe}
                onChange={(e) => setFipeForm({ ...fipeForm, valorFipe: e.target.value })}
                required
              />
              <Input
                type="date"
                value={fipeForm.dataReferencia}
                onChange={(e) => setFipeForm({ ...fipeForm, dataReferencia: e.target.value })}
                required
              />
              <Input
                placeholder={t('pages.vehicleDetail.codigoFipeOpcional')}
                value={fipeForm.codigoFipe}
                onChange={(e) => setFipeForm({ ...fipeForm, codigoFipe: e.target.value })}
              />
              <Button type="submit" size="sm" className="col-span-3" disabled={fipeSaving}>
                {fipeSaving ? t('pages.vehicleDetail.salvando') : t('pages.vehicleDetail.lancarValor')}
              </Button>
            </form>
          </div>
        </TabsContent>

        <TabsContent value="sinistros">
          <div className="space-y-4">
            <div className="flex items-center gap-3 rounded-md border border-border p-3">
              <ShieldAlert className="size-4 text-primary" />
              <div>
                <p className="font-data text-sm font-semibold text-foreground">
                  {t('pages.vehicleDetail.condicao', { score: score ? Number(score.score ?? 0).toFixed(0) : '100' })}
                </p>
                <p className="text-[11px] text-muted-foreground">
                  {score?.algorithmVersion ?? t('pages.vehicleDetail.semSinistroRegistrado')}
                </p>
              </div>
            </div>

            {incidents.length === 0 ? (
              <p className="text-xs text-muted-foreground">{t('pages.vehicleDetail.nenhumSinistro')}</p>
            ) : (
              <ul className="space-y-2">
                {incidents.map((i) => (
                  <li key={i.id} className="rounded-md border border-border p-3 text-xs">
                    <div className="flex items-center justify-between">
                      <span className="font-data text-muted-foreground">{i.data ? formatDateBR(i.data) : '—'}</span>
                      <StatusBadgeSeveridade severidade={i.severidade} />
                    </div>
                    {i.descricao && <p className="mt-1 text-foreground">{i.descricao}</p>}
                    {i.custoReparo != null && (
                      <p className="mt-1 font-data text-muted-foreground">
                        {t('pages.vehicleDetail.reparo', { valor: formatBRL(Number(i.custoReparo)) })}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            )}

            <form onSubmit={handleAddIncident} className="space-y-2 border-t border-border pt-4">
              <div className="grid grid-cols-2 gap-2">
                <Input
                  type="date"
                  value={incidentForm.data}
                  onChange={(e) => setIncidentForm({ ...incidentForm, data: e.target.value })}
                  required
                />
                <Select
                  value={incidentForm.severidade}
                  onChange={(e) =>
                    setIncidentForm({ ...incidentForm, severidade: e.target.value as VehicleIncidentRequest['severidade'] })
                  }
                >
                  <option value="LEVE">{t('status.severidade.LEVE')}</option>
                  <option value="MODERADA">{t('status.severidade.MODERADA')}</option>
                  <option value="GRAVE">{t('status.severidade.GRAVE')}</option>
                </Select>
              </div>
              <Input
                placeholder={t('pages.vehicleDetail.descricaoOpcional')}
                value={incidentForm.descricao ?? ''}
                onChange={(e) => setIncidentForm({ ...incidentForm, descricao: e.target.value || undefined })}
              />
              <Input
                type="number"
                min={0}
                step="0.01"
                placeholder={t('pages.vehicleDetail.custoReparoOpcional')}
                value={incidentForm.custoReparo ?? ''}
                onChange={(e) =>
                  setIncidentForm({ ...incidentForm, custoReparo: e.target.value ? Number(e.target.value) : undefined })
                }
              />
              <Button type="submit" size="sm" className="w-full" disabled={incidentSaving}>
                {incidentSaving ? t('pages.vehicleDetail.registrando') : t('pages.vehicleDetail.registrarSinistro')}
              </Button>
            </form>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function Breadcrumb({ onBack, placa, modelo }: { onBack: () => void; placa?: string; modelo?: string }) {
  const { t } = useTranslation();
  return (
    <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
      <button type="button" onClick={onBack} className="flex items-center gap-1 hover:text-foreground">
        <ChevronLeft className="size-3.5" />
        {t('pages.vehicleDetail.frota')}
      </button>
      {placa && (
        <>
          <span>/</span>
          <span className="text-foreground">
            {placa} {modelo ? `· ${modelo}` : ''}
          </span>
        </>
      )}
    </div>
  );
}

function InfoStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-border p-2.5">
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="mt-0.5 font-data text-sm font-semibold text-foreground">{value}</p>
    </div>
  );
}
