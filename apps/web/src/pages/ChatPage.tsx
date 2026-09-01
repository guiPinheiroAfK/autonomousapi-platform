import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  ArrowLeft,
  Check,
  CheckCheck,
  Forward as ForwardIcon,
  MapPin,
  MessageCirclePlus,
  MessagesSquare,
  MoreVertical,
  Pencil,
  Reply as ReplyIcon,
  Route as RouteIcon,
  Send,
  Smile,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type ChatConversationResponse,
  type ChatMessageResponse,
  type ChatReactionResponse,
  type DriverResponse,
  type RoutePlanResponse,
  type TeamMemberOptionResponse,
  type UserResponse,
} from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Avatar, AvatarFallback } from '../components/ui/avatar';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { cn } from '../lib/utils';
import { formatRelativeShortBR, formatTimeBR, iniciais } from '../lib/format';
import { getDeviceId, getMessages, markChatSeenNow, saveMessages } from '../lib/chatDb';
import { confirmDialog } from '../lib/confirm';
import { toast } from '../lib/toast';

const POLL_INTERVAL_MS = 5000;
const REACTION_EMOJIS = ['👍', '❤️', '😂', '😮', '😢', '🙏'] as const;

/** Nome de quem está do outro lado da conversa: gestor vê o motorista, motorista vê a
 *  frota (não existe nome de pessoa pro gestor — ver ChatConversationResponse.tenantName
 *  no backend). Corrige o achado de sessão anterior (rótulo sempre igual a driverName).
 *  Conversa de equipe (V33) não tem motorista nem tenant do outro lado — o único
 *  identificador que existe é o e-mail (app_user não tem coluna de nome próprio). */
function nomeDoOutroLado(c: ChatConversationResponse, user: UserResponse | null): string {
  if (c.kind === 'EQUIPE') return c.otherParticipantEmail ?? '—';
  if (user?.role === 'MOTORISTA') return c.tenantName ?? '—';
  return c.driverName ?? '—';
}

interface Props {
  /** Motorista-only: quando fornecido, "ver rota" na bolha ATRIBUICAO_ROTA navega pra
   *  tela dedicada em vez de abrir modal (motorista não tem acesso a routePlans.list()). */
  onOpenActiveRoute?: () => void;
}

/**
 * Mini-chat gestor↔motorista (spec 07, ADR 0015). O servidor só devolve uma janela
 * curta — o histórico completo é lido daqui, do IndexedDB local, e mesclado com o que
 * o servidor tem no momento. Depois de persistir, confirma o sync (chat_sync_cursor),
 * o que autoriza o job de limpeza no backend a agir.
 */
