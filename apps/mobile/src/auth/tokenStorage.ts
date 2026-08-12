import * as SecureStore from 'expo-secure-store';

const ACCESS_TOKEN_KEY = 'autonomousapi.accessToken';
const REFRESH_TOKEN_KEY = 'autonomousapi.refreshToken';

/**
 * Tokens ficam no Keychain/Keystore (expo-secure-store), não em AsyncStorage — é
 * credencial, não dado de app comum. Permite reabrir o app sem pedir login de novo.
 */
export async function saveTokens(accessToken: string, refreshToken: string): Promise<void> {
  await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken);
  await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken);
}

export async function loadAccessToken(): Promise<string | null> {
  return SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
}

export async function clearTokens(): Promise<void> {
  await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
  await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
}
