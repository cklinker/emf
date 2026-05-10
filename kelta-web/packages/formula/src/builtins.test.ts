import { describe, it, expect } from 'vitest';
import {
  TODAY,
  NOW,
  ISBLANK,
  BLANKVALUE,
  IF,
  AND_FN,
  OR_FN,
  NOT_FN,
  LEN,
  CONTAINS,
  UPPER,
  LOWER,
  TRIM,
  TEXT,
  VALUE,
  ROUND,
  ABS,
  MAX_FN,
  MIN_FN,
  REGEX,
  DATEDIFF,
  ALL_BUILTINS,
  buildBuiltinMap,
} from './builtins';
import { FormulaException } from './errors';
import type { FormulaContext } from './types';

const ctx: FormulaContext = { fieldValues: {}, functions: buildBuiltinMap() };

describe('TODAY/NOW', () => {
  it('TODAY returns ISO date', () => {
    const d = TODAY.execute([], ctx) as string;
    expect(d).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
  it('NOW returns ISO datetime', () => {
    const d = NOW.execute([], ctx) as string;
    expect(d).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });
});

describe('ISBLANK', () => {
  it('null is blank', () => expect(ISBLANK.execute([null], ctx)).toBe(true));
  it('undefined is blank', () => expect(ISBLANK.execute([undefined], ctx)).toBe(true));
  it('empty string is blank', () => expect(ISBLANK.execute([''], ctx)).toBe(true));
  it('whitespace is blank', () => expect(ISBLANK.execute(['   '], ctx)).toBe(true));
  it('value is not blank', () => expect(ISBLANK.execute(['x'], ctx)).toBe(false));
  it('zero is not blank', () => expect(ISBLANK.execute([0], ctx)).toBe(false));
  it('no args returns true', () => expect(ISBLANK.execute([], ctx)).toBe(true));
});

describe('BLANKVALUE', () => {
  it('returns default when blank', () => expect(BLANKVALUE.execute([null, 'x'], ctx)).toBe('x'));
  it('returns value when present', () => expect(BLANKVALUE.execute(['a', 'x'], ctx)).toBe('a'));
  it('throws on missing args', () =>
    expect(() => BLANKVALUE.execute([null], ctx)).toThrow(FormulaException));
});

describe('IF', () => {
  it('returns then on truthy', () => expect(IF.execute([true, 'yes', 'no'], ctx)).toBe('yes'));
  it('returns else on falsy', () => expect(IF.execute([false, 'yes', 'no'], ctx)).toBe('no'));
  it('throws on missing args', () =>
    expect(() => IF.execute([true], ctx)).toThrow(FormulaException));
});

describe('AND/OR/NOT', () => {
  it('AND all true', () => expect(AND_FN.execute([true, true], ctx)).toBe(true));
  it('AND one false', () => expect(AND_FN.execute([true, false], ctx)).toBe(false));
  it('OR one true', () => expect(OR_FN.execute([false, true], ctx)).toBe(true));
  it('OR all false', () => expect(OR_FN.execute([false, false], ctx)).toBe(false));
  it('NOT', () => expect(NOT_FN.execute([true], ctx)).toBe(false));
  it('NOT throws on no args', () =>
    expect(() => NOT_FN.execute([], ctx)).toThrow(FormulaException));
});

describe('LEN', () => {
  it('counts string', () => expect(LEN.execute(['hello'], ctx)).toBe(5));
  it('null -> 0', () => expect(LEN.execute([null], ctx)).toBe(0));
  it('coerces number', () => expect(LEN.execute([42], ctx)).toBe(2));
});

describe('CONTAINS', () => {
  it('finds substring', () => expect(CONTAINS.execute(['hello world', 'world'], ctx)).toBe(true));
  it('returns false', () => expect(CONTAINS.execute(['hello', 'xyz'], ctx)).toBe(false));
  it('null inputs', () => expect(CONTAINS.execute([null, null], ctx)).toBe(true));
  it('throws on missing arg', () =>
    expect(() => CONTAINS.execute(['a'], ctx)).toThrow(FormulaException));
});

describe('UPPER/LOWER/TRIM', () => {
  it('UPPER', () => expect(UPPER.execute(['abc'], ctx)).toBe('ABC'));
  it('UPPER null', () => expect(UPPER.execute([null], ctx)).toBeNull());
  it('LOWER', () => expect(LOWER.execute(['XYZ'], ctx)).toBe('xyz'));
  it('TRIM', () => expect(TRIM.execute(['  hi  '], ctx)).toBe('hi'));
  it('TRIM null', () => expect(TRIM.execute([null], ctx)).toBeNull());
});

describe('TEXT/VALUE', () => {
  it('TEXT number', () => expect(TEXT.execute([42], ctx)).toBe('42'));
  it('TEXT null -> ""', () => expect(TEXT.execute([null], ctx)).toBe(''));
  it('VALUE string', () => expect(VALUE.execute(['3.14'], ctx)).toBe(3.14));
  it('VALUE null -> 0', () => expect(VALUE.execute([null], ctx)).toBe(0));
  it('VALUE bad string throws', () =>
    expect(() => VALUE.execute(['abc'], ctx)).toThrow(FormulaException));
});

describe('ROUND/ABS/MAX/MIN', () => {
  it('ROUND', () => expect(ROUND.execute([3.14159, 2], ctx)).toBe(3.14));
  it('ROUND zero places', () => expect(ROUND.execute([3.7, 0], ctx)).toBe(4));
  it('ROUND throws on missing places', () =>
    expect(() => ROUND.execute([3.14], ctx)).toThrow(FormulaException));
  it('ABS', () => expect(ABS.execute([-5], ctx)).toBe(5));
  it('ABS throws on no args', () => expect(() => ABS.execute([], ctx)).toThrow(FormulaException));
  it('MAX', () => expect(MAX_FN.execute([3, 7], ctx)).toBe(7));
  it('MIN', () => expect(MIN_FN.execute([3, 7], ctx)).toBe(3));
  it('MAX throws on missing arg', () =>
    expect(() => MAX_FN.execute([3], ctx)).toThrow(FormulaException));
});

describe('REGEX', () => {
  it('matches whole string', () => expect(REGEX.execute(['abc', '[a-z]+'], ctx)).toBe(true));
  it('does not match partial (anchored)', () =>
    expect(REGEX.execute(['abc123', '[a-z]+'], ctx)).toBe(false));
  it('matches with anchor implied', () =>
    expect(REGEX.execute(['abc123', '[a-z]+[0-9]+'], ctx)).toBe(true));
  it('throws on missing arg', () =>
    expect(() => REGEX.execute(['abc'], ctx)).toThrow(FormulaException));
});

describe('DATEDIFF', () => {
  it('positive diff', () => expect(DATEDIFF.execute(['2026-01-10', '2026-01-01'], ctx)).toBe(9));
  it('negative diff', () => expect(DATEDIFF.execute(['2026-01-01', '2026-01-10'], ctx)).toBe(-9));
  it('throws on missing arg', () =>
    expect(() => DATEDIFF.execute(['2026-01-01'], ctx)).toThrow(FormulaException));
  it('throws on invalid date', () =>
    expect(() => DATEDIFF.execute(['nope', '2026-01-01'], ctx)).toThrow(FormulaException));
});

describe('buildBuiltinMap', () => {
  it('contains all built-ins', () => {
    const m = buildBuiltinMap();
    expect(m.size).toBe(ALL_BUILTINS.length);
    for (const fn of ALL_BUILTINS) {
      expect(m.get(fn.name)).toBe(fn);
    }
  });

  it('extra functions override built-ins', () => {
    const custom = { name: 'UPPER', execute: () => 'CUSTOM' };
    const m = buildBuiltinMap([custom]);
    expect(m.get('UPPER')).toBe(custom);
  });
});
