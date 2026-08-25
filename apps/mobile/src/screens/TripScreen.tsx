import { useEffect, useRef, useState } from 'react';
import * as Location from 'expo-location';
import {
  ActivityIndicator,
  Alert,
  Button,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

import { coreApi, type TripResponse, type VehicleResponse } from '../api/client';
import { enqueue, flushBatch, pendingCount, type GpsPing } from '../offline/pingQueue';

interface Props {
  onLogout: () => void;
}

export function TripScreen({ onLogout }: Props) {
  const [loading, setLoading] = useState(true);
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(null);
  const [trip, setTrip] = useState<TripResponse | null>(null);
  const [pending, setPending] = useState(0);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);

  const watchSubscription = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [vehiclePage, tripPage] = await Promise.all([coreApi.vehicles.list(), coreApi.trips.list()]);
        setVehicles(vehiclePage.content);
        const emAndamento = tripPage.content.find((t) => t.status === 'EM_ANDAMENTO') ?? null;
        setTrip(emAndamento);
        if (emAndamento) {
          setSelectedVehicleId(emAndamento.vehicleId);
          startWatchingLocation();
        }
        setPending(await pendingCount());
      } catch (e) {
        Alert.alert('Erro ao carregar', e instanceof Error ? e.message : 'Erro desconhecido');
      } finally {
        setLoading(false);
      }
    })();

    return () => {
      watchSubscription.current?.remove();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function startWatchingLocation() {
    const { status: fgStatus } = await Location.requestForegroundPermissionsAsync();
    if (fgStatus !== 'granted') {
      Alert.alert('Permissão negada', 'Sem acesso à localização não é possível registrar a viagem.');
      return;
    }
    // Pede background também (spec 03) — sem ela, o trajeto só é gravado com o app aberto.
    await Location.requestBackgroundPermissionsAsync();

    watchSubscription.current = await Location.watchPositionAsync(
      { accuracy: Location.Accuracy.Balanced, timeInterval: 15_000, distanceInterval: 50 },
      async (position) => {
        const ping: GpsPing = {
          recordedAt: new Date(position.timestamp).toISOString(),
          lat: position.coords.latitude,
          lon: position.coords.longitude,
          speed: position.coords.speed ?? undefined,
          heading: position.coords.heading ?? undefined,
          accuracy: position.coords.accuracy ?? undefined,
        };
        await enqueue(ping);
        setPending(await pendingCount());
      },
    );
  }

  async function handleStart() {
    if (!selectedVehicleId) {
      Alert.alert('Selecione um veículo', 'Escolha o veículo antes de iniciar a viagem.');
      return;
    }
    setBusy(true);
    try {
      const started = await coreApi.trips.start(selectedVehicleId);
      setTrip(started);
      setStatus('viagem iniciada');
      await startWatchingLocation();
    } catch (e) {
      Alert.alert('Falha ao iniciar viagem', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setBusy(false);
    }
  }

  async function handleStop() {
    if (!trip) return;
    setBusy(true);
    try {
      watchSubscription.current?.remove();
      watchSubscription.current = null;
      const finished = await coreApi.trips.stop(trip.id);
      setTrip(finished.status === 'FINALIZADA' ? null : finished);
      setStatus('viagem finalizada');
    } catch (e) {
      Alert.alert('Falha ao finalizar viagem', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setBusy(false);
    }
  }

  async function handleSync() {
    if (!trip) return;
    setStatus('sincronizando...');
    try {
      // Em lote: uma requisição por lote em vez de uma por ping. Um motorista voltando
      // de uma hora sem sinal tinha ~240 pings na fila = 240 chamadas sequenciais antes.
      const enviados = await flushBatch(async (pings) => {
        const { accepted } = await coreApi.trips.submitPingBatch(trip.id, pings);
        return accepted;
      });
      const restantes = await pendingCount();
      setPending(restantes);
      setStatus(
        restantes === 0
          ? `sincronizado (${enviados} pings)`
          : `${enviados} enviados, ${restantes} na fila para nova tentativa`,
      );
    } catch (e) {
      setPending(await pendingCount());
      setStatus(`falha ao sincronizar (${e instanceof Error ? e.message : 'erro'}) — pings mantidos na fila`);
    }
  }

  if (loading) {
    return (
      <View style={styles.container}>
        <ActivityIndicator />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Viagem</Text>

      {trip ? (
        <>
          <Text style={styles.info}>
            Em andamento desde {new Date(trip.startedAt).toLocaleTimeString('pt-BR')}
          </Text>
          <Text style={styles.info}>Pings na fila: {pending}</Text>
          {status !== '' && <Text style={styles.status}>{status}</Text>}
          <Button title="Sincronizar" onPress={handleSync} disabled={busy} />
          <Button title="Finalizar viagem" color="#a00" onPress={handleStop} disabled={busy} />
        </>
      ) : (
        <>
          <Text style={styles.subtitle}>Selecione o veículo</Text>
          {vehicles.map((v) => (
            <TouchableOpacity
              key={v.id}
              style={[styles.vehicleOption, selectedVehicleId === v.id && styles.vehicleOptionSelected]}
              onPress={() => setSelectedVehicleId(v.id)}
            >
              <Text style={styles.vehicleLabel}>
                {v.plate} — {v.brand} {v.model}
              </Text>
            </TouchableOpacity>
          ))}
          {status !== '' && <Text style={styles.status}>{status}</Text>}
          <Button title="Iniciar viagem" onPress={handleStart} disabled={busy || !selectedVehicleId} />
        </>
      )}

      <View style={styles.spacer} />
      <Button title="Sair" color="#a00" onPress={onLogout} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', padding: 24, gap: 12 },
  title: { fontSize: 28, fontWeight: '700' },
  subtitle: { fontSize: 16, fontWeight: '600', marginTop: 8 },
  info: { fontSize: 16 },
  status: { fontSize: 14, color: '#555' },
  spacer: { height: 24 },
  vehicleOption: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12 },
  vehicleOptionSelected: { borderColor: '#1f3a5f', backgroundColor: '#eef1f5' },
  vehicleLabel: { fontSize: 15 },
});
