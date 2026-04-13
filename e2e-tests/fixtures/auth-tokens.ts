/**
 * Utility for getting API tokens for test fixtures.
 *
 * Tries kelta-auth's direct-login endpoint first (fast, no external IdP dependency),
 * then falls back to Authentik's client_credentials grant for backwards compatibility.
 */

import { attemptDirectLogin } from "../helpers/direct-login";

let cachedToken: string | null = null;
let tokenExpiry = 0;

/** Clear the cached token so the next call to getApiToken() fetches a fresh one. */
export function clearTokenCache(): void {
  cachedToken = null;
  tokenExpiry = 0;
}

/**
 * Get an API bearer token for test data operations.
 *
 * Strategy 1: Use kelta-auth direct-login endpoint (preferred).
 * Strategy 2: Fall back to Authentik client_credentials grant.
 */
export async function getApiToken(): Promise<string> {
  // Return cached token if still valid (with 60s buffer)
  if (cachedToken && Date.now() < tokenExpiry - 60_000) {
    return cachedToken;
  }

  // Strategy 1: Direct login via kelta-auth
  const authBaseUrl =
    process.env.E2E_AUTH_DIRECT_LOGIN_URL ||
    process.env.E2E_AUTH_BASE_URL ||
    "";
  const username = process.env.E2E_TEST_USERNAME || "e2e-admin@kelta.local";
  const password = process.env.E2E_TEST_PASSWORD || "";

  if (authBaseUrl && password) {
    const tenantSlug = process.env.E2E_TENANT_SLUG || "default";
    const result = await attemptDirectLogin({
      authBaseUrl,
      username,
      password,
      tenantSlug,
    });

    if (result) {
      cachedToken = result.access_token;
      tokenExpiry = Date.now() + result.expires_in * 1000;
      return cachedToken;
    }
  }

  // Strategy 2: Authentik client_credentials grant (legacy fallback)
  return getAuthentikToken();
}

async function getAuthentikToken(): Promise<string> {
  const authentikUrl =
    process.env.E2E_AUTHENTIK_URL || "https://authentik.rzware.com";
  const tokenUrl = `${authentikUrl}/application/o/token/`;

  const clientId = process.env.E2E_SERVICE_CLIENT_ID || "";
  const clientSecret = process.env.E2E_SERVICE_CLIENT_SECRET || "";

  if (!clientId || !clientSecret) {
    console.warn(
      "No direct-login URL configured and E2E_SERVICE_CLIENT_ID/SECRET are not set. " +
        "DataFactory API calls will fail. " +
        "Set E2E_AUTH_BASE_URL or configure Authentik service account.",
    );
    return "";
  }

  const params = new URLSearchParams({
    grant_type: "client_credentials",
    client_id: clientId,
    client_secret: clientSecret,
    scope: "openid profile email",
  });

  const response = await fetch(tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params.toString(),
  });

  if (!response.ok) {
    const errorBody = await response.text().catch(() => "");
    console.warn(
      `Authentik token endpoint returned ${response.status}. ` +
        `DataFactory API calls will fail. Body: ${errorBody}`,
    );
    return "";
  }

  const data = (await response.json()) as {
    access_token: string;
    expires_in: number;
  };
  cachedToken = data.access_token;
  tokenExpiry = Date.now() + data.expires_in * 1000;
  return cachedToken;
}

// Re-export with old name for backwards compatibility
export const getAuthentikTokens = getApiToken;
