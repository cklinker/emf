import { describe, expect, it } from 'vitest'
import { deserializeMapping, serializeBindings } from '../mappingSerializer'
import type { MappingMap } from '../types'

describe('PayloadMapper · mappingSerializer', () => {
  const TARGETS = ['subject', 'body', 'meta.priority']

  it('serializes constants verbatim', () => {
    const bindings: MappingMap = {
      subject: { kind: 'constant', value: 'Order approved' },
      body: { kind: 'unset', value: '' },
      'meta.priority': { kind: 'unset', value: '' },
    }

    expect(serializeBindings(bindings)).toEqual({
      subject: 'Order approved',
    })
  })

  it('wraps variable bindings in ${} tokens', () => {
    const bindings: MappingMap = {
      subject: { kind: 'variable', value: '$.record.data.name' },
      body: { kind: 'unset', value: '' },
      'meta.priority': { kind: 'unset', value: '' },
    }

    expect(serializeBindings(bindings)).toEqual({
      subject: '${$.record.data.name}',
    })
  })

  it('prefixes expression bindings with =', () => {
    const bindings: MappingMap = {
      subject: { kind: 'expression', value: '$uppercase(record.name)' },
      body: { kind: 'unset', value: '' },
      'meta.priority': { kind: 'unset', value: '' },
    }

    expect(serializeBindings(bindings)).toEqual({
      subject: '=$uppercase(record.name)',
    })
  })

  it('reifies dotted target paths into nested objects', () => {
    const bindings: MappingMap = {
      subject: { kind: 'unset', value: '' },
      body: { kind: 'unset', value: '' },
      'meta.priority': { kind: 'constant', value: 'high' },
    }

    expect(serializeBindings(bindings)).toEqual({
      meta: { priority: 'high' },
    })
  })

  it('round-trips bindings through serialize → deserialize', () => {
    const bindings: MappingMap = {
      subject: { kind: 'variable', value: '$.record.data.name' },
      body: { kind: 'expression', value: '$sum(items.price)' },
      'meta.priority': { kind: 'constant', value: 'high' },
    }

    const wire = serializeBindings(bindings)
    const decoded = deserializeMapping(wire, TARGETS)

    expect(decoded.subject).toEqual({ kind: 'variable', value: '$.record.data.name' })
    expect(decoded.body).toEqual({ kind: 'expression', value: '$sum(items.price)' })
    expect(decoded['meta.priority']).toEqual({ kind: 'constant', value: 'high' })
  })

  it('treats targets missing from the wire shape as unset', () => {
    const decoded = deserializeMapping({ subject: 'Hi' }, TARGETS)
    expect(decoded.subject).toEqual({ kind: 'constant', value: 'Hi' })
    expect(decoded.body).toEqual({ kind: 'unset', value: '' })
    expect(decoded['meta.priority']).toEqual({ kind: 'unset', value: '' })
  })

  it('does not double-wrap pre-tokenized variables', () => {
    const bindings: MappingMap = {
      subject: { kind: 'variable', value: '${$.x}' },
      body: { kind: 'unset', value: '' },
      'meta.priority': { kind: 'unset', value: '' },
    }
    expect(serializeBindings(bindings)).toEqual({ subject: '${$.x}' })
  })
})
