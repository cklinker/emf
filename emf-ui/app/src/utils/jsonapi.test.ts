import { describe, it, expect } from 'vitest'
import { flattenResource, unwrapResource, unwrapCollection } from './jsonapi'

describe('jsonapi utilities', () => {
  describe('flattenResource', () => {
    it('should flatten a JSON:API resource with attributes', () => {
      const result = flattenResource({
        id: '123',
        type: 'product',
        attributes: { name: 'Test Product', price: 10.0 },
      })

      expect(result).toEqual({
        id: '123',
        name: 'Test Product',
        price: 10.0,
      })
    })

    it('should flatten a resource with relationships', () => {
      const result = flattenResource({
        id: '123',
        type: 'product',
        attributes: { name: 'Test' },
        relationships: {
          owner: { data: { id: 'user-1', type: 'users' } },
          category: { data: null },
        },
      })

      expect(result).toEqual({
        id: '123',
        name: 'Test',
        owner: 'user-1',
      })
    })

    it('should handle resource with no attributes', () => {
      const result = flattenResource({
        id: '123',
        type: 'product',
      })

      expect(result).toEqual({ id: '123' })
    })

    it('should handle resource with empty attributes', () => {
      const result = flattenResource({
        id: '123',
        type: 'product',
        attributes: {},
      })

      expect(result).toEqual({ id: '123' })
    })
  })

  describe('unwrapResource', () => {
    it('should unwrap a JSON:API single-resource response', () => {
      const response = {
        data: {
          id: '4a672036-babc-42b9-b071-77e8133c80cb',
          type: 'product',
          attributes: {
            name: 'testing',
            price: 10.0,
            created_at: '2026-02-13T17:47:54.855+00:00',
            updated_at: '2026-02-13T22:08:38.107+00:00',
          },
          relationships: {
            owner: { data: null },
          },
        },
      }

      const result = unwrapResource(response)

      expect(result).toEqual({
        id: '4a672036-babc-42b9-b071-77e8133c80cb',
        name: 'testing',
        price: 10.0,
        created_at: '2026-02-13T17:47:54.855+00:00',
        updated_at: '2026-02-13T22:08:38.107+00:00',
      })
    })

    it('should pass through already-flat objects', () => {
      const flatResource = {
        id: '123',
        name: 'Test',
        price: 5.0,
      }

      const result = unwrapResource(flatResource)

      expect(result).toEqual(flatResource)
    })

    it('should handle null/undefined gracefully', () => {
      expect(unwrapResource(null)).toBeNull()
      expect(unwrapResource(undefined)).toBeUndefined()
    })
  })

  describe('unwrapCollection', () => {
    it('should unwrap a JSON:API collection response', () => {
      const response = {
        data: [
          {
            id: '1',
            type: 'product',
            attributes: { name: 'Product A', price: 10 },
          },
          {
            id: '2',
            type: 'product',
            attributes: { name: 'Product B', price: 20 },
          },
        ],
        total: 2,
        page: 1,
        pageSize: 20,
      }

      const result = unwrapCollection(response)

      expect(result).toEqual({
        data: [
          { id: '1', name: 'Product A', price: 10 },
          { id: '2', name: 'Product B', price: 20 },
        ],
        total: 2,
        page: 1,
        pageSize: 20,
      })
    })

    it('should pass through already-flat collection responses', () => {
      const response = {
        data: [
          { id: '1', name: 'Product A' },
          { id: '2', name: 'Product B' },
        ],
        total: 2,
        page: 1,
        pageSize: 20,
      }

      const result = unwrapCollection(response)

      expect(result).toEqual(response)
    })

    it('should handle empty data array', () => {
      const response = {
        data: [],
        total: 0,
        page: 1,
        pageSize: 20,
      }

      const result = unwrapCollection(response)

      expect(result).toEqual(response)
    })
  })
})
