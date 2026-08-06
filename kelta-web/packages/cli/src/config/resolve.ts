import { CliError, EXIT } from '../errors.js';
import { getToken, loadConfig } from './store.js';

export interface ResolvedProfile {
  name: string;
  apiUrl?: string;
  tenantSlug?: string;
  authUrl?: string;
  token?: string;
  tokenExpiresAt?: string;
}

/**
 * Resolve the effective profile. Precedence per the parent spec:
 * flags > env (`KELTA_PROFILE`/`KELTA_URL`/`KELTA_TENANT`/`KELTA_TOKEN`) >
 * profile file. Never throws — commands that need auth call
 * {@link requireAuthenticated}.
 */
export function resolveProfile(profileFlag?: string): ResolvedProfile {
  const config = loadConfig();
  const name = profileFlag ?? process.env.KELTA_PROFILE ?? config.defaultProfile ?? 'default';
  const entry = config.profiles[name];
  return {
    name,
    apiUrl: process.env.KELTA_URL ?? entry?.apiUrl,
    tenantSlug: process.env.KELTA_TENANT ?? entry?.tenantSlug,
    authUrl: entry?.authUrl,
    token: process.env.KELTA_TOKEN ?? getToken(name),
    tokenExpiresAt: entry?.tokenExpiresAt,
  };
}

export interface AuthenticatedProfile extends ResolvedProfile {
  apiUrl: string;
  tenantSlug: string;
  token: string;
}

/** Assert the profile is usable for API calls, or fail with exit code 3. */
export function requireAuthenticated(profile: ResolvedProfile): AuthenticatedProfile {
  const missing: string[] = [];
  if (!profile.apiUrl) missing.push('url');
  if (!profile.tenantSlug) missing.push('tenant');
  if (!profile.token) missing.push('token');
  if (missing.length > 0) {
    throw new CliError(
      `Profile "${profile.name}" is missing ${missing.join(', ')}. ` +
        `Run: kelta auth login --url <url> --tenant <slug> --token <klt_...> --profile ${profile.name}`,
      { code: 'AUTH_REQUIRED', exitCode: EXIT.AUTH }
    );
  }
  return profile as AuthenticatedProfile;
}

/**
 * Days until the stored token expires, or undefined when unknown.
 * Negative when already expired.
 */
export function tokenExpiryDays(profile: ResolvedProfile): number | undefined {
  if (!profile.tokenExpiresAt) return undefined;
  const expires = Date.parse(profile.tokenExpiresAt);
  if (Number.isNaN(expires)) return undefined;
  return (expires - Date.now()) / 86_400_000;
}
