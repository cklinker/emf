import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Command } from 'commander';
import { z } from 'zod';
import { bindCommands } from './bind.js';
import { defineCommand, type RegisteredCommand } from './types.js';

let stdout: string[];
let stderr: string[];

beforeEach(() => {
  stdout = [];
  stderr = [];
  vi.spyOn(process.stdout, 'write').mockImplementation((chunk: unknown) => {
    stdout.push(String(chunk));
    return true;
  });
  vi.spyOn(process.stderr, 'write').mockImplementation((chunk: unknown) => {
    stderr.push(String(chunk));
    return true;
  });
  process.exitCode = undefined;
});

afterEach(() => {
  vi.restoreAllMocks();
  process.exitCode = undefined;
});

function program(defs: RegisteredCommand[]): Command {
  const prog = new Command();
  prog
    .name('kelta')
    .exitOverride()
    .option('--profile <name>', 'profile')
    .option('--output <format>', 'format')
    .option('--raw', 'raw')
    .option('--quiet', 'quiet')
    .option('--yes', 'yes');
  bindCommands(prog, defs);
  return prog;
}

const echo = defineCommand({
  group: 'things',
  name: 'echo',
  summary: 'echo input back',
  requiresAuth: false,
  positionals: [{ name: 'target', description: 'target', required: true }],
  options: [
    { flag: '--count <n>', description: 'count', default: '1' },
    { flag: '--tag <tag>', description: 'tags', repeatable: true },
  ],
  input: z.object({
    target: z.string(),
    count: z.coerce.number().int(),
    tag: z.array(z.string()).default([]),
  }),
  handler: (_ctx, input) => Promise.resolve({ data: input }),
});

const boom = defineCommand({
  group: 'things',
  name: 'boom',
  summary: 'destructive',
  requiresAuth: false,
  dangerous: true,
  input: z.object({}),
  handler: () => Promise.resolve({ message: 'boomed' }),
});

describe('bindCommands', () => {
  it('parses positionals, defaults, coercion, and repeatable flags into handler input', async () => {
    await program([echo as RegisteredCommand]).parseAsync(
      ['things', 'echo', 'abc', '--count', '3', '--tag', 'x', '--tag', 'y', '--output', 'json'],
      { from: 'user' }
    );
    expect(process.exitCode).toBeUndefined();
    const printed = JSON.parse(stdout.join('')) as { target: string; count: number; tag: string[] };
    expect(printed).toEqual({ target: 'abc', count: 3, tag: ['x', 'y'] });
  });

  it('maps zod validation failure to exit 2 with a machine-readable stderr line', async () => {
    await program([echo as RegisteredCommand]).parseAsync(
      ['things', 'echo', 'abc', '--count', 'NaN', '--output', 'json'],
      { from: 'user' }
    );
    expect(process.exitCode).toBe(2);
    const payload = JSON.parse(stderr.join('')) as { error: { code: string } };
    expect(payload.error.code).toBe('INVALID_ARGUMENTS');
    expect(stdout.join('')).toBe('');
  });

  it('blocks dangerous commands off-TTY without --yes', async () => {
    await program([boom as RegisteredCommand]).parseAsync(['things', 'boom', '--output', 'json'], {
      from: 'user',
    });
    expect(process.exitCode).toBe(2);
    const payload = JSON.parse(stderr.join('')) as { error: { code: string } };
    expect(payload.error.code).toBe('CONFIRMATION_REQUIRED');
  });

  it('runs dangerous commands with --yes', async () => {
    await program([boom as RegisteredCommand]).parseAsync(
      ['things', 'boom', '--yes', '--output', 'json'],
      { from: 'user' }
    );
    expect(process.exitCode).toBeUndefined();
    expect(JSON.parse(stdout.join(''))).toEqual({ message: 'boomed' });
  });

  it('rejects an unknown --output format with exit 2', async () => {
    await program([echo as RegisteredCommand]).parseAsync(
      ['things', 'echo', 'abc', '--output', 'xml'],
      { from: 'user' }
    );
    expect(process.exitCode).toBe(2);
  });
});
