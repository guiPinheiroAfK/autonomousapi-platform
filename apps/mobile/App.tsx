import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { coreApi, setAuthToken, type UserResponse } from './src/api/client';
import { clearTokens, loadAccessToken } from './src/auth/tokenStorage';
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

  useEffect(() => {
    (async () => {
      setConsentAccepted(await hasAcceptedLocationConsent());

      const token = await loadAccessToken();
      if (token) {
        setAuthToken(token);
        try {
          setUser(await coreApi.me());
        } catch {
          // Token expirado/inválido — segue pro login normalmente.
          setAuthToken(null);
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
