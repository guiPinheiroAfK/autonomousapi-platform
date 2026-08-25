#!/usr/bin/env node
/**
 * react-native (SDK 51, pinado em 0.74.5 — ver fix-mobile-duplicate-react-native.js)
 * só existe fisicamente em apps/mobile/node_modules; nada o instala na raiz do
 * workspace apesar do `overrides` do package.json da raiz declarar a mesma versão
 * globalmente. Vários pacotes só do mobile acabam hoisted pro node_modules da raiz
 * pelo npm workspaces mesmo assim (jest-expo, expo, ...) e cada um que precisa de
 * `react-native/...` internamente falha, porque a resolução do Node nunca desce de
 * volta pra dentro de apps/mobile/node_modules a partir da raiz.
 *
 * Uma única junction na raiz resolve de uma vez pra qualquer pacote hoisted, em vez
 * de remendar caso a caso — sem duplicar o pacote em disco (junction, não symlink:
 * não precisa de privilégio admin no Windows).
 */
const fs = require('fs');
const path = require('path');

const target = path.join(__dirname, '..', 'apps', 'mobile', 'node_modules', 'react-native');
const link = path.join(__dirname, '..', 'node_modules', 'react-native');

if (fs.existsSync(target) && !fs.existsSync(link)) {
  fs.symlinkSync(target, link, 'junction');
  console.log('[fix-mobile-jest-expo-react-native] link criado: ' + link + ' -> ' + target);
}
