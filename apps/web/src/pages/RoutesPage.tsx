import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Clock, MapPin, Navigation, Route as RouteIcon, Search } from 'lucide-react';
import { coreApi, type PlaceResponse, type RouteResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { cn } from '../lib/utils';

/** Nominatim público pede volume baixo — nada de uma busca por tecla digitada. */
const DEBOUNCE_MS = 600;

const MANOBRA_LABEL: Record<string, string> = {
  depart: 'Siga',
  turn: 'Vire',
  'new name': 'Continue',
  continue: 'Continue',
  merge: 'Entre',
  'on ramp': 'Pegue o acesso',
  'off ramp': 'Pegue a saída',
  fork: 'Mantenha-se',
  'end of road': 'No fim da via, vire',
  roundabout: 'Na rotatória, siga',
  rotary: 'Na rotatória, siga',
  'exit roundabout': 'Saia da rotatória',
  'exit rotary': 'Saia da rotatória',
  notification: 'Atenção',
};

const MODIFICADOR_LABEL: Record<string, string> = {
  left: 'à esquerda',
  right: 'à direita',
  'slight left': 'levemente à esquerda',
  'slight right': 'levemente à direita',
  'sharp left': 'fechado à esquerda',
  'sharp right': 'fechado à direita',
};

/**
 * Monta a frase da manobra a partir do par (tipo, modificador) do OSRM.
 *
 * Não é só concatenar: "seguir reto" e "retornar" não combinam com qualquer verbo —
 * colar o modificador direto produzia "Vire em frente" e "Continue em em frente". Esses
 * dois casos têm frase própria, independente do tipo de manobra.
 */
function descreverManobra(tipo?: string, modificador?: string | null): string {
  if (tipo === 'arrive') return 'Chegada';
  if (modificador === 'uturn') return 'Faça o retorno';
  if (modificador === 'straight') return 'Siga em frente';

  const verbo = MANOBRA_LABEL[tipo ?? ''] ?? tipo ?? '';
  const mod = modificador ? MODIFICADOR_LABEL[modificador] : undefined;
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

interface BuscaEnderecoProps {
  id: string;
  label: string;
  selecionado: PlaceResponse | null;
  onSelecionar: (lugar: PlaceResponse | null) => void;
}

function BuscaEndereco({ id, label, selecionado, onSelecionar }: BuscaEnderecoProps) {
  const [termo, setTermo] = useState('');
  const [resultados, setResultados] = useState<PlaceResponse[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [aberto, setAberto] = useState(false);
  // Descarta resposta de busca antiga que chegou depois de uma mais nova.
  const buscaAtual = useRef(0);

  useEffect(() => {
    if (selecionado || termo.trim().length < 3) {
      setResultados([]);
      return;
    }
    const id = ++buscaAtual.current;
    setBuscando(true);
    const timer = setTimeout(() => {
      coreApi.places
        .search(termo)
        .then((res) => {
          if (id !== buscaAtual.current) return;
          setResultados(res);
          setAberto(true);
        })
        .finally(() => {
          if (id === buscaAtual.current) setBuscando(false);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [termo, selecionado]);

  return (
    <div className="relative">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
        <Input
          id={id}
          className="pl-8"
          placeholder="Buscar endereço na área do piloto..."
          value={selecionado ? selecionado.displayName : termo}
          onChange={(e) => {
            onSelecionar(null);
            setTermo(e.target.value);
          }}
          onFocus={() => resultados.length > 0 && setAberto(true)}
          autoComplete="off"
        />
      </div>

      {aberto && !selecionado && (resultados.length > 0 || (!buscando && termo.trim().length >= 3)) && (
        <ul className="absolute z-20 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-border bg-card shadow-lg">
          {resultados.map((lugar) => (
            <li key={`${lugar.lat}-${lugar.lon}-${lugar.displayName}`}>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left text-xs text-foreground hover:bg-muted"
                onClick={() => {
                  onSelecionar(lugar);
                  setAberto(false);
                }}
              >
                {lugar.displayName}
              </button>
            </li>
          ))}
          {resultados.length === 0 && (
            <li className="px-3 py-2 text-xs text-muted-foreground">
              Nenhum endereço encontrado dentro da área do piloto.
            </li>
          )}
        </ul>
      )}
    </div>
  );
}

/**
 * Roteamento ponto-a-ponto (spec 02, Fase 2). O motor é o OSRM sobre extrato do OSM da
 * área do piloto — nenhuma otimização própria, como o spec determina. Peso por
 * `road_readiness_score` é Fase 3 e entra no OSRM, não aqui.
 */
export function RoutesPage() {
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
      setErro(e instanceof Error ? e.message : 'Falha ao calcular a rota');
    } finally {
      setCalculando(false);
    }
  }

  const passos = rota?.steps ?? [];

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Rotas</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Rota sugerida entre dois pontos, calculada sobre o mapa da área do piloto.
        </p>
      </div>

      <Card className="mb-5">
        <div className="grid gap-4 p-4 md:grid-cols-2">
          <BuscaEndereco id="origem" label="Origem" selecionado={origem} onSelecionar={setOrigem} />
          <BuscaEndereco id="destino" label="Destino" selecionado={destino} onSelecionar={setDestino} />
        </div>
        <div className="flex items-center gap-3 border-t border-border px-4 py-3">
          <Button onClick={calcular} disabled={!origem || !destino || calculando}>
            <Navigation /> {calculando ? 'Calculando...' : 'Calcular rota'}
          </Button>
          {(!origem || !destino) && (
            <span className="text-xs text-muted-foreground">Escolha origem e destino para calcular.</span>
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
          <span>{rota.unavailableReason ?? 'Não foi possível calcular a rota.'}</span>
        </div>
      )}

      {rota?.available && (
        <div className="grid gap-4 lg:grid-cols-[1fr_1.2fr]">
          <Card>
            <CardHeader>
              <CardTitle>Resumo</CardTitle>
            </CardHeader>
            <div className="grid grid-cols-2 gap-3 px-4 pb-3">
              <div className="rounded-md border border-border p-2.5">
                <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-muted-foreground">
                  <RouteIcon className="size-3" /> Distância
                </p>
                <p className="mt-0.5 font-data text-sm font-semibold text-foreground">
                  {formatarDistancia(rota.distanceM ?? 0)}
                </p>
              </div>
              <div className="rounded-md border border-border p-2.5">
                <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-muted-foreground">
                  <Clock className="size-3" /> Tempo estimado
                </p>
                <p className="mt-0.5 font-data text-sm font-semibold text-foreground">
                  {formatarDuracao(rota.durationS ?? 0)}
                </p>
              </div>
            </div>
            <div className="px-4 pb-4">
              <TracadoDaRota geometry={rota.geometry ?? []} />
              <p className="mt-1 text-center text-[10px] text-muted-foreground">
                Traçado do percurso (sem mapa de fundo)
              </p>
            </div>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Itinerário</CardTitle>
            </CardHeader>
            <ol className="divide-y divide-border">
              {passos.map((passo, i) => (
                <li key={i} className="flex items-start gap-3 px-4 py-2.5">
                  <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-secondary text-[10px] font-semibold text-muted-foreground">
                    {i + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] text-foreground">
                      {descreverManobra(passo.instructionType, passo.modifier)}
                      {passo.name ? (
                        <span className="font-medium"> · {passo.name}</span>
                      ) : (
                        <span className="text-muted-foreground"> · via sem nome</span>
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
            <p>Escolha uma origem e um destino para ver a rota sugerida.</p>
          </div>
        </Card>
      )}
    </div>
  );
}
