import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Command } from 'commander';
import type { AxiosInstance } from 'axios';

const mockClient = { get: vi.fn(), post: vi.fn() };

vi.mock('../client.js', () => ({ createClient: () => mockClient }));

import {
  parseItemSpec,
  runSandboxCreate,
  runSandboxList,
  runSandboxStatus,
  runSandboxRefresh,
  runPromoteCreate,
  runPromotePreview,
  runPromoteStatus,
  runPromoteApprove,
  runPromoteExecute,
  runPromoteRollback,
  registerEnvironmentCommands,
} from './environments.js';

const client = mockClient as unknown as AxiosInstance;

function envResource(id: string, attributes: Record<string, unknown>) {
  return { id, type: 'environments', attributes };
}

function envList(envs: unknown[]) {
  return { status: 200, data: { data: envs } };
}

function promotion(id: string, attributes: Record<string, unknown>) {
  return { status: 200, data: { data: { id, type: 'promotions', attributes } } };
}

beforeEach(() => {
  mockClient.get.mockReset();
  mockClient.post.mockReset();
});

describe('parseItemSpec', () => {
  it('splits on the first colon only', () => {
    expect(parseItemSpec('collection:orders')).toEqual({
      itemType: 'collection',
      itemName: 'orders',
    });
    expect(parseItemSpec('flow:sync:daily')).toEqual({ itemType: 'flow', itemName: 'sync:daily' });
  });

  it('rejects specs without a type or name', () => {
    expect(() => parseItemSpec('orders')).toThrow(/Invalid --item/);
    expect(() => parseItemSpec(':orders')).toThrow(/Invalid --item/);
    expect(() => parseItemSpec('collection:')).toThrow(/Invalid --item/);
  });
});

