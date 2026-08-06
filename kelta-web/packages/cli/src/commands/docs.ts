import { z } from 'zod';
import { AGENT_GUIDE } from '../docsgen/agentGuide.js';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';

const agent = defineCommand({
  group: 'docs',
  name: 'agent',
  summary: 'Print the condensed agent guide (auth model, contracts, examples)',
  requiresAuth: false,
  input: z.object({}),
  handler: () => Promise.resolve({ text: AGENT_GUIDE }),
});

export const docsCommands: RegisteredCommand[] = [agent];
