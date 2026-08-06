import { readFileSync, writeFileSync } from 'node:fs';
import type { AxiosInstance } from 'axios';
import { z } from 'zod';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

export interface ExportOptions {
  name: string;
  version: string;
  output?: string;
}

/**
 * Export this tenant's metadata as a package file (GitOps-friendly).
 * POST /api/packages/export → write the returned package JSON to disk.
 * Returns the path written.
 */
export async function runExport(client: AxiosInstance, opts: ExportOptions): Promise<string> {
  const res = await client.post(
    '/api/packages/export',
    { name: opts.name, version: opts.version },
    { responseType: 'arraybuffer' }
  );
  if (res.status !== 200) {
    throw new Error(`Export failed (status ${String(res.status)})`);
  }
  const file = opts.output ?? `${opts.name}-${opts.version}.json`;
  writeFileSync(file, Buffer.from(res.data as ArrayBuffer));
  return file;
}

/** Preview the changes a package file would make — POST /api/packages/import/preview (no writes). */
export async function runDiff(client: AxiosInstance, file: string): Promise<unknown> {
  const res = await uploadPackage(client, '/api/packages/import/preview', file);
  return res.data as unknown;
}

/** Apply a package file — POST /api/packages/import (with optional dryRun). */
export async function runApply(
  client: AxiosInstance,
  file: string,
  opts: { dryRun?: boolean }
): Promise<unknown> {
  const url = `/api/packages/import${opts.dryRun ? '?dryRun=true' : ''}`;
  const res = await uploadPackage(client, url, file);
  return res.data as unknown;
}

/** Upload a package file as multipart {@code file=...} and return the response. */
async function uploadPackage(client: AxiosInstance, url: string, file: string) {
  const buffer = readFileSync(file);
  const form = new FormData();
  const name = file.split('/').pop() ?? 'package.json';
  form.append('file', new Blob([new Uint8Array(buffer)], { type: 'application/json' }), name);
  const res = await client.post(url, form);
  if (res.status !== 200) {
    throw new Error(`Request failed (status ${String(res.status)}): ${JSON.stringify(res.data)}`);
  }
  return res;
}

const exportCommand = defineCommand({
  group: 'metadata',
  name: 'export',
  summary: "Export this tenant's metadata as a package file",
  options: [
    { flag: '-n, --name <name>', description: 'Package name' },
    { flag: '-v, --version <version>', description: 'Package version' },
    { flag: '-o, --out <file>', description: 'Output file (default: <name>-<version>.json)' },
  ],
  input: z.object({
    name: z.string().min(1),
    version: z.string().min(1),
    out: z.string().optional(),
  }),
  handler: async (ctx, input) => {
    const path = await runExport(ctx.client.getAxiosInstance(), {
      name: input.name,
      version: input.version,
      output: input.out,
    });
    return { data: { file: path }, message: `Exported package to ${path}` };
  },
});

const diff = defineCommand({
  group: 'metadata',
  name: 'diff',
  summary: 'Preview the changes a package file would make (no writes)',
  positionals: [{ name: 'file', description: 'Package file', required: true }],
  input: z.object({ file: z.string().min(1) }),
  handler: async (ctx, input) => {
    const result = await runDiff(ctx.client.getAxiosInstance(), input.file);
    return { data: result };
  },
});

const apply = defineCommand({
  group: 'metadata',
  name: 'apply',
  summary: 'Apply a package file to this tenant',
  dangerous: (input) => !input.dryRun,
  positionals: [{ name: 'file', description: 'Package file', required: true }],
  options: [{ flag: '--dry-run', description: 'Validate without writing' }],
  input: z.object({ file: z.string().min(1), dryRun: z.boolean().default(false) }),
  handler: async (ctx, input) => {
    const result = await runApply(ctx.client.getAxiosInstance(), input.file, {
      dryRun: input.dryRun,
    });
    return { data: result };
  },
});

export const metadataCommands: RegisteredCommand[] = [exportCommand, diff, apply];
