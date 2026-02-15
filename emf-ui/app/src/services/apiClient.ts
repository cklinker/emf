/**
 * API Client
 *
 * Provides an authenticated fetch wrapper that automatically includes
 * the Bearer token in all API requests.
 *
 * Requirements:
 * - 2.7: Include access token in all API requests
 * - 2.8: Trigger token refresh on 401 responses
 */

/**
 * Structured field-level error from the API
 */
export interface ApiFieldError {
  field: string
  message: string
  code?: string
}

/**
 * Custom API error that preserves the HTTP status code,
 * server message, and field-level validation errors.
 */
export class ApiError extends Error {
  public readonly status: number
  public readonly serverMessage: string
  public readonly fieldErrors: ApiFieldError[]

  constructor(status: number, serverMessage: string, fieldErrors: ApiFieldError[] = []) {
    // Build a user-friendly message
    const fieldMessages = fieldErrors.map((e) => e.message).filter(Boolean)
    const displayMessage =
      fieldMessages.length > 0 ? fieldMessages.join('; ') : serverMessage || 'Request failed'
    super(displayMessage)
    this.name = 'ApiError'
    this.status = status
    this.serverMessage = serverMessage
    this.fieldErrors = fieldErrors
  }
}

/**
 * Parse a non-ok Response into an ApiError.
 * Attempts to read the JSON body for structured error details;
 * falls back to the HTTP status text.
 */
async function parseErrorResponse(response: Response): Promise<ApiError> {
  let serverMessage = response.statusText || 'Request failed'
  let fieldErrors: ApiFieldError[] = []

  try {
    const body = await response.json()
    if (body && typeof body === 'object') {
      if (typeof body.message === 'string') {
        serverMessage = body.message
      }
      if (Array.isArray(body.errors)) {
        fieldErrors = body.errors
          .filter(
            (e: unknown): e is { field: string; message: string; code?: string } =>
              typeof e === 'object' &&
              e !== null &&
              typeof (e as ApiFieldError).message === 'string'
          )
          .map((e: { field: string; message: string; code?: string }) => ({
            field: e.field,
            message: e.message,
            code: e.code,
          }))
      }
    }
  } catch {
    // Response body is not JSON — use the status text
  }

  return new ApiError(response.status, serverMessage, fieldErrors)
}

export interface ApiClientConfig {
  baseUrl?: string
  getAccessToken: () => Promise<string | null>
  onUnauthorized?: () => void
}

export class ApiClient {
  private config: ApiClientConfig

  constructor(config: ApiClientConfig) {
    this.config = {
      baseUrl: config.baseUrl || '',
      ...config,
    }
  }

  /**
   * Make an authenticated API request
   */
  async fetch(url: string, options: RequestInit = {}): Promise<Response> {
    // Get access token
    const token = await this.config.getAccessToken()

    // Prepare headers
    const headers = new Headers(options.headers)
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
    if (!headers.has('Content-Type') && options.body && typeof options.body === 'string') {
      headers.set('Content-Type', 'application/json')
    }

    // Make request
    const fullUrl = url.startsWith('http') ? url : `${this.config.baseUrl}${url}`
    const response = await fetch(fullUrl, {
      ...options,
      headers,
    })

    // Handle 401 Unauthorized
    if (response.status === 401 && this.config.onUnauthorized) {
      this.config.onUnauthorized()
    }

    return response
  }

  /**
   * GET request
   */
  async get<T = unknown>(url: string, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'GET',
    })

    if (!response.ok) {
      throw await parseErrorResponse(response)
    }

    return response.json()
  }

  /**
   * POST request
   */
  async post<T = unknown>(url: string, data?: unknown, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    })

    if (!response.ok) {
      throw await parseErrorResponse(response)
    }

    return response.json()
  }

  /**
   * PUT request
   */
  async put<T = unknown>(url: string, data?: unknown, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    })

    if (!response.ok) {
      throw await parseErrorResponse(response)
    }

    return response.json()
  }

  /**
   * PATCH request
   */
  async patch<T = unknown>(url: string, data?: unknown, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'PATCH',
      body: data ? JSON.stringify(data) : undefined,
    })

    if (!response.ok) {
      throw await parseErrorResponse(response)
    }

    return response.json()
  }

  /**
   * DELETE request
   */
  async delete<T = unknown>(url: string, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'DELETE',
    })

    if (!response.ok) {
      throw await parseErrorResponse(response)
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return undefined as T
    }

    return response.json()
  }
}

/**
 * Create an API client instance
 */
export function createApiClient(config: ApiClientConfig): ApiClient {
  return new ApiClient(config)
}
