/**
 * ApiClient Tests
 *
 * Tests the ApiError class and error-parsing behavior of the ApiClient.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ApiClient, ApiError } from './apiClient'

// Helper to build a mock Response
function mockResponse(status: number, body: unknown, statusText = ''): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    json: () => Promise.resolve(body),
    headers: new Headers(),
    redirected: false,
    type: 'basic' as ResponseType,
    url: '',
    clone: () => mockResponse(status, body, statusText),
    body: null,
    bodyUsed: false,
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    blob: () => Promise.resolve(new Blob()),
    formData: () => Promise.resolve(new FormData()),
    text: () => Promise.resolve(JSON.stringify(body)),
    bytes: () => Promise.resolve(new Uint8Array()),
  }
}

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
  let fetchSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    client = new ApiClient({
      baseUrl: 'https://api.example.com',
      getAccessToken: () => Promise.resolve('test-token'),
    })

    fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('GET', () => {
    it('should throw ApiError with parsed body on non-ok response', async () => {
      fetchSpy.mockResolvedValueOnce(
        mockResponse(
          400,
          {
            message: 'Validation failed',
            errors: [{ field: 'name', message: 'Name is required', code: 'nullable' }],
          },
          'Bad Request'
        )
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

    it('should fall back to statusText when body is not JSON', async () => {
      const resp = mockResponse(500, null, 'Internal Server Error')
      resp.json = () => Promise.reject(new Error('not json'))
      fetchSpy.mockResolvedValueOnce(resp)

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
      fetchSpy.mockResolvedValueOnce(
        mockResponse(
          400,
          {
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
          },
          'Bad Request'
        )
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
      fetchSpy.mockResolvedValueOnce(
        mockResponse(
          400,
          {
            message: 'Validation failed',
            errors: [{ field: 'email', message: 'Invalid email format', code: 'email' }],
          },
          'Bad Request'
        )
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
    it('should throw ApiError on non-ok response', async () => {
      fetchSpy.mockResolvedValueOnce(
        mockResponse(
          409,
          {
            message: 'Unique constraint violation',
            errors: [{ field: 'name', message: 'Name already exists', code: 'unique' }],
          },
          'Conflict'
        )
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
    it('should throw ApiError on non-ok response', async () => {
      fetchSpy.mockResolvedValueOnce(
        mockResponse(
          404,
          {
            message: 'Not found',
            errors: [],
          },
          'Not Found'
        )
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
    it('GET should return parsed JSON on success', async () => {
      fetchSpy.mockResolvedValueOnce(mockResponse(200, { id: '1', name: 'Test' }))
      const result = await client.get('/test')
      expect(result).toEqual({ id: '1', name: 'Test' })
    })

    it('POST should return parsed JSON on success', async () => {
      fetchSpy.mockResolvedValueOnce(mockResponse(201, { id: '2' }))
      const result = await client.post('/test', { name: 'New' })
      expect(result).toEqual({ id: '2' })
    })

    it('DELETE should return undefined on 204', async () => {
      fetchSpy.mockResolvedValueOnce(mockResponse(204, null))
      const result = await client.delete('/test/1')
      expect(result).toBeUndefined()
    })
  })
})
