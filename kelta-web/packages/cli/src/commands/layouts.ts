import { z } from 'zod';
import { collectionIdByName } from '../admin/lookups.js';
import { readDataArgument } from '../data.js';
import { parseFilterSpec, parseList } from '../query.js';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

const layoutList = defineCommand({
  group: 'layouts',
  name: 'list',
  summary: 'List page layouts of a collection',
  positionals: [{ name: 'collection', description: 'Collection name or id', required: true }],
  input: z.object({ collection: z.string().min(1) }),
  handler: async (ctx, input) => {
    const axios = ctx.client.getAxiosInstance();
    const collectionId = await collectionIdByName(axios, input.collection);
    const response = await axios.get<unknown>(
      `/api/page-layouts?filter[collectionId][eq]=${collectionId}`
    );
    return {
      data: response.data,
      columns: [
        { key: 'name', header: 'NAME' },
        { key: 'layoutType', header: 'TYPE' },
        { key: 'isDefault', header: 'DEFAULT' },
      ],
    };
  },
});

const layoutCreate = defineCommand({
  group: 'layouts',
  name: 'create',
  summary: 'Create a page layout (sections/fields via the UI or kelta api)',
  positionals: [{ name: 'collection', description: 'Collection name or id', required: true }],
  options: [
    { flag: '--name <name>', description: 'Layout name (required)' },
    { flag: '--default', description: 'Mark as the default layout' },
    { flag: '--data <json>', description: 'Extra attributes as JSON, @file, or - (merged last)' },
  ],
  input: z.object({
    collection: z.string().min(1),
    name: z.string().min(1),
    default: z.boolean().default(false),
    data: z.string().optional(),
  }),
  handler: async (ctx, input) => {
    const axios = ctx.client.getAxiosInstance();
    const collectionId = await collectionIdByName(axios, input.collection);
    const attributes: Record<string, unknown> = {
      collectionId,
      name: input.name,
      layoutType: 'DETAIL',
      isDefault: input.default,
    };
    if (input.data) Object.assign(attributes, readDataArgument(input.data));
    const response = await axios.post<{ data?: { id?: string } }>('/api/page-layouts', {
      data: { type: 'page-layouts', attributes },
    });
    return {
      data: response.data,
      message: `Layout "${input.name}" created for ${input.collection}`,
      ids: response.data.data?.id ? [response.data.data.id] : [],
    };
  },
});

const layoutUpdate = defineCommand({
  group: 'layouts',
  name: 'update',
  summary: 'Update a page layout by id',
  positionals: [{ name: 'layoutId', description: 'Layout id', required: true }],
  options: [
    { flag: '--name <name>', description: 'Layout name' },
    { flag: '--default <bool>', description: 'true|false' },
    { flag: '--data <json>', description: 'Extra attributes as JSON, @file, or - (merged last)' },
  ],
  input: z.object({
    layoutId: z.string().min(1),
    name: z.string().optional(),
    default: z.enum(['true', 'false']).optional(),
    data: z.string().optional(),
  }),
  handler: async (ctx, input) => {
    const attributes: Record<string, unknown> = {};
    if (input.name) attributes.name = input.name;
    if (input.default !== undefined) attributes.isDefault = input.default === 'true';
    if (input.data) Object.assign(attributes, readDataArgument(input.data));
    // NOTE: the update body type is "pageLayouts" (camelCase) — matches the
    // worker's PATCH contract, which differs from create's "page-layouts".
    const response = await ctx.client
      .getAxiosInstance()
      .patch<unknown>(`/api/page-layouts/${input.layoutId}`, {
        data: { type: 'pageLayouts', id: input.layoutId, attributes },
      });
    return { data: response.data, message: `Layout ${input.layoutId} updated` };
  },
});

const layoutDelete = defineCommand({
  group: 'layouts',
  name: 'delete',
  summary: 'Delete a page layout',
  dangerous: true,
  positionals: [{ name: 'layoutId', description: 'Layout id', required: true }],
  input: z.object({ layoutId: z.string().min(1) }),
  handler: async (ctx, input) => {
    await ctx.client.getAxiosInstance().delete(`/api/page-layouts/${input.layoutId}`);
    return {
      data: { deleted: true, id: input.layoutId },
      message: `Layout ${input.layoutId} deleted`,
      ids: [input.layoutId],
    };
  },
});

const listViewList = defineCommand({
  group: 'list-views',
  name: 'list',
  summary: 'List saved list views of a collection',
  positionals: [{ name: 'collection', description: 'Collection name or id', required: true }],
  input: z.object({ collection: z.string().min(1) }),
  handler: async (ctx, input) => {
    const axios = ctx.client.getAxiosInstance();
    const collectionId = await collectionIdByName(axios, input.collection);
    const response = await axios.get<unknown>(
      `/api/list-views?filter[collectionId][eq]=${collectionId}`
    );
    return {
      data: response.data,
      columns: [
        { key: 'name', header: 'NAME' },
        { key: 'isDefault', header: 'DEFAULT' },
        { key: 'sortField', header: 'SORT' },
      ],
    };
  },
});

const listViewCreate = defineCommand({
  group: 'list-views',
  name: 'create',
  summary: 'Create a saved list view',
  positionals: [{ name: 'collection', description: 'Collection name or id', required: true }],
  options: [
    { flag: '--name <name>', description: 'List view name (required)' },
    { flag: '--columns <list>', description: 'Displayed field names, comma-separated (required)' },
    {
      flag: '--filter <spec>',
      description: 'Filter as field[.op]=value (repeatable)',
      repeatable: true,
    },
    { flag: '--sort <field>', description: 'Sort field, -prefix for descending' },
    { flag: '--default', description: 'Mark as the default list view' },
  ],
  input: z.object({
    collection: z.string().min(1),
    name: z.string().min(1),
    columns: z.string().min(1),
    filter: z.array(z.string()).default([]),
    sort: z.string().optional(),
    default: z.boolean().default(false),
  }),
  handler: async (ctx, input) => {
    const axios = ctx.client.getAxiosInstance();
    const collectionId = await collectionIdByName(axios, input.collection);
    const attributes: Record<string, unknown> = {
      collectionId,
      name: input.name,
      columns: parseList(input.columns),
      isDefault: input.default,
      // always sent, matching the MCP tool — the worker expects the key
      filters: input.filter.map((spec) => {
        const parsed = parseFilterSpec(spec);
        return {
          field: parsed.field,
          operator: parsed.operator.toUpperCase(),
          value: parsed.value,
        };
      }),
    };
    if (input.sort) {
      attributes.sortField = input.sort.replace(/^-/, '');
      attributes.sortDirection = input.sort.startsWith('-') ? 'DESC' : 'ASC';
    }
    const response = await axios.post<{ data?: { id?: string } }>('/api/list-views', {
      data: { type: 'list-views', attributes },
    });
    return {
      data: response.data,
      message: `List view "${input.name}" created for ${input.collection}`,
      ids: response.data.data?.id ? [response.data.data.id] : [],
    };
  },
});

export const layoutCommands: RegisteredCommand[] = [
  layoutList,
  layoutCreate,
  layoutUpdate,
  layoutDelete,
  listViewList,
  listViewCreate,
];
