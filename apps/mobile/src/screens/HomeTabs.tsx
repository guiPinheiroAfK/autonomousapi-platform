import { useState } from 'react';
import { SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { ChargingStationsScreen } from './ChargingStationsScreen';
import { ChatScreen } from './ChatScreen';
import { HomeScreen } from './HomeScreen';
import { RouteScreen } from './RouteScreen';
import { TripScreen } from './TripScreen';

interface Props {
  userId: string;
  onLogout: () => void;
}

type Tab = 'inicio' | 'viagem' | 'chat' | 'recarga' | 'rota';

const TABS: { key: Tab; label: string }[] = [
  { key: 'inicio', label: 'Início' },
  { key: 'viagem', label: 'Viagem' },
  { key: 'chat', label: 'Mensagens' },
  { key: 'recarga', label: 'Recarga' },
  { key: 'rota', label: 'Rota' },
];

/**
 * Navegação por abas simples (sem lib de navegação — mesmo espírito minimalista do
 * resto do app mobile, que já não usa nenhuma dependência de navegação nem de ícones).
 */
export function HomeTabs({ userId, onLogout }: Props) {
  const [tab, setTab] = useState<Tab>('inicio');

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.screen}>
        {tab === 'inicio' && <HomeScreen onLogout={onLogout} />}
        {tab === 'viagem' && <TripScreen onLogout={onLogout} />}
        {tab === 'chat' && <ChatScreen userId={userId} />}
        {tab === 'recarga' && <ChargingStationsScreen />}
        {tab === 'rota' && <RouteScreen />}
      </View>
      <View style={styles.tabBar}>
        {TABS.map((t) => (
          <TouchableOpacity key={t.key} style={styles.tabButton} onPress={() => setTab(t.key)}>
            <Text style={t.key === tab ? styles.tabLabelActive : styles.tabLabel}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  screen: { flex: 1 },
  tabBar: {
    flexDirection: 'row',
    borderTopWidth: 1,
    borderTopColor: '#ddd',
    backgroundColor: '#fff',
  },
  tabButton: { flex: 1, alignItems: 'center', paddingVertical: 12 },
  tabLabel: { fontSize: 13, color: '#888' },
  tabLabelActive: { fontSize: 13, color: '#1f3a5f', fontWeight: '700' },
});
