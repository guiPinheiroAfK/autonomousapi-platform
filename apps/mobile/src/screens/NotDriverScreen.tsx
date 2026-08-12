import { Button, StyleSheet, Text, View } from 'react-native';

interface Props {
  onLogout: () => void;
}

/** Fase 1 do app mobile cobre só o modo motorista (registro de viagem) — modo gestor reduzido é
 *  próximo passo do spec 03, ainda não implementado aqui. */
export function NotDriverScreen({ onLogout }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Conta não é de motorista</Text>
      <Text style={styles.body}>
        Este app registra viagens de motoristas. Use o painel web para gestão da frota.
      </Text>
      <Button title="Sair" color="#a00" onPress={onLogout} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, gap: 12 },
  title: { fontSize: 22, fontWeight: '700' },
  body: { fontSize: 15, color: '#333' },
});
