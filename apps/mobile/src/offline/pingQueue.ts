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
 * reconectar). Persistida em AsyncStorage — sobrevive a reinício do app, porque o motorista
 * frequentemente está em área de sinal ruim.
 *
 * Invariante que o resto do arquivo depende: a fila é FIFO e só cresce pelo fim. Por isso
 * remover "os N primeiros" é sempre seguro, mesmo que um ping novo tenha entrado no meio
 * de um envio em andamento.
 */

/**
 * Serializa toda leitura-modificação-escrita da fila. AsyncStorage é assíncrono e o
 * watcher de GPS pode enfileirar no meio de um flush — sem isso, a escrita de um
 * sobrescreve a do outro e perde ping (bug real, não teórico: o watcher dispara a cada
 * 15s / 50m enquanto a sincronização está no ar).
 */
let cadeia: Promise<unknown> = Promise.resolve();

function comExclusividade<T>(fn: () => Promise<T>): Promise<T> {
  const resultado = cadeia.then(fn, fn);
  // A cadeia nunca deve quebrar por causa de um erro de um dos elos.
  cadeia = resultado.then(
    () => undefined,
    () => undefined,
  );
  return resultado;
}

async function readQueue(): Promise<GpsPing[]> {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  return raw ? (JSON.parse(raw) as GpsPing[]) : [];
}

async function writeQueue(queue: GpsPing[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(queue));
}

export function enqueue(ping: GpsPing): Promise<void> {
  return comExclusividade(async () => {
    const queue = await readQueue();
    queue.push(ping);
    await writeQueue(queue);
  });
}

export async function pendingCount(): Promise<number> {
  return (await readQueue()).length;
}

/** Remove os {@code quantidade} primeiros da fila, relendo o estado atual (ver invariante FIFO). */
function descartarPrimeiros(quantidade: number): Promise<void> {
  return comExclusividade(async () => {
    const atual = await readQueue();
    await writeQueue(atual.slice(quantidade));
  });
}

/**
 * Esvazia a fila em lotes. {@code enviar} recebe um lote e devolve quantos o servidor
 * aceitou — só esses saem da fila, o resto fica para a próxima tentativa.
 *
 * O envio acontece FORA do lock de escrita de propósito: segurar o lock durante uma
 * chamada de rede travaria o watcher de GPS pelo tempo todo da requisição.
 */
export async function flushBatch(
  enviar: (pings: GpsPing[]) => Promise<number>,
  tamanhoDoLote = 200,
): Promise<number> {
  let enviados = 0;

  for (;;) {
    const fila = await readQueue();
    if (fila.length === 0) return enviados;

    const lote = fila.slice(0, tamanhoDoLote);
    const aceitos = await enviar(lote);

    // Nada entrou: para aqui e mantém a fila intacta para nova tentativa.
    if (aceitos <= 0) return enviados;

    await descartarPrimeiros(aceitos);
    enviados += aceitos;

    // Aceite parcial = servidor/geo-api instável no meio do lote; não insiste agora.
    if (aceitos < lote.length) return enviados;
  }
}
