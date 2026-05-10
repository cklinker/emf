/**
 * Client-side evaluator for the admin grid filter expression.
 *
 * Mirrors the operator semantics of `@kelta/sdk`'s filterEval so the same
 * filter JSON can be evaluated client-side here and resolved by the backend
 * when needed. Kept local to avoid pulling an SDK import into the UI bundle.
 */

import type { FilterExpression, FilterClause, FilterOperator } from '@/components/FilterBuilder'

export function evaluateFilter(
  filter: FilterExpression | null | undefined,
  record: Record<string, unknown>
): boolean {
  if (!filter || !filter.filters || filter.filters.length === 0) return true
  const logic = filter.logic ?? 'AND'
  if (logic === 'OR') return filter.filters.some((c) => evaluateClause(c, record))
  return filter.filters.every((c) => evaluateClause(c, record))
}

function evaluateClause(clause: FilterClause, record: Record<string, unknown>): boolean {
  return applyOperator(clause.op, record[clause.field], clause.value)
}

function applyOperator(op: FilterOperator, lhs: unknown, rhs: unknown): boolean {
  switch (op) {
    case 'equals':
      return looseEquals(lhs, rhs)
    case 'not_equals':
      return !looseEquals(lhs, rhs)
    case 'contains':
      return stringIncludes(lhs, rhs)
    case 'starts_with':
      return stringStartsWith(lhs, rhs)
    case 'ends_with':
      return stringEndsWith(lhs, rhs)
    case 'gt':
      return compare(lhs, rhs) > 0
    case 'lt':
      return compare(lhs, rhs) < 0
    case 'gte':
      return compare(lhs, rhs) >= 0
    case 'lte':
      return compare(lhs, rhs) <= 0
    case 'is_null':
      return isBlank(lhs)
    case 'is_not_null':
      return !isBlank(lhs)
    default:
      return false
  }
}

function isBlank(v: unknown): boolean {
  return v === null || v === undefined || v === ''
}

function looseEquals(a: unknown, b: unknown): boolean {
  if (isBlank(a) && isBlank(b)) return true
  if (isBlank(a) || isBlank(b)) return false
  if (typeof a === 'number' || typeof b === 'number') {
    const an = Number(a)
    const bn = Number(b)
    if (Number.isFinite(an) && Number.isFinite(bn)) return an === bn
  }
  return String(a) === String(b)
}

function stringIncludes(lhs: unknown, rhs: unknown): boolean {
  if (isBlank(lhs) || isBlank(rhs)) return false
  return String(lhs).toLowerCase().includes(String(rhs).toLowerCase())
}

function stringStartsWith(lhs: unknown, rhs: unknown): boolean {
  if (isBlank(lhs) || isBlank(rhs)) return false
  return String(lhs).toLowerCase().startsWith(String(rhs).toLowerCase())
}

function stringEndsWith(lhs: unknown, rhs: unknown): boolean {
  if (isBlank(lhs) || isBlank(rhs)) return false
  return String(lhs).toLowerCase().endsWith(String(rhs).toLowerCase())
}

function compare(a: unknown, b: unknown): number {
  if (isBlank(a) || isBlank(b)) return NaN
  const an = Number(a)
  const bn = Number(b)
  if (Number.isFinite(an) && Number.isFinite(bn)) return an === bn ? 0 : an < bn ? -1 : 1
  const as = String(a)
  const bs = String(b)
  return as === bs ? 0 : as < bs ? -1 : 1
}
