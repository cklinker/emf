/**
 * Utility for getting API tokens for test fixtures.
 *
 * Uses Authentik's token endpoint to get tokens for API-level operations
 * (creating test data, cleaning up, etc.) independent of the browser session.
 */

let cachedToken: string | null = null;
let tokenExpiry = 0;

export async function getAuthentikTokens(): Promise<string> {
  // Return cached token if still valid (with 60s buffer)
  if (cachedToken && Date.now() < tokenExpiry - 60_000) {
    return cachedToken;
  }

  const authentikUrl =
    process.env.E2E_AUTHENTIK_URL || 'https://authentik.rzware.com';
  const tokenUrl = `${authentikUrl}/application/o/token/`;

  const params = new URLSearchParams({
    grant_type: 'password',
    client_id: process.env.E2E_CLIENT_ID || '',
    username: process.env.E2E_TEST_USERNAME || 'e2e-admin@kelta.local',
    password: process.env.E2E_TEST_PASSWORD || '',
    scope: 'openid profile email',
  });

  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params.toString(),
  });

  if (!response.ok) {
    // If password grant is not available, return empty token
    // Tests using dataFactory will need the password grant to work.
    console.warn(
      `Authentik token endpoint returned ${response.status}. ` +
        'DataFactory API calls may fail. Ensure password grant is enabled in Authentik.',
    );
    return '';
  }

  const data = (await response.json()) as {
    access_token: string;
    expires_in: number;
  };
  cachedToken = data.access_token;
  tokenExpiry = Date.now() + data.expires_in * 1000;
  return cachedToken;
}
