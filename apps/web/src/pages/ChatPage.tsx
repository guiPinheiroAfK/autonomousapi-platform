import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { MessageCirclePlus, Send } from 'lucide-react';
import {
  coreApi,
  type ChatConversationResponse,
  type ChatMessageResponse,
  type DriverResponse,
} from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Avatar, AvatarFallback } from '../components/ui/avatar';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { cn } from '../lib/utils';
import { formatTimeBR, iniciais } from '../lib/format';
import { getDeviceId, getMessages, saveMessages } from '../lib/chatDb';

const POLL_INTERVAL_MS = 5000;

/**
 * Mini-chat gestor↔motorista (spec 07, ADR 0015). O servidor só devolve uma janela
 * curta — o histórico completo é lido daqui, do IndexedDB local, e mesclado com o que
 * o servidor tem no momento. Depois de persistir, confirma o sync (chat_sync_cursor),
 * o que autoriza o job de limpeza no backend a agir.
 */
export function ChatPage() {
  const { user } = useAuth();
  const [conversations, setConversations] = useState<ChatConversationResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [newBody, setNewBody] = useState('');
  const [sending, setSending] = useState(false);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [eligibleDrivers, setEligibleDrivers] = useState<DriverResponse[]>([]);
  const [pickedDriverId, setPickedDriverId] = useState('');

  const bottomRef = useRef<HTMLDivElement>(null);
  const deviceId = useMemo(() => getDeviceId(), []);

  function refreshConversations() {
    coreApi.chat
      .listConversations()
      .then(setConversations)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar conversas'))
      .finally(() => setLoading(false));
  }

  useEffect(refreshConversations, []);

  // Ao trocar de conversa: histórico local primeiro (instantâneo), depois mescla o que
  // o servidor tem agora e confirma o sync.
  useEffect(() => {
    if (!selectedId) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    getMessages(selectedId).then((local) => {
      if (!cancelled) setMessages(local);
    });
    loadServerWindow(selectedId);
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  // Poll simples (spec 07: baixo volume não justifica infraestrutura de tempo real).
  useEffect(() => {
    if (!selectedId) return;
    const interval = setInterval(() => loadServerWindow(selectedId), POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [selectedId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [messages]);

  async function loadServerWindow(conversationId: string) {
    try {
      const serverMessages = await coreApi.chat.listMessages(conversationId);
      if (serverMessages.length === 0) return;

      await saveMessages(serverMessages);
      const merged = await getMessages(conversationId);
      setMessages(merged);

      // Confirma sync (gestor-only no backend — motorista não chama isso, o app dele
      // não precisa reter histórico longo, spec 07/ADR 0015).
      if (user?.role !== 'MOTORISTA') {
        const maxSentAt = merged.reduce((max, m) => (m.sentAt! > max ? m.sentAt! : max), merged[0].sentAt!);
        coreApi.chat.syncCursor({ deviceId, syncedAt: maxSentAt }).catch(() => {
          // Falha ao confirmar sync não é crítica — só adia a limpeza no servidor, não perde nada.
        });
      }
    } catch {
      // Poll silencioso: erro passageiro de rede não deve interromper a conversa aberta.
    }
  }

  async function handleSend(e: FormEvent) {
    e.preventDefault();
    if (!selectedId || !newBody.trim()) return;
    setSending(true);
    setError('');
    try {
      const sent = await coreApi.chat.sendMessage(selectedId, { body: newBody.trim() });
      await saveMessages([sent]);
      setMessages((prev) => [...prev, sent]);
      setNewBody('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao enviar mensagem');
    } finally {
      setSending(false);
    }
  }

  function openPicker() {
    setPickedDriverId('');
    coreApi.drivers.list().then((all) => {
      const jaTemConversa = new Set(conversations.map((c) => c.driverId));
      setEligibleDrivers(all.filter((d) => d.hasLogin && !jaTemConversa.has(d.id!)));
    });
    setPickerOpen(true);
  }

  async function handleStartConversation(e: FormEvent) {
    e.preventDefault();
    if (!pickedDriverId) return;
    try {
      const conv = await coreApi.chat.createConversation({ driverId: pickedDriverId });
      setPickerOpen(false);
      refreshConversations();
      setSelectedId(conv.id!);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao iniciar conversa');
    }
  }

  const selected = conversations.find((c) => c.id === selectedId);

  return (
    <div className="flex h-full overflow-hidden">
      <div className="flex w-72 shrink-0 flex-col border-r border-border">
        <div className="flex items-center justify-between border-b border-border p-4">
          <h2 className="font-display text-sm font-semibold text-foreground">Conversas</h2>
          <button
            type="button"
            onClick={openPicker}
            className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
            title="Iniciar conversa"
          >
            <MessageCirclePlus className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <p className="p-4 text-center text-xs text-muted-foreground">Carregando...</p>
          ) : conversations.length === 0 ? (
            <p className="p-4 text-center text-xs text-muted-foreground">Nenhuma conversa ainda.</p>
          ) : (
            conversations.map((c) => (
              <button
                key={c.id}
                type="button"
                onClick={() => setSelectedId(c.id!)}
                className={cn(
                  'flex w-full items-center gap-2.5 border-b border-border p-3 text-left hover:bg-muted/50',
                  selectedId === c.id && 'bg-muted',
                )}
              >
                <Avatar className="size-8">
                  <AvatarFallback>{iniciais(c.driverName ?? '?')}</AvatarFallback>
                </Avatar>
                <div className="min-w-0">
                  <p className="truncate text-xs font-medium text-foreground">{c.driverName}</p>
                  {c.vehiclePlate && <p className="truncate text-[11px] text-muted-foreground">{c.vehiclePlate}</p>}
                </div>
              </button>
            ))
          )}
        </div>
      </div>

      <div className="flex flex-1 flex-col">
        {!selected ? (
          <div className="flex flex-1 items-center justify-center">
            <p className="text-xs text-muted-foreground">Selecione uma conversa ou inicie uma nova.</p>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-2.5 border-b border-border p-4">
              <Avatar className="size-8">
                <AvatarFallback>{iniciais(selected.driverName ?? '?')}</AvatarFallback>
              </Avatar>
              <p className="text-sm font-medium text-foreground">{selected.driverName}</p>
            </div>

            <div className="flex-1 space-y-2 overflow-y-auto p-4">
              {messages.length === 0 && (
                <p className="pt-8 text-center text-xs text-muted-foreground">Nenhuma mensagem ainda.</p>
              )}
              {messages.map((m) => {
                const mine = m.senderUserId === user?.id;
                return (
                  <div key={m.id} className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
                    <div
                      className={cn(
                        'max-w-[70%] rounded-lg px-3 py-2 text-xs',
                        mine ? 'bg-primary text-primary-foreground' : 'bg-muted text-foreground',
                      )}
                    >
                      <p>{m.body}</p>
                      <p className={cn('mt-1 text-[10px]', mine ? 'text-primary-foreground/70' : 'text-muted-foreground')}>
                        {m.sentAt ? formatTimeBR(m.sentAt) : ''}
                      </p>
                    </div>
                  </div>
                );
              })}
              <div ref={bottomRef} />
            </div>

            {error && <p className="px-4 text-xs text-status-danger">{error}</p>}

            <form onSubmit={handleSend} className="flex items-center gap-2 border-t border-border p-3">
              <Input
                placeholder="Escreva uma mensagem..."
                value={newBody}
                onChange={(e) => setNewBody(e.target.value)}
                className="flex-1"
              />
              <Button type="submit" size="sm" disabled={!newBody.trim() || sending}>
                <Send className="size-4" />
              </Button>
            </form>
          </>
        )}
      </div>

      <Modal open={pickerOpen} onClose={() => setPickerOpen(false)} title="Iniciar conversa">
        {eligibleDrivers.length === 0 ? (
          <p className="text-xs text-muted-foreground">
            Nenhum motorista disponível — só quem já aceitou o convite de acesso ao app pode conversar.
          </p>
        ) : (
          <form onSubmit={handleStartConversation} className="space-y-3">
            <Select value={pickedDriverId} onChange={(e) => setPickedDriverId(e.target.value)}>
              <option value="">Selecione um motorista...</option>
              {eligibleDrivers.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => setPickerOpen(false)}>
                Cancelar
              </Button>
              <Button type="submit" size="sm" disabled={!pickedDriverId}>
                Iniciar
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
