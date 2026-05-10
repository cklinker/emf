/**
 * Client-side evaluator for LayoutFilter expressions.
 *
 * The operator vocabulary mirrors the backend filter operators exposed by
 * `useCollectionRecords` in kelta-ui so that conditions written for server-side
 * record filtering behave the same when evaluated locally against a single
 * record (used by AdminClient.layouts.resolve).
 *
 * Semantics:
 *  - String compares for contains/starts_with/ends_with are case-insensitive,
 *    matching the backend.
 *  - is_null matches null OR undefined OR empty-string ''.
 *  - gt/lt/gte/lte compare numerically when both sides are finite numbers, else
 *    fall back to string comparison.
 *  - An empty filter list evaluates to true.
 */

import type { LayoutFilter, LayoutFilterClause, LayoutFilterOperator } from './types';

type Record_ = Record<string, unknown>;

export function evaluateFilter(filter: LayoutFilter | null | undefined, record: Record_): boolean {
  if (!filter || !filter.filters || filter.filters.length === 0) {
    return true;
  }
  const logic = filter.logic ?? 'AND';
  if (logic === 'OR') {
    return filter.filters.some((clause) => evaluateClause(clause, record));
  }
  return filter.filters.every((clause) => evaluateClause(clause, record));
}

function evaluateClause(clause: LayoutFilterClause, record: Record_): boolean {
  const lhs = record[clause.field];
  return applyOperator(clause.op, lhs, clause.value);
}

function applyOperator(op: LayoutFilterOperator, lhs: unknown, rhs: unknown): boolean {
  switch (op) {
    case 'equals':
      return looseEquals(lhs, rhs);
    case 'not_equals':
      return !looseEquals(lhs, rhs);
    case 'contains':
      return stringIncludes(lhs, rhs);
    case 'starts_with':
      return stringStartsWith(lhs, rhs);
    case 'ends_with':
      return stringEndsWith(lhs, rhs);
    case 'gt':
      return compare(lhs, rhs) > 0;
    case 'lt':
      return compare(lhs, rhs) < 0;
    case 'gte':
      return compare(lhs, rhs) >= 0;
    case 'lte':
      return compare(lhs, rhs) <= 0;
    case 'is_null':
      return isBlank(lhs);
    case 'is_not_null':
      return !isBlank(lhs);
    default:
      return false;
  }
}

function isBlank(v: unknown): boolean {
  return v === null || v === undefined || v === '';
}

function looseEquals(a: unknown, b: unknown): boolean {
  if (isBlank(a) && isBlank(b)) return true;
  if (isBlank(a) || isBlank(b)) return false;
  if (typeof a === 'number' || typeof b === 'number') {
    const an = Number(a);
    const bn = Number(b);
    if (Number.isFinite(an) && Number.isFinite(bn)) return an === bn;
  }
  return String(a) === String(b);
}

function stringIncludes(lhs: unknown, rhs: unknown): boolean {
  if (isBlank(lhs) || isBlank(rhs)) return false;
  return String(lhs).toLowerCase().includes(String(rhs).toLowerCase());
}

function stringStartsWith(lhs: unknown, rhs: unknown): boolean {
  if (isBlank(lhs) || isBlank(rhs)) return false;
  return String(lhs).toLowerCase().startsWith(String(rhs).toLowerCase());
}

function stringEndsWith(lhs: unknown, rhs: unknown): boolean {
  if (isBlank(lhs) || isBlank(rhs)) return false;
  return String(lhs).toLowerCase().endsWith(String(rhs).toLowerCase());
}

function compare(a: unknown, b: unknown): number {
  if (isBlank(a) || isBlank(b)) return NaN;
  const an = Number(a);
  const bn = Number(b);
  if (Number.isFinite(an) && Number.isFinite(bn)) {
    return an === bn ? 0 : an < bn ? -1 : 1;
  }
  const as = String(a);
  const bs = String(b);
  return as === bs ? 0 : as < bs ? -1 : 1;
}