describe('sandbox ops', () => {
  describe('runSandboxCreate', () => {
    it('posts the sandbox create contract and returns the resource', async () => {
      mockClient.post.mockResolvedValue({
        status: 202,
        data: {
          data: envResource('env-1', {
            status: 'PROVISIONING',
            sandboxSlug: 'acme-dev',
            adminUsername: 'admin@acme-dev',
            adminInitialPassword: 's3cret',
          }),
        },
      });

      const env = await runSandboxCreate(client, { name: 'dev', description: 'Dev sandbox' });

      expect(mockClient.post).toHaveBeenCalledWith('/api/environments', {
        data: { attributes: { name: 'dev', type: 'SANDBOX', description: 'Dev sandbox' } },
      });
      expect(env.id).toBe('env-1');
      expect(env.attributes?.adminInitialPassword).toBe('s3cret');
    });

    it('omits description when not provided', async () => {
      mockClient.post.mockResolvedValue({
        status: 202,
        data: { data: envResource('env-1', { status: 'PROVISIONING' }) },
      });

      await runSandboxCreate(client, { name: 'dev' });

      expect(mockClient.post).toHaveBeenCalledWith('/api/environments', {
        data: { attributes: { name: 'dev', type: 'SANDBOX' } },
      });
    });

    it('throws on a non-2xx response', async () => {
      mockClient.post.mockResolvedValue({ status: 403, data: { errors: [] } });
      await expect(runSandboxCreate(client, { name: 'dev' })).rejects.toThrow(/status 403/);
    });
  });

  describe('runSandboxList', () => {
    it('returns the environment resources', async () => {
      mockClient.get.mockResolvedValue(envList([envResource('env-1', { name: 'dev' })]));
      const envs = await runSandboxList(client);
      expect(mockClient.get).toHaveBeenCalledWith('/api/environments');
      expect(envs).toHaveLength(1);
      expect(envs[0].id).toBe('env-1');
    });

    it('returns an empty list when data is missing', async () => {
      mockClient.get.mockResolvedValue({ status: 200, data: {} });
      await expect(runSandboxList(client)).resolves.toEqual([]);
    });

    it('throws on a non-200 response', async () => {
      mockClient.get.mockResolvedValue({ status: 500, data: {} });
      await expect(runSandboxList(client)).rejects.toThrow(/status 500/);
    });
  });

  describe('runSandboxStatus', () => {
    it('returns the matching environment without wait', async () => {
      mockClient.get.mockResolvedValue(
        envList([
          envResource('env-1', { status: 'PROVISIONING' }),
          envResource('env-2', { status: 'ACTIVE' }),
        ])
      );
      const env = await runSandboxStatus(client, 'env-2');
      expect(env.attributes?.status).toBe('ACTIVE');
      expect(mockClient.get).toHaveBeenCalledTimes(1);
    });

    it('throws when the environment is not found', async () => {
      mockClient.get.mockResolvedValue(envList([]));
      await expect(runSandboxStatus(client, 'nope')).rejects.toThrow(/not found/);
    });

    it('polls until the environment reaches a terminal status with wait', async () => {
      mockClient.get
        .mockResolvedValueOnce(envList([envResource('env-1', { status: 'PROVISIONING' })]))
        .mockResolvedValueOnce(envList([envResource('env-1', { status: 'PROVISIONING' })]))
        .mockResolvedValueOnce(envList([envResource('env-1', { status: 'ACTIVE' })]));

      const env = await runSandboxStatus(client, 'env-1', { wait: true, intervalMs: 0 });

      expect(env.attributes?.status).toBe('ACTIVE');
      expect(mockClient.get).toHaveBeenCalledTimes(3);
    });

    it('times out when the environment never becomes terminal', async () => {
      mockClient.get.mockResolvedValue(envList([envResource('env-1', { status: 'PROVISIONING' })]));
      await expect(
        runSandboxStatus(client, 'env-1', { wait: true, intervalMs: 0, maxAttempts: 2 })
      ).rejects.toThrow(/Timed out/);
    });
  });

  describe('runSandboxRefresh', () => {
    it('posts to the refresh endpoint', async () => {
      mockClient.post.mockResolvedValue({ status: 202, data: {} });
      await runSandboxRefresh(client, 'env-1');
      expect(mockClient.post).toHaveBeenCalledWith('/api/environments/env-1/refresh');
    });

    it('throws on a non-2xx response', async () => {
      mockClient.post.mockResolvedValue({ status: 404, data: {} });
      await expect(runSandboxRefresh(client, 'env-1')).rejects.toThrow(/status 404/);
    });
  });
});

