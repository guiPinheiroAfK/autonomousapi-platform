import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Button, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { coreApi, type RoutePlanResponse, type RouteStopResponse } from '../api/client';

/**
 * Rota do dia designada ao motorista (spec 11, gap de prioridade 1 do levantamento
 * 2026-08-25: até aqui o app não mostrava rota nenhuma — só existia no painel web). Mesmo
 * comportamento de `DriverRoutePage.tsx` (web): paradas na ordem definida pelo gestor, um
 * botão por parada (marcar concluída), sem reordenar nem editar. TRANSFER (trajeto único)
 * renderiza um cartão único em vez da lista numerada.
 */
export function MinhaRotaScreen() {
  const [route, setRoute] = useState<RoutePlanResponse | null | undefined>(undefined);
  const [completingId, setCompletingId] = useState<string | null>(null);
  const [notifyingId, setNotifyingId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setRoute(await coreApi.routePlans.active());
    } catch (e) {
      Alert.alert('Erro ao carregar rota', e instanceof Error ? e.message : 'Erro desconhecido');
      setRoute(null);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function concluir(stopId: string) {
    setCompletingId(stopId);
    try {
      await coreApi.routePlans.completeStop(stopId);
      await refresh();
    } catch (e) {
      Alert.alert('Falha ao concluir parada', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setCompletingId(null);
    }
  }

  /** Spec 14: disparo manual, sob demanda — cobre atraso, mudança de ponto de encontro
   *  combinada por telefone, ou qualquer coisa fora do timing dos 3 gatilhos automáticos.
   *  Fire-and-forget de verdade: sem confirmação de entrega, só um toast de "enviado". */
  async function avisarPassageiro(stopId: string) {
    setNotifyingId(stopId);
    try {
      await coreApi.routePlans.notifyPassenger(stopId);
      Alert.alert('Aviso enviado', 'O passageiro foi notificado.');
    } catch (e) {
      Alert.alert('Falha ao avisar passageiro', e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setNotifyingId(null);
    }
  }

  /**
   * Motorista nunca cancela/troca sozinho — só solicita, pelo chat (ADR 0021); o gestor
   * decide. A conversa é resolvida como a primeira do motorista (mesmo padrão de
   * ChatScreen.tsx — hoje só existe uma conversa por motorista, com o próprio gestor).
   */
  async function solicitar(tipo: 'cancelamento' | 'troca') {
    const titulo = tipo === 'cancelamento' ? 'Solicitar cancelamento' : 'Solicitar troca de motorista';
    const mensagem =
      tipo === 'cancelamento'
        ? 'Isso avisa o gestor que você quer cancelar a rota. Ele decide — a rota continua ativa até lá.'
        : 'Isso avisa o gestor que você quer passar a rota pra outra pessoa. Ele decide — a rota continua ativa até lá.';
    Alert.alert(titulo, mensagem, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Solicitar',
        onPress: async () => {
          try {
            const conversas = await coreApi.chat.listConversations();
            const conversationId = conversas[0]?.id;
            if (!conversationId) {
              Alert.alert('Sem conversa com o gestor', 'Abra a aba Mensagens pra iniciar uma conversa primeiro.');
              return;
            }
            if (tipo === 'cancelamento') await coreApi.chat.solicitarCancelamento(conversationId);
            else await coreApi.chat.solicitarTroca(conversationId);
            Alert.alert('Solicitação enviada', 'O gestor foi avisado.');
          } catch (e) {
            Alert.alert('Falha ao enviar solicitação', e instanceof Error ? e.message : 'Erro desconhecido');
          }
        },
      },
    ]);
  }

  if (route === undefined) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  if (route === null) {
    return (
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Minha rota</Text>
        <Text style={styles.vazio}>Nenhuma rota atribuída no momento.</Text>
        <Button title="Atualizar" onPress={refresh} />
      </ScrollView>
    );
  }

  if (route.categoria === 'TRANSFER') {
    const origem = route.stops[0] as RouteStopResponse | undefined;
    const destino = route.stops[1] as RouteStopResponse | undefined;
    const proxima = origem && !origem.concluidaEm ? origem : destino && !destino.concluidaEm ? destino : null;
    return (
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Transfer</Text>
        <ParadaLinha rotulo="Origem" stop={origem} />
        <ParadaLinha rotulo="Destino" stop={destino} />
        {route.valor != null && <Text style={styles.info}>Valor combinado: R$ {route.valor.toFixed(2)}</Text>}
        {proxima && (
          <Button
            title={completingId === proxima.id ? 'Marcando...' : proxima === origem ? 'Iniciar' : 'Concluir'}
            onPress={() => concluir(proxima.id)}
            disabled={completingId === proxima.id}
          />
        )}
        {proxima?.passengerId && (
          <TouchableOpacity
            style={styles.botaoAvisar}
            onPress={() => avisarPassageiro(proxima.id)}
            disabled={notifyingId === proxima.id}
          >
            <Text style={styles.botaoAvisarTexto}>
              {notifyingId === proxima.id ? 'Enviando...' : 'Avisar passageiro'}
            </Text>
          </TouchableOpacity>
        )}
        <SolicitacoesFooter onSolicitar={solicitar} onAtualizar={refresh} />
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Minha rota</Text>
      <Text style={styles.subtitle}>{route.stops.length} parada(s)</Text>
      {route.stops.map((s, i) => {
        const concluida = !!s.concluidaEm;
        return (
          <View key={s.id} style={styles.paradaCard}>
            <View style={[styles.paradaNumero, concluida && styles.paradaNumeroConcluida]}>
              <Text style={styles.paradaNumeroTexto}>{concluida ? '✓' : i + 1}</Text>
            </View>
            <View style={styles.paradaInfo}>
              <Text style={styles.paradaLabel} numberOfLines={2}>
                {s.label}
              </Text>
              <Text style={styles.paradaTipo}>{s.tipo === 'COLETA' ? 'Coleta' : 'Entrega'}</Text>
            </View>
            <View style={styles.paradaAcoes}>
              {!concluida && (
                <TouchableOpacity
                  style={styles.botaoConcluir}
                  onPress={() => concluir(s.id)}
                  disabled={completingId === s.id}
                >
                  <Text style={styles.botaoConcluirTexto}>{completingId === s.id ? 'Marcando...' : 'Concluir'}</Text>
                </TouchableOpacity>
              )}
              {s.passengerId && (
                <TouchableOpacity
                  style={styles.botaoAvisarPequeno}
                  onPress={() => avisarPassageiro(s.id)}
                  disabled={notifyingId === s.id}
                >
                  <Text style={styles.botaoAvisarTexto}>{notifyingId === s.id ? 'Enviando...' : 'Avisar'}</Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        );
      })}
      <SolicitacoesFooter onSolicitar={solicitar} onAtualizar={refresh} />
    </ScrollView>
  );
}

function SolicitacoesFooter({
  onSolicitar,
  onAtualizar,
}: {
  onSolicitar: (tipo: 'cancelamento' | 'troca') => void;
  onAtualizar: () => void;
}) {
  return (
    <>
      <View style={styles.spacer} />
      <View style={styles.solicitacoesLinha}>
        <TouchableOpacity style={styles.botaoSolicitar} onPress={() => onSolicitar('cancelamento')}>
          <Text style={styles.botaoSolicitarTexto}>Solicitar cancelamento</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.botaoSolicitar} onPress={() => onSolicitar('troca')}>
          <Text style={styles.botaoSolicitarTexto}>Solicitar troca</Text>
        </TouchableOpacity>
      </View>
      <View style={styles.spacer} />
      <Button title="Atualizar" onPress={onAtualizar} />
    </>
  );
}

function ParadaLinha({ rotulo, stop }: { rotulo: string; stop: RouteStopResponse | undefined }) {
  if (!stop) return null;
  const concluida = !!stop.concluidaEm;
  return (
    <View style={styles.transferLinha}>
      <View style={[styles.paradaNumero, concluida && styles.paradaNumeroConcluida]}>
        <Text style={styles.paradaNumeroTexto}>{concluida ? '✓' : '•'}</Text>
      </View>
      <View>
        <Text style={styles.transferRotulo}>{rotulo}</Text>
        <Text style={styles.paradaLabel}>{stop.label}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { flexGrow: 1, padding: 24, gap: 12 },
  title: { fontSize: 28, fontWeight: '700' },
  subtitle: { fontSize: 14, color: '#555' },
  vazio: { fontSize: 15, color: '#666', marginVertical: 12 },
  info: { fontSize: 15, color: '#333' },
  spacer: { height: 12 },
  solicitacoesLinha: { flexDirection: 'row', gap: 8 },
  botaoSolicitar: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  botaoSolicitarTexto: { fontSize: 12, color: '#555', fontWeight: '600' },
  paradaCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 8,
    padding: 12,
  },
  paradaNumero: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#eef1f5',
    alignItems: 'center',
    justifyContent: 'center',
  },
  paradaNumeroConcluida: { backgroundColor: '#d7f0dd' },
  paradaNumeroTexto: { fontSize: 12, fontWeight: '700', color: '#1f3a5f' },
  paradaInfo: { flex: 1 },
  paradaLabel: { fontSize: 14, color: '#111' },
  paradaTipo: { fontSize: 12, color: '#888', marginTop: 2 },
  botaoConcluir: { backgroundColor: '#1f3a5f', borderRadius: 6, paddingVertical: 8, paddingHorizontal: 12 },
  botaoConcluirTexto: { color: '#fff', fontSize: 12, fontWeight: '600' },
  paradaAcoes: { gap: 6, alignItems: 'flex-end' },
  botaoAvisarPequeno: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 6,
    paddingVertical: 6,
    paddingHorizontal: 10,
  },
  botaoAvisar: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  botaoAvisarTexto: { fontSize: 12, color: '#555', fontWeight: '600' },
  transferLinha: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  transferRotulo: { fontSize: 11, color: '#888', textTransform: 'uppercase' },
});
