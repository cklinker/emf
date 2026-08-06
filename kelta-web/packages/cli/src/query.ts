import { CliError, EXIT } from './errors.js';

export interface FilterSpec {
  field: string;
  operator: string;
  value: string;
}

export interface SortSpec {
  field: string;
  direction: 'asc' | 'desc';
}

/**
 * Parse one repeatable `--filter` spec. Grammar (parent spec):
 * `field=value` (op defaults to eq) or `field.op=value` →
 * `filter[field][op]=value`. Field names never contain dots, so the last
 * dot-segment before `=` is always the operator.
 */
export function parseFilterSpec(spec: string): FilterSpec {
  const eq = spec.indexOf('=');
  if (eq <= 0) {
    throw new CliError(`Invalid --filter "${spec}" (expected field[.op]=value)`, {
      code: 'INVALID_ARGUMENTS',
      exitCode: EXIT.USAGE,
    });
  }
  const left = spec.slice(0, eq);
  const value = spec.slice(eq + 1);
  const dot = left.lastIndexOf('.');
  if (dot === -1) return { field: left, operator: 'eq', value };
  const field = left.slice(0, dot);
  const operator = left.slice(dot + 1);
  if (!field || !operator) {
    throw new CliError(`Invalid --filter "${spec}" (expected field[.op]=value)`, {
      code: 'INVALID_ARGUMENTS',
      exitCode: EXIT.USAGE,
    });
  }
  return { field, operator: operator.toLowerCase(), value };
}

/** Parse `--sort a,-b` into SDK sort specs (leading `-` = descending). */
export function parseSort(sort: string): SortSpec[] {
  return sort
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
    .map((part) =>
      part.startsWith('-')
        ? { field: part.slice(1), direction: 'desc' as const }
        : { field: part, direction: 'asc' as const }
    );
}

/** Parse a comma-separated list flag (`--fields a,b` / `--include x,y`). */
export function parseList(value: string): string[] {
  return value
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}
