import AsyncStorage from '@react-native-async-storage/async-storage';

export interface GpsPing {
  recordedAt: string; // ISO-8601
  lat: number;
  lon: number;
  speed?: number;
  heading?: number;
  accuracy?: number;
}

const STORAGE_KEY = 'autonomousapi.pingQueue';

/**
 * Fila offline-first de pings de GPS (spec 03: registrar viagem offline, sincronizar ao
 * reconectar). Backing store é AsyncStorage — sobrevive a reinício do app, motorista
 * frequentemente está em área de sinal ruim (spec 03).
 */
async function readQueue(): Promise<GpsPing[]> {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  return raw ? (JSON.parse(raw) as GpsPing[]) : [];
}

async function writeQueue(queue: GpsPing[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(queue));
}

export async function enqueue(ping: GpsPing): Promise<void> {
  const queue = await readQueue();
  queue.push(ping);
  await writeQueue(queue);
}

export async function pendingCount(): Promise<number> {
  return (await readQueue()).length;
}

/**
 * Envia os pings na ordem. Se o envio de um ping falhar, ele permanece na fila (não
 * descarta dado) e o erro é propagado — quem chama decide se tenta de novo depois.
 */
export async function flush(send: (ping: GpsPing) => Promise<void>): Promise<void> {
  let queue = await readQueue();
  while (queue.length > 0) {
    await send(queue[0]);
    queue = queue.slice(1);
    await writeQueue(queue);
  }
}
