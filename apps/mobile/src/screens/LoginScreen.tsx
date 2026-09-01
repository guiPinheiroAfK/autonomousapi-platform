import { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Button,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { coreApi, setAuthToken, setRefreshToken, type TokenResponse } from '../api/client';
import { saveTokens } from '../auth/tokenStorage';

interface Props {
  onLogin: (tokens: TokenResponse) => void;
}

export function LoginScreen({ onLogin }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleLogin() {
    setLoading(true);
    try {
      const tokens = await coreApi.login(email.trim(), password);
      setAuthToken(tokens.accessToken);
      setRefreshToken(tokens.refreshToken);
      await saveTokens(tokens.accessToken, tokens.refreshToken);
      onLogin(tokens);
    } catch (e) {
      Alert.alert('Falha no login', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>AutonomousAPI</Text>
      <Text style={styles.subtitle}>Entrar</Text>
      <TextInput
        style={styles.input}
        placeholder="E-mail"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={setEmail}
      />
      <TextInput
        style={styles.input}
        placeholder="Senha"
        secureTextEntry
        value={password}
        onChangeText={setPassword}
      />
      {loading ? <ActivityIndicator /> : <Button title="Entrar" onPress={handleLogin} />}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, gap: 12 },
  title: { fontSize: 28, fontWeight: '700' },
  subtitle: { fontSize: 18, marginBottom: 8, color: '#555' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12, fontSize: 16 },
});