describe('promotion ops', () => {
  describe('runPromoteCreate', () => {
    it('posts the promotion create contract with items', async () => {
      mockClient.post.mockResolvedValue(promotion('p-1', { status: 'PENDING_APPROVAL' }));

      const created = await runPromoteCreate(client, {
        sourceEnvId: 'env-1',
        targetEnvId: 'env-2',
        promotionType: 'SELECTIVE',
        conflictMode: 'OVERWRITE',
        items: [{ itemType: 'collection', itemName: 'orders' }],
      });

      expect(mockClient.post).toHaveBeenCalledWith('/api/promotions', {
        data: {
          attributes: {
            sourceEnvId: 'env-1',
            targetEnvId: 'env-2',
            promotionType: 'SELECTIVE',
            conflictMode: 'OVERWRITE',
            items: [{ itemType: 'collection', itemName: 'orders' }],
          },
        },
      });
      expect(created.id).toBe('p-1');
    });

    it('omits items when the list is empty', async () => {
      mockClient.post.mockResolvedValue(promotion('p-1', { status: 'PENDING_APPROVAL' }));

      await runPromoteCreate(client, {
        sourceEnvId: 'env-1',
        targetEnvId: 'env-2',
        promotionType: 'FULL',
        conflictMode: 'SKIP',
        items: [],
      });

      expect(mockClient.post).toHaveBeenCalledWith('/api/promotions', {
        data: {
          attributes: {
            sourceEnvId: 'env-1',
            targetEnvId: 'env-2',
            promotionType: 'FULL',
            conflictMode: 'SKIP',
          },
        },
      });
    });

    it('throws on a non-2xx response', async () => {
      mockClient.post.mockResolvedValue({ status: 422, data: { errors: [] } });
      await expect(
        runPromoteCreate(client, {
          sourceEnvId: 'env-1',
          targetEnvId: 'env-2',
          promotionType: 'FULL',
          conflictMode: 'SKIP',
        })
      ).rejects.toThrow(/status 422/);
    });
  });

  describe('runPromotePreview', () => {
    it('fetches the preview for the source environment', async () => {
      mockClient.get.mockResolvedValue({
        status: 200,
        data: { changes: [{ action: 'CREATE', type: 'collection', name: 'orders' }] },
      });

      const preview = await runPromotePreview(client, 'env-1');

      expect(mockClient.get).toHaveBeenCalledWith('/api/promotions/preview?sourceEnvId=env-1');
      expect(preview.changes).toHaveLength(1);
    });

    it('throws on a non-200 response', async () => {
      mockClient.get.mockResolvedValue({ status: 400, data: {} });
      await expect(runPromotePreview(client, 'env-1')).rejects.toThrow(/status 400/);
    });
  });

  describe('runPromoteStatus', () => {
    it('fetches the promotion by id', async () => {
      mockClient.get.mockResolvedValue(promotion('p-1', { status: 'RUNNING' }));
      const res = await runPromoteStatus(client, 'p-1');
      expect(mockClient.get).toHaveBeenCalledWith('/api/promotions/p-1');
      expect(res.attributes?.status).toBe('RUNNING');
    });

    it('throws when the response has no resource', async () => {
      mockClient.get.mockResolvedValue({ status: 200, data: {} });
      await expect(runPromoteStatus(client, 'p-1')).rejects.toThrow(/not found/);
    });
  });

  describe('runPromoteApprove', () => {
    it('posts to the approve endpoint', async () => {
      mockClient.post.mockResolvedValue(promotion('p-1', { status: 'APPROVED' }));
      const res = await runPromoteApprove(client, 'p-1');
      expect(mockClient.post).toHaveBeenCalledWith('/api/promotions/p-1/approve');
      expect(res?.attributes?.status).toBe('APPROVED');
    });

    it('explains a 409 self-approval rejection', async () => {
      mockClient.post.mockResolvedValue({ status: 409, data: {} });
      await expect(runPromoteApprove(client, 'p-1')).rejects.toThrow(/cannot be approved/);
    });
  });

  describe('runPromoteExecute', () => {
    it('posts execute and returns null without wait', async () => {
      mockClient.post.mockResolvedValue({ status: 202, data: {} });

      const res = await runPromoteExecute(client, 'p-1');

      expect(mockClient.post).toHaveBeenCalledWith('/api/promotions/p-1/execute');
      expect(res).toBeNull();
      expect(mockClient.get).not.toHaveBeenCalled();
    });

    it('polls until the promotion is terminal with wait', async () => {
      mockClient.post.mockResolvedValue({ status: 202, data: {} });
      mockClient.get
        .mockResolvedValueOnce(promotion('p-1', { status: 'RUNNING' }))
        .mockResolvedValueOnce(promotion('p-1', { status: 'COMPLETED', items_promoted: 3 }));

      const res = await runPromoteExecute(client, 'p-1', { wait: true, intervalMs: 0 });

      expect(mockClient.get).toHaveBeenCalledWith('/api/promotions/p-1');
      expect(mockClient.get).toHaveBeenCalledTimes(2);
      expect(res?.attributes?.status).toBe('COMPLETED');
    });

    it('returns the FAILED promotion when execution fails', async () => {
      mockClient.post.mockResolvedValue({ status: 202, data: {} });
      mockClient.get.mockResolvedValue(
        promotion('p-1', { status: 'FAILED', error_message: 'boom' })
      );

      const res = await runPromoteExecute(client, 'p-1', { wait: true, intervalMs: 0 });
      expect(res?.attributes?.status).toBe('FAILED');
    });

    it('times out when the promotion never finishes', async () => {
      mockClient.post.mockResolvedValue({ status: 202, data: {} });
      mockClient.get.mockResolvedValue(promotion('p-1', { status: 'RUNNING' }));

      await expect(
        runPromoteExecute(client, 'p-1', { wait: true, intervalMs: 0, maxAttempts: 2 })
      ).rejects.toThrow(/Timed out/);
    });

    it('throws when the execute request is rejected', async () => {
      mockClient.post.mockResolvedValue({ status: 409, data: {} });
      await expect(runPromoteExecute(client, 'p-1')).rejects.toThrow(/status 409/);
    });
  });

  describe('runPromoteRollback', () => {
    it('posts to the rollback endpoint', async () => {
      mockClient.post.mockResolvedValue({ status: 202, data: {} });
      await runPromoteRollback(client, 'p-1');
      expect(mockClient.post).toHaveBeenCalledWith('/api/promotions/p-1/rollback');
    });

    it('throws on a non-2xx response', async () => {
      mockClient.post.mockResolvedValue({ status: 400, data: {} });
      await expect(runPromoteRollback(client, 'p-1')).rejects.toThrow(/status 400/);
    });
  });
});

