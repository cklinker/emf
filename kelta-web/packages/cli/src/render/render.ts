import { stringify as yamlStringify } from 'yaml';
import { extractIds, flattenBody } from './flatten.js';
import { renderCsv, renderTable, type ColumnSpec } from './table.js';

export type OutputFormat = 'table' | 'json' | 'yaml' | 'csv' | 'ndjson';

export const OUTPUT_FORMATS: OutputFormat[] = ['table', 'json', 'yaml', 'csv', 'ndjson'];

export interface RenderOptions {
  format: OutputFormat;
  raw: boolean;
  quiet: boolean;
}

export interface HandlerResult {
  /** Response body (JSON:API envelope or plain object); flattened unless --raw. */
  data?: unknown;
  /** Table columns for the flattened rows (table/csv modes). */
  columns?: ColumnSpec[];
  /** Custom human rendering — wins over the generic table in table mode. */
  human?: string;
  /** One-line human success message (table mode; wrapped as JSON elsewhere). */
  message?: string;
  /** Explicit ids for --quiet; defaults to ids extracted from data. */
  ids?: string[];
}

/**
 * Pick the effective output format: an explicit `--output` always wins;
 * otherwise table for humans (TTY) and json for pipes/agents.
 */
export function pickFormat(explicit: OutputFormat | undefined, isTty: boolean): OutputFormat {
  return explicit ?? (isTty ? 'table' : 'json');
}

function toRows(flat: unknown): Record<string, unknown>[] {
  const rows = Array.isArray(flat) ? flat : [flat];
  return rows.filter((row): row is Record<string, unknown> => {
    return typeof row === 'object' && row !== null;
  });
}

/** Serialize a handler result to the string written to stdout. */
export function renderResult(result: HandlerResult, options: RenderOptions): string {
  if (options.quiet) {
    const ids = result.ids ?? (result.data !== undefined ? extractIds(result.data) : []);
    return ids.length > 0 ? ids.join('\n') + '\n' : '';
  }

  if (result.data === undefined) {
    if (options.format === 'table') return result.message ? result.message + '\n' : '';
    return JSON.stringify({ message: result.message ?? 'ok' }) + '\n';
  }

  const payload = options.raw ? result.data : flattenBody(result.data);

  switch (options.format) {
    case 'json':
      return JSON.stringify(payload, null, 2) + '\n';
    case 'yaml':
      return yamlStringify(payload);
    case 'ndjson': {
      const rows = Array.isArray(payload) ? payload : [payload];
      return rows.map((row) => JSON.stringify(row)).join('\n') + '\n';
    }
    case 'csv':
      return renderCsv(toRows(payload), result.columns);
    case 'table': {
      if (result.human !== undefined) return result.human;
      const table = renderTable(toRows(payload), result.columns);
      const message = result.message ? result.message + '\n' : '';
      return table + message;
    }
  }
}
