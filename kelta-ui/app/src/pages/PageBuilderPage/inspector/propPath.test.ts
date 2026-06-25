import { describe, it, expect } from 'vitest'
import { getByPath, setByPath, deleteByPath } from './propPath'
import type { PropValue } from '../model/pageModel'

describe('propPath', () => {
  describe('getByPath', () => {
    it('reads a flat key', () => {
      expect(getByPath({ text: 'Orders' }, 'text')).toBe('Orders')
    })

    it('reads a nested key', () => {
      expect(getByPath({ dataView: { collection: 'orders' } }, 'dataView.collection')).toBe(
        'orders'
      )
    })

    it('returns undefined for a missing flat key', () => {
      expect(getByPath({ text: 'x' }, 'missing')).toBeUndefined()
    })

    it('returns undefined for a missing nested branch', () => {
      expect(getByPath({ dataView: {} }, 'dataView.collection')).toBeUndefined()
      expect(getByPath({}, 'dataView.collection')).toBeUndefined()
    })

    it('returns undefined for an undefined source', () => {
      expect(getByPath(undefined, 'text')).toBeUndefined()
    })
  })

  describe('setByPath', () => {
    it('writes a flat key without mutating the input', () => {
      const input: Record<string, PropValue> = { text: 'a' }
      const out = setByPath(input, 'text', 'b')
      expect(out).toEqual({ text: 'b' })
      expect(input).toEqual({ text: 'a' })
      expect(out).not.toBe(input)
    })

    it('writes a nested key, creating intermediate objects', () => {
      const input: Record<string, PropValue> = {}
      const out = setByPath(input, 'dataView.collection', 'orders')
      expect(out).toEqual({ dataView: { collection: 'orders' } })
      expect(input).toEqual({})
    })

    it('merges into an existing nested object immutably', () => {
      const input: Record<string, PropValue> = { dataView: { collection: 'orders', limit: 25 } }
      const out = setByPath(input, 'dataView.collection', 'invoices')
      expect(out).toEqual({ dataView: { collection: 'invoices', limit: 25 } })
      expect(input.dataView).toEqual({ collection: 'orders', limit: 25 })
      expect(out.dataView).not.toBe(input.dataView)
    })

    it('replaces a non-object intermediate with a fresh object', () => {
      const input: Record<string, PropValue> = { dataView: 'oops' }
      const out = setByPath(input, 'dataView.collection', 'orders')
      expect(out).toEqual({ dataView: { collection: 'orders' } })
    })
  })

  describe('deleteByPath', () => {
    it('deletes a flat key immutably', () => {
      const input: Record<string, PropValue> = { a: 1, b: 2 }
      const out = deleteByPath(input, 'a')
      expect(out).toEqual({ b: 2 })
      expect(input).toEqual({ a: 1, b: 2 })
    })

    it('deletes a nested key immutably', () => {
      const input: Record<string, PropValue> = { dataView: { collection: 'orders', limit: 25 } }
      const out = deleteByPath(input, 'dataView.limit')
      expect(out).toEqual({ dataView: { collection: 'orders' } })
      expect(input.dataView).toEqual({ collection: 'orders', limit: 25 })
    })

    it('is a no-op for a missing key', () => {
      const input: Record<string, PropValue> = { a: 1 }
      expect(deleteByPath(input, 'missing')).toEqual({ a: 1 })
    })
  })
})
