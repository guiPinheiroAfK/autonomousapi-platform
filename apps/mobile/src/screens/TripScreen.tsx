import { ActivityIndicator, Button, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import type { TripResponse, VehicleResponse } from '../api/client';

interface Props {
  loading: boolean;
  vehicles: VehicleResponse[];
  selectedVehicleId: string | null;
  onSelectVehicle: (id: string) => void;
  trip: TripResponse | null;
  pending: number;
  status: string;
  busy: boolean;
  onStart: () => void;
  onStop: () => void;
  onSync: () => void;
  onLogout: () => void;
}

/**
 * Só apresentação — o estado da viagem e o rastreamento de GPS vivem em
 * `useTripTracking`/`HomeTabs`, não aqui (ver comentário no hook: essa tela desmontava a
 * cada troca de aba e derrubava o GPS junto).
 */
export function TripScreen({
  loading,
  vehicles,
  selectedVehicleId,
  onSelectVehicle,
  trip,
  pending,
  status,
  busy,
  onStart,
  onStop,
  onSync,
  onLogout,
}: Props) {
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
          <Button title="Sincronizar" onPress={onSync} disabled={busy} />
          <Button title="Finalizar viagem" color="#a00" onPress={onStop} disabled={busy} />
        </>
      ) : (
        <>
          <Text style={styles.subtitle}>Selecione o veículo</Text>
          {vehicles.map((v) => (
            <TouchableOpacity
              key={v.id}
              style={[styles.vehicleOption, selectedVehicleId === v.id && styles.vehicleOptionSelected]}
              onPress={() => onSelectVehicle(v.id)}
            >
              <Text style={styles.vehicleLabel}>
                {v.plate} — {v.brand} {v.model}
              </Text>
            </TouchableOpacity>
          ))}
          {status !== '' && <Text style={styles.status}>{status}</Text>}
          <Button title="Iniciar viagem" onPress={onStart} disabled={busy || !selectedVehicleId} />
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
