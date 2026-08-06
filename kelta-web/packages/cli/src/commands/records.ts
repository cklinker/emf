import { z } from 'zod';
import { readDataArgument } from '../data.js';
import { parseFilterSpec, parseList, parseSort } from '../query.js';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

/** Hard client-side ceiling for `--all` auto-pagination. */
const ALL_PAGES_CAP = 10_000;
/** HTTP page-size clamp (server-enforced); used as the `--all` page size. */
const MAX_HTTP_PAGE_SIZE = 200;

const listInput = z.object({
  collection: z.string().min(1),
  filter: z.array(z.string()).default([]),
  sort: z.string().optional(),
  fields: z.string().optional(),
  include: z.string().optional(),
  page: z.coerce.number().int().positive().default(1),
  size: z.coerce.number().int().positive().max(MAX_HTTP_PAGE_SIZE).default(25),
  all: z.boolean().default(false),
});

interface ListEnvelope {
  data?: unknown[];
  [key: string]: unknown;
}

const list = defineCommand({
  group: 'records',
  name: 'list',
  summary: 'List records in a collection',
  positionals: [{ name: 'collection', description: 'Collection name', required: true }],
  options: [
    {
      flag: '--filter <spec>',
      description: 'Filter as field[.op]=value (repeatable)',
      repeatable: true,
    },
    { flag: '--sort <fields>', description: 'Sort fields, comma-separated, -prefix for desc' },
    { flag: '--fields <fields>', description: 'Sparse fieldset, comma-separated' },
    { flag: '--include <rels>', description: 'Related resources to include, comma-separated' },
    { flag: '--page <number>', description: 'Page number', default: '1' },
    { flag: '--size <number>', description: 'Page size (max 200)', default: '25' },
    { flag: '--all', description: 'Auto-paginate through every page (client cap 10k records)' },
  ],
  input: listInput,
  handler: async (ctx, input) => {
    const resource = ctx.client.resource(input.collection);
    const options = {
      filters: input.filter.map(parseFilterSpec),
      sort: input.sort ? parseSort(input.sort) : undefined,
      fields: input.fields ? parseList(input.fields) : undefined,
      include: input.include ? parseList(input.include) : undefined,
    };

    if (!input.all) {
      const body = (await resource.list({
        ...options,
        page: input.page,
        size: input.size,
      })) as ListEnvelope;
      return { data: body };
    }

    const rows: unknown[] = [];
    let page = 1;
    for (;;) {
      const body = (await resource.list({
        ...options,
        page,
        size: MAX_HTTP_PAGE_SIZE,
      })) as ListEnvelope;
      const batch = Array.isArray(body.data) ? body.data : [];
      rows.push(...batch);
      if (batch.length < MAX_HTTP_PAGE_SIZE) break;
      if (rows.length >= ALL_PAGES_CAP) {
        ctx.log(`Warning: --all stopped at the ${String(ALL_PAGES_CAP)}-record client cap`);
        break;
      }
      page += 1;
    }
    return { data: { data: rows } };
  },
});

const get = defineCommand({
  group: 'records',
  name: 'get',
  summary: 'Get a single record',
  positionals: [
    { name: 'collection', description: 'Collection name', required: true },
    { name: 'id', description: 'Record id', required: true },
  ],
  options: [{ flag: '--include <rels>', description: 'Related resources to include' }],
  input: z.object({
    collection: z.string().min(1),
    id: z.string().min(1),
    include: z.string().optional(),
  }),
  handler: async (ctx, input) => {
    const body = await ctx.client
      .resource(input.collection)
      .get(input.id, input.include ? { include: parseList(input.include) } : undefined);
    return { data: body };
  },
});

const create = defineCommand({
  group: 'records',
  name: 'create',
  summary: 'Create a record',
  positionals: [{ name: 'collection', description: 'Collection name', required: true }],
  options: [{ flag: '--data <json>', description: 'Attributes as JSON, @file, or - for stdin' }],
  input: z.object({ collection: z.string().min(1), data: z.string().min(1) }),
  handler: async (ctx, input) => {
    const attributes = readDataArgument(input.data);
    const body = await ctx.client.resource(input.collection).create(attributes);
    return { data: body };
  },
});

const update = defineCommand({
  group: 'records',
  name: 'update',
  summary: 'Update a record (partial)',
  positionals: [
    { name: 'collection', description: 'Collection name', required: true },
    { name: 'id', description: 'Record id', required: true },
  ],
  options: [{ flag: '--data <json>', description: 'Attributes as JSON, @file, or - for stdin' }],
  input: z.object({
    collection: z.string().min(1),
    id: z.string().min(1),
    data: z.string().min(1),
  }),
  handler: async (ctx, input) => {
    const attributes = readDataArgument(input.data);
    const body = await ctx.client.resource(input.collection).patch(input.id, attributes);
    return { data: body };
  },
});

const remove = defineCommand({
  group: 'records',
  name: 'delete',
  summary: 'Delete a record',
  dangerous: true,
  positionals: [
    { name: 'collection', description: 'Collection name', required: true },
    { name: 'id', description: 'Record id', required: true },
  ],
  input: z.object({ collection: z.string().min(1), id: z.string().min(1) }),
  handler: async (ctx, input) => {
    await ctx.client.resource(input.collection).delete(input.id);
    return {
      data: { deleted: true, collection: input.collection, id: input.id },
      message: `Deleted ${input.collection}/${input.id}`,
      ids: [input.id],
    };
  },
});

export const recordCommands: RegisteredCommand[] = [list, get, create, update, remove];
