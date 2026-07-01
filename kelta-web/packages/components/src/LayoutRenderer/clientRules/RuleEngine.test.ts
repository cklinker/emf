import { describe, it, expect, beforeEach } from 'vitest';
import type { LayoutRule, CollectionValidationRule } from '@kelta/sdk';
import { RuleEngine } from './RuleEngine';
import type { FormBinding } from './types';

class TestForm implements FormBinding {
  private values: Record<string, unknown>;
  private errors: Record<string, string> = {};
  setValueLog: Array<[string, unknown]> = [];
  setErrorLog: Array<[string, string]> = [];
  clearErrorLog: string[] = [];

  constructor(initial: Record<string, unknown> = {}) {
    this.values = { ...initial };
  }

  getValue(field: string): unknown {
    return this.values[field];
  }
  getValues(): Record<string, unknown> {
    return { ...this.values };
  }
  setValue(field: string, value: unknown): void {
    this.values[field] = value;
    this.setValueLog.push([field, value]);
  }
  setError(field: string, message: string): void {
    this.errors[field] = message;
    this.setErrorLog.push([field, message]);
  }
  clearError(field: string): void {
    delete this.errors[field];
    this.clearErrorLog.push(field);
  }

  getErrors(): Record<string, string> {
    return { ...this.errors };
  }
}

