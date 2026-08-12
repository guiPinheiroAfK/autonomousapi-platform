import AsyncStorage from '@react-native-async-storage/async-storage';

const CONSENT_KEY = 'autonomousapi.locationConsentAccepted';

export async function hasAcceptedLocationConsent(): Promise<boolean> {
  return (await AsyncStorage.getItem(CONSENT_KEY)) === 'true';
}

export async function acceptLocationConsent(): Promise<void> {
  await AsyncStorage.setItem(CONSENT_KEY, 'true');
}
