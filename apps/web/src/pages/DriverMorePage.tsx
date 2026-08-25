import { useEffect, useState, type FormEvent } from 'react';
import { AlertTriangle, Car, ClipboardList, IdCard, MapPinned } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type DriverAssignmentResponse,
  type DriverProfileResponse,
  type TripResponse,
  type WorkOrderResponse,
} from '../api/client';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { StatusBadgeOS, StatusBadgeSeveridade } from '../components/shared/StatusBadge';
import { formatDateBR, formatDateTimeBR } from '../lib/format';
import { toast } from '../lib/toast';

/**
 * Menu secundário do motorista (spec 07) — veículo, CNH, OS e histórico de viagens em
 * versão resumida, read-only, movidos pra cá pra manter a home "Hoje" enxuta. Reaproveita
 * endpoints que já existem no backend pro mobile (`/v1/me/*`), só nunca tinham sido
 * consumidos no web.
 */
export function DriverMorePage() {
  const { t } = useTranslation();
  const [vehicle, setVehicle] = useState<DriverAssignmentResponse | null>(null);
  const [profile, setProfile] = useState<DriverProfileResponse | null>(null);
  const [workOrders, setWorkOrders] = useState<WorkOrderResponse[]>([]);
  const [trips, setTrips] = useState<TripResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const [reportOpen, setReportOpen] = useState(false);
  const [severidade, setSeveridade] = useState<'LEVE' | 'MODERADA' | 'GRAVE'>('LEVE');
  const [descricao, setDescricao] = useState('');
  const [sending, setSending] = useState(false);
  const [reportError, setReportError] = useState('');

  function refresh() {
    Promise.all([coreApi.me.vehicle(), coreApi.me.profile(), coreApi.me.vehicleWorkOrders(), coreApi.me.trips()])
      .then(([v, p, wo, t]) => {
        setVehicle(v);
        setProfile(p);
        setWorkOrders(wo);
        setTrips(t);
      })
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  async function enviarOcorrencia(e: FormEvent) {
    e.preventDefault();
    setSending(true);
    setReportError('');
    try {
      await coreApi.me.reportIncident({ data: new Date().toISOString().slice(0, 10), severidade, descricao });
      setReportOpen(false);
      toast.success(t('pages.driverMore.toasts.ocorrenciaReportada'));
      setSeveridade('LEVE');
      setDescricao('');
    } catch (err) {
      setReportError(err instanceof Error ? err.message : t('pages.driverMore.toasts.falhaReportar'));
    } finally {
      setSending(false);
    }
  }

  if (loading) return <p className="p-5 text-xs text-muted-foreground">{t('common.carregando')}</p>;

  return (
    <div className="p-5">
      <h2 className="mb-5 font-display text-lg font-semibold text-foreground">{t('pages.driverMore.mais')}</h2>

      <div className="space-y-3">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-1.5">
              <Car className="size-3.5" /> {t('pages.driverMore.seuVeiculo')}
            </CardTitle>
            <Button size="sm" variant="secondary" onClick={() => setReportOpen(true)}>
              <AlertTriangle className="size-3.5" /> {t('pages.driverMore.reportarOcorrencia')}
            </Button>
          </CardHeader>
          <div className="px-5 pb-4 text-sm text-foreground">
            {vehicle ? (
              <>
                <p className="font-medium">{vehicle.plate}</p>
                <p className="text-xs text-muted-foreground">
                  {vehicle.brand} {vehicle.model}
                </p>
              </>
            ) : (
              <p className="text-xs text-muted-foreground">{t('pages.driverMore.nenhumVeiculoDesignado')}</p>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <IdCard className="size-3.5" /> {t('pages.driverMore.cnh')}
            </CardTitle>
          </CardHeader>
          <div className="px-5 pb-4 text-xs text-muted-foreground">
            {profile?.cnhValidade ? (
              <p>{t('pages.driverMore.validaAte', { data: formatDateBR(profile.cnhValidade) })}</p>
            ) : (
              <p>{t('pages.driverMore.semValidadeCadastrada')}</p>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <ClipboardList className="size-3.5" /> {t('pages.driverMore.ordensDeServicoDoVeiculo')}
            </CardTitle>
          </CardHeader>
          <div className="px-5 pb-4">
            {workOrders.length === 0 ? (
              <p className="text-xs text-muted-foreground">{t('pages.driverMore.nenhumaOS')}</p>
            ) : (
              <ul className="space-y-2">
                {workOrders.map((w) => (
                  <li key={w.id} className="flex items-center justify-between text-xs">
                    <span className="text-foreground">{w.numero}</span>
                    <StatusBadgeOS status={w.status} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <MapPinned className="size-3.5" /> {t('pages.driverMore.historicoDeViagens')}
            </CardTitle>
          </CardHeader>
          <div className="px-5 pb-4">
            {trips.length === 0 ? (
              <p className="text-xs text-muted-foreground">{t('pages.driverMore.nenhumaViagem')}</p>
            ) : (
              <ul className="space-y-1.5 text-xs text-muted-foreground">
                {trips.slice(0, 10).map((t) => (
                  <li key={t.id}>
                    {t.startedAt ? formatDateTimeBR(t.startedAt) : '—'} · {t.status}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      </div>

      <Modal open={reportOpen} onClose={() => setReportOpen(false)} title={t('pages.driverMore.reportarOcorrencia')}>
        <form onSubmit={enviarOcorrencia} className="space-y-3">
          <Select value={severidade} onChange={(e) => setSeveridade(e.target.value as typeof severidade)}>
            <option value="LEVE">{t('status.severidade.LEVE')}</option>
            <option value="MODERADA">{t('status.severidade.MODERADA')}</option>
            <option value="GRAVE">{t('status.severidade.GRAVE')}</option>
          </Select>
          <StatusBadgeSeveridade severidade={severidade} />
          <textarea
            placeholder={t('pages.driverMore.oQueAconteceu')}
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
            maxLength={500}
            rows={3}
            className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring sm:text-sm"
          />
          {reportError && <p className="text-xs text-status-danger">{reportError}</p>}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setReportOpen(false)}>
              {t('pages.driverMore.cancelar')}
            </Button>
            <Button type="submit" size="sm" disabled={sending}>
              {sending ? t('pages.driverMore.enviando') : t('pages.driverMore.enviar')}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
