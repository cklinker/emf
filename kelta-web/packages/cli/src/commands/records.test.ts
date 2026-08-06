import { describe, it, expect, vi } from 'vitest';
import type { CommandContext } from '../registry/types.js';
import { recordCommands } from './records.js';

function command(name: string) {
  const def = recordCommands.find((c) => c.name === name);
  if (!def) throw new Error(`no command ${name}`);
  return def;
}

interface FakeResource {
  list: ReturnType<typeof vi.fn>;
  get: ReturnType<typeof vi.fn>;
  create: ReturnType<typeof vi.fn>;
  patch: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
}

function fakeCtx(resource: Partial<FakeResource>) {
  const resourceFor = vi.fn().mockReturnValue(resource);
  const ctx = {
    profile: { name: 'test' },
    global: { raw: false, quiet: false, yes: true },
    log: vi.fn(),
    client: { resource: resourceFor },
  } as unknown as CommandContext;
  return { ctx, resourceFor };
}

describe('records list', () => {
  it('maps flags to SDK list options', async () => {
    const list = vi.fn().mockResolvedValue({ data: [{ id: '1', attributes: {} }] });
    const { ctx, resourceFor } = fakeCtx({ list });
    const def = command('list');
    const input = def.input.parse({
      collection: 'invoices',
      filter: ['status=open', 'amount.gte=10'],
      sort: '-createdAt',
      fields: 'status,amount',
      include: 'customer',
      page: '2',
      size: '50',
      all: false,
    });
    const result = await def.handler(ctx, input as never);

    expect(resourceFor).toHaveBeenCalledWith('invoices');
    expect(list).toHaveBeenCalledWith({
      filters: [
        { field: 'status', operator: 'eq', value: 'open' },
        { field: 'amount', operator: 'gte', value: '10' },
      ],
      sort: [{ field: 'createdAt', direction: 'desc' }],
      fields: ['status', 'amount'],
      include: ['customer'],
      page: 2,
      size: 50,
    });
    expect(result.data).toEqual({ data: [{ id: '1', attributes: {} }] });
  });

  it('rejects a size above the HTTP clamp', () => {
    const def = command('list');
    expect(() => def.input.parse({ collection: 'x', filter: [], size: '500' })).toThrow();
  });

  it('--all paginates until a short page', async () => {
    const pageOf = (n: number, size: number) =>
      Array.from({ length: size }, (_, i) => ({ id: `${String(n)}-${String(i)}` }));
    const list = vi
      .fn()
      .mockResolvedValueOnce({ data: pageOf(1, 200) })
      .mockResolvedValueOnce({ data: pageOf(2, 3) });
    const { ctx } = fakeCtx({ list });
    const def = command('list');
    const input = def.input.parse({ collection: 'x', filter: [], all: true });
    const result = await def.handler(ctx, input as never);

    expect(list).toHaveBeenCalledTimes(2);
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 200 }));
    expect((result.data as { data: unknown[] }).data).toHaveLength(203);
  });
});

describe('records get/create/update/delete', () => {
  it('get passes include options', async () => {
    const get = vi.fn().mockResolvedValue({ data: { id: '9' } });
    const { ctx } = fakeCtx({ get });
    const def = command('get');
    await def.handler(ctx, def.input.parse({ collection: 'x', id: '9', include: 'a,b' }) as never);
    expect(get).toHaveBeenCalledWith('9', { include: ['a', 'b'] });
  });

  it('create parses --data and calls the SDK', async () => {
    const create = vi.fn().mockResolvedValue({ data: { id: 'n1' } });
    const { ctx } = fakeCtx({ create });
    const def = command('create');
    await def.handler(ctx, def.input.parse({ collection: 'x', data: '{"name":"a"}' }) as never);
    expect(create).toHaveBeenCalledWith({ name: 'a' });
  });

  it('update patches attributes', async () => {
    const patch = vi.fn().mockResolvedValue({ data: { id: 'n1' } });
    const { ctx } = fakeCtx({ patch });
    const def = command('update');
    await def.handler(
      ctx,
      def.input.parse({ collection: 'x', id: 'n1', data: '{"a":2}' }) as never
    );
    expect(patch).toHaveBeenCalledWith('n1', { a: 2 });
  });

  it('delete is dangerous and reports the deleted id', async () => {
    const del = vi.fn().mockResolvedValue(undefined);
    const { ctx } = fakeCtx({ delete: del });
    const def = command('delete');
    expect(def.dangerous).toBe(true);
    const result = await def.handler(ctx, def.input.parse({ collection: 'x', id: 'n1' }) as never);
    expect(del).toHaveBeenCalledWith('n1');
    expect(result.ids).toEqual(['n1']);
  });
});