const baseRule = {
  tenantId: 't',
  layoutId: 'l',
  description: '',
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('RuleEngine', () => {
  describe('compute', () => {
    let engine: RuleEngine;
    const computeRule: LayoutRule = {
      ...baseRule,
      id: 'r1',
      name: 'Line total',
      kind: 'compute',
      target: 'line_total',
      formula: '(quantity * unit_price) - discount',
      when: ['onChange', 'onLoad'],
      sortOrder: 10,
    };

    beforeEach(() => {
      engine = new RuleEngine({ layoutRules: [computeRule] });
    });

    it('isComputed returns true for compute targets', () => {
      expect(engine.isComputed('line_total')).toBe(true);
      expect(engine.isComputed('quantity')).toBe(false);
    });

    it('computes on load', () => {
      const form = new TestForm({ quantity: 3, unit_price: 24, discount: 5 });
      engine.onLoad(form);
      expect(form.getValue('line_total')).toBe(67);
    });

    it('recomputes on change', () => {
      const form = new TestForm({ quantity: 1, unit_price: 24, discount: 0 });
      engine.onLoad(form);
      expect(form.getValue('line_total')).toBe(24);
      form.setValue('quantity', 5);
      engine.onFieldChange('quantity', form);
      expect(form.getValue('line_total')).toBe(120);
    });

    it('does not recurse when engine writes target', () => {
      const form = new TestForm({ quantity: 2, unit_price: 10, discount: 0 });
      engine.onLoad(form);
      const writesBefore = form.setValueLog.length;
      // Simulate a field-change event for the engine-written target — engine
      // should ignore re-entrant writes.
      engine.onFieldChange('line_total', form);
      // No new compute write should have happened.
      expect(form.setValueLog.length).toBe(writesBefore);
    });
  });

  describe('default', () => {
    it('fills empty target on load', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 'd1',
        name: 'Default currency',
        kind: 'default',
        target: 'currency',
        formula: "'USD'",
        when: ['onLoad'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ currency: '' });
      engine.onLoad(form);
      expect(form.getValue('currency')).toBe('USD');
    });

    it('does not overwrite existing value', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 'd1',
        name: 'Default currency',
        kind: 'default',
        target: 'currency',
        formula: "'USD'",
        when: ['onLoad'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ currency: 'EUR' });
      engine.onLoad(form);
      expect(form.getValue('currency')).toBe('EUR');
    });
  });

  describe('validate', () => {
    const rule: LayoutRule = {
      ...baseRule,
      id: 'v1',
      name: 'Discount cap',
      kind: 'validate',
      target: 'discount',
      formula: 'discount > unit_price * 0.5',
      errorMessage: 'Discount cannot exceed 50%',
      enforce: 'block',
      when: ['onChange', 'onBeforeSave'],
      sortOrder: 0,
    };

    it('sets error when condition is true', () => {
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ discount: 20, unit_price: 30 });
      engine.onFieldChange('discount', form);
      expect(form.getErrors().discount).toBe('Discount cannot exceed 50%');
    });

    it('clears error when condition is false', () => {
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ discount: 20, unit_price: 30 });
      engine.onFieldChange('discount', form);
      expect(form.getErrors().discount).toBeDefined();
      form.setValue('discount', 5);
      engine.onFieldChange('discount', form);
      expect(form.getErrors().discount).toBeUndefined();
    });

    it('runBeforeSave blocks on error severity', () => {
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ discount: 50, unit_price: 30 });
      const result = engine.runBeforeSave(form);
      expect(result.blocked).toBe(true);
      expect(result.violations).toHaveLength(1);
    });

    it('runBeforeSave does not block on warn severity', () => {
      const warnRule: LayoutRule = { ...rule, id: 'v2', enforce: 'warn' };
      const engine = new RuleEngine({ layoutRules: [warnRule] });
      const form = new TestForm({ discount: 50, unit_price: 30 });
      const result = engine.runBeforeSave(form);
      expect(result.blocked).toBe(false);
      expect(result.violations).toHaveLength(1);
      expect(result.violations[0].severity).toBe('warning');
    });
  });

  describe('transform', () => {
    it('uppercases on blur', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 't1',
        name: 'SKU upper',
        kind: 'transform',
        target: 'sku',
        transform: { type: 'upper' },
        when: ['onBlur'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ sku: 'abc-123' });
      engine.onFieldBlur('sku', form);
      expect(form.getValue('sku')).toBe('ABC-123');
    });

    it('trims on blur', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 't2',
        name: 'Name trim',
        kind: 'transform',
        target: 'name',
        transform: { type: 'trim' },
        when: ['onBlur'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ name: '  Alice  ' });
      engine.onFieldBlur('name', form);
      expect(form.getValue('name')).toBe('Alice');
    });

    it('formula transform', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 't3',
        name: 'SKU normalize',
        kind: 'transform',
        target: 'sku',
        transform: { type: 'formula', formula: 'TRIM(UPPER(sku))' },
        when: ['onBlur'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ sku: '  abc-123  ' });
      engine.onFieldBlur('sku', form);
      expect(form.getValue('sku')).toBe('ABC-123');
    });

    it('skips transform when target is null', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 't4',
        name: 'SKU upper',
        kind: 'transform',
        target: 'sku',
        transform: { type: 'upper' },
        when: ['onBlur'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ sku: null });
      engine.onFieldBlur('sku', form);
      expect(form.setValueLog).toHaveLength(0);
    });
  });

  describe('cycle detection', () => {
    it('disables engine when cycle present', () => {
      const a: LayoutRule = {
        ...baseRule,
        id: 'a',
        name: 'a',
        kind: 'compute',
        target: 'fa',
        formula: 'fb + 1',
        when: ['onChange'],
        sortOrder: 0,
      };
      const b: LayoutRule = {
        ...baseRule,
        id: 'b',
        name: 'b',
        kind: 'compute',
        target: 'fb',
        formula: 'fa + 1',
        when: ['onChange'],
        sortOrder: 1,
      };
      const engine = new RuleEngine({ layoutRules: [a, b] });
      expect(engine.isDisabled()).toBe(true);
      const diags = engine.getDiagnostics();
      expect(diags.length).toBeGreaterThan(0);
      expect(diags[0].message).toContain('cycle');
    });

    it('disabled engine ignores events', () => {
      const a: LayoutRule = {
        ...baseRule,
        id: 'a',
        name: 'a',
        kind: 'compute',
        target: 'fa',
        formula: 'fb + 1',
        when: ['onChange'],
        sortOrder: 0,
      };
      const b: LayoutRule = {
        ...baseRule,
        id: 'b',
        name: 'b',
        kind: 'compute',
        target: 'fb',
        formula: 'fa + 1',
        when: ['onChange'],
        sortOrder: 1,
      };
      const engine = new RuleEngine({ layoutRules: [a, b] });
      const form = new TestForm();
      engine.onLoad(form);
      engine.onFieldChange('fa', form);
      expect(form.setValueLog).toHaveLength(0);
    });
  });

  describe('inactive rules', () => {
    it('skipped entirely', () => {
      const rule: LayoutRule = {
        ...baseRule,
        id: 'r1',
        name: 'inactive compute',
        active: false,
        kind: 'compute',
        target: 'sum',
        formula: 'a + b',
        when: ['onChange'],
        sortOrder: 0,
      };
      const engine = new RuleEngine({ layoutRules: [rule] });
      const form = new TestForm({ a: 1, b: 2 });
      engine.onLoad(form);
      expect(form.getValue('sum')).toBeUndefined();
    });
  });

  describe('parse failure', () => {
    it('records diagnostic and skips bad rule', () => {
      const bad: LayoutRule = {
        ...baseRule,
        id: 'bad',
        name: 'bad',
        kind: 'compute',
        target: 'x',
        formula: '1 + +',
        when: ['onLoad'],
        sortOrder: 0,
      };
      const good: LayoutRule = {
        ...baseRule,
        id: 'good',
        name: 'good',
        kind: 'compute',
        target: 'y',
        formula: '1 + 2',
        when: ['onLoad'],
        sortOrder: 1,
      };
      const engine = new RuleEngine({ layoutRules: [bad, good] });
      const diags = engine.getDiagnostics();
      expect(diags.some((d) => d.ruleId === 'bad')).toBe(true);
      const form = new TestForm();
      engine.onLoad(form);
      expect(form.getValue('y')).toBe(3);
    });
  });

  describe('collection validation rules with enforceOnClient', () => {
    const cv: CollectionValidationRule = {
      id: 'cv1',
      collectionId: 'order_items',
      name: 'Quantity positive',
      active: true,
      errorConditionFormula: 'quantity <= 0',
      errorMessage: 'Quantity must be positive',
      errorField: 'quantity',
      evaluateOn: 'CREATE_AND_UPDATE',
      enforceOnClient: true,
      severity: 'ERROR',
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    };

    it('triggers in beforeSave when violated', () => {
      const engine = new RuleEngine({ layoutRules: [], validationRules: [cv] });
      const form = new TestForm({ quantity: 0 });
      const r = engine.runBeforeSave(form);
      expect(r.blocked).toBe(true);
      expect(r.violations[0].message).toBe('Quantity must be positive');
    });

    it('does not block on WARNING severity', () => {
      const cvWarn: CollectionValidationRule = { ...cv, severity: 'WARNING' };
      const engine = new RuleEngine({ layoutRules: [], validationRules: [cvWarn] });
      const form = new TestForm({ quantity: 0 });
      const r = engine.runBeforeSave(form);
      expect(r.blocked).toBe(false);
      expect(r.violations[0].severity).toBe('warning');
    });

    it('skips rules with enforceOnClient=false', () => {
      const cvServer: CollectionValidationRule = { ...cv, enforceOnClient: false };
      const engine = new RuleEngine({ layoutRules: [], validationRules: [cvServer] });
      const form = new TestForm({ quantity: 0 });
      const r = engine.runBeforeSave(form);
      expect(r.violations).toHaveLength(0);
    });
  });

  describe('multi-rule dependency chain', () => {
    it('evaluates in topological order', () => {
      const subtotal: LayoutRule = {
        ...baseRule,
        id: 's',
        name: 'sub',
        kind: 'compute',
        target: 'subtotal',
        formula: 'qty * price',
        when: ['onChange', 'onLoad'],
        sortOrder: 1,
      };
      const tax: LayoutRule = {
        ...baseRule,
        id: 't',
        name: 'tax',
        kind: 'compute',
        target: 'tax',
        formula: 'subtotal * 0.07',
        when: ['onChange', 'onLoad'],
        sortOrder: 2,
      };
      const total: LayoutRule = {
        ...baseRule,
        id: 'g',
        name: 'total',
        kind: 'compute',
        target: 'total',
        formula: 'subtotal + tax',
        when: ['onChange', 'onLoad'],
        sortOrder: 3,
      };
      const engine = new RuleEngine({ layoutRules: [total, tax, subtotal] });
      const form = new TestForm({ qty: 2, price: 50 });
      engine.onLoad(form);
      expect(form.getValue('subtotal')).toBe(100);
      expect(form.getValue('tax')).toBeCloseTo(7);
      expect(form.getValue('total')).toBeCloseTo(107);
    });
  });

  describe('script', () => {
    const discountGuard: LayoutRule = {
      ...baseRule,
      id: 's1',
      name: 'Discount guard',
      kind: 'script',
      target: 'discount',
      expression: 'IF(discount > 0.5, "Discount over 50% needs approval", "")',
      when: ['onChange', 'onBeforeSave'],
      sortOrder: 10,
    };

    it('blocks onBeforeSave when the expression returns a non-empty message', () => {
      const engine = new RuleEngine({ layoutRules: [discountGuard] });
      const form = new TestForm({ discount: 0.75 });
      const result = engine.runBeforeSave(form);
      expect(result.blocked).toBe(true);
      expect(result.violations).toHaveLength(1);
      expect(result.violations[0].message).toBe('Discount over 50% needs approval');
      expect(result.violations[0].field).toBe('discount');
      expect(form.getErrors().discount).toBe('Discount over 50% needs approval');
    });

    it('does not block when the expression returns an empty string', () => {
      const engine = new RuleEngine({ layoutRules: [discountGuard] });
      const form = new TestForm({ discount: 0.25 });
      const result = engine.runBeforeSave(form);
      expect(result.blocked).toBe(false);
      expect(result.violations).toHaveLength(0);
    });

    it('surfaces the message live on onChange and clears it when valid', () => {
      const engine = new RuleEngine({ layoutRules: [discountGuard] });
      const form = new TestForm({ discount: 0.75 });
      engine.onFieldChange('discount', form);
      expect(form.getErrors().discount).toBe('Discount over 50% needs approval');
      form.setValue('discount', 0.1);
      engine.onFieldChange('discount', form);
      expect(form.getErrors().discount).toBeUndefined();
    });

    it('fires on onLoad', () => {
      const engine = new RuleEngine({
        layoutRules: [{ ...discountGuard, when: ['onLoad'] }],
      });
      const form = new TestForm({ discount: 0.9 });
      engine.onLoad(form);
      expect(form.getErrors().discount).toBe('Discount over 50% needs approval');
    });

    it('uses the static message when the expression returns boolean true', () => {
      const boolGuard: LayoutRule = {
        ...baseRule,
        id: 's2',
        name: 'Positive amount',
        kind: 'script',
        target: 'amount',
        expression: 'amount < 0',
        message: 'Amount must not be negative',
        when: ['onBeforeSave'],
        sortOrder: 10,
      };
      const engine = new RuleEngine({ layoutRules: [boolGuard] });
      const blocked = engine.runBeforeSave(new TestForm({ amount: -5 }));
      expect(blocked.blocked).toBe(true);
      expect(blocked.violations[0].message).toBe('Amount must not be negative');
      const ok = engine.runBeforeSave(new TestForm({ amount: 5 }));
      expect(ok.blocked).toBe(false);
    });

    it('attaches to _form when no target is set', () => {
      const formLevel: LayoutRule = {
        ...baseRule,
        id: 's3',
        name: 'Form guard',
        kind: 'script',
        expression: 'IF(total > limit, "Over limit", "")',
        when: ['onBeforeSave'],
        sortOrder: 10,
      };
      const engine = new RuleEngine({ layoutRules: [formLevel] });
      const form = new TestForm({ total: 100, limit: 50 });
      const result = engine.runBeforeSave(form);
      expect(result.blocked).toBe(true);
      expect(result.violations[0].field).toBeUndefined();
      expect(form.getErrors()._form).toBe('Over limit');
    });

    it('is sandboxed — a bare `window` reference resolves from scope, not the JS global', () => {
      // The AST evaluator has no reach to real globals: `window` is treated as
      // a field ref, absent from scope → undefined → falsy → no block. If it
      // reached the real window object it would be truthy and block.
      const sandboxed: LayoutRule = {
        ...baseRule,
        id: 's4',
        name: 'Sandbox',
        kind: 'script',
        expression: 'window',
        when: ['onBeforeSave'],
        sortOrder: 10,
      };
      const engine = new RuleEngine({ layoutRules: [sandboxed] });
      const result = engine.runBeforeSave(new TestForm({}));
      expect(result.blocked).toBe(false);
    });

    it('fails open on a runtime error (unknown function) and records a diagnostic', () => {
      const broken: LayoutRule = {
        ...baseRule,
        id: 's5',
        name: 'Broken',
        kind: 'script',
        expression: 'EVAL("1")',
        when: ['onBeforeSave'],
        sortOrder: 10,
      };
      const engine = new RuleEngine({ layoutRules: [broken] });
      const result = engine.runBeforeSave(new TestForm({}));
      expect(result.blocked).toBe(false);
      expect(engine.getDiagnostics().some((d) => d.ruleId === 's5')).toBe(true);
    });
  });
});
