import { useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';

import { coreApi, type ChargingStationItem } from '../api/client';

const STATUS_LABEL: Record<ChargingStationItem['status'], string> = {
  DISPONIVEL: 'Disponível',
  OCUPADO: 'Ocupado',
  FORA_DE_SERVICO: 'Fora de serviço',
  DESCONHECIDO: 'Status desconhecido',
};

const STATUS_COLOR: Record<ChargingStationItem['status'], string> = {
  DISPONIVEL: '#1a7f37',
  OCUPADO: '#9a6700',
  FORA_DE_SERVICO: '#cf222e',
  DESCONHECIDO: '#666',
};

/**
 * Recarga elétrica (spec 06, item 1) — lista simples, sem mapa (app não tem lib de mapa
 * ainda). Falha do provedor (RNF011) nunca vira tela de erro: sem chave configurada ou
 * provedor fora do ar, a lista fica vazia com um aviso explicando por quê.
 */
export function ChargingStationsScreen() {
  const [loading, setLoading] = useState(true);
  const [providerAvailable, setProviderAvailable] = useState(true);
  const [stations, setStations] = useState<ChargingStationItem[]>([]);

  useEffect(() => {
    coreApi.chargingStations
      .list()
      .then((res) => {
        setStations(res.stations);
        setProviderAvailable(res.providerAvailable);
      })
      .catch(() => {
        setStations([]);
        setProviderAvailable(false);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Pontos de Recarga</Text>
      {!providerAvailable && (
        <Text style={styles.warning}>
          Provedor de dado de recarga não configurado ainda — nenhuma estação sincronizada no momento.
        </Text>
      )}
      <FlatList
        data={stations}
        keyExtractor={(s) => s.id}
        contentContainerStyle={stations.length === 0 ? styles.emptyContainer : undefined}
        ListEmptyComponent={<Text style={styles.empty}>Nenhuma estação de recarga sincronizada ainda.</Text>}
        renderItem={({ item }) => (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.stationName}>{item.name ?? 'Estação sem nome'}</Text>
              <Text style={[styles.status, { color: STATUS_COLOR[item.status] }]}>{STATUS_LABEL[item.status]}</Text>
            </View>
            {item.address && <Text style={styles.detail}>{item.address}</Text>}
            {(item.connectorType || item.powerKw != null) && (
              <Text style={styles.detail}>
                {item.connectorType}
                {item.connectorType && item.powerKw != null ? ' · ' : ''}
                {item.powerKw != null ? `${item.powerKw} kW` : ''}
              </Text>
            )}
          </View>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { flex: 1, padding: 16 },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 8 },
  warning: {
    fontSize: 13,
    color: '#9a6700',
    backgroundColor: '#fff8e6',
    padding: 10,
    borderRadius: 8,
    marginBottom: 12,
  },
  emptyContainer: { flexGrow: 1, justifyContent: 'center' },
  empty: { textAlign: 'center', color: '#888', fontSize: 13 },
  card: { borderWidth: 1, borderColor: '#eee', borderRadius: 8, padding: 12, marginBottom: 10 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  stationName: { fontSize: 15, fontWeight: '600', flexShrink: 1 },
  status: { fontSize: 12, fontWeight: '600' },
  detail: { fontSize: 12, color: '#666', marginTop: 4 },
});
