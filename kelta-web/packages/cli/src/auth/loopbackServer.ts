import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { CliError, EXIT } from '../errors.js';

const CLOSE_PAGE = `<!doctype html><html><head><title>Kelta CLI</title></head>
<body style="font-family: system-ui; text-align: center; padding-top: 4rem">
<h2>Login complete</h2><p>You can close this window and return to the terminal.</p>
</body></html>`;

export interface LoopbackServer {
  /** OS-assigned port on 127.0.0.1. */
  port: number;
  /** Resolves with the authorization code, rejects on error/state-mismatch/timeout. */
  code: Promise<string>;
  close(): void;
}

/**
 * One-shot RFC 8252 loopback listener. Binds 127.0.0.1 on an OS-assigned port
 * and waits for exactly one authorization callback on `callbackPath` — which is
 * tenant-scoped (`/{slug}/auth/callback`) because kelta-auth derives the login's
 * tenant from the redirect_uri path. The state parameter MUST match or the code
 * is rejected (CSRF). Times out after `timeoutMs` so an abandoned login never
 * hangs the terminal.
 */
export function startLoopbackServer(
  expectedState: string,
  callbackPath: string,
  timeoutMs = 120_000
): Promise<LoopbackServer> {
  return new Promise((resolveServer, rejectServer) => {
    let settle: ((value: string) => void) | undefined;
    let fail: ((error: CliError) => void) | undefined;
    const code = new Promise<string>((resolve, reject) => {
      settle = resolve;
      fail = reject;
    });

    const server: Server = createServer((req, res) => {
      const url = new URL(req.url ?? '/', 'http://127.0.0.1');
      if (url.pathname !== callbackPath) {
        res.writeHead(404).end();
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }).end(CLOSE_PAGE);

      const error = url.searchParams.get('error');
      const state = url.searchParams.get('state');
      const authCode = url.searchParams.get('code');
      if (error) {
        fail?.(
          new CliError(
            `Authorization failed: ${error}${url.searchParams.get('error_description') ? ` — ${url.searchParams.get('error_description') ?? ''}` : ''}`,
            { code: 'AUTHORIZATION_DENIED', exitCode: EXIT.AUTH }
          )
        );
      } else if (!state || state !== expectedState) {
        fail?.(
          new CliError('Authorization callback state mismatch — aborting login', {
            code: 'STATE_MISMATCH',
            exitCode: EXIT.AUTH,
          })
        );
      } else if (!authCode) {
        fail?.(
          new CliError('Authorization callback carried no code', {
            code: 'NO_AUTHORIZATION_CODE',
            exitCode: EXIT.AUTH,
          })
        );
      } else {
        settle?.(authCode);
      }
    });

    const timer = setTimeout(() => {
      fail?.(
        new CliError(
          `Timed out after ${String(Math.round(timeoutMs / 1000))}s waiting for the browser login`,
          {
            code: 'LOGIN_TIMEOUT',
            exitCode: EXIT.AUTH,
          }
        )
      );
      server.close();
    }, timeoutMs);
    timer.unref();

    const close = (): void => {
      clearTimeout(timer);
      server.close();
    };
    // whatever the outcome, the listener is one-shot
    code.finally(close).catch(() => undefined);

    server.on('error', (error) => {
      clearTimeout(timer);
      rejectServer(
        new CliError(`Could not start the loopback listener: ${error.message}`, {
          code: 'LOOPBACK_LISTEN_FAILED',
          exitCode: EXIT.AUTH,
        })
      );
    });
    server.listen(0, '127.0.0.1', () => {
      const address = server.address() as AddressInfo;
      resolveServer({ port: address.port, code, close });
    });
  });
}
