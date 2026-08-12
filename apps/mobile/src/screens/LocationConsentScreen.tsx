import { Button, StyleSheet, Text, View } from 'react-native';

interface Props {
  onAccept: () => void;
}

/**
 * Tela de consentimento própria (spec 03) — linguagem clara, não escondida em termos de
 * uso. Aparece uma vez no onboarding, antes de qualquer pedido de permissão do sistema.
 */
export function LocationConsentScreen({ onAccept }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Localização da viagem</Text>
      <Text style={styles.body}>
        Para registrar o trajeto das suas viagens, o AutonomousAPI precisa acessar sua
        localização — inclusive quando o app está minimizado, enquanto uma viagem estiver em
        andamento.
      </Text>
      <Text style={styles.body}>
        Esse dado é usado só para calcular rota, distância e custo por km da frota. Você pode
        revogar o acesso a qualquer momento nas configurações do seu telefone.
      </Text>
      <View style={styles.spacer} />
      <Button title="Entendi, continuar" onPress={onAccept} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, gap: 12 },
  title: { fontSize: 24, fontWeight: '700' },
  body: { fontSize: 15, color: '#333', lineHeight: 22 },
  spacer: { height: 12 },
});
