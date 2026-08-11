import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';

import { LoginScreen } from './src/screens/LoginScreen';
import { TripScreen } from './src/screens/TripScreen';

export default function App() {
  const [accessToken, setAccessToken] = useState<string | null>(null);

  return (
    <>
      <StatusBar style="auto" />
      {accessToken ? (
        <TripScreen onLogout={() => setAccessToken(null)} />
      ) : (
        <LoginScreen onLogin={setAccessToken} />
      )}
    </>
  );
}
