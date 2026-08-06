#!/usr/bin/env node

import { Command } from 'commander';
import { bindCommands } from './registry/bind.js';
import { allCommands } from './registry/registry.js';
import { VERSION } from './version.js';

const program = new Command();

program
  .name('kelta')
  .description('Kelta Platform CLI — manage collections, records, and metadata')
  .version(VERSION)
  .option('--profile <name>', 'Connection profile to use (default: active profile)')
  .option('--output <format>', 'Output format: table|json|yaml|csv|ndjson')
  .option('--raw', 'Emit the unflattened JSON:API envelope')
  .option('--quiet', 'Print ids only')
  .option('--yes', 'Skip confirmation for destructive commands');

bindCommands(program, allCommands);

program.parseAsync(process.argv).catch((error: unknown) => {
  process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
});
