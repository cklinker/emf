import { describe, it, expect } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useMemo, useState } from 'react';
import type { LayoutRule } from '@kelta/sdk';
import { useLayoutRules } from './useLayoutRules';

const baseRule = {
  tenantId: 't',
  layoutId: 'l',
  description: '',
  active: true,
  createdAt: '2026-01-01',
  updatedAt: '2026-01-01',
};

function useTestForm(rules: LayoutRule[], initial: Record<string, unknown>) {
  // Stabilize the rules list and initial values across renders so the engine
  // memo only fires once. Real callers (ResourceFormPage) get this for free
  // via TanStack Query's reference identity.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const stableRules = useMemo(() => rules, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const stableInitial = useMemo(() => initial, []);

  const [values, setValues] = useState(stableInitial);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const setFieldValue = (field: string, value: unknown) =>
    setValues((p) => ({ ...p, [field]: value }));
  const setFieldError = (field: string, message: string) =>
    setErrors((p) => ({ ...p, [field]: message }));
  const clearFieldError = (field: string) =>
    setErrors((p) => {
      const n = { ...p };
      delete n[field];
      return n;
    });

  const result = useLayoutRules({
    rules: stableRules,
    values,
    setFieldValue,
    setFieldError,
    clearFieldError,
  });

  return { values, errors, ...result };
}

describe('useLayoutRules', () => {
  it('runs onLoad on mount and computes target', async () => {
    const rule: LayoutRule = {
      ...baseRule,
      id: 'r1',
      name: 'Line total',
      kind: 'compute',
      target: 'line_total',
      formula: '(quantity * unit_price) - discount',
      when: ['onLoad', 'onChange'],
      sortOrder: 10,
    };

    const { result } = renderHook(() =>
      useTestForm([rule], { quantity: 3, unit_price: 24, discount: 5 }),
    );

    // useEffect runs after render — wait one tick.
    await act(async () => {});
    expect(result.current.values.line_total).toBe(67);
    expect(result.current.isComputed('line_total')).toBe(true);
    expect(result.current.enabled).toBe(true);
  });

  it('recomputes on field change', async () => {
    const rule: LayoutRule = {
      ...baseRule,
      id: 'r1',
      name: 'sum',
      kind: 'compute',
      target: 'sum',
      formula: 'a + b',
      when: ['onLoad', 'onChange'],
      sortOrder: 0,
    };

    const { result } = renderHook(() => useTestForm([rule], { a: 1, b: 2 }));
    await act(async () => {});
    expect(result.current.values.sum).toBe(3);

    await act(async () => {
      // simulate user typing
      result.current.onFieldChange('a');
      // pre-change: onFieldChange reads current values
    });
  });

  it('runBeforeSave returns blocked on validate violation', async () => {
    const rule: LayoutRule = {
      ...baseRule,
      id: 'v1',
      name: 'discount cap',
      kind: 'validate',
      target: 'discount',
      formula: 'discount > unit_price * 0.5',
      errorMessage: 'too high',
      enforce: 'block',
      when: ['onBeforeSave'],
      sortOrder: 0,
    };

    const { result } = renderHook(() => useTestForm([rule], { discount: 50, unit_price: 30 }));
    await act(async () => {});

    let blocked = false;
    act(() => {
      const r = result.current.runBeforeSave();
      blocked = r.blocked;
    });
    expect(blocked).toBe(true);
  });

  it('disabled when no rules supplied', () => {
    const { result } = renderHook(() => useTestForm([], { a: 1 }));
    expect(result.current.enabled).toBe(false);
  });
});
