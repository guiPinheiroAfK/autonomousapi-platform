import { registerRootComponent } from 'expo';

import App from './App';

// Entry monorepo-safe: registra o App explicitamente em vez de depender do
// expo/AppEntry (que resolve caminho errado em workspace hoisteado).
registerRootComponent(App);
