import { homedir } from 'node:os';
import { join } from 'node:path';

// Test-only overrides. Module state (per worker thread) rather than
// process.env: vitest threads share process.env, so env-based isolation
// races across concurrently-running test files.
let configDirOverride: string | undefined;
let legacyRcOverride: string | undefined;

/** Override the config dir (tests). Pass undefined to reset. */
export function setConfigDirForTesting(dir?: string): void {
  configDirOverride = dir;
}

/** Override the legacy ~/.keltarc path (tests). Pass undefined to reset. */
export function setLegacyRcForTesting(path?: string): void {
  legacyRcOverride = path;
}

/** Config directory (`~/.kelta`), overridable via KELTA_CONFIG_DIR. */
export function configDir(): string {
  return configDirOverride ?? process.env.KELTA_CONFIG_DIR ?? join(homedir(), '.kelta');
}

export function configPath(): string {
  return join(configDir(), 'config.json');
}

export function credentialsPath(): string {
  return join(configDir(), 'credentials.json');
}

/** Legacy single-profile config migrated on first run. */
export function legacyKeltarcPath(): string {
  return legacyRcOverride ?? join(homedir(), '.keltarc');
}
