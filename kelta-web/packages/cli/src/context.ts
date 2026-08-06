import { KeltaClient } from '@kelta/sdk';
import { requireAuthenticated, resolveProfile, tokenExpiryDays } from './config/resolve.js';
import type { CommandContext, GlobalOptions } from './registry/types.js';

const EXPIRY_WARN_DAYS = 7;

function log(message: string): void {
  process.stderr.write(message + '\n');
}

/**
 * Build the per-invocation command context. The SDK client is constructed
 * lazily so profile-management commands work without credentials; touching
 * `ctx.client` with an incomplete profile fails with AUTH_REQUIRED (exit 3).
 */
export function buildContext(global: GlobalOptions): CommandContext {
  const profile = resolveProfile(global.profile);
  let client: KeltaClient | undefined;

  const days = tokenExpiryDays(profile);
  if (days !== undefined && days < EXPIRY_WARN_DAYS && process.stderr.isTTY) {
    log(
      days < 0
        ? `Warning: token for profile "${profile.name}" expired — run: kelta auth login`
        : `Warning: token for profile "${profile.name}" expires in ${String(Math.ceil(days))} day(s)`
    );
  }

  return {
    profile,
    global,
    log,
    get client(): KeltaClient {
      if (!client) {
        const authenticated = requireAuthenticated(profile);
        client = new KeltaClient({
          baseUrl: authenticated.apiUrl,
          tenantSlug: authenticated.tenantSlug,
          tokenProvider: { getToken: () => Promise.resolve(authenticated.token) },
          validation: false,
        });
      }
      return client;
    },
  };
}
