import { authCommands } from '../commands/auth.js';
import { collectionCommands } from '../commands/collections.js';
import { environmentCommands } from '../commands/environments.js';
import { metadataCommands } from '../commands/metadata.js';
import { profileCommands } from '../commands/profile.js';
import { recordCommands } from '../commands/records.js';
import { sdkCommands } from '../commands/sdk.js';
import type { RegisteredCommand } from './types.js';

/** Every CLI command. The manifest and local MCP tools derive from this list. */
export const allCommands: RegisteredCommand[] = [
  ...authCommands,
  ...profileCommands,
  ...collectionCommands,
  ...recordCommands,
  ...metadataCommands,
  ...environmentCommands,
  ...sdkCommands,
];
