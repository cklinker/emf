// @vitest-environment node
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { hostname } from 'node:os';

vi.mock('axios', () => ({
  default: { post: vi.fn() },
}));

import axios from 'axios';
import { browserLogin, deriveAuthUrl } from './loginFlow.js';

const post = vi.mocked(axios.post);

/** Drive the flow: parse the authorize URL and fire the loopback callback. */
function completeInBrowser(
  authorizeUrl: string,
  overrides: { state?: string; code?: string } = {}
): void {
  const url = new URL(authorizeUrl);
  const redirectUri = url.searchParams.get('redirect_uri');
  const state = overrides.state ?? url.searchParams.get('state');
  const code = overrides.code ?? 'auth-code-1';
  if (!redirectUri || !state) throw new Error('authorize URL missing params');
  void fetch(`${redirectUri}?code=${code}&state=${encodeURIComponent(state)}`);
}

const PARAMS = {
  apiUrl: 'https://api.kelta.io',
  tenantSlug: 'acme',
  authUrl: 'https://auth.kelta.io',
  expiresInDays: 30,
  log: vi.fn(),
};

describe('deriveAuthUrl', () => {
  it('swaps a leading api. label for auth.', () => {
    expect(deriveAuthUrl('https://api.kelta.io')).toBe('https://auth.kelta.io');
    expect(deriveAuthUrl('https://api.stg.example.com/base')).toBe('https://auth.stg.example.com');
  });

  it('returns undefined when the convention does not apply', () => {
    expect(deriveAuthUrl('https://gateway.example.com')).toBeUndefined();
    expect(deriveAuthUrl('not a url')).toBeUndefined();
  });
});

describe('browserLogin', () => {
  beforeEach(() => {
    post.mockReset();
  });

  it('runs the full loopback + PKCE + PAT-mint flow', async () => {
    post.mockResolvedValueOnce({ data: { access_token: 'jwt-1' } }).mockResolvedValueOnce({
      data: {
        token: 'klt_new1234567890',
        tokenPrefix: 'klt_new1',
        expiresAt: '2026-11-01T00:00:00Z',
      },
    });

    const minted = await browserLogin({ ...PARAMS, navigate: completeInBrowser });

    expect(minted.token).toBe('klt_new1234567890');
    expect(minted.expiresAt).toBe('2026-11-01T00:00:00Z');
    expect(minted.name).toContain(hostname());
    // second-precision stamp — (user, name) is unique server-side, so a
    // date-only name 409s on the second login of the day
    expect(minted.name).toMatch(/ \d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}$/);

    // token exchange: form-encoded PKCE request to the auth server
    const [tokenUrl, tokenBody] = post.mock.calls[0] as [string, URLSearchParams];
    expect(tokenUrl).toBe('https://auth.kelta.io/oauth2/token');
    expect(tokenBody.get('grant_type')).toBe('authorization_code');
    expect(tokenBody.get('client_id')).toBe('kelta-cli');
    expect(tokenBody.get('code')).toBe('auth-code-1');
    expect(tokenBody.get('code_verifier')).toMatch(/^[A-Za-z0-9_-]{43}$/);
    // the tenant slug MUST ride in the callback path — kelta-auth's
    // TenantContextFilter reads it from here to scope the login (regression:
    // a slug-less /callback authorized fine then failed with "no tenant
    // context in session")
    expect(tokenBody.get('redirect_uri')).toMatch(
      /^http:\/\/127\.0\.0\.1:\d+\/acme\/auth\/callback$/
    );

    // PAT mint: tenant-scoped endpoint, JWT bearer, requested lifetime
    const [patUrl, patBody, patConfig] = post.mock.calls[1] as [
      string,
      { name: string; expiresInDays: number },
      { headers: Record<string, string> },
    ];
    expect(patUrl).toBe('https://api.kelta.io/acme/api/me/tokens');
    expect(patBody.expiresInDays).toBe(30);
    expect(patConfig.headers.Authorization).toBe('Bearer jwt-1');
  });

  it('sends S256 challenge derived from the verifier it later presents', async () => {
    let authorizeUrl = '';
    post
      .mockResolvedValueOnce({ data: { access_token: 'jwt-1' } })
      .mockResolvedValueOnce({ data: { token: 'klt_x1234567890' } });

    await browserLogin({
      ...PARAMS,
      navigate: (url) => {
        authorizeUrl = url;
        completeInBrowser(url);
      },
    });

    const challenge = new URL(authorizeUrl).searchParams.get('code_challenge');
    const method = new URL(authorizeUrl).searchParams.get('code_challenge_method');
    expect(method).toBe('S256');
    const { challengeS256 } = await import('./pkce.js');
    const verifier = (post.mock.calls[0][1] as URLSearchParams).get('code_verifier');
    expect(challenge).toBe(challengeS256(verifier ?? ''));
  });

  it('fails with TOKEN_EXCHANGE_FAILED when no access token comes back', async () => {
    post.mockResolvedValueOnce({ data: {} });
    await expect(browserLogin({ ...PARAMS, navigate: completeInBrowser })).rejects.toMatchObject({
      code: 'TOKEN_EXCHANGE_FAILED',
      exitCode: 3,
    });
  });

  it('fails with PAT_MINT_FAILED when the PAT endpoint returns no token', async () => {
    post
      .mockResolvedValueOnce({ data: { access_token: 'jwt-1' } })
      .mockResolvedValueOnce({ data: {} });
    await expect(browserLogin({ ...PARAMS, navigate: completeInBrowser })).rejects.toMatchObject({
      code: 'PAT_MINT_FAILED',
    });
  });

  it('builds an authorize URL whose redirect_uri carries the tenant slug', async () => {
    let authorizeUrl = '';
    post
      .mockResolvedValueOnce({ data: { access_token: 'jwt-1' } })
      .mockResolvedValueOnce({ data: { token: 'klt_x1234567890' } });

    await browserLogin({
      ...PARAMS,
      tenantSlug: 'couchpicks',
      navigate: (url) => {
        authorizeUrl = url;
        completeInBrowser(url);
      },
    });

    const redirectUri = new URL(authorizeUrl).searchParams.get('redirect_uri') ?? '';
    expect(new URL(redirectUri).pathname).toBe('/couchpicks/auth/callback');
  });

  it('aborts on a forged state without calling the token endpoint', async () => {
    await expect(
      browserLogin({
        ...PARAMS,
        navigate: (url) => completeInBrowser(url, { state: 'forged' }),
      })
    ).rejects.toMatchObject({ code: 'STATE_MISMATCH' });
    expect(post).not.toHaveBeenCalled();
  });
});
