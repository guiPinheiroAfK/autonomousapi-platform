import { useMemo, useState } from 'react';
import { AlertTriangle, Clock, MapPin, Navigation, Route as RouteIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { coreApi, type PlaceResponse, type RouteResponse } from '../api/client';
import { BuscaEndereco } from '../components/shared/BuscaEndereco';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { cn } from '../lib/utils';

/** Chave OSRM crua (com espaço, ex.: "on ramp") → chave i18n (camelCase). */
const MANOBRA_KEY: Record<string, string> = {
  depart: 'depart',
  turn: 'turn',
  'new name': 'newName',
  continue: 'continue',
  merge: 'merge',
  'on ramp': 'onRamp',
  'off ramp': 'offRamp',
  fork: 'fork',
  'end of road': 'endOfRoad',
  roundabout: 'roundabout',
  rotary: 'roundabout',
  'exit roundabout': 'exitRoundabout',
  'exit rotary': 'exitRoundabout',
  notification: 'notification',
};

const MODIFICADOR_KEY: Record<string, string> = {
  left: 'left',
  right: 'right',
  'slight left': 'slightLeft',
  'slight right': 'slightRight',
  'sharp left': 'sharpLeft',
  'sharp right': 'sharpRight',
};

/**
 * Monta a frase da manobra a partir do par (tipo, modificador) do OSRM.
 *
 * Não é só concatenar: "seguir reto" e "retornar" não combinam com qualquer verbo —
 * colar o modificador direto produzia "Vire em frente" e "Continue em em frente". Esses
 * dois casos têm frase própria, independente do tipo de manobra.
 */
function descreverManobra(t: TFunction, tipo?: string, modificador?: string | null): string {
  if (tipo === 'arrive') return t('pages.routes.manobra.chegada');
  if (modificador === 'uturn') return t('pages.routes.manobra.facaRetorno');
  if (modificador === 'straight') return t('pages.routes.manobra.sigaEmFrente');

  const chaveVerbo = MANOBRA_KEY[tipo ?? ''];
  const verbo = chaveVerbo ? t(`pages.routes.manobra.${chaveVerbo}`) : (tipo ?? '');
  const chaveMod = modificador ? MODIFICADOR_KEY[modificador] : undefined;
  const mod = chaveMod ? t(`pages.routes.modificador.${chaveMod}`) : undefined;
  return mod ? `${verbo} ${mod}` : verbo;
}

function formatarDistancia(metros: number): string {
  return metros >= 1000 ? `${(metros / 1000).toFixed(1)} km` : `${Math.round(metros)} m`;
}

function formatarDuracao(segundos: number): string {
  const min = Math.round(segundos / 60);
  if (min < 60) return `${min} min`;
  return `${Math.floor(min / 60)} h ${min % 60} min`;
}

/**
 * Desenha a geometria da rota em SVG puro. Não é um mapa — não há basemap nem lib de
 * tiles no projeto — mas mostra a forma real do trajeto, que já diferencia "foi reto"
 * de "deu a volta no quarteirão" sem adicionar dependência nenhuma.
 */
function TracadoDaRota({ geometry }: { geometry: number[][] }) {
  const path = useMemo(() => {
    if (geometry.length < 2) return null;

    const lons = geometry.map((c) => c[0]);
    const lats = geometry.map((c) => c[1]);
    const minLon = Math.min(...lons);
    const maxLon = Math.max(...lons);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);

    // Grau de longitude "encolhe" com a latitude — sem corrigir, a rota sai esticada na
    // horizontal. cos(lat) é a aproximação padrão e basta nesta escala (poucos km).
    const fatorLon = Math.cos(((minLat + maxLat) / 2) * (Math.PI / 180));
    const larguraGraus = Math.max((maxLon - minLon) * fatorLon, 1e-6);
    const alturaGraus = Math.max(maxLat - minLat, 1e-6);
    const escala = Math.min(100 / larguraGraus, 100 / alturaGraus);

    const larguraUtil = larguraGraus * escala;
    const alturaUtil = alturaGraus * escala;
    const offsetX = (100 - larguraUtil) / 2;
    const offsetY = (100 - alturaUtil) / 2;

    return geometry
      .map(([lon, lat], i) => {
        const x = offsetX + (lon - minLon) * fatorLon * escala;
        // SVG cresce para baixo, latitude cresce para cima — daí a inversão.
        const y = offsetY + (maxLat - lat) * escala;
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
  }, [geometry]);

  if (!path) return null;

  const [primeiro] = geometry;
  const ultimo = geometry[geometry.length - 1];

  return (
    <svg viewBox="-6 -6 112 112" className="h-56 w-full" role="img" aria-label="Traçado da rota">
      <path
        d={path}
        fill="none"
        stroke="currentColor"
        strokeWidth={2.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="text-primary"
        vectorEffect="non-scaling-stroke"
      />
      {[primeiro, ultimo].map((_, i) => {
        const partes = path.split(' ');
        const ponto = i === 0 ? partes[0] : partes[partes.length - 1];
        const [x, y] = ponto.slice(1).split(',').map(Number);
        return (
          <circle
            key={i}
            cx={x}
            cy={y}
            r={3}
            className={i === 0 ? 'fill-status-success' : 'fill-status-danger'}
          />
        );
      })}
    </svg>
  );
}

/**
 * Roteamento ponto-a-ponto (spec 02, Fase 2). O motor é o OSRM sobre extrato do OSM da
 * área do piloto — nenhuma otimização própria, como o spec determina. Peso por
 * `road_readiness_score` é Fase 3 e entra no OSRM, não aqui.
 */
export function RoutesPage() {
  const { t } = useTranslation();
  const [origem, setOrigem] = useState<PlaceResponse | null>(null);
  const [destino, setDestino] = useState<PlaceResponse | null>(null);
  const [rota, setRota] = useState<RouteResponse | null>(null);
  const [calculando, setCalculando] = useState(false);
  const [erro, setErro] = useState('');

  async function calcular() {
    if (!origem || !destino) return;
    setCalculando(true);
    setErro('');
    setRota(null);
    try {
      setRota(await coreApi.routes.preview(origem.lat!, origem.lon!, destino.lat!, destino.lon!));
    } catch (e) {
      setErro(e instanceof Error ? e.message : t('pages.routes.falhaCalcular'));
    } finally {
      setCalculando(false);
    }
  }

  const passos = rota?.steps ?? [];

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.routes.titulo')}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.routes.subtitulo')}</p>
      </div>

      <Card className="mb-5">
        <div className="grid gap-4 p-4 md:grid-cols-2">
          <BuscaEndereco id="origem" label={t('pages.routes.origem')} selecionado={origem} onSelecionar={setOrigem} />
          <BuscaEndereco id="destino" label={t('pages.routes.destino')} selecionado={destino} onSelecionar={setDestino} />
        </div>
        <div className="flex items-center gap-3 border-t border-border px-4 py-3">
          <Button onClick={calcular} disabled={!origem || !destino || calculando}>
            <Navigation /> {calculando ? t('pages.routes.calculando') : t('pages.routes.calcularRota')}
          </Button>
          {(!origem || !destino) && (
            <span className="text-xs text-muted-foreground">{t('pages.routes.escolhaParaCalcular')}</span>
          )}
        </div>
      </Card>

      {erro && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {erro}
        </div>
      )}

      {rota && !rota.available && (
        <div className="mb-4 flex items-start gap-2.5 rounded-md border border-status-warning-bg bg-status-warning-bg px-3 py-2.5 text-xs text-status-warning">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" />
          <span>{rota.unavailableReason ?? t('pages.routes.naoFoiPossivelCalcular')}</span>
        </div>
      )}

      {rota?.available && (
        <div className="grid gap-4 lg:grid-cols-[1fr_1.2fr]">
          <Card>
            <CardHeader>
              <CardTitle>{t('pages.routes.resumo')}</CardTitle>
            </CardHeader>
            <div className="grid grid-cols-2 gap-3 px-4 pb-3">
              <div className="rounded-md border border-border p-2.5">
                <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-muted-foreground">
                  <RouteIcon className="size-3" /> {t('pages.routes.distancia')}
                </p>
                <p className="mt-0.5 font-data text-sm font-semibold text-foreground">
                  {formatarDistancia(rota.distanceM ?? 0)}
                </p>
              </div>
              <div className="rounded-md border border-border p-2.5">
                <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-muted-foreground">
                  <Clock className="size-3" /> {t('pages.routes.tempoEstimado')}
                </p>
                <p className="mt-0.5 font-data text-sm font-semibold text-foreground">
                  {formatarDuracao(rota.durationS ?? 0)}
                </p>
              </div>
            </div>
            <div className="px-4 pb-4">
              <TracadoDaRota geometry={rota.geometry ?? []} />
              <p className="mt-1 text-center text-[10px] text-muted-foreground">{t('pages.routes.tracadoSemMapa')}</p>
            </div>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('pages.routes.itinerario')}</CardTitle>
            </CardHeader>
            <ol className="divide-y divide-border">
              {passos.map((passo, i) => (
                <li key={i} className="flex items-start gap-3 px-4 py-2.5">
                  <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-secondary text-[10px] font-semibold text-muted-foreground">
                    {i + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] text-foreground">
                      {descreverManobra(t, passo.instructionType, passo.modifier)}
                      {passo.name ? (
                        <span className="font-medium"> · {passo.name}</span>
                      ) : (
                        <span className="text-muted-foreground"> · {t('pages.routes.viaSemNome')}</span>
                      )}
                    </p>
                  </div>
                  <span className={cn('shrink-0 font-data text-[11px] text-muted-foreground')}>
                    {formatarDistancia(passo.distanceM ?? 0)}
                  </span>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      )}

      {!rota && !calculando && (
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <MapPin className="size-6 text-muted-foreground/60" />
            <p>{t('pages.routes.escolhaParaVerRota')}</p>
          </div>
        </Card>
      )}
    </div>
  );
}
