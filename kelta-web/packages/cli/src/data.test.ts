import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { CliError } from './errors.js';
import { readDataArgument } from './data.js';

let dir: string;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'kelta-cli-data-'));
});

afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
});

describe('readDataArgument', () => {
  it('parses inline JSON', () => {
    expect(readDataArgument('{"a":1}')).toEqual({ a: 1 });
  });

  it('reads @file JSON', () => {
    const file = join(dir, 'payload.json');
    writeFileSync(file, '{"name":"x"}');
    expect(readDataArgument(`@${file}`)).toEqual({ name: 'x' });
  });

  it('fails cleanly on a missing file', () => {
    expect(() => readDataArgument(`@${join(dir, 'nope.json')}`)).toThrow(CliError);
  });

  it('rejects invalid JSON and non-objects', () => {
    expect(() => readDataArgument('{oops')).toThrow(CliError);
    expect(() => readDataArgument('[1,2]')).toThrow(CliError);
    expect(() => readDataArgument('"str"')).toThrow(CliError);
  });
});
