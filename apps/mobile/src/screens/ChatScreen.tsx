import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import { coreApi, type ChatConversationResponse, type ChatMessageResponse } from '../api/client';

interface Props {
  userId: string;
}

const POLL_INTERVAL_MS = 5000;

/**
 * Chat do motorista (spec 07 item 7, ADR 0015). Sem confirmação de sync-cursor aqui —
 * isso é gestor-only, o app do motorista não precisa (nem deveria) reter histórico
 * longo local; só mostra a janela que o servidor está guardando no momento.
 */
export function ChatScreen({ userId }: Props) {
  const [loading, setLoading] = useState(true);
  const [conversation, setConversation] = useState<ChatConversationResponse | null>(null);
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [body, setBody] = useState('');
  const [sending, setSending] = useState(false);
  const listRef = useRef<FlatList<ChatMessageResponse>>(null);

  useEffect(() => {
    coreApi.chat
      .listConversations()
      .then((all) => setConversation(all[0] ?? null))
      .catch((e: unknown) => Alert.alert('Erro ao carregar', e instanceof Error ? e.message : 'Erro desconhecido'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!conversation) return;
    loadMessages(conversation.id);
    const interval = setInterval(() => loadMessages(conversation.id), POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [conversation]);

  function loadMessages(conversationId: string) {
    coreApi.chat
      .listMessages(conversationId)
      .then(setMessages)
      .catch(() => {
        // Poll silencioso — erro passageiro de rede não deve travar a tela.
      });
  }

  async function handleSend() {
    if (!conversation || !body.trim()) return;
    setSending(true);
    try {
      const sent = await coreApi.chat.sendMessage(conversation.id, body.trim());
      setMessages((prev) => [...prev, sent]);
      setBody('');
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 100);
    } catch (e) {
      Alert.alert('Falha ao enviar', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setSending(false);
    }
  }

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  if (!conversation) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>Nenhuma conversa ainda.</Text>
        <Text style={styles.emptySubtext}>Seu gestor pode iniciar uma conversa com você por aqui.</Text>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={80}
    >
      <FlatList
        ref={listRef}
        data={messages}
        keyExtractor={(m) => m.id}
        contentContainerStyle={styles.messages}
        renderItem={({ item }) => {
          const mine = item.senderUserId === userId;
          return (
            <View style={[styles.bubbleRow, mine ? styles.bubbleRowMine : styles.bubbleRowTheirs]}>
              <View style={[styles.bubble, mine ? styles.bubbleMine : styles.bubbleTheirs]}>
                <Text style={mine ? styles.bubbleTextMine : styles.bubbleTextTheirs}>{item.body}</Text>
                <Text style={mine ? styles.bubbleTimeMine : styles.bubbleTimeTheirs}>
                  {new Date(item.sentAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                </Text>
              </View>
            </View>
          );
        }}
        onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: false })}
      />
      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          placeholder="Escreva uma mensagem..."
          value={body}
          onChangeText={setBody}
        />
        <TouchableOpacity
          style={[styles.sendButton, (!body.trim() || sending) && styles.sendButtonDisabled]}
          onPress={handleSend}
          disabled={!body.trim() || sending}
        >
          <Text style={styles.sendButtonText}>Enviar</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24, gap: 6 },
  emptyText: { fontSize: 16, fontWeight: '600' },
  emptySubtext: { fontSize: 13, color: '#888', textAlign: 'center' },
  messages: { padding: 16, gap: 8 },
  bubbleRow: { flexDirection: 'row' },
  bubbleRowMine: { justifyContent: 'flex-end' },
  bubbleRowTheirs: { justifyContent: 'flex-start' },
  bubble: { maxWidth: '75%', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 8 },
  bubbleMine: { backgroundColor: '#1f3a5f' },
  bubbleTheirs: { backgroundColor: '#eee' },
  bubbleTextMine: { color: '#fff', fontSize: 14 },
  bubbleTextTheirs: { color: '#000', fontSize: 14 },
  bubbleTimeMine: { fontSize: 10, color: 'rgba(255,255,255,0.6)', marginTop: 4 },
  bubbleTimeTheirs: { fontSize: 10, color: '#888', marginTop: 4 },
  inputRow: { flexDirection: 'row', gap: 8, padding: 12, borderTopWidth: 1, borderTopColor: '#eee' },
  input: { flex: 1, borderWidth: 1, borderColor: '#ccc', borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8 },
  sendButton: { backgroundColor: '#1f3a5f', borderRadius: 20, paddingHorizontal: 16, justifyContent: 'center' },
  sendButtonDisabled: { opacity: 0.5 },
  sendButtonText: { color: '#fff', fontWeight: '600' },
});