describe('environment command wiring', () => {
  beforeEach(() => {
    vi.spyOn(process.stdout, 'write').mockImplementation(() => true);
    vi.spyOn(process.stderr, 'write').mockImplementation(() => true);
    vi.spyOn(process, 'exit').mockImplementation((() => undefined) as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function run(args: string[]) {
    const program = new Command();
    registerEnvironmentCommands(program);
    return program.parseAsync(args, { from: 'user' });
  }

  it('sandbox create prints the one-time credentials block', async () => {
    mockClient.post.mockResolvedValue({
      status: 202,
      data: {
        data: envResource('env-1', {
          status: 'PROVISIONING',
          sandboxSlug: 'acme-dev',
          adminUsername: 'admin@acme-dev',
          adminInitialPassword: 's3cret',
        }),
      },
    });

    await run(['sandbox', 'create', '--name', 'dev', '--description', 'Dev sandbox']);

    const stdout = vi.mocked(process.stdout.write);
    expect(stdout).toHaveBeenCalledWith(expect.stringContaining('Sandbox environment created'));
    expect(stdout).toHaveBeenCalledWith(expect.stringContaining('will NOT be shown again'));
    expect(stdout).toHaveBeenCalledWith(expect.stringContaining('s3cret'));
  });

  it('sandbox list prints an environment table', async () => {
    mockClient.get.mockResolvedValue(
      envList([
        envResource('env-1', {
          name: 'dev',
          type: 'SANDBOX',
          status: 'ACTIVE',
          sandbox_slug: 'acme-dev',
        }),
      ])
    );

    await run(['sandbox', 'list']);

    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('acme-dev')
    );
  });

  it('sandbox status prints the environment', async () => {
    mockClient.get.mockResolvedValue(
      envList([envResource('env-1', { name: 'dev', type: 'SANDBOX', status: 'ACTIVE' })])
    );

    await run(['sandbox', 'status', 'env-1']);

    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('Status: ACTIVE')
    );
  });

  it('sandbox refresh posts and confirms', async () => {
    mockClient.post.mockResolvedValue({ status: 202, data: {} });

    await run(['sandbox', 'refresh', 'env-1']);

    expect(mockClient.post).toHaveBeenCalledWith('/api/environments/env-1/refresh');
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('Refresh started')
    );
  });

  it('promote create uppercases choices and maps repeated --item flags', async () => {
    mockClient.post.mockResolvedValue(promotion('p-1', { status: 'PENDING_APPROVAL' }));

    await run([
      'promote',
      'create',
      '--source',
      'env-1',
      '--target',
      'env-2',
      '--type',
      'selective',
      '--conflict',
      'overwrite',
      '--item',
      'collection:orders',
      '--item',
      'flow:sync',
    ]);

    expect(mockClient.post).toHaveBeenCalledWith('/api/promotions', {
      data: {
        attributes: {
          sourceEnvId: 'env-1',
          targetEnvId: 'env-2',
          promotionType: 'SELECTIVE',
          conflictMode: 'OVERWRITE',
          items: [
            { itemType: 'collection', itemName: 'orders' },
            { itemType: 'flow', itemName: 'sync' },
          ],
        },
      },
    });
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('Promotion created')
    );
  });

  it('promote create rejects an invalid promotion type', async () => {
    await run(['promote', 'create', '--source', 'a', '--target', 'b', '--type', 'partial']);

    expect(mockClient.post).not.toHaveBeenCalled();
    expect(vi.mocked(process.exit)).toHaveBeenCalledWith(1);
    expect(vi.mocked(process.stderr.write)).toHaveBeenCalledWith(
      expect.stringContaining('Invalid promotion type')
    );
  });

  it('promote preview prints the change list', async () => {
    mockClient.get.mockResolvedValue({
      status: 200,
      data: { changes: [{ action: 'CREATE', type: 'collection', name: 'orders' }] },
    });

    await run(['promote', 'preview', '--source', 'env-1']);

    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(expect.stringContaining('orders'));
  });

  it('promote approve confirms approval', async () => {
    mockClient.post.mockResolvedValue(promotion('p-1', { status: 'APPROVED' }));

    await run(['promote', 'approve', 'p-1']);

    expect(mockClient.post).toHaveBeenCalledWith('/api/promotions/p-1/approve');
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('approved')
    );
  });

  it('promote execute --wait polls to the terminal status and prints it', async () => {
    mockClient.post.mockResolvedValue({ status: 202, data: {} });
    mockClient.get.mockResolvedValue(
      promotion('p-1', { status: 'COMPLETED', items_promoted: 2, items_skipped: 1 })
    );

    await run(['promote', 'execute', 'p-1', '--wait']);

    expect(mockClient.post).toHaveBeenCalledWith('/api/promotions/p-1/execute');
    expect(mockClient.get).toHaveBeenCalledWith('/api/promotions/p-1');
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('COMPLETED')
    );
  });

  it('promote execute without --wait prints the follow-up hint', async () => {
    mockClient.post.mockResolvedValue({ status: 202, data: {} });

    await run(['promote', 'execute', 'p-1']);

    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('kelta promote status p-1')
    );
  });

  it('promote execute --wait exits non-zero on FAILED', async () => {
    mockClient.post.mockResolvedValue({ status: 202, data: {} });
    mockClient.get.mockResolvedValue(promotion('p-1', { status: 'FAILED', error_message: 'boom' }));

    await run(['promote', 'execute', 'p-1', '--wait']);

    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(expect.stringContaining('boom'));
    expect(vi.mocked(process.exit)).toHaveBeenCalledWith(1);
  });

  it('promote status prints the promotion details', async () => {
    mockClient.get.mockResolvedValue(
      promotion('p-1', {
        status: 'COMPLETED',
        promotion_type: 'FULL',
        conflict_mode: 'SKIP',
        items_promoted: 5,
        source_env_name: 'dev',
        target_env_name: 'prod',
      })
    );

    await run(['promote', 'status', 'p-1']);

    const stdout = vi.mocked(process.stdout.write);
    expect(stdout).toHaveBeenCalledWith(expect.stringContaining('5 promoted'));
    expect(stdout).toHaveBeenCalledWith(expect.stringContaining('prod'));
  });

  it('promote rollback posts and confirms', async () => {
    mockClient.post.mockResolvedValue({ status: 202, data: {} });

    await run(['promote', 'rollback', 'p-1']);

    expect(mockClient.post).toHaveBeenCalledWith('/api/promotions/p-1/rollback');
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('Rollback started')
    );
  });

  it('exits non-zero when an op fails', async () => {
    mockClient.post.mockResolvedValue({ status: 500, data: {} });

    await run(['sandbox', 'create', '--name', 'dev']);

    expect(vi.mocked(process.exit)).toHaveBeenCalledWith(1);
    expect(vi.mocked(process.stderr.write)).toHaveBeenCalledWith(
      expect.stringContaining('status 500')
    );
  });
});
