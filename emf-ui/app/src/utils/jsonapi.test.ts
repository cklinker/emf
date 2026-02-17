import { describe, it, expect } from 'vitest'
import { flattenResource, unwrapResource, unwrapCollection, wrapResource } from './jsonapi'

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
        _rel_owner: { id: 'user-1', type: 'users' },
        category: null,
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
        owner: null,
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

  describe('wrapResource', () => {
    it('should wrap a flat object into JSON:API format', () => {
      const result = wrapResource('products', { name: 'Test', price: 10 })

      expect(result).toEqual({
        data: {
          type: 'products',
          attributes: { name: 'Test', price: 10 },
        },
      })
    })

    it('should include id when provided', () => {
      const result = wrapResource('products', { name: 'Test' }, '123')

      expect(result).toEqual({
        data: {
          type: 'products',
          id: '123',
          attributes: { name: 'Test' },
        },
      })
    })

    it('should strip system fields from attributes', () => {
      const result = wrapResource('products', {
        id: '123',
        name: 'Test',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-02',
      })

      expect(result.data.attributes).toEqual({ name: 'Test' })
    })

    it('should strip _rel_ prefixed fields from attributes', () => {
      const result = wrapResource('products', {
        name: 'Test',
        category: 'cat-1',
        _rel_category: { type: 'categories', id: 'cat-1' },
      })

      expect(result.data.attributes).toEqual({ name: 'Test', category: 'cat-1' })
    })

    it('should move relationship fields to relationships section', () => {
      const result = wrapResource('products', { name: 'Test', category: 'cat-1' }, undefined, {
        category: 'categories',
      })

      expect(result).toEqual({
        data: {
          type: 'products',
          attributes: { name: 'Test' },
          relationships: {
            category: { data: { type: 'categories', id: 'cat-1' } },
          },
        },
      })
    })

    it('should set relationship data to null for empty values', () => {
      const result = wrapResource('products', { name: 'Test', category: null }, undefined, {
        category: 'categories',
      })

      expect(result).toEqual({
        data: {
          type: 'products',
          attributes: { name: 'Test' },
          relationships: {
            category: { data: null },
          },
        },
      })
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

    it('should unwrap runtime metadata envelope response', () => {
      const response = {
        data: [
          { id: '1', name: 'Product A', price: 10 },
          { id: '2', name: 'Product B', price: 20 },
        ],
        metadata: {
          totalCount: 50,
          currentPage: 2,
          pageSize: 25,
          totalPages: 2,
        },
      }

      const result = unwrapCollection(response)

      expect(result).toEqual({
        data: [
          { id: '1', name: 'Product A', price: 10 },
          { id: '2', name: 'Product B', price: 20 },
        ],
        total: 50,
        page: 2,
        pageSize: 25,
      })
    })

    it('should prefer metadata over top-level fields when both present', () => {
      const response = {
        data: [{ id: '1', name: 'Product A' }],
        total: 999,
        page: 99,
        pageSize: 10,
        metadata: {
          totalCount: 100,
          currentPage: 1,
          pageSize: 20,
          totalPages: 5,
        },
      }

      const result = unwrapCollection(response)

      expect(result).toEqual({
        data: [{ id: '1', name: 'Product A' }],
        total: 100,
        page: 1,
        pageSize: 20,
      })
    })
  })
})
