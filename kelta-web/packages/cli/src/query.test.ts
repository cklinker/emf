import { describe, it, expect } from 'vitest';
import { CliError } from './errors.js';
import { parseFilterSpec, parseList, parseSort } from './query.js';

describe('parseFilterSpec', () => {
  it('defaults the operator to eq', () => {
    expect(parseFilterSpec('status=open')).toEqual({
      field: 'status',
      operator: 'eq',
      value: 'open',
    });
  });

  it('parses field.op=value and lowercases the operator', () => {
    expect(parseFilterSpec('amount.GTE=100')).toEqual({
      field: 'amount',
      operator: 'gte',
      value: '100',
    });
  });

  it('keeps = characters inside the value', () => {
    expect(parseFilterSpec('note=a=b')).toEqual({ field: 'note', operator: 'eq', value: 'a=b' });
  });

  it('rejects malformed specs', () => {
    expect(() => parseFilterSpec('nofield')).toThrow(CliError);
    expect(() => parseFilterSpec('=value')).toThrow(CliError);
    expect(() => parseFilterSpec('.op=value')).toThrow(CliError);
  });
});

describe('parseSort', () => {
  it('parses direction prefixes', () => {
    expect(parseSort('-createdAt, name')).toEqual([
      { field: 'createdAt', direction: 'desc' },
      { field: 'name', direction: 'asc' },
    ]);
  });
});

describe('parseList', () => {
  it('splits and trims', () => {
    expect(parseList('a, b ,c,')).toEqual(['a', 'b', 'c']);
  });
});
