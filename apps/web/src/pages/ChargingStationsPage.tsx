import { useEffect, useState } from 'react';
import { AlertTriangle, MapPin, Plug, Zap } from 'lucide-react';
import { coreApi, type ChargingStationItem } from '../api/client';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { cn } from '../lib/utils';

const STATUS_LABEL: Record<string, string> = {
  DISPONIVEL: 'Disponível',
  OCUPADO: 'Ocupado',
  FORA_DE_SERVICO: 'Fora de serviço',
  DESCONHECIDO: 'Status desconhecido',
};

const STATUS_CLASS: Record<string, string> = {
  DISPONIVEL: 'bg-status-success-bg text-status-success',
  OCUPADO: 'bg-status-warning-bg text-status-warning',
  FORA_DE_SERVICO: 'bg-status-danger-bg text-status-danger',
  DESCONHECIDO: 'bg-status-neutral-bg text-status-neutral',
};

/**
 * Recarga elétrica (spec 06, item 1). Lista simples — sem mapa (nenhuma lib de mapa no
 * projeto ainda). Falha do provedor externo (RNF011) nunca vira erro de tela: sem chave
 * configurada ou provedor fora do ar, a lista fica vazia com o aviso explicando por quê,
 * em vez de spinner infinito ou mensagem de erro genérica.
 */
export function ChargingStationsPage() {
  const [stations, setStations] = useState<ChargingStationItem[]>([]);
  const [providerAvailable, setProviderAvailable] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    coreApi.chargingStations
      .list()
      .then((res) => {
        setStations(res.stations ?? []);
        setProviderAvailable(res.providerAvailable ?? false);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar pontos de recarga'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Pontos de Recarga</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Estações de recarga elétrica agregadas de provedores externos — útil para planejar rota da frota elétrica.
        </p>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {!loading && !providerAvailable && (
        <div className="mb-4 flex items-start gap-2.5 rounded-md border border-status-warning-bg bg-status-warning-bg px-3 py-2.5 text-xs text-status-warning">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" />
          <span>
            Provedor de dado de recarga não configurado ainda — nenhuma estação sincronizada no momento. Isso não é um
            erro, é um recurso ainda não ligado.
          </span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Estações conhecidas</CardTitle>
        </CardHeader>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
        ) : stations.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <Plug className="size-6 text-muted-foreground/60" />
            <p>Nenhuma estação de recarga sincronizada ainda.</p>
          </div>
        ) : (
          <ul className="divide-y divide-border">
            {stations.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-3 px-5 py-3">
                <div className="flex items-start gap-3">
                  <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md bg-secondary">
                    <Zap className="size-4 text-muted-foreground" />
                  </div>
                  <div>
                    <p className="text-[13px] font-medium text-foreground">{s.name ?? 'Estação sem nome'}</p>
                    {s.address && (
                      <p className="mt-0.5 flex items-center gap-1 text-[11px] text-muted-foreground">
                        <MapPin className="size-3" />
                        {s.address}
                      </p>
                    )}
                    {(s.connectorType || s.powerKw != null) && (
                      <p className="mt-0.5 text-[11px] text-muted-foreground">
                        {s.connectorType}
                        {s.connectorType && s.powerKw != null ? ' · ' : ''}
                        {s.powerKw != null ? `${s.powerKw} kW` : ''}
                      </p>
                    )}
                  </div>
                </div>
                <span
                  className={cn(
                    'shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium',
                    STATUS_CLASS[s.status ?? ''] ?? STATUS_CLASS.DESCONHECIDO,
                  )}
                >
                  {STATUS_LABEL[s.status ?? ''] ?? s.status}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
