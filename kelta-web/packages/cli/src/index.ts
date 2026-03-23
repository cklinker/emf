#!/usr/bin/env node

import { Command } from 'commander';
import { registerAuthCommands } from './commands/auth.js';
import { registerCollectionCommands } from './commands/collections.js';
import { registerRecordCommands } from './commands/records.js';

const program = new Command();

program
  .name('kelta')
  .description('Kelta Platform CLI — manage collections and records')
  .version('1.0.0');

registerAuthCommands(program);
registerCollectionCommands(program);
registerRecordCommands(program);

program.parse();
