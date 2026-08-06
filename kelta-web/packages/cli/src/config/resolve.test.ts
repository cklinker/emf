import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { CliError } from '../errors.js';
import { requireAuthenticated, resolveProfile, tokenExpiryDays } from './resolve.js';
import { saveConfig, setToken } from './store.js';
import { setConfigDirForTesting, setLegacyRcForTesting } from './paths.js';

let dir: string;
const ENV_KEYS = ['KELTA_PROFILE', 'KELTA_URL', 'KELTA_TENANT', 'KELTA_TOKEN'] as const;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'kelta-cli-test-'));
  setConfigDirForTesting(join(dir, '.kelta'));
  setLegacyRcForTesting(join(dir, '.keltarc'));
  for (const key of ENV_KEYS) delete process.env[key];
  saveConfig({
    version: 1,
    defaultProfile: 'prod',
    profiles: {
      prod: {
        apiUrl: 'https://api.kelta.io',
        tenantSlug: 'acme',
        tokenExpiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
      },
      staging: { apiUrl: 'https://api.stg.kelta.io', tenantSlug: 'acme-stg' },
    },
  });
  setToken('prod', 'klt_prodtoken');
});

afterEach(() => {
  for (const key of ENV_KEYS) delete process.env[key];
  setConfigDirForTesting();
  setLegacyRcForTesting();
  rmSync(dir, { recursive: true, force: true });
});

describe('resolveProfile precedence', () => {
  it('uses the default profile when nothing is specified', () => {
    const profile = resolveProfile();
    expect(profile.name).toBe('prod');
    expect(profile.apiUrl).toBe('https://api.kelta.io');
    expect(profile.token).toBe('klt_prodtoken');
  });

  it('flag beats KELTA_PROFILE beats default', () => {
    process.env.KELTA_PROFILE = 'staging';
    expect(resolveProfile().name).toBe('staging');
    expect(resolveProfile('prod').name).toBe('prod');
  });

  it('env value overrides the profile file per field', () => {
    process.env.KELTA_URL = 'https://override.example';
    process.env.KELTA_TOKEN = 'klt_envtoken';
    const profile = resolveProfile();
    expect(profile.apiUrl).toBe('https://override.example');
    expect(profile.tenantSlug).toBe('acme'); // untouched field still from file
    expect(profile.token).toBe('klt_envtoken');
  });

  it('resolves an unknown profile to empty settings', () => {
    const profile = resolveProfile('nope');
    expect(profile.apiUrl).toBeUndefined();
    expect(profile.token).toBeUndefined();
  });
});

describe('requireAuthenticated', () => {
  it('passes a complete profile through', () => {
    const auth = requireAuthenticated(resolveProfile('prod'));
    expect(auth.token).toBe('klt_prodtoken');
  });

  it('fails with AUTH_REQUIRED and exit 3 listing missing pieces', () => {
    try {
      requireAuthenticated(resolveProfile('staging'));
      expect.unreachable('should have thrown');
    } catch (error) {
      const cliError = error as CliError;
      expect(cliError).toBeInstanceOf(CliError);
      expect(cliError.code).toBe('AUTH_REQUIRED');
      expect(cliError.exitCode).toBe(3);
      expect(cliError.message).toContain('token');
    }
  });
});

describe('tokenExpiryDays', () => {
  it('reports days until expiry', () => {
    const days = tokenExpiryDays(resolveProfile('prod'));
    expect(days).toBeGreaterThan(29);
    expect(days).toBeLessThan(31);
  });

  it('is undefined without an expiry', () => {
    expect(tokenExpiryDays(resolveProfile('staging'))).toBeUndefined();
  });
});
