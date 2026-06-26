/** `{{…}}` interpolation unit tests (slice 2d). */
import { describe, it, expect } from 'vitest'
import { interpolate, isTemplate } from './interpolate'
import type { BindingScope } from './bindingScope'

describe('interpolate', () => {
  it('passes a plain string through unchanged', () => {
    expect(interpolate('hello', {})).toBe('hello')
  })

  it('resolves a single path tag', () => {
    const scope: BindingScope = { record: { name: 'Ada' } }
    expect(interpolate('Hi {{record.name}}', scope)).toBe('Hi Ada')
  })

  it('resolves an indexed tag', () => {
    const scope: BindingScope = { data: { accounts: [{ name: 'Acme' }] } }
    expect(interpolate('{{data.accounts[0].name}}', scope)).toBe('Acme')
  })

  it('resolves an expr tag with the leading "="', () => {
    const scope: BindingScope = { vars: { count: 1 } }
    expect(interpolate('{{= IF(count > 0, "a", "b") }}', scope)).toBe('a')
  })

  it('renders a missing tag as the empty string', () => {
    expect(interpolate('x {{record.nope}} y', { record: {} })).toBe('x  y')
  })

  it('resolves multiple tags mixed with literals', () => {
    const scope: BindingScope = { record: { first: 'Ada', last: 'Lovelace' } }
    expect(interpolate('{{record.first}} {{record.last}}!', scope)).toBe('Ada Lovelace!')
  })
})

describe('isTemplate', () => {
  it('detects strings containing a tag', () => {
    expect(isTemplate('a {{x}} b')).toBe(true)
    expect(isTemplate('plain')).toBe(false)
    expect(isTemplate(42)).toBe(false)
  })
})
