import { readFileSync } from 'node:fs';
import { CliError, EXIT } from './errors.js';

function readRaw(value: string): string {
  if (value === '-') return readFileSync(0, 'utf-8');
  if (value.startsWith('@')) {
    try {
      return readFileSync(value.slice(1), 'utf-8');
    } catch {
      throw new CliError(`Cannot read file "${value.slice(1)}"`, {
        code: 'FILE_NOT_FOUND',
        exitCode: EXIT.USAGE,
      });
    }
  }
  return value;
}

/**
 * Parse a `--data` value (inline JSON, `@file`, or `-` for stdin) into ANY
 * JSON value — the raw `kelta api` escape hatch takes bodies verbatim.
 */
export function readJsonArgument(value: string): unknown {
  try {
    return JSON.parse(readRaw(value));
  } catch {
    throw new CliError('Invalid JSON in --data', {
      code: 'INVALID_JSON',
      exitCode: EXIT.USAGE,
    });
  }
}

/**
 * Parse a `--data` value: inline JSON, `@file`, or `-` for stdin. Returns the
 * parsed object; anything non-object (arrays included) is rejected — record
 * attributes are always a JSON object.
 */
export function readDataArgument(value: string): Record<string, unknown> {
  const parsed = readJsonArgument(value);
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new CliError('--data must be a JSON object of attributes', {
      code: 'INVALID_JSON',
      exitCode: EXIT.USAGE,
    });
  }
  return parsed as Record<string, unknown>;
}
