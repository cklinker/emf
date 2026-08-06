import { hostname } from 'node:os';
import axios from 'axios';
import { CliError, EXIT } from '../errors.js';
import { openBrowser } from './browser.js';
import { startLoopbackServer } from './loopbackServer.js';
import { challengeS256, generateState, generateVerifier } from './pkce.js';

const CLIENT_ID = 'kelta-cli';
const TOKEN_PREFIX_LENGTH = 8;

export interface BrowserLoginParams {
  apiUrl: string;
  tenantSlug: string;
  authUrl: string;
  expiresInDays: number;
  /** Print the URL instead of launching a browser. */
  noBrowser?: boolean;
  log: (message: string) => void;
  /** Test seam: receives the authorize URL; default opens the browser. */
  navigate?: (url: string) => void;
  timeoutMs?: number;
}

export interface MintedPat {
  token: string;
  tokenPrefix: string;
  expiresAt: string;
  name: string;
}

/**
 * Derive the auth-server URL from the API URL by swapping a leading `api.`
 * host label for `auth.` (api.kelta.io → auth.kelta.io). Returns undefined
 * when the convention doesn't apply — the caller must then pass --auth-url.
 */
export function deriveAuthUrl(apiUrl: string): string | undefined {
  try {
    const url = new URL(apiUrl);
    if (url.hostname.startsWith('api.')) {
      url.hostname = 'auth.' + url.hostname.slice(4);
      url.pathname = '';
      return url.origin;
    }
  } catch {
    // fall through
  }
  return undefined;
}

/**
 * RFC 8252 browser login: loopback listener + authorization_code/PKCE against
 * kelta-auth, then immediately mint a PAT with the short-lived JWT. The JWT is
 * never persisted — the returned PAT is the only durable credential.
 */
export async function browserLogin(params: BrowserLoginParams): Promise<MintedPat> {
  const verifier = generateVerifier();
  const state = generateState();
  const server = await startLoopbackServer(state, params.timeoutMs);

  try {
    const redirectUri = `http://127.0.0.1:${String(server.port)}/callback`;
    const authorizeUrl =
      `${params.authUrl.replace(/\/$/, '')}/oauth2/authorize?` +
      new URLSearchParams({
        response_type: 'code',
        client_id: CLIENT_ID,
        redirect_uri: redirectUri,
        scope: 'openid profile email',
        state,
        code_challenge: challengeS256(verifier),
        code_challenge_method: 'S256',
      }).toString();

    const navigate =
      params.navigate ??
      ((url: string): void => {
        if (!params.noBrowser && openBrowser(url)) {
          params.log('Opening your browser to complete login…');
        } else {
          params.log('Open this URL in your browser to complete login:');
        }
        params.log(url);
      });
    navigate(authorizeUrl);

    const code = await server.code;
    const accessToken = await exchangeCode(params.authUrl, code, redirectUri, verifier);
    return await mintPat(params, accessToken);
  } finally {
    server.close();
  }
}

async function exchangeCode(
  authUrl: string,
  code: string,
  redirectUri: string,
  verifier: string
): Promise<string> {
  const response = await axios.post<{ access_token?: string }>(
    `${authUrl.replace(/\/$/, '')}/oauth2/token`,
    new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri,
      client_id: CLIENT_ID,
      code_verifier: verifier,
    }),
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );
  const accessToken = response.data.access_token;
  if (!accessToken) {
    throw new CliError('Token exchange returned no access token', {
      code: 'TOKEN_EXCHANGE_FAILED',
      exitCode: EXIT.AUTH,
    });
  }
  return accessToken;
}

async function mintPat(params: BrowserLoginParams, accessToken: string): Promise<MintedPat> {
  const name = `kelta-cli ${hostname()} ${new Date().toISOString().slice(0, 10)}`;
  const response = await axios.post<{ token?: string; tokenPrefix?: string; expiresAt?: string }>(
    `${params.apiUrl.replace(/\/$/, '')}/${params.tenantSlug}/api/me/tokens`,
    { name, expiresInDays: params.expiresInDays },
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  const token = response.data.token;
  if (!token) {
    throw new CliError('PAT creation returned no token', {
      code: 'PAT_MINT_FAILED',
      exitCode: EXIT.AUTH,
    });
  }
  return {
    token,
    tokenPrefix: response.data.tokenPrefix ?? token.slice(0, TOKEN_PREFIX_LENGTH),
    expiresAt: response.data.expiresAt ?? '',
    name,
  };
}
