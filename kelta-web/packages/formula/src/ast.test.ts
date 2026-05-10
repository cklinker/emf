import { describe, it, expect } from 'vitest';
import { evaluateAst, extractFieldRefs, toDouble, toBoolean } from './ast';
import { buildBuiltinMap } from './builtins';
import { FormulaException } from './errors';
import type { FormulaContext } from './types';

const ctx = (fieldValues: Record<string, unknown> = {}): FormulaContext => ({
  fieldValues,
  functions: buildBuiltinMap(),
});

describe('toDouble', () => {
  it('null -> 0', () => expect(toDouble(null)).toBe(0));
  it('undefined -> 0', () => expect(toDouble(undefined)).toBe(0));
  it('number passthrough', () => expect(toDouble(3.14)).toBe(3.14));
  it('boolean true -> 1', () => expect(toDouble(true)).toBe(1));
  it('boolean false -> 0', () => expect(toDouble(false)).toBe(0));
  it('numeric string', () => expect(toDouble('42')).toBe(42));
  it('throws on non-numeric string', () => {
    expect(() => toDouble('abc')).toThrow(FormulaException);
  });
});

describe('toBoolean', () => {
  it('null -> false', () => expect(toBoolean(null)).toBe(false));
  it('boolean passthrough', () => expect(toBoolean(true)).toBe(true));
  it('zero -> false', () => expect(toBoolean(0)).toBe(false));
  it('non-zero -> true', () => expect(toBoolean(1)).toBe(true));
  it('"true" -> true (case-insensitive)', () => expect(toBoolean('TRUE')).toBe(true));
  it('any other string -> false', () => expect(toBoolean('yes')).toBe(false));
});

describe('evaluateAst', () => {
  it('literal', () => {
    expect(evaluateAst({ kind: 'literal', value: 5 }, ctx())).toBe(5);
  });

  it('fieldRef returns context value', () => {
    expect(evaluateAst({ kind: 'fieldRef', fieldName: 'x' }, ctx({ x: 7 }))).toBe(7);
  });

  it('fieldRef returns null for missing', () => {
    expect(evaluateAst({ kind: 'fieldRef', fieldName: 'missing' }, ctx())).toBeNull();
  });

  it('binary +', () => {
    const ast = {
      kind: 'binaryOp' as const,
      operator: '+' as const,
      left: { kind: 'literal' as const, value: 2 },
      right: { kind: 'literal' as const, value: 3 },
    };
    expect(evaluateAst(ast, ctx())).toBe(5);
  });

  it('binary + concatenates strings', () => {
    const ast = {
      kind: 'binaryOp' as const,
      operator: '+' as const,
      left: { kind: 'literal' as const, value: 'a' },
      right: { kind: 'literal' as const, value: 'b' },
    };
    expect(evaluateAst(ast, ctx())).toBe('ab');
  });

  it('division by zero throws', () => {
    const ast = {
      kind: 'binaryOp' as const,
      operator: '/' as const,
      left: { kind: 'literal' as const, value: 1 },
      right: { kind: 'literal' as const, value: 0 },
    };
    expect(() => evaluateAst(ast, ctx())).toThrow(FormulaException);
  });

  it('unknown function throws', () => {
    const ast = { kind: 'functionCall' as const, functionName: 'NOPE', arguments: [] };
    expect(() => evaluateAst(ast, ctx())).toThrow(FormulaException);
  });

  it('unary - negates', () => {
    const ast = {
      kind: 'unaryOp' as const,
      operator: '-' as const,
      operand: { kind: 'literal' as const, value: 7 },
    };
    expect(evaluateAst(ast, ctx())).toBe(-7);
  });
});

describe('extractFieldRefs', () => {
  it('returns empty for literal', () => {
    expect([...extractFieldRefs({ kind: 'literal', value: 1 })]).toEqual([]);
  });

  it('extracts single ref', () => {
    expect([...extractFieldRefs({ kind: 'fieldRef', fieldName: 'a' })]).toEqual(['a']);
  });

  it('walks binary op', () => {
    const ast = {
      kind: 'binaryOp' as const,
      operator: '+' as const,
      left: { kind: 'fieldRef' as const, fieldName: 'a' },
      right: { kind: 'fieldRef' as const, fieldName: 'b' },
    };
    expect([...extractFieldRefs(ast)].sort()).toEqual(['a', 'b']);
  });

  it('walks function call', () => {
    const ast = {
      kind: 'functionCall' as const,
      functionName: 'IF',
      arguments: [
        { kind: 'fieldRef' as const, fieldName: 'a' },
        { kind: 'fieldRef' as const, fieldName: 'b' },
        { kind: 'fieldRef' as const, fieldName: 'c' },
      ],
    };
    expect([...extractFieldRefs(ast)].sort()).toEqual(['a', 'b', 'c']);
  });

  it('dedupes refs', () => {
    const ast = {
      kind: 'binaryOp' as const,
      operator: '+' as const,
      left: { kind: 'fieldRef' as const, fieldName: 'a' },
      right: { kind: 'fieldRef' as const, fieldName: 'a' },
    };
    expect([...extractFieldRefs(ast)]).toEqual(['a']);
  });
});
