/**
 * Direct login helper for e2e tests.
 *
 * Calls kelta-auth's /auth/direct-login endpoint to get tokens without going
 * through the browser-based OIDC redirect flow. This avoids the about:blank
 * issue in headless Chrome where the OIDC authorization redirect fails.
 *
 * Requires DIRECT_LOGIN_ENABLED=true on kelta-auth.
 */

export interface DirectLoginResult {
  access_token: string;
  id_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
}

/**
 * Attempt direct login via kelta-auth's API endpoint.
 * Returns null if the endpoint is not available (feature disabled or unreachable).
 */
export async function attemptDirectLogin(options: {
  authBaseUrl: string;
  username: string;
  password: string;
  tenantSlug?: string;
}): Promise<DirectLoginResult | null> {
  const url = `${options.authBaseUrl}/auth/direct-login`;

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: options.username,
        password: options.password,
        tenantSlug: options.tenantSlug,
      }),
    });

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      console.warn(
        `Direct login returned ${response.status}: ${body}. ` +
          "Falling back to browser-based login.",
      );
      return null;
    }

    return (await response.json()) as DirectLoginResult;
  } catch (err) {
    console.warn(
      `Direct login endpoint not reachable: ${err}. ` +
        "Falling back to browser-based login.",
    );
    return null;
  }
}

/**
 * Convert a direct login response into the sessionStorage token map
 * that the SPA expects (kelta_auth_tokens JSON string).
 */
export function toSessionTokens(
  result: DirectLoginResult,
): Record<string, string> {
  const tokens = {
    accessToken: result.access_token,
    idToken: result.id_token,
    refreshToken: result.refresh_token,
    expiresAt: Date.now() + result.expires_in * 1000,
  };

  return {
    kelta_auth_tokens: JSON.stringify(tokens),
  };
}
