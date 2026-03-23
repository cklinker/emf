import { Command } from 'commander';
import { createClient } from '../client.js';

export function registerCollectionCommands(program: Command): void {
  const collections = program.command('collections').description('Collection management');

  collections
    .command('list')
    .description('List all collections')
    .option('--json', 'Output raw JSON')
    .action(async (opts: { json?: boolean }) => {
      const client = createClient();
      const res = await client.get('/api/collections');

      if (res.status !== 200) {
        console.error(`Error ${res.status}: ${JSON.stringify(res.data)}`);
        process.exit(1);
      }

      if (opts.json) {
        console.log(JSON.stringify(res.data, null, 2));
        return;
      }

      const collections = res.data?.data || [];
      if (collections.length === 0) {
        console.log('No collections found.');
        return;
      }

      console.log(`${'Name'.padEnd(30)} ${'Display Name'.padEnd(30)} ${'Fields'.padEnd(8)} Read-Only`);
      console.log('-'.repeat(80));
      for (const col of collections) {
        const attrs = col.attributes || col;
        const name = (attrs.name || col.id || '').padEnd(30);
        const display = (attrs.displayName || '').padEnd(30);
        const fields = String(attrs.fields?.length || 0).padEnd(8);
        const readOnly = attrs.readOnly ? 'Yes' : 'No';
        console.log(`${name} ${display} ${fields} ${readOnly}`);
      }
    });

  collections
    .command('describe <name>')
    .description('Show collection details and fields')
    .option('--json', 'Output raw JSON')
    .action(async (name: string, opts: { json?: boolean }) => {
      const client = createClient();
      const res = await client.get(`/api/collections/${name}`);

      if (res.status !== 200) {
        console.error(`Error ${res.status}: ${JSON.stringify(res.data)}`);
        process.exit(1);
      }

      if (opts.json) {
        console.log(JSON.stringify(res.data, null, 2));
        return;
      }

      const col = res.data?.data?.attributes || res.data?.data || res.data;
      console.log(`Collection: ${col.name || name}`);
      console.log(`Display:    ${col.displayName || ''}`);
      console.log(`Read-Only:  ${col.readOnly ? 'Yes' : 'No'}`);
      console.log(`\nFields:`);
      console.log(`  ${'Name'.padEnd(25)} ${'Type'.padEnd(15)} Nullable`);
      console.log(`  ${'-'.repeat(50)}`);
      for (const field of col.fields || []) {
        console.log(`  ${(field.name || '').padEnd(25)} ${(field.type || '').padEnd(15)} ${field.nullable ? 'Yes' : 'No'}`);
      }
    });
}