export function ChatPage({ onOpenActiveRoute }: Props) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [conversations, setConversations] = useState<ChatConversationResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const [newBody, setNewBody] = useState('');
  const [sending, setSending] = useState(false);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [eligibleDrivers, setEligibleDrivers] = useState<DriverResponse[]>([]);
  const [pickedDriverId, setPickedDriverId] = useState('');

  // Chat em equipe (V33) — mesma UI de "iniciar conversa", separado do picker de
  // motorista porque a lista/endpoint de origem são diferentes.
  const [teamPickerOpen, setTeamPickerOpen] = useState(false);
  const [eligibleTeamMembers, setEligibleTeamMembers] = useState<TeamMemberOptionResponse[]>([]);
  const [pickedTeamMemberId, setPickedTeamMemberId] = useState('');

  const [attachOpen, setAttachOpen] = useState(false);
  const [attachableRoutes, setAttachableRoutes] = useState<RoutePlanResponse[]>([]);
  const [pickedRouteId, setPickedRouteId] = useState('');
  const [attachSending, setAttachSending] = useState(false);
  const [attachError, setAttachError] = useState('');

  const [routeDetail, setRouteDetail] = useState<RoutePlanResponse | null>(null);
  const [otherTyping, setOtherTyping] = useState(false);

  const [replyingTo, setReplyingTo] = useState<ChatMessageResponse | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [reactingId, setReactingId] = useState<string | null>(null);
  const [forwardMessage, setForwardMessage] = useState<ChatMessageResponse | null>(null);
  const [forwardTargetId, setForwardTargetId] = useState('');
  const [forwardSending, setForwardSending] = useState(false);

  const bottomRef = useRef<HTMLDivElement>(null);
  const deviceId = useMemo(() => getDeviceId(), []);
  const lastTypingPingAt = useRef(0);

  function refreshConversations() {
    coreApi.chat
      .listConversations()
      .then(setConversations)
      // Toast, não banner: uma falha aqui pode acontecer com a lista de conversas vazia
      // (sem nenhum <Card/> pra "hospedar" um banner ainda visível nesse estado).
      .catch((e: unknown) => toast.error(e instanceof Error ? e.message : t('pages.chat.toasts.falhaCarregarConversas')))
      .finally(() => setLoading(false));
  }

  useEffect(refreshConversations, []);

  // Abrir a tela de Mensagens já conta como "viu o chat" — zera o badge de não lida do
  // Home do motorista (ver lib/chatDb.ts, sem read-receipt de verdade no backend pra isso).
  useEffect(() => {
    if (user?.role === 'MOTORISTA') markChatSeenNow();
  }, [user?.role]);

  // Achado de sessão anterior: sem isso, quem não tinha nenhuma conversa aberta só via
  // uma conversa nova depois de dar F5 — a lista em si nunca era recarregada sozinha.
  useEffect(() => {
    const interval = setInterval(refreshConversations, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

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

  // Indicador de "digitando" (spec 07) — poll mais curto que o de mensagens porque um
  // sinal que já é efêmero (expira em 6s no servidor, ver TypingIndicatorService) fica
  // sempre atrasado demais em 5s; 2s é o mínimo pra parecer "ao vivo" sem virar tempo real.
  useEffect(() => {
    if (!selectedId) {
      setOtherTyping(false);
      return;
    }
    const interval = setInterval(() => {
      coreApi.chat.isOtherTyping(selectedId).then(setOtherTyping).catch(() => {});
    }, 2000);
    return () => clearInterval(interval);
  }, [selectedId]);

  // Abrir/revisitar a conversa marca as mensagens do outro lado como lidas.
  useEffect(() => {
    if (!selectedId) return;
    coreApi.chat.markAsRead(selectedId).catch(() => {});
  }, [selectedId, messages.length]);

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
    try {
      if (editingId) {
        const updated = await coreApi.chat.editMessage(selectedId, editingId, { body: newBody.trim() });
        await applyUpdatedMessage(updated);
        setEditingId(null);
        setNewBody('');
      } else {
        const sent = await coreApi.chat.sendMessage(selectedId, {
          body: newBody.trim(),
          replyToMessageId: replyingTo?.id,
        });
        await saveMessages([sent]);
        setMessages((prev) => [...prev, sent]);
        setNewBody('');
        setReplyingTo(null);
        refreshConversations();
      }
    } catch (err) {
      // Sem toast de sucesso aqui de propósito: a mensagem aparecendo na conversa já é a
      // confirmação — um toast a mais seria ruído numa tela pensada pra troca rápida.
      toast.error(
        err instanceof Error ? err.message : t(editingId ? 'pages.chat.toasts.falhaEditar' : 'pages.chat.toasts.falhaEnviar'),
      );
    } finally {
      setSending(false);
    }
  }

  /** Merge local (state + IndexedDB) depois de editar/excluir uma mensagem — mesmo padrão
   *  de upsert por id já usado no poll normal (saveMessages). */
  async function applyUpdatedMessage(updated: ChatMessageResponse) {
    setMessages((prev) => prev.map((mm) => (mm.id === updated.id ? updated : mm)));
    await saveMessages([updated]);
  }

  async function applyReactions(messageId: string, newReactions: ChatReactionResponse[]) {
    let patched: ChatMessageResponse | undefined;
    setMessages((prev) =>
      prev.map((mm) => {
        if (mm.id !== messageId) return mm;
        patched = { ...mm, reactions: newReactions };
        return patched;
      }),
    );
    if (patched) await saveMessages([patched]);
  }

  function closeMenus() {
    setOpenMenuId(null);
    setReactingId(null);
  }

  function startReply(m: ChatMessageResponse) {
    setEditingId(null);
    setReplyingTo(m);
    closeMenus();
  }

  function startEdit(m: ChatMessageResponse) {
    setReplyingTo(null);
    setEditingId(m.id!);
    setNewBody(m.body ?? '');
    closeMenus();
  }

  function cancelEdit() {
    setEditingId(null);
    setNewBody('');
  }

  async function handleDeleteMessage(m: ChatMessageResponse) {
    closeMenus();
    if (!selectedId) return;
    if (!(await confirmDialog(t('pages.chat.confirmarExcluirMensagem')))) return;
    try {
      const updated = await coreApi.chat.deleteMessage(selectedId, m.id!);
      await applyUpdatedMessage(updated);
      toast.success(t('pages.chat.toasts.mensagemExcluida'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.chat.toasts.falhaExcluirMensagem'));
    }
  }

  async function handleReact(m: ChatMessageResponse, emoji: string) {
    closeMenus();
    if (!selectedId) return;
    const minhaReacaoAtual = (m.reactions ?? []).find((r) => r.userId === user?.id);
    try {
      const updated =
        minhaReacaoAtual?.emoji === emoji
          ? await coreApi.chat.removeReaction(selectedId, m.id!)
          : await coreApi.chat.react(selectedId, m.id!, { emoji });
      await applyReactions(m.id!, updated);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.chat.toasts.falhaReagir'));
    }
  }

  function openForward(m: ChatMessageResponse) {
    closeMenus();
    setForwardTargetId('');
    setForwardMessage(m);
  }

  async function handleForwardConfirm(e: FormEvent) {
    e.preventDefault();
    if (!selectedId || !forwardMessage || !forwardTargetId) return;
    setForwardSending(true);
    try {
      const sent = await coreApi.chat.forwardMessage(selectedId, forwardMessage.id!, {
        targetConversationId: forwardTargetId,
      });
      await saveMessages([sent]);
      setForwardMessage(null);
      refreshConversations();
      toast.success(t('pages.chat.toasts.mensagemEncaminhada'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.chat.toasts.falhaEncaminhar'));
    } finally {
      setForwardSending(false);
    }
  }

  function openPicker() {
    setPickedDriverId('');
    coreApi.drivers.list().then((res) => {
      const jaTemConversa = new Set(conversations.map((c) => c.driverId));
      setEligibleDrivers(res.content.filter((d) => d.hasLogin && !jaTemConversa.has(d.id!)));
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
      toast.success(t('pages.chat.toasts.conversaIniciada'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.chat.toasts.falhaIniciar'));
    }
  }

  /** V33 — qualquer membro do time (Gestor/Despachante/Visualizador), exceto quem já tem
   *  conversa aberta com ele (mesmo raciocínio do openPicker de motorista). */
  function openTeamPicker() {
    setPickedTeamMemberId('');
    coreApi.chat
      .listTeamMembers()
      .then((res) => {
        const jaTemConversa = new Set(
          conversations.filter((c) => c.kind === 'EQUIPE').map((c) => c.otherParticipantUserId),
        );
        setEligibleTeamMembers(res.filter((m) => !jaTemConversa.has(m.userId)));
      })
      .catch(() => setEligibleTeamMembers([]));
    setTeamPickerOpen(true);
  }

  async function handleStartTeamConversation(e: FormEvent) {
    e.preventDefault();
    if (!pickedTeamMemberId) return;
    try {
      const conv = await coreApi.chat.createTeamConversation({ otherUserId: pickedTeamMemberId });
      setTeamPickerOpen(false);
      refreshConversations();
      setSelectedId(conv.id!);
      toast.success(t('pages.chat.toasts.conversaIniciada'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.chat.toasts.falhaIniciar'));
    }
  }

  function openAttach() {
    if (!selected) return;
    setPickedRouteId('');
    setAttachError('');
    coreApi.routePlans.list().then((res) => {
      // Rotas PLANEJADA/EM_ANDAMENTO — sem motorista (anexa normal), já com este mesmo
      // motorista (reenvio) ou com outro motorista (vira troca, ADR 0021, ao confirmar).
      // CONCLUIDA/CANCELADA fica de fora, não faz sentido (re)atribuir.
      setAttachableRoutes(
        res.content.filter((r) => r.status === 'PLANEJADA' || r.status === 'EM_ANDAMENTO'),
      );
    });
    setAttachOpen(true);
  }

  async function handleAttachRoute(e: FormEvent) {
    e.preventDefault();
    if (!selectedId || !pickedRouteId) return;
    setAttachSending(true);
    setAttachError('');
    try {
      const rota = attachableRoutes.find((r) => r.id === pickedRouteId);
      const ehTroca = rota?.driverId != null && rota.driverId !== selected?.driverId;
      const sent = ehTroca
        ? await coreApi.chat.trocaMotorista(selectedId, { routePlanId: pickedRouteId })
        : await coreApi.chat.sendRoutePlan(selectedId, { routePlanId: pickedRouteId });
      await saveMessages([sent]);
      setMessages((prev) => [...prev, sent]);
      setAttachOpen(false);
      refreshConversations();
      toast.success(t('pages.chat.toasts.rotaAnexada'));
    } catch (err) {
      setAttachError(err instanceof Error ? err.message : t('pages.chat.toasts.falhaAnexar'));
    } finally {
      setAttachSending(false);
    }
  }

  function openRouteDetail(routePlanId: string) {
    if (user?.role === 'MOTORISTA') {
      onOpenActiveRoute?.();
      return;
    }
    coreApi.routePlans.list().then((res) => {
      setRouteDetail(res.content.find((r) => r.id === routePlanId) ?? null);
    });
  }

  /** Gestor-only, chamado do modal de detalhe (ADR 0021) — único caminho que cancela rota
   *  já EM_ANDAMENTO; PLANEJADA cancela direto pela tela de Rotas. */
  async function handleCancelarRota() {
    if (!selectedId || !routeDetail?.id) return;
    try {
      await coreApi.chat.cancelRoutePlan(selectedId, { routePlanId: routeDetail.id });
      setRouteDetail(null);
      await loadServerWindow(selectedId);
      toast.success(t('pages.chat.toasts.rotaCancelada'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.chat.toasts.falhaCancelar'));
    }
  }

  const selected = conversations.find((c) => c.id === selectedId);

  // Abaixo de `md`, lista e conversa viram 2 painéis sobrepostos (position: absolute, cada
  // um 100% do container) que deslizam pra dentro/fora via transição CSS pura — mesma
  // técnica do WhatsApp Web em tela estreita. Em `md`+ os painéis voltam ao normal
  // (position: static, lado a lado) via classes do Tailwind — puro CSS, sem JS decidindo
  // largura de tela (nenhuma detecção de breakpoint em JS, só `md:` do Tailwind, que nunca
  // erra por causa de timing de emulação de viewport). `motion-reduce:transition-none`
  // respeita a preferência do sistema sem precisar de hook nenhum.
  return (
    <div className="relative flex h-full overflow-hidden md:gap-3 md:p-3">
      <Card
        className={cn(
          'absolute inset-3 flex flex-col overflow-hidden transition-transform duration-300 ease-in-out motion-reduce:transition-none md:static md:inset-auto md:w-72 md:shrink-0 md:translate-x-0',
          selectedId != null ? '-translate-x-full' : 'translate-x-0',
        )}
      >
        <div className="flex items-center justify-between border-b border-border p-4">
          <h2 className="font-display text-sm font-semibold text-foreground">{t('pages.chat.conversas')}</h2>
          <div className="flex items-center gap-1">
            {(user?.role === 'GESTOR_FROTA' || user?.role === 'ADMIN') && (
              <button
                type="button"
                onClick={openPicker}
                className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                title={t('pages.chat.iniciarConversa')}
              >
                <MessageCirclePlus className="size-4" />
              </button>
            )}
            {/* V33, chat em equipe — qualquer membro (Gestor/Despachante/Visualizador). */}
            {user?.role !== 'MOTORISTA' && (
              <button
                type="button"
                onClick={openTeamPicker}
                className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                title={t('pages.chat.iniciarConversaEquipe')}
              >
                <Users className="size-4" />
              </button>
            )}
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <p className="p-4 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>
          ) : conversations.length === 0 ? (
            <div className="flex flex-col items-center gap-2 p-8 text-center">
              <MessagesSquare className="size-6 text-muted-foreground/60" />
              <p className="text-xs text-muted-foreground">{t('pages.chat.nenhumaConversa')}</p>
            </div>
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
                <Avatar className="size-8 shrink-0">
                  <AvatarFallback>{iniciais(nomeDoOutroLado(c, user))}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate text-xs font-medium text-foreground">{nomeDoOutroLado(c, user)}</p>
                    {c.lastMessageAt && (
                      <span className="shrink-0 text-[10px] text-muted-foreground">
                        {formatRelativeShortBR(c.lastMessageAt)}
                      </span>
                    )}
                  </div>
                  <p className="truncate text-[11px] text-muted-foreground">
                    {c.lastMessageBody ?? (c.vehiclePlate ? c.vehiclePlate : t('pages.chat.semMensagensAinda'))}
                  </p>
                </div>
              </button>
            ))
          )}
        </div>
      </Card>

      <Card
        className={cn(
          'absolute inset-3 flex flex-col overflow-hidden transition-transform duration-300 ease-in-out motion-reduce:transition-none md:static md:inset-auto md:flex-1 md:translate-x-0',
          selectedId != null ? 'translate-x-0' : 'translate-x-full',
        )}
      >
        {!selected ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-2 text-center">
            <MessagesSquare className="size-6 text-muted-foreground/60" />
            <p className="text-xs text-muted-foreground">{t('pages.chat.selecioneOuInicie')}</p>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-2.5 border-b border-border p-4">
              <button
                type="button"
                onClick={() => setSelectedId(null)}
                aria-label={t('pages.chat.voltar')}
                className="flex size-8 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground md:hidden"
              >
                <ArrowLeft className="size-4" />
              </button>
              <Avatar className="size-8">
                <AvatarFallback>{iniciais(nomeDoOutroLado(selected, user))}</AvatarFallback>
              </Avatar>
              <p className="text-sm font-medium text-foreground">{nomeDoOutroLado(selected, user)}</p>
              {selected.vehiclePlate && (
                <span className="text-[11px] text-muted-foreground">· {selected.vehiclePlate}</span>
              )}
              {selected.kind === 'EQUIPE' && selected.otherParticipantRole && (
                <span className="text-[11px] text-muted-foreground">· {selected.otherParticipantRole}</span>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-4" onClick={closeMenus}>
            <div className="mx-auto flex max-w-3xl flex-col space-y-2">
              {messages.length === 0 && (
                <p className="pt-8 text-center text-xs text-muted-foreground">{t('pages.chat.nenhumaMensagem')}</p>
              )}
              {messages.map((m) => {
                const mine = m.senderUserId === user?.id;
                const ehRota =
                  m.messageType === 'ATRIBUICAO_ROTA' ||
                  m.messageType === 'CANCELAMENTO_ROTA' ||
                  m.messageType === 'TROCA_MOTORISTA' ||
                  m.messageType === 'SOLICITACAO_CANCELAMENTO' ||
                  m.messageType === 'SOLICITACAO_TROCA_MOTORISTA';
                if (ehRota) {
                  // Gestor vê botão de ação nas solicitações do motorista (nunca nas suas
                  // próprias mensagens de ação, "mine" já É a ação) — ADR 0021: motorista só
                  // solicita, quem decide é o gestor, abrindo o detalhe da rota pra agir.
                  const ehSolicitacao =
                    m.messageType === 'SOLICITACAO_CANCELAMENTO' || m.messageType === 'SOLICITACAO_TROCA_MOTORISTA';
                  return (
                    <div key={m.id} className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
                      <div className="max-w-[75%] rounded-lg border border-border bg-secondary/60 px-3 py-2.5 text-xs">
                        <p className="flex items-center gap-1.5 font-medium text-foreground">
                          <RouteIcon className="size-3.5 text-primary" /> {m.body}
                        </p>
                        {m.routePlanId && (!ehSolicitacao || user?.role !== 'MOTORISTA') && (
                          <button
                            type="button"
                            onClick={() => openRouteDetail(m.routePlanId!)}
                            className="mt-1.5 text-[11px] font-medium text-primary hover:underline"
                          >
                            {ehSolicitacao ? t('pages.chat.verRota') : t('pages.chat.verDetalhes')}
                          </button>
                        )}
                        <p className="mt-1 text-[10px] text-muted-foreground">
                          {m.sentAt ? formatTimeBR(m.sentAt) : ''}
                        </p>
                      </div>
                    </div>
                  );
                }
                const apagada = m.deletedAt != null;
                // Prazo pra editar/excluir a própria mensagem (pedido do Guilherme) — o
                // backend também recusa depois desses minutos, isso aqui só evita mostrar
                // um botão que ia dar erro. Excluir tem folga maior que editar de propósito.
                const minutosDesdeEnvio = m.sentAt ? (Date.now() - new Date(m.sentAt).getTime()) / 60000 : Infinity;
                const podeEditar = mine && !apagada && m.stillOnServer && minutosDesdeEnvio <= 20;
                const podeExcluir = mine && !apagada && m.stillOnServer && minutosDesdeEnvio <= 35;
                const podeEncaminhar = !apagada && conversations.some((c) => c.id !== selectedId);
                const menuAberto = openMenuId === m.id;
                const paletaAberta = reactingId === m.id;
                const minhaReacao = (m.reactions ?? []).find((r) => r.userId === user?.id);
                const contagemPorEmoji = (m.reactions ?? []).reduce<Record<string, number>>((acc, r) => {
                  if (!r.emoji) return acc;
                  acc[r.emoji] = (acc[r.emoji] ?? 0) + 1;
                  return acc;
                }, {});

                const menu = (menuAberto || paletaAberta) && (
                  <div
                    className={cn(
                      'absolute top-7 z-10 min-w-[9rem] rounded-md border border-border bg-popover p-1 text-xs shadow-lg',
                      mine ? 'right-0' : 'left-0',
                    )}
                  >
                    {paletaAberta ? (
                      <div className="flex items-center gap-1 p-1">
                        {REACTION_EMOJIS.map((emoji) => (
                          <button
                            key={emoji}
                            type="button"
                            onClick={() => handleReact(m, emoji)}
                            className="flex size-7 items-center justify-center rounded-md text-base hover:bg-secondary"
                          >
                            {emoji}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <div className="flex flex-col">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setReactingId(m.id!);
                          }}
                          className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left hover:bg-secondary"
                        >
                          <Smile className="size-3.5" /> {t('pages.chat.reagir')}
                        </button>
                        <button
                          type="button"
                          onClick={() => startReply(m)}
                          className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left hover:bg-secondary"
                        >
                          <ReplyIcon className="size-3.5" /> {t('pages.chat.responder')}
                        </button>
                        {podeEncaminhar && (
                          <button
                            type="button"
                            onClick={() => openForward(m)}
                            className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left hover:bg-secondary"
                          >
                            <ForwardIcon className="size-3.5" /> {t('pages.chat.encaminhar')}
                          </button>
                        )}
                        {podeEditar && (
                          <button
                            type="button"
                            onClick={() => startEdit(m)}
                            className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left hover:bg-secondary"
                          >
                            <Pencil className="size-3.5" /> {t('pages.chat.editar')}
                          </button>
                        )}
                        {podeExcluir && (
                          <button
                            type="button"
                            onClick={() => handleDeleteMessage(m)}
                            className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-status-danger hover:bg-secondary"
                          >
                            <Trash2 className="size-3.5" /> {t('pages.chat.excluir')}
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                );

                return (
                  <div key={m.id} className={cn('group flex items-start gap-1', mine ? 'justify-end' : 'justify-start')}>
                    {!mine && !apagada && (
                      <div className="relative shrink-0">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            menuAberto ? closeMenus() : setOpenMenuId(m.id!);
                          }}
                          className="mt-1 flex size-6 items-center justify-center rounded-md text-muted-foreground opacity-100 hover:bg-secondary md:opacity-0 md:group-hover:opacity-100"
                        >
                          <MoreVertical className="size-3.5" />
                        </button>
                        {menu}
                      </div>
                    )}
                    <div className={cn('flex flex-col gap-1', mine ? 'items-end' : 'items-start')}>
                      <div
                        className={cn(
                          'max-w-[70%] rounded-lg px-3 py-2 text-xs',
                          apagada
                            ? 'border border-dashed border-border text-muted-foreground italic'
                            : mine
                              ? 'bg-primary text-primary-foreground'
                              : 'bg-muted text-foreground',
                        )}
                      >
                        {m.forwardedFromMessageId && !apagada && (
                          <p
                            className={cn(
                              'mb-1 flex items-center gap-1 text-[10px]',
                              mine ? 'text-primary-foreground/70' : 'text-muted-foreground',
                            )}
                          >
                            <ForwardIcon className="size-3" /> {t('pages.chat.encaminhadaLabel')}
                          </p>
                        )}
                        {m.replyToMessageId && !apagada && (
                          <p
                            className={cn(
                              'mb-1 truncate border-l-2 pl-1.5 text-[11px] opacity-80',
                              mine ? 'border-primary-foreground/40' : 'border-foreground/30',
                            )}
                          >
                            {m.replyToBody}
                          </p>
                        )}
                        {apagada ? (
                          <p>{t('pages.chat.mensagemApagada')}</p>
                        ) : (
                          <p>{m.body}</p>
                        )}
                        <p
                          className={cn(
                            'mt-1 flex items-center justify-end gap-1 text-[10px]',
                            mine ? 'text-primary-foreground/70' : 'text-muted-foreground',
                          )}
                        >
                          {m.editedAt && !apagada && <span>{t('pages.chat.editadoSufixo')}</span>}
                          {m.sentAt ? formatTimeBR(m.sentAt) : ''}
                          {mine && (m.lidoEm ? <CheckCheck className="size-3" /> : <Check className="size-3" />)}
                        </p>
                      </div>
                      {Object.keys(contagemPorEmoji).length > 0 && (
                        <div className={cn('-mt-2.5 flex flex-wrap gap-0.5 px-1', mine && 'justify-end')}>
                          {Object.entries(contagemPorEmoji).map(([emoji, n]) => (
                            <button
                              key={emoji}
                              type="button"
                              onClick={() => handleReact(m, emoji)}
                              className={cn(
                                'flex items-center gap-0.5 rounded-full border bg-card px-1 py-0.5 leading-none shadow-sm',
                                minhaReacao?.emoji === emoji ? 'border-primary' : 'border-border',
                              )}
                            >
                              <span className="text-[11px] leading-none">{emoji}</span>
                              {n > 1 && <span className="text-[9px] leading-none text-muted-foreground">{n}</span>}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                    {mine && !apagada && (
                      <div className="relative shrink-0">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            menuAberto ? closeMenus() : setOpenMenuId(m.id!);
                          }}
                          className="mt-1 flex size-6 items-center justify-center rounded-md text-muted-foreground opacity-100 hover:bg-secondary md:opacity-0 md:group-hover:opacity-100"
                        >
                          <MoreVertical className="size-3.5" />
                        </button>
                        {menu}
                      </div>
                    )}
                  </div>
                );
              })}
              {otherTyping && (
                <div className="flex justify-start">
                  <div className="rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">{t('pages.chat.digitando')}</div>
                </div>
              )}
              <div ref={bottomRef} />
            </div>
            </div>

            {replyingTo && (
              <div className="border-t border-border bg-secondary/40 px-3 py-2 text-xs">
                <div className="mx-auto flex max-w-3xl items-center gap-2">
                  <ReplyIcon className="size-3.5 shrink-0 text-muted-foreground" />
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-foreground">
                      {t('pages.chat.respondendoA', {
                        nome: replyingTo.senderUserId === user?.id ? (user?.email ?? '') : nomeDoOutroLado(selected, user),
                      })}
                    </p>
                    <p className="truncate text-muted-foreground">{replyingTo.body}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setReplyingTo(null)}
                    className="flex size-6 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              </div>
            )}
            {editingId && (
              <div className="border-t border-border bg-secondary/40 px-3 py-2 text-xs">
                <div className="mx-auto flex max-w-3xl items-center gap-2">
                  <Pencil className="size-3.5 shrink-0 text-muted-foreground" />
                  <p className="flex-1 font-medium text-foreground">{t('pages.chat.editar')}</p>
                  <button
                    type="button"
                    onClick={cancelEdit}
                    className="flex size-6 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              </div>
            )}
            <div className="border-t border-border p-3">
            <form onSubmit={handleSend} className="mx-auto flex max-w-3xl items-center gap-2">
              {user?.role !== 'MOTORISTA' && selected.kind !== 'EQUIPE' && !editingId && (
                <button
                  type="button"
                  onClick={openAttach}
                  title={t('pages.chat.anexarRota')}
                  className="flex size-9 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                >
                  <RouteIcon className="size-4" />
                </button>
              )}
              <Input
                placeholder={t('pages.chat.escrevaMensagem')}
                value={newBody}
                onChange={(e) => {
                  setNewBody(e.target.value);
                  const now = Date.now();
                  if (selectedId && now - lastTypingPingAt.current > 2000) {
                    lastTypingPingAt.current = now;
                    coreApi.chat.typing(selectedId).catch(() => {});
                  }
                }}
                className="flex-1"
              />
              <Button type="submit" size="sm" disabled={!newBody.trim() || sending}>
                <Send className="size-4" />
              </Button>
            </form>
            </div>
          </>
        )}
      </Card>

      <Modal open={pickerOpen} onClose={() => setPickerOpen(false)} title={t('pages.chat.iniciarConversa')}>
        {eligibleDrivers.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t('pages.chat.nenhumMotoristaDisponivel')}</p>
        ) : (
          <form onSubmit={handleStartConversation} className="space-y-3">
            <Select value={pickedDriverId} onChange={(e) => setPickedDriverId(e.target.value)}>
              <option value="">{t('pages.chat.selecioneMotorista')}</option>
              {eligibleDrivers.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => setPickerOpen(false)}>
                {t('pages.chat.cancelar')}
              </Button>
              <Button type="submit" size="sm" disabled={!pickedDriverId}>
                {t('pages.chat.iniciar')}
              </Button>
            </div>
          </form>
        )}
      </Modal>

      <Modal open={teamPickerOpen} onClose={() => setTeamPickerOpen(false)} title={t('pages.chat.iniciarConversaEquipe')}>
        {eligibleTeamMembers.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t('pages.chat.nenhumMembroDisponivel')}</p>
        ) : (
          <form onSubmit={handleStartTeamConversation} className="space-y-3">
            <Select value={pickedTeamMemberId} onChange={(e) => setPickedTeamMemberId(e.target.value)}>
              <option value="">{t('pages.chat.selecioneMembro')}</option>
              {eligibleTeamMembers.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.email} — {m.role}
                </option>
              ))}
            </Select>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => setTeamPickerOpen(false)}>
                {t('pages.chat.cancelar')}
              </Button>
              <Button type="submit" size="sm" disabled={!pickedTeamMemberId}>
                {t('pages.chat.iniciar')}
              </Button>
            </div>
          </form>
        )}
      </Modal>

      <Modal open={attachOpen} onClose={() => setAttachOpen(false)} title={t('pages.chat.anexarRota')}>
        {attachableRoutes.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t('pages.chat.nenhumaRotaDisponivel')}</p>
        ) : (
          <form onSubmit={handleAttachRoute} className="space-y-3">
            <Select value={pickedRouteId} onChange={(e) => setPickedRouteId(e.target.value)}>
              <option value="">{t('pages.chat.selecioneRota')}</option>
              {attachableRoutes.map((r) => (
                <option key={r.id} value={r.id}>
                  {t('pages.chat.paradaContagem', { n: r.stops?.length ?? 0 })}
                  {r.driverId && r.driverId !== selected?.driverId
                    ? ` · ${t('pages.chat.trocaDe', { motorista: r.driverName ?? '' })}`
                    : r.driverId
                      ? ` ${t('pages.chat.jaDesignadaMotorista')}`
                      : ''}
                </option>
              ))}
            </Select>
            {attachError && <p className="text-xs text-status-danger">{attachError}</p>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => setAttachOpen(false)}>
                {t('pages.chat.cancelar')}
              </Button>
              <Button type="submit" size="sm" disabled={!pickedRouteId || attachSending}>
                {t('pages.chat.anexar')}
              </Button>
            </div>
          </form>
        )}
      </Modal>

      <Modal open={!!routeDetail} onClose={() => setRouteDetail(null)} title={t('pages.chat.paradasDaRota')}>
        {routeDetail && (
          <div className="space-y-3">
            <ol className="space-y-2">
              {(routeDetail.stops ?? []).map((s, i) => (
                <li key={s.id} className="flex items-start gap-2.5 text-xs">
                  <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-secondary text-[10px] font-semibold text-muted-foreground">
                    {i + 1}
                  </span>
                  <div>
                    <p className="flex items-center gap-1 font-medium text-foreground">
                      <MapPin className="size-3" /> {s.label} <span className="text-muted-foreground">· {s.tipo}</span>
                    </p>
                  </div>
                </li>
              ))}
            </ol>
            {user?.role !== 'MOTORISTA' && routeDetail.status === 'EM_ANDAMENTO' && (
              <Button type="button" variant="secondary" size="sm" onClick={handleCancelarRota}>
                {t('pages.chat.cancelarRota')}
              </Button>
            )}
          </div>
        )}
      </Modal>

      <Modal open={!!forwardMessage} onClose={() => setForwardMessage(null)} title={t('pages.chat.encaminharTitulo')}>
        {(() => {
          const outrasConversas = conversations.filter((c) => c.id !== selectedId);
          return outrasConversas.length === 0 ? (
            <p className="text-xs text-muted-foreground">{t('pages.chat.nenhumaOutraConversa')}</p>
          ) : (
            <form onSubmit={handleForwardConfirm} className="space-y-3">
              <p className="truncate rounded-md bg-secondary/60 px-3 py-2 text-xs text-muted-foreground">
                {forwardMessage?.body}
              </p>
              <Select value={forwardTargetId} onChange={(e) => setForwardTargetId(e.target.value)}>
                <option value="">{t('pages.chat.selecioneConversaDestino')}</option>
                {outrasConversas.map((c) => (
                  <option key={c.id} value={c.id}>
                    {nomeDoOutroLado(c, user)}
                  </option>
                ))}
              </Select>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="ghost" size="sm" onClick={() => setForwardMessage(null)}>
                  {t('pages.chat.cancelar')}
                </Button>
                <Button type="submit" size="sm" disabled={!forwardTargetId || forwardSending}>
                  {t('pages.chat.encaminhar')}
                </Button>
              </div>
            </form>
          );
        })()}
      </Modal>
    </div>
  );
}
