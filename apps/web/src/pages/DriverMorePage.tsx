import { useEffect, useState, type FormEvent } from 'react';
import { AlertTriangle, Car, ClipboardList, IdCard, MapPinned } from 'lucide-react';
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

/**
 * Menu secundário do motorista (spec 07) — veículo, CNH, OS e histórico de viagens em
 * versão resumida, read-only, movidos pra cá pra manter a home "Hoje" enxuta. Reaproveita
 * endpoints que já existem no backend pro mobile (`/v1/me/*`), só nunca tinham sido
 * consumidos no web.
 */
export function DriverMorePage() {
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
  const [reportSent, setReportSent] = useState(false);

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
      setReportSent(true);
      setSeveridade('LEVE');
      setDescricao('');
    } catch (err) {
      setReportError(err instanceof Error ? err.message : 'Falha ao reportar ocorrência');
    } finally {
      setSending(false);
    }
  }

  if (loading) return <p className="p-5 text-xs text-muted-foreground">Carregando...</p>;

  return (
    <div className="p-5">
      <h2 className="mb-5 font-display text-lg font-semibold text-foreground">Mais</h2>

      {reportSent && (
        <div className="mb-4 rounded-md border border-status-success-bg bg-status-success-bg px-3 py-2 text-xs text-status-success">
          Ocorrência reportada — o gestor vai revisar.
        </div>
      )}

      <div className="space-y-3">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-1.5">
              <Car className="size-3.5" /> Seu veículo
            </CardTitle>
            <Button size="sm" variant="secondary" onClick={() => setReportOpen(true)}>
              <AlertTriangle className="size-3.5" /> Reportar ocorrência
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
              <p className="text-xs text-muted-foreground">Nenhum veículo designado no momento.</p>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <IdCard className="size-3.5" /> CNH
            </CardTitle>
          </CardHeader>
          <div className="px-5 pb-4 text-xs text-muted-foreground">
            {profile?.cnhValidade ? <p>Válida até {formatDateBR(profile.cnhValidade)}</p> : <p>Sem validade cadastrada.</p>}
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-1.5">
              <ClipboardList className="size-3.5" /> Ordens de serviço do veículo
            </CardTitle>
          </CardHeader>
          <div className="px-5 pb-4">
            {workOrders.length === 0 ? (
              <p className="text-xs text-muted-foreground">Nenhuma OS registrada.</p>
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
              <MapPinned className="size-3.5" /> Histórico de viagens
            </CardTitle>
          </CardHeader>
          <div className="px-5 pb-4">
            {trips.length === 0 ? (
              <p className="text-xs text-muted-foreground">Nenhuma viagem registrada ainda.</p>
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

      <Modal open={reportOpen} onClose={() => setReportOpen(false)} title="Reportar ocorrência">
        <form onSubmit={enviarOcorrencia} className="space-y-3">
          <Select value={severidade} onChange={(e) => setSeveridade(e.target.value as typeof severidade)}>
            <option value="LEVE">Leve</option>
            <option value="MODERADA">Moderada</option>
            <option value="GRAVE">Grave</option>
          </Select>
          <StatusBadgeSeveridade severidade={severidade} />
          <textarea
            placeholder="O que aconteceu?"
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
            maxLength={500}
            rows={3}
            className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
          {reportError && <p className="text-xs text-status-danger">{reportError}</p>}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setReportOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" size="sm" disabled={sending}>
              {sending ? 'Enviando...' : 'Enviar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
