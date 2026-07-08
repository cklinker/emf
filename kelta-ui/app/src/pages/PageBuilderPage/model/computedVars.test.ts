/** Computed page variables (app-platform slice 2): ordering, cycles, error semantics. */
import { describe, it, expect } from 'vitest'
import { evaluateComputedVariables, computedVariablesOf } from './computedVars'
import type { PageVariable } from '../pageConfig'

const computed = (name: string, expression: string): PageVariable => ({
  name,
  type: 'json',
  kind: 'computed',
  expression,
})

describe('computedVariablesOf', () => {
  it('keeps only named computed variables with an expression', () => {
    const vars: PageVariable[] = [
      { name: 'a', type: 'number', default: 1 },
      computed('b', 'vars.a + 1'),
      { name: '', type: 'json', kind: 'computed', expression: '1' },
      { name: 'c', type: 'json', kind: 'computed', expression: '  ' },
    ]
    expect(computedVariablesOf(vars).map((v) => v.name)).toEqual(['b'])
  })
})

describe('evaluateComputedVariables', () => {
  it('returns an empty map with no computed variables', () => {
    expect(evaluateComputedVariables([{ name: 'a', type: 'number' }], {})).toEqual({})
  })

  it('evaluates a chain in dependency order regardless of declaration order', () => {
    // Bare identifiers per the 2d expr contract: `a` reads the static var, `b` the sibling.
    const vars = [computed('c', 'b * 2'), computed('b', 'a + 1')]
    const out = evaluateComputedVariables(vars, { vars: { a: 1 } })
    expect(out.b).toBe(2)
    expect(out.c).toBe(4)
  })

  it('a dotted reference is unparseable by the engine and evaluates to null', () => {
    const out = evaluateComputedVariables([computed('bad', 'vars.a + 1')], { vars: { a: 1 } })
    expect(out.bad).toBeNull()
  })

  it('evaluates cycle members to null without throwing', () => {
    const vars = [computed('x', 'y + 1'), computed('y', 'x + 1'), computed('z', '5')]
    const out = evaluateComputedVariables(vars, {})
    expect(out.x).toBeNull()
    expect(out.y).toBeNull()
    expect(out.z).toBe(5)
  })

  it('evaluates a malformed expression to null', () => {
    const out = evaluateComputedVariables([computed('bad', '((')], {})
    expect(out.bad).toBeNull()
  })

  it('bare sibling names also count as dependencies', () => {
    const vars = [computed('b', 'a + 1'), computed('a', '2')]
    const out = evaluateComputedVariables(vars, {})
    expect(out.a).toBe(2)
    expect(out.b).toBe(3)
  })
})
