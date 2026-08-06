import { z } from 'zod';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

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

const list = defineCommand({
  group: 'collections',
  name: 'list',
  summary: 'List all collections',
  input: z.object({}),
  handler: async (ctx) => {
    const body = (await ctx.client.resource('collections').list({ size: 200 })) as {
      data?: CollectionResource[];
    };
    const rows = (body.data ?? []).map((col) => ({
      id: col.id,
      name: col.attributes?.name ?? col.id,
      displayName: col.attributes?.displayName ?? '',
      fields: col.attributes?.fields?.length ?? 0,
      readOnly: col.attributes?.readOnly ?? false,
    }));
    return {
      data: body,
      columns: [
        { key: 'name', header: 'NAME' },
        { key: 'displayName', header: 'DISPLAY NAME' },
        { key: 'fields', header: 'FIELDS' },
        { key: 'readOnly', header: 'READ-ONLY' },
      ],
      human: rowsAsTable(rows),
    };
  },
});

function rowsAsTable(
  rows: { name: string; displayName: string; fields: number; readOnly: boolean }[]
): string {
  if (rows.length === 0) return 'No collections found.\n';
  const out = [
    `${'NAME'.padEnd(30)} ${'DISPLAY NAME'.padEnd(30)} ${'FIELDS'.padEnd(8)} READ-ONLY`,
    '-'.repeat(80),
  ];
  for (const row of rows) {
    out.push(
      `${row.name.padEnd(30)} ${row.displayName.padEnd(30)} ${String(row.fields).padEnd(8)} ${row.readOnly ? 'Yes' : 'No'}`
    );
  }
  return out.join('\n') + '\n';
}

const describe = defineCommand({
  group: 'collections',
  name: 'describe',
  summary: 'Show collection details and fields',
  positionals: [{ name: 'name', description: 'Collection name', required: true }],
  input: z.object({ name: z.string().min(1) }),
  handler: async (ctx, input) => {
    const body = (await ctx.client.resource('collections').get(input.name)) as {
      data?: CollectionResource;
    };
    const attrs = body.data?.attributes ?? {};
    const lines = [
      `Collection: ${attrs.name ?? input.name}`,
      `Display:    ${attrs.displayName ?? ''}`,
      `Read-Only:  ${attrs.readOnly ? 'Yes' : 'No'}`,
      '',
      'Fields:',
      `  ${'Name'.padEnd(25)} ${'Type'.padEnd(15)} Nullable`,
      `  ${'-'.repeat(50)}`,
      ...(attrs.fields ?? []).map(
        (field) =>
          `  ${field.name.padEnd(25)} ${field.type.padEnd(15)} ${field.nullable ? 'Yes' : 'No'}`
      ),
    ];
    return { data: body, human: lines.join('\n') + '\n' };
  },
});

export const collectionCommands: RegisteredCommand[] = [list, describe];
