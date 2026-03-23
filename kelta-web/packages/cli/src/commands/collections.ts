import { Command } from 'commander';
import { createClient } from '../client.js';

interface CollectionAttrs {
  name?: string;
  displayName?: string;
  fields?: { name: string; type: string; nullable: boolean }[];
  readOnly?: boolean;
}

interface CollectionResource {
  id: string;
  attributes?: CollectionAttrs;
}

interface CollectionResponse {
  data?: CollectionResource[] | CollectionResource;
}

export function registerCollectionCommands(program: Command): void {
  const collections = program.command('collections').description('Collection management');

  collections
    .command('list')
    .description('List all collections')
    .option('--json', 'Output raw JSON')
    .action(async (opts: { json?: boolean }) => {
      const client = createClient();
      const res = await client.get<CollectionResponse>('/api/collections');

      if (res.status !== 200) {
        process.stderr.write(`Error ${String(res.status)}: ${JSON.stringify(res.data)}\n`);
        process.exit(1);
      }

      if (opts.json) {
        process.stdout.write(JSON.stringify(res.data, null, 2) + '\n');
        return;
      }

      const data = Array.isArray(res.data?.data) ? res.data.data : [];
      if (data.length === 0) {
        process.stdout.write('No collections found.\n');
        return;
      }

      process.stdout.write(`${'Name'.padEnd(30)} ${'Display Name'.padEnd(30)} ${'Fields'.padEnd(8)} Read-Only\n`);
      process.stdout.write('-'.repeat(80) + '\n');
      for (const col of data) {
        const attrs: CollectionAttrs = col.attributes ?? {};
        const name = (attrs.name ?? col.id ?? '').padEnd(30);
        const display = (attrs.displayName ?? '').padEnd(30);
        const fields = String(attrs.fields?.length ?? 0).padEnd(8);
        const readOnly = attrs.readOnly ? 'Yes' : 'No';
        process.stdout.write(`${name} ${display} ${fields} ${readOnly}\n`);
      }
    });

  collections
    .command('describe <name>')
    .description('Show collection details and fields')
    .option('--json', 'Output raw JSON')
    .action(async (name: string, opts: { json?: boolean }) => {
      const client = createClient();
      const res = await client.get<CollectionResponse>(`/api/collections/${name}`);

      if (res.status !== 200) {
        process.stderr.write(`Error ${String(res.status)}: ${JSON.stringify(res.data)}\n`);
        process.exit(1);
      }

      if (opts.json) {
        process.stdout.write(JSON.stringify(res.data, null, 2) + '\n');
        return;
      }

      const resource = !Array.isArray(res.data?.data) ? res.data?.data : undefined;
      const attrs: CollectionAttrs = resource?.attributes ?? {};
      process.stdout.write(`Collection: ${attrs.name ?? name}\n`);
      process.stdout.write(`Display:    ${attrs.displayName ?? ''}\n`);
      process.stdout.write(`Read-Only:  ${attrs.readOnly ? 'Yes' : 'No'}\n`);
      process.stdout.write('\nFields:\n');
      process.stdout.write(`  ${'Name'.padEnd(25)} ${'Type'.padEnd(15)} Nullable\n`);
      process.stdout.write(`  ${'-'.repeat(50)}\n`);
      for (const field of attrs.fields ?? []) {
        process.stdout.write(`  ${field.name.padEnd(25)} ${field.type.padEnd(15)} ${field.nullable ? 'Yes' : 'No'}\n`);
      }
    });
}
