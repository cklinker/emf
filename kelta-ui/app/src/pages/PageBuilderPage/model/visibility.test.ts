/** Conditional-visibility semantics (app-platform slice 1). */
import { describe, it, expect } from 'vitest'
import { isHiddenValue, visibilityKind } from './visibility'

describe('isHiddenValue', () => {
  it('treats absent (undefined) as visible', () => {
    expect(isHiddenValue(undefined)).toBe(false)
  })

  it('hides on false, "false", 0, empty string, and null', () => {
    expect(isHiddenValue(false)).toBe(true)
    expect(isHiddenValue('false')).toBe(true)
    expect(isHiddenValue(0)).toBe(true)
    expect(isHiddenValue('')).toBe(true)
    expect(isHiddenValue(null)).toBe(true)
  })

  it('shows on true, truthy strings, and numbers', () => {
    expect(isHiddenValue(true)).toBe(false)
    expect(isHiddenValue('true')).toBe(false)
    expect(isHiddenValue(1)).toBe(false)
    expect(isHiddenValue('yes')).toBe(false)
  })
})

describe('visibilityKind', () => {
  it('classifies absent/true as default', () => {
    expect(visibilityKind(undefined)).toBe('default')
    expect(visibilityKind(true)).toBe('default')
  })

  it('classifies a literal false as literal-hidden', () => {
    expect(visibilityKind(false)).toBe('literal-hidden')
  })

  it('classifies a binding as bound', () => {
    expect(visibilityKind({ $bind: 'vars.show', mode: 'expr' })).toBe('bound')
  })
})
