import { describe, it, expect } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { z } from 'zod';
import { CliError, EXIT, mapError, toErrorPayload } from './errors.js';

function axiosError(status: number, body: unknown): AxiosError {
  const headers = new AxiosHeaders();
  const config = { headers };
  return new AxiosError(
    `Request failed with status code ${String(status)}`,
    'ERR_BAD_REQUEST',
    config as never,
    {},
    {
      status,
      statusText: '',
      headers: {},
      config: config as never,
      data: body,
    }
  );
}

describe('mapError', () => {
  it('passes CliError through unchanged', () => {
    const original = new CliError('x', { code: 'X', exitCode: 5 });
    expect(mapError(original)).toBe(original);
  });

  it('extracts the JSON:API error contract (code, detail, requestId)', () => {
    const mapped = mapError(
      axiosError(400, {
        errors: [
          {
            status: '400',
            code: 'VALIDATION_FAILED',
            detail: 'name is required',
            meta: { requestId: 'req-1' },
          },
        ],
      })
    );
    expect(mapped.code).toBe('VALIDATION_FAILED');
    expect(mapped.message).toBe('name is required');
    expect(mapped.requestId).toBe('req-1');
    expect(mapped.exitCode).toBe(EXIT.API);
  });

  it.each([
    [401, EXIT.AUTH, 'UNAUTHENTICATED'],
    [404, EXIT.NOT_FOUND, 'NOT_FOUND'],
    [409, EXIT.CONFLICT, 'CONFLICT'],
    [429, EXIT.CONFLICT, 'RATE_LIMIT_EXCEEDED'],
    [500, EXIT.API, 'SERVER_ERROR'],
  ])('maps status %i to exit %i / %s without a JSON:API body', (status, exitCode, code) => {
    const mapped = mapError(axiosError(status, 'oops'));
    expect(mapped.exitCode).toBe(exitCode);
    expect(mapped.code).toBe(code);
    expect(mapped.status).toBe(status);
  });

  it('maps network errors (no response)', () => {
    const error = new AxiosError('socket hang up', 'ECONNRESET');
    const mapped = mapError(error);
    expect(mapped.code).toBe('NETWORK_ERROR');
    expect(mapped.exitCode).toBe(EXIT.API);
  });

  it('maps zod failures to usage errors (exit 2)', () => {
    const result = z.object({ name: z.string() }).safeParse({});
    expect(result.success).toBe(false);
    if (!result.success) {
      const mapped = mapError(result.error);
      expect(mapped.code).toBe('INVALID_ARGUMENTS');
      expect(mapped.exitCode).toBe(EXIT.USAGE);
      expect(mapped.message).toContain('name');
    }
  });

  it('maps SDK KeltaError-like errors by statusCode', () => {
    const sdkError = Object.assign(new Error('denied'), { statusCode: 401 });
    const mapped = mapError(sdkError);
    expect(mapped.exitCode).toBe(EXIT.AUTH);
    expect(mapped.code).toBe('UNAUTHENTICATED');
  });

  it('falls back for plain and unknown errors', () => {
    expect(mapError(new Error('boom')).code).toBe('ERROR');
    expect(mapError('boom').code).toBe('ERROR');
  });
});

describe('toErrorPayload', () => {
  it('emits the single-line machine contract', () => {
    const payload = toErrorPayload(
      new CliError('bad', { code: 'X', exitCode: 1, status: 400, requestId: 'r1' })
    );
    expect(JSON.parse(payload)).toEqual({
      error: { code: 'X', status: 400, detail: 'bad', requestId: 'r1' },
    });
    expect(payload).not.toContain('\n');
  });
});
