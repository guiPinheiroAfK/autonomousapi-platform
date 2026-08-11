import { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';

import { type GpsPing, enqueue, flush, pendingCount } from '../offline/pingQueue';

interface Props {
  onLogout: () => void;
}

export function TripScreen({ onLogout }: Props) {
  const [pending, setPending] = useState(0);
  const [status, setStatus] = useState('');

  function registrarPing() {
    // Simula um ping de GPS (na Fase 1 real, vem do expo-location em background).
    const ping: GpsPing = {
      vehicleId: '00000000-0000-0000-0000-000000000000',
      recordedAt: new Date().toISOString(),
      lat: -23.55 + Math.random() * 0.01,
      lon: -46.63 + Math.random() * 0.01,
    };
    enqueue(ping);
    setPending(pendingCount());
    setStatus('ping na fila local (offline-first)');
  }

  async function sincronizar() {
    setStatus('sincronizando...');
    try {
      // TODO Fase 1/2: enviar cada ping ao core-api (que orquestra o geo-api).
      await flush(async () => {
        /* placeholder de envio — mobile fala só com o core-api */
      });
      setPending(pendingCount());
      setStatus('sincronizado');
    } catch {
      setPending(pendingCount());
      setStatus('falha — pings mantidos na fila para reenvio');
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Viagem</Text>
      <Text style={styles.info}>Pings na fila: {pending}</Text>
      {status !== '' && <Text style={styles.status}>{status}</Text>}
      <Button title="Registrar ping" onPress={registrarPing} />
      <Button title="Sincronizar" onPress={sincronizar} />
      <View style={styles.spacer} />
      <Button title="Sair" color="#a00" onPress={onLogout} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, gap: 12 },
  title: { fontSize: 28, fontWeight: '700' },
  info: { fontSize: 16 },
  status: { fontSize: 14, color: '#555' },
  spacer: { height: 24 },
});
