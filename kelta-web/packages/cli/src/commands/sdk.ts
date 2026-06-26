import { Command } from 'commander';
import { writeFileSync } from 'node:fs';
import type { AxiosInstance } from 'axios';
import { generateTypesFromSpec } from '@kelta/sdk/cli';
import { createClient } from '../client.js';

export interface GenerateTypesResult {
  file: string;
  typesGenerated: number;
}

/**
 * Generates TypeScript types for this tenant by fetching its live OpenAPI
 * document (`GET /api/docs/openapi.json`) through the authenticated CLI client
 * and delegating to the `@kelta/sdk` OpenAPI type generator. Returns the file
 * written and the number of types generated.
 *
 * <p>This is the CLI-native, authenticated entry point to the same generator the
 * `kelta-generate-types` bin exposes — no OpenAPI URL or token needs to be passed
 * once `kelta auth login` has run.
 */
export async function runGenerateTypes(
  client: AxiosInstance,
  opts: { output?: string }
): Promise<GenerateTypesResult> {
  const res = await client.get('/api/docs/openapi.json');
  if (res.status !== 200) {
    throw new Error(`Failed to fetch OpenAPI spec (status ${String(res.status)})`);
  }
  const file = opts.output ?? 'kelta-types.ts';
  const { content, result } = generateTypesFromSpec(res.data as unknown, file, {
    includeRequests: true,
    includeResponses: true,
  });
  if (!result.success) {
    throw new Error(`Type generation failed: ${(result.errors ?? ['unknown error']).join('; ')}`);
  }
  writeFileSync(file, content);
  return { file, typesGenerated: result.typesGenerated };
}

export function registerSdkCommands(program: Command): void {
  const sdk = program
    .command('sdk')
    .description('Generate typed SDK artifacts from this tenant’s schema');

  sdk
    .command('types')
    .description('Generate TypeScript types from this tenant’s live OpenAPI document')
    .option('-o, --output <file>', 'Output file (default: kelta-types.ts)')
    .action(async (opts: { output?: string }) => {
      try {
        const { file, typesGenerated } = await runGenerateTypes(createClient(), opts);
        process.stdout.write(`Wrote ${String(typesGenerated)} types to ${file}\n`);
      } catch (e) {
        process.stderr.write(`${(e as Error).message}\n`);
        process.exit(1);
      }
    });
}
