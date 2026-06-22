import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Command } from 'commander';
import type { AxiosInstance } from 'axios';

const mockClient = { get: vi.fn() };
// vi.hoisted so the mock fn exists before vi.mock's factory (which is hoisted) runs.
const { generateTypesFromSpec } = vi.hoisted(() => ({ generateTypesFromSpec: vi.fn() }));

vi.mock('../client.js', () => ({ createClient: () => mockClient }));
vi.mock('@kelta/sdk/cli', () => ({ generateTypesFromSpec }));
vi.mock('node:fs', () => {
  const writeFileSync = vi.fn();
  return { writeFileSync, default: { writeFileSync } };
});

import { writeFileSync } from 'node:fs';
import { runGenerateTypes, registerSdkCommands } from './sdk.js';

const client = mockClient as unknown as AxiosInstance;

beforeEach(() => {
  mockClient.get.mockReset();
  generateTypesFromSpec.mockReset();
  vi.mocked(writeFileSync).mockClear();
});

describe('runGenerateTypes', () => {
  it('fetches the OpenAPI doc, delegates to the generator, and writes the file', async () => {
    mockClient.get.mockResolvedValue({ status: 200, data: { openapi: '3.0.0', paths: {} } });
    generateTypesFromSpec.mockReturnValue({
      content: '// types',
      result: { success: true, typesGenerated: 4, outputPath: 'kelta-types.ts' },
    });

    const res = await runGenerateTypes(client, {});

    expect(res).toEqual({ file: 'kelta-types.ts', typesGenerated: 4 });
    expect(mockClient.get).toHaveBeenCalledWith('/api/docs/openapi.json');
    expect(generateTypesFromSpec).toHaveBeenCalledWith(
      { openapi: '3.0.0', paths: {} },
      'kelta-types.ts',
      expect.objectContaining({ includeRequests: true, includeResponses: true })
    );
    expect(writeFileSync).toHaveBeenCalledWith('kelta-types.ts', '// types');
  });

  it('honours an explicit output path', async () => {
    mockClient.get.mockResolvedValue({ status: 200, data: { openapi: '3.0.0' } });
    generateTypesFromSpec.mockReturnValue({
      content: '// out',
      result: { success: true, typesGenerated: 1, outputPath: 'out.ts' },
    });

    const res = await runGenerateTypes(client, { output: 'out.ts' });

    expect(res.file).toBe('out.ts');
    expect(generateTypesFromSpec).toHaveBeenCalledWith(
      expect.anything(),
      'out.ts',
      expect.anything()
    );
    expect(writeFileSync).toHaveBeenCalledWith('out.ts', '// out');
  });

  it('throws when the spec fetch fails', async () => {
    mockClient.get.mockResolvedValue({ status: 500, data: {} });
    await expect(runGenerateTypes(client, {})).rejects.toThrow(/Failed to fetch OpenAPI spec/);
    expect(generateTypesFromSpec).not.toHaveBeenCalled();
  });

  it('throws (and does not write) when generation reports failure', async () => {
    mockClient.get.mockResolvedValue({ status: 200, data: {} });
    generateTypesFromSpec.mockReturnValue({
      content: '',
      result: {
        success: false,
        typesGenerated: 0,
        outputPath: 'kelta-types.ts',
        errors: ['bad spec'],
      },
    });

    await expect(runGenerateTypes(client, {})).rejects.toThrow(/Type generation failed: bad spec/);
    expect(writeFileSync).not.toHaveBeenCalled();
  });
});

describe('sdk command wiring', () => {
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
    registerSdkCommands(program);
    return program.parseAsync(args, { from: 'user' });
  }

  it('types command writes a file and prints the count + path', async () => {
    mockClient.get.mockResolvedValue({ status: 200, data: { openapi: '3.0.0' } });
    generateTypesFromSpec.mockReturnValue({
      content: '// t',
      result: { success: true, typesGenerated: 7, outputPath: 'types.ts' },
    });
    await run(['sdk', 'types', '-o', 'types.ts']);
    expect(vi.mocked(process.stdout.write)).toHaveBeenCalledWith(
      expect.stringContaining('Wrote 7 types to types.ts')
    );
  });

  it('exits non-zero when generation fails', async () => {
    mockClient.get.mockResolvedValue({ status: 500, data: {} });
    await run(['sdk', 'types']);
    expect(vi.mocked(process.exit)).toHaveBeenCalledWith(1);
    expect(vi.mocked(process.stderr.write)).toHaveBeenCalled();
  });
});
