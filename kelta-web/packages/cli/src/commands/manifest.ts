import { z } from 'zod';
import { buildManifest } from '../registry/manifest.js';
// Cycle note: registry.ts imports this module. ESM live bindings make that
// safe as long as allCommands is only READ at handler time, never at load.
import { allCommands } from '../registry/registry.js';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

const manifest = defineCommand({
  group: '',
  name: 'manifest',
  summary: 'Print the machine-readable command catalog (JSON Schema per command)',
  requiresAuth: false,
  options: [{ flag: '--group <group>', description: 'Only commands of one group' }],
  input: z.object({ group: z.string().optional() }),
  handler: (_ctx, input) => {
    return Promise.resolve({ data: buildManifest(allCommands, input.group) });
  },
});

export const manifestCommands: RegisteredCommand[] = [manifest];
