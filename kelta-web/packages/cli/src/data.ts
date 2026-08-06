import { readFileSync } from 'node:fs';
import { CliError, EXIT } from './errors.js';

/**
 * Parse a `--data` value: inline JSON, `@file`, or `-` for stdin. Returns the
 * parsed object; anything non-object (arrays included) is rejected — record
 * attributes are always a JSON object.
 */
export function readDataArgument(value: string): Record<string, unknown> {
  let text = value;
  if (value === '-') {
    text = readFileSync(0, 'utf-8');
  } else if (value.startsWith('@')) {
    try {
      text = readFileSync(value.slice(1), 'utf-8');
    } catch {
      throw new CliError(`Cannot read file "${value.slice(1)}"`, {
        code: 'FILE_NOT_FOUND',
        exitCode: EXIT.USAGE,
      });
    }
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new CliError('Invalid JSON in --data', {
      code: 'INVALID_JSON',
      exitCode: EXIT.USAGE,
    });
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new CliError('--data must be a JSON object of attributes', {
      code: 'INVALID_JSON',
      exitCode: EXIT.USAGE,
    });
  }
  return parsed as Record<string, unknown>;
}
