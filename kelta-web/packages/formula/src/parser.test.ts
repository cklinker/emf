import { describe, it, expect } from 'vitest';
import { FormulaParser } from './parser';
import { FormulaException } from './errors';

describe('FormulaParser', () => {
  const parser = new FormulaParser();

  it('parses integer literal', () => {
    expect(parser.parse('42')).toEqual({ kind: 'literal', value: 42 });
  });

  it('parses decimal literal', () => {
    expect(parser.parse('3.14')).toEqual({ kind: 'literal', value: 3.14 });
  });

  it('parses leading-dot decimal', () => {
    expect(parser.parse('.5')).toEqual({ kind: 'literal', value: 0.5 });
  });

  it('parses single-quoted string', () => {
    expect(parser.parse("'hi'")).toEqual({ kind: 'literal', value: 'hi' });
  });

  it('parses double-quoted string', () => {
    expect(parser.parse('"hi"')).toEqual({ kind: 'literal', value: 'hi' });
  });

  it('parses string with escaped quote', () => {
    expect(parser.parse("'it\\'s'")).toEqual({ kind: 'literal', value: "it's" });
  });

  it('parses true/false/null case-insensitively', () => {
    expect(parser.parse('TRUE')).toEqual({ kind: 'literal', value: true });
    expect(parser.parse('False')).toEqual({ kind: 'literal', value: false });
    expect(parser.parse('NULL')).toEqual({ kind: 'literal', value: null });
  });

  it('parses field reference', () => {
    expect(parser.parse('amount')).toEqual({ kind: 'fieldRef', fieldName: 'amount' });
  });

  it('parses field reference with underscore', () => {
    expect(parser.parse('unit_price')).toEqual({ kind: 'fieldRef', fieldName: 'unit_price' });
  });

  it('parses function call no args', () => {
    expect(parser.parse('TODAY()')).toEqual({
      kind: 'functionCall',
      functionName: 'TODAY',
      arguments: [],
    });
  });

  it('parses function call multiple args', () => {
    expect(parser.parse('IF(a, b, c)')).toEqual({
      kind: 'functionCall',
      functionName: 'IF',
      arguments: [
        { kind: 'fieldRef', fieldName: 'a' },
        { kind: 'fieldRef', fieldName: 'b' },
        { kind: 'fieldRef', fieldName: 'c' },
      ],
    });
  });

  it('parses operator precedence: mul before add', () => {
    const ast = parser.parse('1 + 2 * 3');
    expect(ast).toEqual({
      kind: 'binaryOp',
      operator: '+',
      left: { kind: 'literal', value: 1 },
      right: {
        kind: 'binaryOp',
        operator: '*',
        left: { kind: 'literal', value: 2 },
        right: { kind: 'literal', value: 3 },
      },
    });
  });

  it('parens override precedence', () => {
    const ast = parser.parse('(1 + 2) * 3');
    expect(ast).toMatchObject({
      kind: 'binaryOp',
      operator: '*',
    });
  });

  it('left-associates / and -', () => {
    const ast = parser.parse('20 / 5 / 2');
    expect(ast).toMatchObject({
      kind: 'binaryOp',
      operator: '/',
      left: { kind: 'binaryOp', operator: '/' },
      right: { kind: 'literal', value: 2 },
    });
  });

  it('parses && and || with comparison precedence', () => {
    const ast = parser.parse('a > 0 && b < 10');
    expect(ast).toMatchObject({
      kind: 'binaryOp',
      operator: '&&',
      left: { kind: 'binaryOp', operator: '>' },
      right: { kind: 'binaryOp', operator: '<' },
    });
  });

  it('parses unary minus', () => {
    expect(parser.parse('-5')).toEqual({
      kind: 'unaryOp',
      operator: '-',
      operand: { kind: 'literal', value: 5 },
    });
  });

  it('parses unary not', () => {
    expect(parser.parse('!flag')).toEqual({
      kind: 'unaryOp',
      operator: '!',
      operand: { kind: 'fieldRef', fieldName: 'flag' },
    });
  });

  it('throws on empty', () => {
    expect(() => parser.parse('')).toThrow(FormulaException);
  });

  it('throws on whitespace-only', () => {
    expect(() => parser.parse('   ')).toThrow(FormulaException);
  });

  it('throws on unterminated string', () => {
    expect(() => parser.parse("'oops")).toThrow(FormulaException);
  });

  it('throws on trailing characters', () => {
    expect(() => parser.parse('1 + 2 oops')).toThrow(FormulaException);
  });

  it('throws on unbalanced paren', () => {
    expect(() => parser.parse('(1 + 2')).toThrow(FormulaException);
  });

  it('throws on unexpected character', () => {
    expect(() => parser.parse('@')).toThrow(FormulaException);
  });

  it('records position on error', () => {
    try {
      parser.parse('1 + @');
      throw new Error('should not reach');
    } catch (e) {
      expect(e).toBeInstanceOf(FormulaException);
      expect((e as FormulaException).position).toBeGreaterThan(0);
    }
  });

  it('handles all comparison operators', () => {
    for (const op of ['>=', '<=', '!=', '<>', '==', '>', '<', '=']) {
      const ast = parser.parse(`a ${op} b`);
      expect(ast).toMatchObject({ kind: 'binaryOp', operator: op });
    }
  });

  it('handles nested function calls', () => {
    const ast = parser.parse('UPPER(TRIM(name))');
    expect(ast).toMatchObject({
      kind: 'functionCall',
      functionName: 'UPPER',
      arguments: [{ kind: 'functionCall', functionName: 'TRIM' }],
    });
  });
});
