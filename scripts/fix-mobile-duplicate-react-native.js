#!/usr/bin/env node
/**
 * Workaround: @expo/vector-icons@14.1.0 (transitive dep of expo@51 under this monorepo's
 * npm workspace hoisting) pulls a nested react-native@0.87.0 that conflicts with the
 * SDK 51-pinned react-native@0.74.5 our mobile app uses. `npm overrides` in package.json
 * doesn't fully stop npm from installing this nested duplicate (tested: still recreated
 * even after a clean reinstall with the override in place) — root cause looks like an
 * npm limitation resolving deeply-nested transitive overrides, not something fixable from
 * our package.json alone. Deleting the stray nested copy after install is the reliable fix:
 * Metro's module resolution walks up from the requiring file and finds the nearest
 * node_modules, so once this duplicate is gone it correctly falls back to the single
 * 0.74.5 copy at apps/mobile/node_modules.
 *
 * Safe to delete: this file has never been imported by anything we ship — @expo/vector-icons
 * itself isn't even used in our code, its own peerDependency on react-native is a wildcard.
 */
const fs = require('fs');
const path = require('path');

const target = path.join(__dirname, '..', 'node_modules', 'expo', 'node_modules', 'react-native');
if (fs.existsSync(target)) {
  fs.rmSync(target, { recursive: true, force: true });
  console.log('[fix-mobile-duplicate-react-native] removido node_modules/expo/node_modules/react-native duplicado');
}
