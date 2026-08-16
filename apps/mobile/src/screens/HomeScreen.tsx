import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Button,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import {
  coreApi,
  type DriverProfileResponse,
  type MyVehicleResponse,
  type VehicleIncidentRequest,
  type WorkOrderResponse,
} from '../api/client';

interface Props {
  onLogout: () => void;
}

const SEVERIDADES: VehicleIncidentRequest['severidade'][] = ['LEVE', 'MODERADA', 'GRAVE'];

/** Dias até uma data "YYYY-MM-DD" (negativo = já vencida) — mesmo cálculo do painel web. */
function diasAteVencer(isoDate: string): number {
  const [y, m, d] = isoDate.split('-').map(Number);
  const alvo = Date.UTC(y, m - 1, d);
  const hoje = new Date();
  const hojeUtc = Date.UTC(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  return Math.round((alvo - hojeUtc) / 86_400_000);
}

/**
 * Tela inicial do motorista (spec 07, itens 1-4): meu veículo, minha CNH, OS do
 * veículo (read-only) e reportar ocorrência. Deliberadamente sem nada de avaliação —
 * o backend nem manda esse campo pra cá (spec 06/07).
 */
export function HomeScreen({ onLogout }: Props) {
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState<DriverProfileResponse | null>(null);
  const [vehicle, setVehicle] = useState<MyVehicleResponse | null>(null);
  const [workOrders, setWorkOrders] = useState<WorkOrderResponse[]>([]);

  const [incidentOpen, setIncidentOpen] = useState(false);
  const [incidentDate, setIncidentDate] = useState(new Date().toISOString().slice(0, 10));
  const [incidentSeveridade, setIncidentSeveridade] = useState<VehicleIncidentRequest['severidade']>('LEVE');
  const [incidentDescricao, setIncidentDescricao] = useState('');
  const [incidentSaving, setIncidentSaving] = useState(false);

  useEffect(() => {
    refresh();
  }, []);

  async function refresh() {
    setLoading(true);
    try {
      const [p, v] = await Promise.all([coreApi.my.profile(), coreApi.my.vehicle()]);
      setProfile(p);
      setVehicle(v);
      setWorkOrders(v ? await coreApi.my.workOrders() : []);
    } catch (e) {
      Alert.alert('Erro ao carregar', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setLoading(false);
    }
  }

  async function handleReportIncident() {
    setIncidentSaving(true);
    try {
      await coreApi.my.reportIncident({
        data: incidentDate,
        severidade: incidentSeveridade,
        descricao: incidentDescricao || undefined,
      });
      setIncidentOpen(false);
      setIncidentDescricao('');
      Alert.alert('Ocorrência registrada', 'O gestor vai revisar e decidir os próximos passos.');
    } catch (e) {
      Alert.alert('Falha ao registrar', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setIncidentSaving(false);
    }
  }

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  const diasCnh = profile?.cnhValidade ? diasAteVencer(profile.cnhValidade) : null;

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Olá, {profile?.name?.split(' ')[0]}</Text>

      <View style={styles.card}>
        <Text style={styles.cardLabel}>MINHA CNH</Text>
        {profile?.cnhValidade ? (
          <>
            <Text style={styles.cardValue}>Válida até {profile.cnhValidade.split('-').reverse().join('/')}</Text>
            {diasCnh != null && diasCnh <= 30 && (
              <Text style={diasCnh < 0 ? styles.alertDanger : styles.alertWarning}>
                {diasCnh < 0 ? 'CNH vencida' : `Vence em ${diasCnh} dia(s)`}
              </Text>
            )}
          </>
        ) : (
          <Text style={styles.cardMuted}>Validade não cadastrada.</Text>
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardLabel}>MEU VEÍCULO</Text>
        {vehicle ? (
          <Text style={styles.cardValue}>
            {vehicle.plate} — {vehicle.brand} {vehicle.model}
          </Text>
        ) : (
          <Text style={styles.cardMuted}>Nenhum veículo designado no momento.</Text>
        )}
      </View>

      {vehicle && (
        <View style={styles.card}>
          <Text style={styles.cardLabel}>ORDENS DE SERVIÇO</Text>
          {workOrders.length === 0 ? (
            <Text style={styles.cardMuted}>Nenhuma OS registrada para este veículo.</Text>
          ) : (
            workOrders.map((os) => (
              <View key={os.id} style={styles.osRow}>
                <Text style={styles.osNumero}>{os.numero}</Text>
                <Text style={styles.osStatus}>{os.status}</Text>
              </View>
            ))
          )}
        </View>
      )}

      <Button title="Reportar ocorrência" onPress={() => setIncidentOpen(true)} disabled={!vehicle} />
      {!vehicle && <Text style={styles.hint}>Precisa ter um veículo designado para reportar ocorrência.</Text>}

      <View style={styles.spacer} />
      <Button title="Sair" color="#a00" onPress={onLogout} />

      <Modal visible={incidentOpen} animationType="slide" onRequestClose={() => setIncidentOpen(false)}>
        <ScrollView contentContainerStyle={styles.container}>
          <Text style={styles.title}>Reportar ocorrência</Text>
          <Text style={styles.subtitle}>Severidade</Text>
          <View style={styles.severidadeRow}>
            {SEVERIDADES.map((s) => (
              <TouchableOpacity
                key={s}
                style={[styles.severidadeOption, incidentSeveridade === s && styles.severidadeOptionSelected]}
                onPress={() => setIncidentSeveridade(s)}
              >
                <Text style={incidentSeveridade === s ? styles.severidadeLabelSelected : styles.severidadeLabel}>
                  {s === 'LEVE' ? 'Leve' : s === 'MODERADA' ? 'Moderada' : 'Grave'}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          <TextInput
            style={styles.input}
            placeholder="Data (AAAA-MM-DD)"
            value={incidentDate}
            onChangeText={setIncidentDate}
          />
          <TextInput
            style={styles.input}
            placeholder="Descrição (opcional)"
            value={incidentDescricao}
            onChangeText={setIncidentDescricao}
            multiline
          />
          {incidentSaving ? (
            <ActivityIndicator />
          ) : (
            <Button title="Registrar" onPress={handleReportIncident} />
          )}
          <View style={styles.spacer} />
          <Button title="Cancelar" color="#555" onPress={() => setIncidentOpen(false)} />
        </ScrollView>
      </Modal>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { flexGrow: 1, padding: 24, gap: 12 },
  title: { fontSize: 24, fontWeight: '700' },
  subtitle: { fontSize: 15, fontWeight: '600', marginTop: 8 },
  card: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 14, gap: 4 },
  cardLabel: { fontSize: 11, fontWeight: '700', color: '#888', letterSpacing: 0.5 },
  cardValue: { fontSize: 16, fontWeight: '600' },
  cardMuted: { fontSize: 14, color: '#888' },
  alertWarning: { fontSize: 13, color: '#b8860b', fontWeight: '600' },
  alertDanger: { fontSize: 13, color: '#a00', fontWeight: '600' },
  osRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
  osNumero: { fontSize: 14, fontWeight: '600' },
  osStatus: { fontSize: 13, color: '#555' },
  hint: { fontSize: 12, color: '#888' },
  spacer: { height: 12 },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12, fontSize: 16 },
  severidadeRow: { flexDirection: 'row', gap: 8 },
  severidadeOption: { flex: 1, borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 10, alignItems: 'center' },
  severidadeOptionSelected: { borderColor: '#1f3a5f', backgroundColor: '#eef1f5' },
  severidadeLabel: { fontSize: 14 },
  severidadeLabelSelected: { fontSize: 14, fontWeight: '700', color: '#1f3a5f' },
});
