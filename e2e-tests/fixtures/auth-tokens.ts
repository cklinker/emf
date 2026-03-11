/**
 * Utility for getting API tokens for test fixtures.
 *
 * Uses Authentik's token endpoint with client_credentials grant to get tokens
 * for API-level operations (creating test data, cleaning up, etc.) independent
 * of the browser session.
 */

let cachedToken: string | null = null;
let tokenExpiry = 0;

/** Clear the cached token so the next call to getAuthentikTokens() fetches a fresh one. */
export function clearTokenCache(): void {
  cachedToken = null;
  tokenExpiry = 0;
}

export async function getAuthentikTokens(): Promise<string> {
  // Return cached token if still valid (with 60s buffer)
  if (cachedToken && Date.now() < tokenExpiry - 60_000) {
    return cachedToken;
  }

  const authentikUrl =
    process.env.E2E_AUTHENTIK_URL || "https://authentik.rzware.com";
  const tokenUrl = `${authentikUrl}/application/o/token/`;

  const clientId = process.env.E2E_SERVICE_CLIENT_ID || "";
  const clientSecret = process.env.E2E_SERVICE_CLIENT_SECRET || "";

  if (!clientId || !clientSecret) {
    console.warn(
      "E2E_SERVICE_CLIENT_ID and E2E_SERVICE_CLIENT_SECRET are not set. " +
        "DataFactory API calls will fail. " +
        "Configure an Authentik service account with client_credentials grant.",
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
