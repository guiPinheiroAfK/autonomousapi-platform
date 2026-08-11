export interface GpsPing {
  vehicleId: string;
  recordedAt: string; // ISO-8601
  lat: number;
  lon: number;
  speed?: number;
  heading?: number;
  accuracy?: number;
}

/**
 * Fila offline-first de pings de GPS (spec 03: registrar viagem offline, sincronizar
 * ao reconectar). Este é o esqueleto em memória — troque o backing store por
 * AsyncStorage/SQLite para persistir entre reinícios do app. A semântica de reenvio
 * (manter na fila em caso de falha) já está aqui.
 */
const queue: GpsPing[] = [];

export function enqueue(ping: GpsPing): void {
  queue.push(ping);
}

export function pendingCount(): number {
  return queue.length;
}

/**
 * Envia os pings na ordem. Se o envio de um ping falhar, ele permanece na fila
 * (não descarta dado) e o erro é propagado para nova tentativa depois.
 */
export async function flush(send: (ping: GpsPing) => Promise<void>): Promise<void> {
  while (queue.length > 0) {
    await send(queue[0]);
    queue.shift();
  }
}
