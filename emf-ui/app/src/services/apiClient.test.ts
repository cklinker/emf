/**
 * ApiClient Tests
 *
 * Tests the ApiError class and Axios-based error handling of the ApiClient.
 * The ApiClient wraps an Axios instance (from the SDK's EMFClient), so all
 * tests mock Axios methods rather than fetch.
 */

import { describe, it, expect, beforeEach } from 'vitest'
import { ApiClient, ApiError } from './apiClient'
import { mockAxios, createAxiosError, resetMockAxios } from '../test/testUtils'

describe('ApiError', () => {
  it('should use field error messages when available', () => {
    const error = new ApiError(400, 'Validation failed', [
      { field: 'quantity_on_hand', message: 'Value must be >= 100', code: 'validationRule' },
    ])
    expect(error.message).toBe('Value must be >= 100')
    expect(error.status).toBe(400)
    expect(error.serverMessage).toBe('Validation failed')
    expect(error.fieldErrors).toHaveLength(1)
    expect(error.fieldErrors[0].field).toBe('quantity_on_hand')
  })

  it('should join multiple field error messages', () => {
    const error = new ApiError(400, 'Validation failed', [
      { field: 'name', message: 'Name is required' },
      { field: 'email', message: 'Invalid email' },
    ])
    expect(error.message).toBe('Name is required; Invalid email')
  })

  it('should fall back to server message when no field errors', () => {
    const error = new ApiError(400, 'Bad Request', [])
    expect(error.message).toBe('Bad Request')
  })

  it('should fall back to "Request failed" when no messages at all', () => {
    const error = new ApiError(500, '', [])
    expect(error.message).toBe('Request failed')
  })

  it('should be instanceof Error', () => {
    const error = new ApiError(400, 'test')
    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe('ApiError')
  })
})

describe('ApiClient error handling', () => {
  let client: ApiClient

  beforeEach(() => {
    resetMockAxios()
    // Create an ApiClient wrapping the shared mock Axios instance
    client = new ApiClient(mockAxios as never)
  })

  describe('GET', () => {
    it('should throw ApiError with parsed body on error response', async () => {
      mockAxios.get.mockRejectedValueOnce(
        createAxiosError(400, {
          message: 'Validation failed',
          errors: [{ field: 'name', message: 'Name is required', code: 'nullable' }],
        })
      )

      try {
        await client.get('/test')
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(400)
        expect(apiError.serverMessage).toBe('Validation failed')
        expect(apiError.fieldErrors).toHaveLength(1)
        expect(apiError.fieldErrors[0].field).toBe('name')
        expect(apiError.message).toBe('Name is required')
      }
    })

    it('should fall back to statusText when body has no message', async () => {
      mockAxios.get.mockRejectedValueOnce(createAxiosError(500, null, 'Internal Server Error'))

      try {
        await client.get('/test')
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(500)
        expect(apiError.serverMessage).toBe('Internal Server Error')
        expect(apiError.fieldErrors).toHaveLength(0)
      }
    })
  })

  describe('PATCH', () => {
    it('should throw ApiError with validation details on 400', async () => {
      mockAxios.patch.mockRejectedValueOnce(
        createAxiosError(400, {
          requestId: 'abc123',
          status: 400,
          error: 'Bad Request',
          message: 'Record failed 1 validation rule(s)',
          path: '/api/products/123',
          errors: [
            {
              field: 'quantity_on_hand',
              message: 'Quantity on hand must be at least 100',
              code: 'validationRule',
            },
          ],
        })
      )

      try {
        await client.patch('/api/products/123', { data: {} })
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(400)
        expect(apiError.message).toBe('Quantity on hand must be at least 100')
        expect(apiError.fieldErrors[0].field).toBe('quantity_on_hand')
        expect(apiError.fieldErrors[0].code).toBe('validationRule')
      }
    })
  })

  describe('POST', () => {
    it('should throw ApiError with field errors on 400', async () => {
      mockAxios.post.mockRejectedValueOnce(
        createAxiosError(400, {
          message: 'Validation failed',
          errors: [{ field: 'email', message: 'Invalid email format', code: 'email' }],
        })
      )

      try {
        await client.post('/test', { email: 'bad' })
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.fieldErrors).toHaveLength(1)
        expect(apiError.message).toBe('Invalid email format')
      }
    })
  })

  describe('PUT', () => {
    it('should throw ApiError on error response', async () => {
      mockAxios.put.mockRejectedValueOnce(
        createAxiosError(409, {
          message: 'Unique constraint violation',
          errors: [{ field: 'name', message: 'Name already exists', code: 'unique' }],
        })
      )

      try {
        await client.put('/test', {})
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(409)
        expect(apiError.fieldErrors[0].field).toBe('name')
      }
    })
  })

  describe('DELETE', () => {
    it('should throw ApiError on error response', async () => {
      mockAxios.delete.mockRejectedValueOnce(
        createAxiosError(404, {
          message: 'Not found',
          errors: [],
        })
      )

      try {
        await client.delete('/test/123')
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(404)
        expect(apiError.serverMessage).toBe('Not found')
      }
    })
  })

  describe('successful requests', () => {
    it('GET should return parsed data on success', async () => {
      mockAxios.get.mockResolvedValueOnce({ data: { id: '1', name: 'Test' } })
      const result = await client.get('/test')
      expect(result).toEqual({ id: '1', name: 'Test' })
    })

    it('POST should return parsed data on success', async () => {
      mockAxios.post.mockResolvedValueOnce({ data: { id: '2' } })
      const result = await client.post('/test', { name: 'New' })
      expect(result).toEqual({ id: '2' })
    })

    it('DELETE should return undefined when response data is undefined', async () => {
      mockAxios.delete.mockResolvedValueOnce({ data: undefined })
      const result = await client.delete('/test/1')
      expect(result).toBeUndefined()
    })
  })

  describe('network errors', () => {
    it('should wrap non-Axios errors into ApiError with status 0', async () => {
      mockAxios.get.mockRejectedValueOnce(new Error('Network error'))

      try {
        await client.get('/test')
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(0)
        expect(apiError.message).toBe('Network error')
      }
    })
  })
})
