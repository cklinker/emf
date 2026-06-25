/**
 * Real-resolver unit tests (slice 2d): literal passthrough/identity, `mode:'path'` dot/`[n]` access,
 * `mode:'expr'` via the flat-scope bridge to `@kelta/formula`, missing → null, and the
 * prototype-pollution guard. The literal-identity cases are the contract the 2a golden snapshot relies
 * on (the now-real resolver must not change literal rendering).
 */
import { describe, it, expect } from 'vitest'
import { resolveBindings, resolveBinding, flattenScopeForExpr } from './resolveBindings'
import type { BindingScope } from './bindingScope'

describe('resolveBindings', () => {
  it('passes literal props through unchanged (identity on non-bindings)', () => {
    const props = { a: 'x', b: 3, c: true, d: null, e: ['p', 1, false] }
    const out = resolveBindings(props, {})
    expect(out).toEqual(props)
  })

  it('resolves a flat path binding (record.name)', () => {
    const scope: BindingScope = { record: { name: 'Acme' } }
    expect(resolveBindings({ text: { $bind: 'record.name', mode: 'path' } }, scope)).toEqual({
      text: 'Acme',
    })
  })

  it('resolves a nested + indexed path (data.accounts[0].name)', () => {
    const scope: BindingScope = { data: { accounts: [{ name: 'Acme' }, { name: 'Globex' }] } }
    expect(resolveBindings({ v: { $bind: 'data.accounts[0].name', mode: 'path' } }, scope)).toEqual(
      { v: 'Acme' }
    )
    expect(resolveBindings({ v: { $bind: 'data.accounts[1].name', mode: 'path' } }, scope)).toEqual(
      { v: 'Globex' }
    )
  })

  it('resolves a missing path to null (no throw)', () => {
    const scope: BindingScope = { record: { name: 'Acme' } }
    expect(resolveBinding({ $bind: 'record.nope', mode: 'path' }, scope)).toBeNull()
    expect(resolveBinding({ $bind: 'record.a.b.c', mode: 'path' }, scope)).toBeNull()
  })

  it('defaults to path mode when mode is omitted', () => {
    const scope: BindingScope = { vars: { x: 42 } }
    expect(resolveBinding({ $bind: 'vars.x' }, scope)).toBe(42)
  })

  describe('expr mode (flat-scope bridge)', () => {
    it('flattens referenced leaves then evaluates via @kelta/formula', () => {
      const scope: BindingScope = { vars: { count: 2 } }
      // flattenScopeForExpr spreads vars leaves → { count: 2 }; IF(count > 0, …) → 'yes'.
      expect(flattenScopeForExpr('IF(count > 0, "yes", "no")', scope)).toMatchObject({ count: 2 })
      expect(resolveBinding({ $bind: 'IF(count > 0, "yes", "no")', mode: 'expr' }, scope)).toBe(
        'yes'
      )
    })

    it('evaluates the false branch when the leaf is falsey', () => {
      const scope: BindingScope = { vars: { count: 0 } }
      expect(resolveBinding({ $bind: 'IF(count > 0, "yes", "no")', mode: 'expr' }, scope)).toBe(
        'no'
      )
    })

    it('treats a missing leaf as null → 0 in arithmetic', () => {
      // extractFieldRefs('count + 1') → ['count']; getPath → null; toDouble(null)=0 → 1.
      expect(resolveBinding({ $bind: 'count + 1', mode: 'expr' }, {})).toBe(1)
    })

    it('spreads record/item leaves so bare names resolve like field formulas', () => {
      const scope: BindingScope = { record: { total: 10 }, item: { total: 5 } }
      // item is spread last → wins over record for the same leaf name.
      expect(resolveBinding({ $bind: 'total * 2', mode: 'expr' }, scope)).toBe(10)
    })

    it('returns null (no throw) for a malformed expression', () => {
      expect(resolveBinding({ $bind: 'IF(', mode: 'expr' }, { vars: { count: 1 } })).toBeNull()
    })
  })

  it('recurses into nested arrays and objects', () => {
    const scope: BindingScope = { record: { name: 'Ada' }, vars: { n: 7 } }
    const out = resolveBindings(
      {
        items: [{ label: { $bind: 'record.name', mode: 'path' } }, 'literal'],
        nested: { deep: { $bind: 'vars.n', mode: 'path' } },
      },
      scope
    )
    expect(out).toEqual({
      items: [{ label: 'Ada' }, 'literal'],
      nested: { deep: 7 },
    })
  })

  describe('prototype-pollution guard', () => {
    it('resolves __proto__ / constructor / prototype tokens to null', () => {
      const scope: BindingScope = { record: { name: 'x' } }
      expect(resolveBinding({ $bind: '__proto__', mode: 'path' }, scope)).toBeNull()
      expect(resolveBinding({ $bind: 'constructor', mode: 'path' }, scope)).toBeNull()
      expect(resolveBinding({ $bind: 'record.__proto__.x', mode: 'path' }, scope)).toBeNull()
      expect(
        resolveBinding({ $bind: 'record.constructor.prototype', mode: 'path' }, scope)
      ).toBeNull()
    })
  })
})
