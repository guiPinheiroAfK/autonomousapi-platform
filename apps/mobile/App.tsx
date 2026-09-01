import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { coreApi, setAuthToken, setRefreshToken, setTokensRefreshedHandler, type UserResponse } from './src/api/client';
import { clearTokens, loadAccessToken, loadRefreshToken, saveTokens } from './src/auth/tokenStorage';
import { acceptLocationConsent, hasAcceptedLocationConsent } from './src/onboarding/consent';
import { registerPushToken } from './src/push/registerPush';
import { HomeTabs } from './src/screens/HomeTabs';
import { LocationConsentScreen } from './src/screens/LocationConsentScreen';
import { LoginScreen } from './src/screens/LoginScreen';
import { NotDriverScreen } from './src/screens/NotDriverScreen';

export default function App() {
  const [bootstrapping, setBootstrapping] = useState(true);
  const [consentAccepted, setConsentAccepted] = useState(false);
  const [user, setUser] = useState<UserResponse | null>(null);

  // Registrado antes do bootstrap abaixo — se o access token salvo já expirou (dura só
  // 15min), a própria chamada a `coreApi.me()` no bootstrap já dispara o refresh
  // silencioso do client (ver `api/client.ts`) antes de cair no catch. Sem isso, reabrir
  // o app depois de um tempo fechado jogava pro login de novo mesmo com um refresh token
  // de 30 dias ainda válido guardado no SecureStore à toa (achado nesta sessão).
  useEffect(() => {
    setTokensRefreshedHandler(saveTokens);
    return () => setTokensRefreshedHandler(null);
  }, []);

  useEffect(() => {
    (async () => {
      setConsentAccepted(await hasAcceptedLocationConsent());

      const [token, refreshToken] = await Promise.all([loadAccessToken(), loadRefreshToken()]);
      if (refreshToken) setRefreshToken(refreshToken);
      if (token) {
        setAuthToken(token);
        try {
          setUser(await coreApi.me());
        } catch {
          // Nem o refresh silencioso conseguiu segurar a sessão — segue pro login normalmente.
          setAuthToken(null);
          setRefreshToken(null);
          await clearTokens();
        }
      }
      setBootstrapping(false);
    })();
  }, []);

  // Cobre tanto login novo quanto sessão restaurada no boot — os dois passam por setUser.
  useEffect(() => {
    if (user?.role === 'MOTORISTA') {
      registerPushToken();
    }
  }, [user]);

  async function handleLogout() {
    setAuthToken(null);
    setRefreshToken(null);
    await clearTokens();
    setUser(null);
  }

  if (bootstrapping) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator />
      </View>
    );
  }

  if (!consentAccepted) {
    return (
      <LocationConsentScreen
        onAccept={async () => {
          await acceptLocationConsent();
          setConsentAccepted(true);
        }}
      />
    );
  }

  if (!user) {
    return (
      <>
        <StatusBar style="auto" />
        <LoginScreen onLogin={() => coreApi.me().then(setUser)} />
      </>
    );
  }

  return (
    <>
      <StatusBar style="auto" />
      {user.role === 'MOTORISTA' ? (
        <HomeTabs userId={user.id} onLogout={handleLogout} />
      ) : (
        <NotDriverScreen onLogout={handleLogout} />
      )}
    </>
  );
}
