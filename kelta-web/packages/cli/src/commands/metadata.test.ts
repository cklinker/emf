import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Command } from 'commander';
import type { AxiosInstance } from 'axios';

const mockClient = { post: vi.fn() };

vi.mock('../client.js', () => ({ createClient: () => mockClient }));
vi.mock('node:fs', () => {
  const readFileSync = vi.fn(() => Buffer.from('{"name":"app"}'));
  const writeFileSync = vi.fn();
  return { readFileSync, writeFileSync, default: { readFileSync, writeFileSync } };
});

import { writeFileSync } from 'node:fs';
import { runExport, runDiff, runApply, registerMetadataCommands } from './metadata.js';

const client = mockClient as unknown as AxiosInstance;

beforeEach(() => {
  mockClient.post.mockReset();
  vi.mocked(writeFileSync).mockClear();
});

describe('metadata ops', () => {
  describe('runExport', () => {
    it('posts options and writes the package to a default filename', async () => {
      mockClient.post.mockResolvedValue({ status: 200, data: new Uint8Array([1, 2, 3]).buffer });

      const path = await runExport(client, { name: 'app', version: '1.0' });

      expect(path).toBe('app-1.0.json');
      expect(mockClient.post).toHaveBeenCalledWith(
        '/api/packages/export',
        { name: 'app', version: '1.0' },
        expect.objectContaining({ responseType: 'arraybuffer' })
      );
      expect(writeFileSync).toHaveBeenCalledWith('app-1.0.json', expect.any(Buffer));
    });

    it('honours an explicit output path', async () => {
      mockClient.post.mockResolvedValue({ status: 200, data: new Uint8Array([1]).buffer });
      const path = await runExport(client, { name: 'app', version: '1.0', output: 'out.json' });
      expect(path).toBe('out.json');
    });

    it('throws on a non-200 export', async () => {
      mockClient.post.mockResolvedValue({ status: 500, data: {} });
      await expect(runExport(client, { name: 'app', version: '1.0' })).rejects.toThrow(
        /Export failed/
      );
    });
  });

  describe('runDiff', () => {
    it('uploads the file to the preview endpoint and returns the diff', async () => {
      mockClient.post.mockResolvedValue({
        status: 200,
        data: { changes: [{ type: 'collection' }] },
      });

      const diff = await runDiff(client, 'pkg.json');

      expect(diff).toEqual({ changes: [{ type: 'collection' }] });
      expect(mockClient.post).toHaveBeenCalledWith(
        '/api/packages/import/preview',
        expect.any(FormData)
      );
    });

    it('throws on a non-200 response', async () => {
      mockClient.post.mockResolvedValue({ status: 400, data: { error: 'bad package' } });
      await expect(runDiff(client, 'pkg.json')).rejects.toThrow(/Request failed/);
    });
  });

  describe('runApply', () => {
    it('applies without dryRun', async () => {
      mockClient.post.mockResolvedValue({ status: 200, data: { applied: true } });
      await runApply(client, 'pkg.json', {});
      expect(mockClient.post).toHaveBeenCalledWith('/api/packages/import', expect.any(FormData));
    });

    it('passes dryRun as a query param', async () => {
      mockClient.post.mockResolvedValue({ status: 200, data: { applied: false } });
      await runApply(client, 'pkg.json', { dryRun: true });
      expect(mockClient.post).toHaveBeenCalledWith(
        '/api/packages/import?dryRun=true',
        expect.any(FormData)
      );
    });
  });
});

describe('metadata command wiring', () => {
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
    registerMetadataCommands(program);
    return program.parseAsync(args, { from: 'user' });
  }

  it('export command writes a file and prints the path', async () => {
    mockClient.post.mockResolvedValue({ status: 200, data: new Uint8Array([1]).buffer });
    await run(['metadata', 'export', '-n', 'app', '-v', '2']);
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('Exported package to app-2.json')
    );
  });

  it('diff command prints the preview JSON', async () => {
    mockClient.post.mockResolvedValue({ status: 200, data: { changes: [] } });
    await run(['metadata', 'diff', 'pkg.json']);
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('"changes"')
    );
  });

  it('apply command prints the result JSON', async () => {
    mockClient.post.mockResolvedValue({ status: 200, data: { applied: true } });
    await run(['metadata', 'apply', 'pkg.json', '--dry-run']);
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('"applied"')
    );
  });

  it('exits non-zero when an op fails', async () => {
    mockClient.post.mockResolvedValue({ status: 500, data: {} });
    await run(['metadata', 'export', '-n', 'app', '-v', '2']);
    expect(vi.mocked(process.exit)).toHaveBeenCalledWith(1);
    expect(vi.mocked(process.stderr.write)).toHaveBeenCalled();
  });

  it('diff exits non-zero on a failed upload', async () => {
    mockClient.post.mockResolvedValue({ status: 400, data: { error: 'bad' } });
    await run(['metadata', 'diff', 'pkg.json']);
    expect(vi.mocked(process.exit)).toHaveBeenCalledWith(1);
  });
});
