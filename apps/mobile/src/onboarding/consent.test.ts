import AsyncStorage from '@react-native-async-storage/async-storage';
import { acceptLocationConsent, hasAcceptedLocationConsent } from './consent';

jest.mock(
  '@react-native-async-storage/async-storage',
  () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

beforeEach(async () => {
  await AsyncStorage.clear();
});

describe('hasAcceptedLocationConsent', () => {
  it('começa como false (consentimento não é assumido por padrão)', async () => {
    expect(await hasAcceptedLocationConsent()).toBe(false);
  });

  it('vira true depois de aceito', async () => {
    await acceptLocationConsent();
    expect(await hasAcceptedLocationConsent()).toBe(true);
  });

  it('qualquer valor salvo diferente de "true" continua contando como não aceito', async () => {
    // Defesa contra dado corrompido/de versão antiga no AsyncStorage — só a string
    // exata "true" (a que acceptLocationConsent grava) conta como consentimento dado.
    await AsyncStorage.setItem('autonomousapi.locationConsentAccepted', 'yes');
    expect(await hasAcceptedLocationConsent()).toBe(false);
  });
});
