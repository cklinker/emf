// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { CliError } from '../errors.js';
import { startLoopbackServer } from './loopbackServer.js';

async function hit(port: number, path: string): Promise<number> {
  const response = await fetch(`http://127.0.0.1:${String(port)}${path}`);
  await response.text();
  return response.status;
}

describe('startLoopbackServer', () => {
  it('resolves the code on a matching-state callback and serves the close page', async () => {
    const server = await startLoopbackServer('state-1', '/acme/auth/callback');
    const status = await hit(server.port, '/acme/auth/callback?code=abc123&state=state-1');
    expect(status).toBe(200);
    await expect(server.code).resolves.toBe('abc123');
  });

  it('rejects on state mismatch (CSRF)', async () => {
    const server = await startLoopbackServer('expected', '/acme/auth/callback');
    await hit(server.port, '/acme/auth/callback?code=abc123&state=forged');
    await expect(server.code).rejects.toMatchObject({ code: 'STATE_MISMATCH', exitCode: 3 });
  });

  it('rejects when the IdP returns an error', async () => {
    const server = await startLoopbackServer('state-1', '/acme/auth/callback');
    await hit(
      server.port,
      '/acme/auth/callback?error=access_denied&error_description=nope&state=state-1'
    );
    await expect(server.code).rejects.toMatchObject({ code: 'AUTHORIZATION_DENIED' });
  });

  it('rejects a callback with no code', async () => {
    const server = await startLoopbackServer('state-1', '/acme/auth/callback');
    await hit(server.port, '/acme/auth/callback?state=state-1');
    await expect(server.code).rejects.toMatchObject({ code: 'NO_AUTHORIZATION_CODE' });
  });

  it('404s other paths without settling', async () => {
    const server = await startLoopbackServer('state-1', '/acme/auth/callback', 5_000);
    expect(await hit(server.port, '/favicon.ico')).toBe(404);
    // still waiting — now complete it properly
    await hit(server.port, '/acme/auth/callback?code=ok&state=state-1');
    await expect(server.code).resolves.toBe('ok');
  });

  it('times out when the browser never comes back', async () => {
    const server = await startLoopbackServer('state-1', '/acme/auth/callback', 50);
    await expect(server.code).rejects.toMatchObject({ code: 'LOGIN_TIMEOUT', exitCode: 3 });
  });

  it('rejects CliError instances (mapped, not generic)', async () => {
    const server = await startLoopbackServer('state-1', '/acme/auth/callback', 50);
    await expect(server.code).rejects.toBeInstanceOf(CliError);
  });
});
