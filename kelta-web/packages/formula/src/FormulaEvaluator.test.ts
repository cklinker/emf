import { describe, it, expect } from 'vitest';
import { FormulaEvaluator } from './FormulaEvaluator';
import { FormulaException } from './errors';

describe('FormulaEvaluator', () => {
  it('evaluates arithmetic', () => {
    const ev = new FormulaEvaluator();
    expect(ev.evaluate('2 + 3 * 4', {})).toBe(14);
  });

  it('evaluates with field values', () => {
    const ev = new FormulaEvaluator();
    expect(ev.evaluate('(qty * price) - discount', { qty: 3, price: 10, discount: 5 })).toBe(25);
  });

  it('evaluateBoolean works', () => {
    const ev = new FormulaEvaluator();
    expect(ev.evaluateBoolean('amount > 100', { amount: 200 })).toBe(true);
    expect(ev.evaluateBoolean('amount > 100', { amount: 50 })).toBe(false);
  });

  it('evaluateBoolean throws on non-boolean result', () => {
    const ev = new FormulaEvaluator();
    expect(() => ev.evaluateBoolean('1 + 2', {})).toThrow(FormulaException);
  });

  it('validate passes valid expressions', () => {
    const ev = new FormulaEvaluator();
    expect(() => ev.validate('a + b')).not.toThrow();
  });

  it('validate throws on invalid', () => {
    const ev = new FormulaEvaluator();
    expect(() => ev.validate('1 +')).toThrow(FormulaException);
  });

  it('extractFieldRefs returns dependencies', () => {
    const ev = new FormulaEvaluator();
    expect(ev.extractFieldRefs('(qty * price) - discount').sort()).toEqual(['discount', 'price', 'qty']);
  });

  it('caches compiled ASTs', () => {
    const ev = new FormulaEvaluator();
    expect(ev.cacheSize()).toBe(0);
    ev.evaluate('1 + 1', {});
    expect(ev.cacheSize()).toBe(1);
    ev.evaluate('1 + 1', {});
    expect(ev.cacheSize()).toBe(1);
  });

  it('evict removes one entry', () => {
    const ev = new FormulaEvaluator();
    ev.evaluate('1 + 1', {});
    ev.evict('1 + 1');
    expect(ev.cacheSize()).toBe(0);
  });

  it('clearCache empties cache', () => {
    const ev = new FormulaEvaluator();
    ev.evaluate('1 + 1', {});
    ev.evaluate('2 + 2', {});
    ev.clearCache();
    expect(ev.cacheSize()).toBe(0);
  });

  it('cache evicts when bound exceeded', () => {
    const ev = new FormulaEvaluator({ cacheMaxSize: 2 });
    ev.evaluate('1 + 1', {});
    ev.evaluate('2 + 2', {});
    expect(ev.cacheSize()).toBe(2);
    ev.evaluate('3 + 3', {});
    expect(ev.cacheSize()).toBe(1);
  });

  it('registerFunction adds custom function', () => {
    const ev = new FormulaEvaluator();
    ev.registerFunction({ name: 'DOUBLE', execute: (args) => Number(args[0]) * 2 });
    expect(ev.evaluate('DOUBLE(5)', {})).toBe(10);
  });

  it('opts.functions overrides built-in', () => {
    const ev = new FormulaEvaluator({
      functions: [{ name: 'UPPER', execute: () => 'CUSTOM' }],
    });
    expect(ev.evaluate("UPPER('x')", {})).toBe('CUSTOM');
  });

  it('parse exposes AST', () => {
    const ev = new FormulaEvaluator();
    expect(ev.parse('5')).toEqual({ kind: 'literal', value: 5 });
  });
});
