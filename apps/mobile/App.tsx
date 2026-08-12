import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { coreApi, setAuthToken, type UserResponse } from './src/api/client';
import { clearTokens, loadAccessToken } from './src/auth/tokenStorage';
import { acceptLocationConsent, hasAcceptedLocationConsent } from './src/onboarding/consent';
import { LocationConsentScreen } from './src/screens/LocationConsentScreen';
import { LoginScreen } from './src/screens/LoginScreen';
import { NotDriverScreen } from './src/screens/NotDriverScreen';
import { TripScreen } from './src/screens/TripScreen';

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
        <TripScreen onLogout={handleLogout} />
      ) : (
        <NotDriverScreen onLogout={handleLogout} />
      )}
    </>
  );
}
