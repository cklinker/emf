#!/usr/bin/env node

import { Command } from 'commander';
import { registerAuthCommands } from './commands/auth.js';
import { registerCollectionCommands } from './commands/collections.js';
import { registerRecordCommands } from './commands/records.js';
import { registerMetadataCommands } from './commands/metadata.js';
import { registerEnvironmentCommands } from './commands/environments.js';
import { registerSdkCommands } from './commands/sdk.js';

const program = new Command();

program
  .name('kelta')
  .description('Kelta Platform CLI — manage collections, records, and metadata')
  .version('1.0.0');

registerAuthCommands(program);
registerCollectionCommands(program);
registerRecordCommands(program);
registerMetadataCommands(program);
registerEnvironmentCommands(program);
registerSdkCommands(program);

program.parse();
