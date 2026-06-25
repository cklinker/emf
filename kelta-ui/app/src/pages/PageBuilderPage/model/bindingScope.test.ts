/** `getPath` walker unit tests (slice 2d), including the prototype-pollution guard. */
import { describe, it, expect } from 'vitest'
import { getPath, type BindingScope } from './bindingScope'

const scope: BindingScope = {
  record: { name: 'Acme', owner: { name: 'Ada' } },
  data: { accounts: [{ name: 'A' }, { name: 'B' }] },
  vars: { count: 3 },
}

describe('getPath', () => {
  it('walks a dotted path', () => {
    expect(getPath(scope, 'record.name')).toBe('Acme')
    expect(getPath(scope, 'record.owner.name')).toBe('Ada')
  })

  it('walks an indexed path', () => {
    expect(getPath(scope, 'data.accounts[1].name')).toBe('B')
  })

  it('walks a mixed dotted + indexed path', () => {
    expect(getPath(scope, 'data.accounts[0].name')).toBe('A')
  })

  it('returns null for a missing root or leaf', () => {
    expect(getPath(scope, 'nope')).toBeNull()
    expect(getPath(scope, 'record.nope')).toBeNull()
  })

  it('returns null when a mid-path value is null/undefined', () => {
    expect(getPath({ record: { owner: null } as never }, 'record.owner.name')).toBeNull()
  })

  it('returns null when a mid-path value is a non-object', () => {
    expect(getPath({ record: { name: 'Acme' } }, 'record.name.length')).toBeNull()
  })

  it('returns null for an empty path', () => {
    expect(getPath(scope, '')).toBeNull()
  })

  describe('prototype-pollution guard', () => {
    it('refuses __proto__ / constructor / prototype tokens', () => {
      expect(getPath(scope, '__proto__')).toBeNull()
      expect(getPath(scope, 'constructor')).toBeNull()
      expect(getPath(scope, 'record.__proto__')).toBeNull()
      expect(getPath(scope, 'record.constructor.prototype')).toBeNull()
      expect(getPath(scope, 'a.constructor.prototype')).toBeNull()
    })
  })
})
