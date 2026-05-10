import { describe, it, expect } from 'vitest';
import { evaluateFilter } from './filterEval';
import type { LayoutFilter } from './types';

const f = (
  filter: LayoutFilter['filters'],
  logic: LayoutFilter['logic'] = 'AND'
): LayoutFilter => ({
  logic,
  filters: filter,
});

describe('evaluateFilter', () => {
  const record = {
    status: 'Active',
    industry: 'Tech',
    revenue: 100000,
    employees: 50,
    notes: null as string | null,
    region: '',
  };

  it('returns true for null/empty filter', () => {
    expect(evaluateFilter(null, record)).toBe(true);
    expect(evaluateFilter(undefined, record)).toBe(true);
    expect(evaluateFilter(f([]), record)).toBe(true);
  });

  it('equals / not_equals', () => {
    expect(evaluateFilter(f([{ field: 'industry', op: 'equals', value: 'Tech' }]), record)).toBe(
      true
    );
    expect(evaluateFilter(f([{ field: 'industry', op: 'equals', value: 'Finance' }]), record)).toBe(
      false
    );
    expect(
      evaluateFilter(f([{ field: 'industry', op: 'not_equals', value: 'Finance' }]), record)
    ).toBe(true);
  });

  it('numeric equals coerces strings', () => {
    expect(evaluateFilter(f([{ field: 'revenue', op: 'equals', value: '100000' }]), record)).toBe(
      true
    );
  });

  it('contains/starts_with/ends_with are case-insensitive', () => {
    expect(evaluateFilter(f([{ field: 'industry', op: 'contains', value: 'ec' }]), record)).toBe(
      true
    );
    expect(evaluateFilter(f([{ field: 'industry', op: 'contains', value: 'EC' }]), record)).toBe(
      true
    );
    expect(evaluateFilter(f([{ field: 'industry', op: 'starts_with', value: 'te' }]), record)).toBe(
      true
    );
    expect(evaluateFilter(f([{ field: 'industry', op: 'ends_with', value: 'CH' }]), record)).toBe(
      true
    );
  });

  it('gt/lt/gte/lte numeric', () => {
    expect(evaluateFilter(f([{ field: 'revenue', op: 'gt', value: 50000 }]), record)).toBe(true);
    expect(evaluateFilter(f([{ field: 'revenue', op: 'lt', value: 50000 }]), record)).toBe(false);
    expect(evaluateFilter(f([{ field: 'employees', op: 'gte', value: 50 }]), record)).toBe(true);
    expect(evaluateFilter(f([{ field: 'employees', op: 'lte', value: 49 }]), record)).toBe(false);
  });

  it('comparisons against null are false', () => {
    expect(evaluateFilter(f([{ field: 'notes', op: 'gt', value: 0 }]), record)).toBe(false);
    expect(evaluateFilter(f([{ field: 'notes', op: 'lt', value: 0 }]), record)).toBe(false);
  });

  it('is_null matches null, undefined, empty string', () => {
    expect(evaluateFilter(f([{ field: 'notes', op: 'is_null' }]), record)).toBe(true);
    expect(evaluateFilter(f([{ field: 'region', op: 'is_null' }]), record)).toBe(true);
    expect(evaluateFilter(f([{ field: 'missing', op: 'is_null' }]), record)).toBe(true);
    expect(evaluateFilter(f([{ field: 'status', op: 'is_null' }]), record)).toBe(false);
  });

  it('is_not_null is the inverse', () => {
    expect(evaluateFilter(f([{ field: 'status', op: 'is_not_null' }]), record)).toBe(true);
    expect(evaluateFilter(f([{ field: 'notes', op: 'is_not_null' }]), record)).toBe(false);
  });

  it('AND logic requires all clauses', () => {
    expect(
      evaluateFilter(
        f(
          [
            { field: 'industry', op: 'equals', value: 'Tech' },
            { field: 'revenue', op: 'gt', value: 50000 },
          ],
          'AND'
        ),
        record
      )
    ).toBe(true);
    expect(
      evaluateFilter(
        f(
          [
            { field: 'industry', op: 'equals', value: 'Tech' },
            { field: 'revenue', op: 'gt', value: 500000 },
          ],
          'AND'
        ),
        record
      )
    ).toBe(false);
  });

  it('OR logic requires any clause', () => {
    expect(
      evaluateFilter(
        f(
          [
            { field: 'industry', op: 'equals', value: 'Finance' },
            { field: 'revenue', op: 'gt', value: 50000 },
          ],
          'OR'
        ),
        record
      )
    ).toBe(true);
    expect(
      evaluateFilter(
        f(
          [
            { field: 'industry', op: 'equals', value: 'Finance' },
            { field: 'revenue', op: 'lt', value: 50000 },
          ],
          'OR'
        ),
        record
      )
    ).toBe(false);
  });
});
