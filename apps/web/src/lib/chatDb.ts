import type { ChatMessageResponse } from '../api/client';

/**
 * Histórico completo do chat, local no dispositivo do gestor (ADR 0015). O servidor só
 * guarda uma janela curta — aqui é onde a conversa inteira vive de verdade. Cada
 * navegador é um "dispositivo" próprio (deviceId gerado uma vez, guardado no
 * localStorage), o que é exatamente o modelo que o chat_sync_cursor espera: o job de
 * limpeza só age depois que ESTE dispositivo confirmar que já persistiu até certo ponto.
 */
const DB_NAME = 'autonomousapi-chat';
const DB_VERSION = 1;
const STORE = 'messages';
const DEVICE_ID_KEY = 'autonomousapi.chatDeviceId';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'id' });
        store.createIndex('conversationId', 'conversationId');
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/** Um id estável por navegador — sobrevive a reload, não sobrevive a limpar dados do site. */
export function getDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

/** Upsert por id — mensagem que já existe localmente é substituída, não duplicada. */
export async function saveMessages(messages: ChatMessageResponse[]): Promise<void> {
  if (messages.length === 0) return;
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    const store = tx.objectStore(STORE);
    messages.forEach((m) => store.put(m));
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

/** Histórico completo local de uma conversa, mais antiga primeiro. */
export async function getMessages(conversationId: string): Promise<ChatMessageResponse[]> {
  const db = await openDb();
  const result = await new Promise<ChatMessageResponse[]>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const index = tx.objectStore(STORE).index('conversationId');
    const req = index.getAll(IDBKeyRange.only(conversationId));
    req.onsuccess = () => resolve(req.result as ChatMessageResponse[]);
    req.onerror = () => reject(req.error);
  });
  db.close();
  return result.sort((a, b) => (a.sentAt! < b.sentAt! ? -1 : 1));
}
