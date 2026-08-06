import { describe, it, expect, vi } from 'vitest';
import type { CommandContext, RegisteredCommand } from '../registry/types.js';
import { apiCommands } from './api.js';

function fakeCtx() {
  const request = vi.fn().mockResolvedValue({ data: { data: [{ id: '1' }], meta: { x: 1 } } });
  const ctx = {
    profile: { name: 'test' },
    global: { raw: false, quiet: false, yes: true },
    log: vi.fn(),
    client: { getAxiosInstance: () => ({ request }) },
  } as unknown as CommandContext;
  return { ctx, request };
}

const def = apiCommands[0] as RegisteredCommand;

async function run(input: Record<string, unknown>, ctx: CommandContext) {
  return def.handler(ctx, def.input.parse(input) as never);
}

describe('kelta api', () => {
  it('passes method/path/body/headers through and returns the body verbatim', async () => {
    const { ctx, request } = fakeCtx();
    const result = await run(
      {
        method: 'post',
        path: '/api/collections?page[size]=5',
        data: '[1,2]',
        header: ['X-Trace: abc', 'Accept: application/vnd.api+json'],
      },
      ctx
    );
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: '/api/collections?page[size]=5',
      data: [1, 2],
      headers: { 'X-Trace': 'abc', Accept: 'application/vnd.api+json' },
    });
    // verbatim: the JSON:API envelope is NOT flattened
    expect(result.verbatim).toBe(true);
    expect(result.data).toEqual({ data: [{ id: '1' }], meta: { x: 1 } });
  });

  it('accepts any JSON body shape (arrays, scalars)', async () => {
    const { ctx, request } = fakeCtx();
    await run({ method: 'put', path: '/api/governor-limits', data: '{"a":1}' }, ctx);
    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'PUT', data: { a: 1 } })
    );
  });

  it('rejects unsupported methods, relative paths, and malformed headers', async () => {
    const { ctx } = fakeCtx();
    await expect(run({ method: 'trace', path: '/x' }, ctx)).rejects.toMatchObject({
      code: 'INVALID_ARGUMENTS',
    });
    await expect(run({ method: 'get', path: 'api/x' }, ctx)).rejects.toMatchObject({
      code: 'INVALID_ARGUMENTS',
    });
    await expect(
      run({ method: 'get', path: '/x', header: ['noseparator'] }, ctx)
    ).rejects.toMatchObject({ code: 'INVALID_ARGUMENTS' });
  });

  it('treats non-GET as dangerous and GET as safe', () => {
    const dangerous = def.dangerous as (input: { method: string }) => boolean;
    expect(dangerous(def.input.parse({ method: 'get', path: '/x' }) as { method: string })).toBe(
      false
    );
    expect(dangerous(def.input.parse({ method: 'delete', path: '/x' }) as { method: string })).toBe(
      true
    );
  });
});
