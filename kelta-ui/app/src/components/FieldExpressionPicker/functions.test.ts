import { describe, it, expect } from 'vitest'
import { buildFunctionStub, FUNCTIONS } from './functions'
import type { FunctionDef } from './types'

describe('buildFunctionStub', () => {
  it('renders zero-arg functions as just NAME()', () => {
    const today = FUNCTIONS.find((f) => f.name === 'TODAY') as FunctionDef
    expect(buildFunctionStub(today)).toBe('TODAY()')
  })

  it('renders named placeholders for each argument', () => {
    const ifFn = FUNCTIONS.find((f) => f.name === 'IF') as FunctionDef
    expect(buildFunctionStub(ifFn)).toBe('IF(${condition}, ${then}, ${else})')
  })

  it('renders one-arg functions correctly', () => {
    const upper = FUNCTIONS.find((f) => f.name === 'UPPER') as FunctionDef
    expect(buildFunctionStub(upper)).toBe('UPPER(${text})')
  })
})

describe('FUNCTIONS catalog', () => {
  it('exposes the same names as the backend BuiltInFunctions', () => {
    const names = new Set(FUNCTIONS.map((f) => f.name))
    // BuiltInFunctions.java registers exactly these names — keep in sync.
    const expected = [
      'IF',
      'AND',
      'OR',
      'NOT',
      'ISBLANK',
      'BLANKVALUE',
      'LEN',
      'CONTAINS',
      'UPPER',
      'LOWER',
      'TRIM',
      'TEXT',
      'VALUE',
      'REGEX',
      'ROUND',
      'ABS',
      'MAX',
      'MIN',
      'TODAY',
      'NOW',
      'DATEDIFF',
    ]
    for (const e of expected) {
      expect(names.has(e), `missing function ${e}`).toBe(true)
    }
  })

  it('every entry has a description', () => {
    for (const fn of FUNCTIONS) {
      expect(fn.description.length).toBeGreaterThan(0)
    }
  })
})
