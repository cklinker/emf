import { Command } from 'commander';
import { createClient } from '../client.js';

export function registerRecordCommands(program: Command): void {
  const records = program.command('records').description('Record CRUD operations');

  records
    .command('list <collection>')
    .description('List records in a collection')
    .option('--json', 'Output raw JSON')
    .option('--page <number>', 'Page number', '1')
    .option('--size <number>', 'Page size', '25')
    .action(async (collection: string, opts: { json?: boolean; page: string; size: string }) => {
      const client = createClient();
      const res = await client.get(`/api/${collection}`, {
        params: { 'page[number]': opts.page, 'page[size]': opts.size },
      });

      handleResponse(res, opts.json);
    });

  records
    .command('get <collection> <id>')
    .description('Get a single record')
    .option('--json', 'Output raw JSON')
    .action(async (collection: string, id: string, opts: { json?: boolean }) => {
      const client = createClient();
      const res = await client.get(`/api/${collection}/${id}`);
      handleResponse(res, opts.json);
    });

  records
    .command('create <collection>')
    .description('Create a new record')
    .requiredOption('--data <json>', 'Record attributes as JSON')
    .option('--json', 'Output raw JSON')
    .action(async (collection: string, opts: { data: string; json?: boolean }) => {
      const client = createClient();
      const attributes = JSON.parse(opts.data);
      const res = await client.post(`/api/${collection}`, {
        data: { type: collection, attributes },
      });
      handleResponse(res, opts.json);
    });

  records
    .command('update <collection> <id>')
    .description('Update a record')
    .requiredOption('--data <json>', 'Record attributes as JSON')
    .option('--json', 'Output raw JSON')
    .action(async (collection: string, id: string, opts: { data: string; json?: boolean }) => {
      const client = createClient();
      const attributes = JSON.parse(opts.data);
      const res = await client.patch(`/api/${collection}/${id}`, {
        data: { type: collection, id, attributes },
      });
      handleResponse(res, opts.json);
    });

  records
    .command('delete <collection> <id>')
    .description('Delete a record')
    .action(async (collection: string, id: string) => {
      const client = createClient();
      const res = await client.delete(`/api/${collection}/${id}`);

      if (res.status === 204 || res.status === 200) {
        console.log(`Deleted ${collection}/${id}`);
      } else {
        console.error(`Error ${res.status}: ${JSON.stringify(res.data)}`);
        process.exit(1);
      }
    });
}

function handleResponse(res: { status: number; data: unknown }, json?: boolean): void {
  if (res.status >= 400) {
    console.error(`Error ${res.status}: ${JSON.stringify(res.data, null, 2)}`);
    process.exit(1);
  }

  if (json) {
    console.log(JSON.stringify(res.data, null, 2));
  } else {
    console.log(JSON.stringify(res.data, null, 2));
  }
}
