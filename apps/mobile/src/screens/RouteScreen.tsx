import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import { coreApi, type PlaceResponse, type RouteResponse } from '../api/client';

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
 * Monta a frase da manobra a partir do par (tipo, modificador) do OSRM. "Seguir reto" e
 * "retornar" não combinam com qualquer verbo — concatenar direto produzia "Vire em
 * frente" e "Continue em em frente". (Mesma regra do web, ver RoutesPage.tsx.)
 */
function descreverManobra(tipo: string, modificador: string | null): string {
  if (tipo === 'arrive') return 'Chegada';
  if (modificador === 'uturn') return 'Faça o retorno';
  if (modificador === 'straight') return 'Siga em frente';

  const verbo = MANOBRA_LABEL[tipo] ?? tipo;
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
 * Rota até um destino digitado pelo motorista (spec 02, "exposto no web/mobile").
 *
 * A origem é a designação do dia? Não: enquanto não existir plano de rota com paradas
 * (`route_plan`/`route_stop`, extensão seguinte da spec 02), o caso real é o motorista
 * digitando o endereço da entrega. Origem e destino são ambos buscados — quando o plano
 * de rota existir, a origem passa a vir da parada anterior.
 */
export function RouteScreen() {
  const [origem, setOrigem] = useState<PlaceResponse | null>(null);
  const [destino, setDestino] = useState<PlaceResponse | null>(null);
  const [rota, setRota] = useState<RouteResponse | null>(null);
  const [calculando, setCalculando] = useState(false);

  async function calcular() {
    if (!origem || !destino) return;
    setCalculando(true);
    setRota(null);
    try {
      setRota(await coreApi.routes.preview(origem.lat, origem.lon, destino.lat, destino.lon));
    } catch {
      setRota({
        available: false,
        distanceM: null,
        durationS: null,
        geometry: [],
        steps: [],
        unavailableReason: 'Não foi possível falar com o servidor.',
      });
    } finally {
      setCalculando(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Rota</Text>

      <BuscaEndereco rotulo="Origem" selecionado={origem} onSelecionar={setOrigem} />
      <BuscaEndereco rotulo="Destino" selecionado={destino} onSelecionar={setDestino} />

      <TouchableOpacity
        style={[styles.botao, (!origem || !destino || calculando) && styles.botaoDesabilitado]}
        onPress={calcular}
        disabled={!origem || !destino || calculando}
      >
        <Text style={styles.botaoTexto}>{calculando ? 'Calculando...' : 'Calcular rota'}</Text>
      </TouchableOpacity>

      {calculando && <ActivityIndicator style={styles.espacoTopo} />}

      {rota && !rota.available && (
        <Text style={styles.aviso}>{rota.unavailableReason ?? 'Não foi possível calcular a rota.'}</Text>
      )}

      {rota?.available && (
        <>
          <View style={styles.resumo}>
            <Text style={styles.resumoTexto}>
              {formatarDistancia(rota.distanceM ?? 0)} · {formatarDuracao(rota.durationS ?? 0)}
            </Text>
          </View>
          <FlatList
            data={rota.steps}
            keyExtractor={(_, i) => String(i)}
            renderItem={({ item, index }) => (
              <View style={styles.passo}>
                <Text style={styles.passoNumero}>{index + 1}</Text>
                <Text style={styles.passoTexto}>
                  {descreverManobra(item.instructionType, item.modifier)}
                  {item.name ? ` · ${item.name}` : ' · via sem nome'}
                </Text>
                <Text style={styles.passoDistancia}>{formatarDistancia(item.distanceM)}</Text>
              </View>
            )}
          />
        </>
      )}
    </View>
  );
}

interface BuscaEnderecoProps {
  rotulo: string;
  selecionado: PlaceResponse | null;
  onSelecionar: (lugar: PlaceResponse | null) => void;
}

function BuscaEndereco({ rotulo, selecionado, onSelecionar }: BuscaEnderecoProps) {
  const [termo, setTermo] = useState('');
  const [resultados, setResultados] = useState<PlaceResponse[]>([]);
  // Descarta resposta antiga que chegou depois de uma mais nova.
  const buscaAtual = useRef(0);

  useEffect(() => {
    if (selecionado || termo.trim().length < 3) {
      setResultados([]);
      return;
    }
    const id = ++buscaAtual.current;
    const timer = setTimeout(() => {
      coreApi.places
        .search(termo)
        .then((res) => {
          if (id === buscaAtual.current) setResultados(res);
        })
        .catch(() => {
          if (id === buscaAtual.current) setResultados([]);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [termo, selecionado]);

  return (
    <View style={styles.campo}>
      <Text style={styles.rotulo}>{rotulo}</Text>
      <TextInput
        style={styles.input}
        placeholder="Buscar endereço..."
        value={selecionado ? selecionado.displayName : termo}
        onChangeText={(t) => {
          onSelecionar(null);
          setTermo(t);
        }}
        autoCorrect={false}
      />
      {!selecionado &&
        resultados.map((lugar) => (
          <TouchableOpacity
            key={`${lugar.lat}-${lugar.lon}-${lugar.displayName}`}
            style={styles.sugestao}
            onPress={() => {
              onSelecionar(lugar);
              setResultados([]);
            }}
          >
            <Text style={styles.sugestaoTexto} numberOfLines={2}>
              {lugar.displayName}
            </Text>
          </TouchableOpacity>
        ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 12 },
  campo: { marginBottom: 12 },
  rotulo: { fontSize: 12, color: '#666', marginBottom: 4 },
  input: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10, fontSize: 14 },
  sugestao: { borderWidth: 1, borderTopWidth: 0, borderColor: '#eee', padding: 10 },
  sugestaoTexto: { fontSize: 12, color: '#333' },
  botao: { backgroundColor: '#1f3a5f', borderRadius: 8, padding: 12, alignItems: 'center' },
  botaoDesabilitado: { backgroundColor: '#aab' },
  botaoTexto: { color: '#fff', fontSize: 14, fontWeight: '600' },
  espacoTopo: { marginTop: 16 },
  aviso: {
    marginTop: 16,
    fontSize: 13,
    color: '#9a6700',
    backgroundColor: '#fff8e6',
    padding: 10,
    borderRadius: 8,
  },
  resumo: { marginTop: 16, marginBottom: 8 },
  resumoTexto: { fontSize: 16, fontWeight: '700' },
  passo: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 8 },
  passoNumero: { fontSize: 11, color: '#888', width: 18 },
  passoTexto: { flex: 1, fontSize: 13 },
  passoDistancia: { fontSize: 11, color: '#666' },
});
