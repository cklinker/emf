import { z } from 'zod';
import { loadConfig, removeToken, saveConfig, setToken } from '../config/store.js';
import { resolveProfile } from '../config/resolve.js';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

const TOKEN_PREFIX_LENGTH = 8;

const login = defineCommand({
  group: 'auth',
  name: 'login',
  summary: 'Save API credentials for a profile (browser login lands in a later slice)',
  requiresAuth: false,
  options: [
    { flag: '--url <url>', description: 'Kelta API URL (e.g., https://api.kelta.io)' },
    { flag: '--tenant <slug>', description: 'Tenant slug' },
    { flag: '--token <token>', description: 'Personal access token (klt_...) or JWT' },
  ],
  input: z.object({
    url: z.string().url(),
    tenant: z.string().min(1),
    token: z.string().min(1),
  }),
  handler: (ctx, input) => {
    const name = ctx.global.profile ?? process.env.KELTA_PROFILE ?? 'default';
    const config = loadConfig();
    config.profiles[name] = {
      ...config.profiles[name],
      apiUrl: input.url.replace(/\/$/, ''),
      tenantSlug: input.tenant,
      tokenPrefix: input.token.slice(0, TOKEN_PREFIX_LENGTH),
    };
    config.defaultProfile ??= name;
    saveConfig(config);
    setToken(name, input.token);
    return Promise.resolve({
      data: { profile: name, url: config.profiles[name].apiUrl, tenant: input.tenant },
      message: `Authenticated profile "${name}" (tenant "${input.tenant}")`,
    });
  },
});

const logout = defineCommand({
  group: 'auth',
  name: 'logout',
  summary: 'Remove the stored credential for the active profile',
  requiresAuth: false,
  input: z.object({}),
  handler: (ctx) => {
    const name =
      ctx.global.profile ?? process.env.KELTA_PROFILE ?? loadConfig().defaultProfile ?? 'default';
    removeToken(name);
    const config = loadConfig();
    if (config.profiles[name]) {
      delete config.profiles[name].tokenPrefix;
      delete config.profiles[name].tokenExpiresAt;
      saveConfig(config);
    }
    return Promise.resolve({ message: `Credential removed for profile "${name}"` });
  },
});

const status = defineCommand({
  group: 'auth',
  name: 'status',
  summary: 'Show the resolved connection settings for the active profile',
  requiresAuth: false,
  input: z.object({}),
  handler: (ctx) => {
    const profile = resolveProfile(ctx.global.profile);
    const data = {
      profile: profile.name,
      url: profile.apiUrl ?? null,
      tenant: profile.tenantSlug ?? null,
      token: profile.token ? profile.token.slice(0, TOKEN_PREFIX_LENGTH) + '…' : null,
      tokenExpiresAt: profile.tokenExpiresAt ?? null,
      authenticated: Boolean(profile.apiUrl && profile.tenantSlug && profile.token),
    };
    const human = [
      `Profile: ${data.profile}`,
      `URL:     ${data.url ?? '(not set)'}`,
      `Tenant:  ${data.tenant ?? '(not set)'}`,
      `Token:   ${data.token ?? '(not set)'}`,
      ...(data.tokenExpiresAt ? [`Expires: ${data.tokenExpiresAt}`] : []),
    ].join('\n');
    return Promise.resolve({ data, human: human + '\n' });
  },
});

export const authCommands: RegisteredCommand[] = [login, logout, status];
