import { describe, it, expect } from 'vitest'
import { parseDisplayColumns } from './parseDisplayColumns'

describe('parseDisplayColumns', () => {
  it('returns empty array for null/undefined/empty input', () => {
    expect(parseDisplayColumns(null)).toEqual([])
    expect(parseDisplayColumns(undefined)).toEqual([])
    expect(parseDisplayColumns('')).toEqual([])
    expect(parseDisplayColumns('   ')).toEqual([])
  })

  it('returns array input as-is when JSONB deserializes to a real array', () => {
    expect(parseDisplayColumns(['productName', 'quantity'])).toEqual(['productName', 'quantity'])
  })

  it('drops non-string entries from a real array input', () => {
    // Cast to bypass TS — we are deliberately testing runtime resilience.
    expect(parseDisplayColumns(['a', 1, null, 'b'] as unknown)).toEqual(['a', 'b'])
  })

  it('returns empty array when input is neither string nor array', () => {
    expect(parseDisplayColumns(42 as unknown)).toEqual([])
    expect(parseDisplayColumns({ foo: 'bar' } as unknown)).toEqual([])
  })

  it('parses JSON-stringified array (current builder format)', () => {
    expect(parseDisplayColumns('["productName","quantity","unitPrice"]')).toEqual([
      'productName',
      'quantity',
      'unitPrice',
    ])
  })

  it('drops non-string entries from a JSON array', () => {
    expect(parseDisplayColumns('["a", 1, null, "b"]')).toEqual(['a', 'b'])
  })

  it('returns empty array for malformed JSON', () => {
    expect(parseDisplayColumns('[not, valid, json')).toEqual([])
  })

  it('parses comma-separated legacy format', () => {
    expect(parseDisplayColumns('productName, quantity ,unitPrice')).toEqual([
      'productName',
      'quantity',
      'unitPrice',
    ])
  })

  it('drops empty fragments from comma-separated input', () => {
    expect(parseDisplayColumns('a,,b, ,c')).toEqual(['a', 'b', 'c'])
  })
})
