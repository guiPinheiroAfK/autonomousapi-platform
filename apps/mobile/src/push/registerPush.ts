import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { coreApi } from '../api/client';

/**
 * Registra o device token de push (spec 07 item 5, ADR 0016). Mesmo raciocínio do
 * backend com Stripe/SMTP: funciona sem credencial nenhuma configurada — sem projeto
 * EAS ou sem permissão, falha silenciosa, não bloqueia o app.
 */
export async function registerPushToken(): Promise<void> {
  try {
    const { status: existing } = await Notifications.getPermissionsAsync();
    let finalStatus = existing;
    if (existing !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }
    if (finalStatus !== 'granted') return;

    const { data: token } = await Notifications.getExpoPushTokenAsync();
    const plataforma = Platform.OS === 'ios' ? 'IOS' : 'ANDROID';
    await coreApi.push.registerDevice(token, plataforma);
  } catch {
    // Sem projeto EAS configurado (sem projectId em app.json) ou sem permissão do
    // usuário — não é erro fatal, só significa que push não vai ser entregue de verdade.
  }
}
