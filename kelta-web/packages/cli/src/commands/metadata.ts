import { Command } from 'commander';
import { readFileSync, writeFileSync } from 'node:fs';
import type { AxiosInstance } from 'axios';
import { createClient } from '../client.js';

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

/** Run an op and print its JSON result; exit non-zero with a message on failure. */
async function emit(op: Promise<unknown>): Promise<void> {
  try {
    const result = await op;
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  } catch (e) {
    process.stderr.write(`${(e as Error).message}\n`);
    process.exit(1);
  }
}

export function registerMetadataCommands(program: Command): void {
  const meta = program
    .command('metadata')
    .description('Export, diff, and apply tenant metadata (GitOps for config)');

  meta
    .command('export')
    .description("Export this tenant's metadata as a package file")
    .requiredOption('-n, --name <name>', 'Package name')
    .requiredOption('-v, --version <version>', 'Package version')
    .option('-o, --output <file>', 'Output file (default: <name>-<version>.json)')
    .action(async (opts: ExportOptions) => {
      try {
        const path = await runExport(createClient(), opts);
        process.stdout.write(`Exported package to ${path}\n`);
      } catch (e) {
        process.stderr.write(`${(e as Error).message}\n`);
        process.exit(1);
      }
    });

  meta
    .command('diff <file>')
    .description('Preview the changes a package file would make (no writes)')
    .action(async (file: string) => {
      await emit(runDiff(createClient(), file));
    });

  meta
    .command('apply <file>')
    .description('Apply a package file to this tenant')
    .option('--dry-run', 'Validate without writing')
    .action(async (file: string, opts: { dryRun?: boolean }) => {
      await emit(runApply(createClient(), file, { dryRun: opts.dryRun }));
    });
}
