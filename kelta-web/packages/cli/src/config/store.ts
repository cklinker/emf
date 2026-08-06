import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { configPath, credentialsPath, legacyKeltarcPath } from './paths.js';

export interface ProfileEntry {
  apiUrl: string;
  tenantSlug: string;
  authUrl?: string;
  /** First 8 chars of the stored PAT, for display only. */
  tokenPrefix?: string;
  /** ISO timestamp; drives the expiry warning. */
  tokenExpiresAt?: string;
}

export interface ConfigFile {
  version: 1;
  defaultProfile?: string;
  profiles: Record<string, ProfileEntry>;
}

interface CredentialsFile {
  version: 1;
  profiles: Record<string, { token: string }>;
}

// Fresh objects on every miss — callers mutate the returned value, so a
// shared constant would leak state between loads in one process.
const emptyConfig = (): ConfigFile => ({ version: 1, profiles: {} });
const emptyCredentials = (): CredentialsFile => ({ version: 1, profiles: {} });

function readJson<T>(path: string, fallback: () => T): T {
  if (!existsSync(path)) return fallback();
  try {
    return JSON.parse(readFileSync(path, 'utf-8')) as T;
  } catch {
    return fallback();
  }
}

function writeJson(path: string, value: unknown, mode?: number): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify(value, null, 2) + '\n', mode ? { mode } : undefined);
}

export function loadConfig(): ConfigFile {
  migrateLegacyKeltarc();
  return readJson(configPath(), emptyConfig);
}

export function saveConfig(config: ConfigFile): void {
  writeJson(configPath(), config);
}

export function getToken(profileName: string): string | undefined {
  return readJson(credentialsPath(), emptyCredentials).profiles[profileName]?.token;
}

export function setToken(profileName: string, token: string): void {
  const credentials = readJson(credentialsPath(), emptyCredentials);
  credentials.profiles[profileName] = { token };
  writeJson(credentialsPath(), credentials, 0o600);
}

export function removeToken(profileName: string): void {
  const credentials = readJson(credentialsPath(), emptyCredentials);
  if (!(profileName in credentials.profiles)) return;
  delete credentials.profiles[profileName];
  writeJson(credentialsPath(), credentials, 0o600);
}

export function renameToken(oldName: string, newName: string): void {
  const credentials = readJson(credentialsPath(), emptyCredentials);
  const entry = credentials.profiles[oldName];
  if (!entry) return;
  delete credentials.profiles[oldName];
  credentials.profiles[newName] = entry;
  writeJson(credentialsPath(), credentials, 0o600);
}

/**
 * One-time silent migration of the legacy `~/.keltarc` single profile
 * (`{url, token, tenant}`) into the profile store as `default`. The legacy
 * file is left in place; migration only runs while no config.json exists.
 */
function migrateLegacyKeltarc(): void {
  if (existsSync(configPath())) return;
  const legacy = readJson<{ url?: string; token?: string; tenant?: string } | null>(
    legacyKeltarcPath(),
    () => null
  );
  if (!legacy?.url || !legacy.tenant) return;
  const config: ConfigFile = {
    version: 1,
    defaultProfile: 'default',
    profiles: {
      default: {
        apiUrl: legacy.url.replace(/\/$/, ''),
        tenantSlug: legacy.tenant,
        tokenPrefix: legacy.token?.slice(0, 8),
      },
    },
  };
  writeJson(configPath(), config);
  if (legacy.token) setToken('default', legacy.token);
}
