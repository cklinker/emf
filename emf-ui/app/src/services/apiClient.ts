/**
 * API Client
 *
 * Provides a thin wrapper around the SDK's Axios instance that preserves
 * the same get/post/put/patch/delete interface used throughout the UI.
 *
 * All HTTP calls are routed through the SDK's EMFClient Axios instance,
 * which provides: auth token injection, retry with exponential backoff,
 * and consistent error handling.
 *
 * Requirements:
 * - 2.7: Include access token in all API requests (via SDK TokenProvider)
 * - 2.8: Trigger token refresh on 401 responses (via SDK interceptor)
 */

import type { AxiosInstance } from 'axios'
import axios from 'axios'

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
 * Parse an Axios error into an ApiError with structured field-level details.
 */
function parseAxiosError(error: unknown): ApiError {
  if (axios.isAxiosError(error) && error.response) {
    const { status, data } = error.response
    let serverMessage = error.response.statusText || 'Request failed'
    let fieldErrors: ApiFieldError[] = []

    if (data && typeof data === 'object') {
      const body = data as Record<string, unknown>
      if (typeof body.message === 'string') {
        serverMessage = body.message
      }
      if (Array.isArray(body.errors)) {
        fieldErrors = (body.errors as unknown[])
          .filter(
            (e: unknown): e is { field: string; message: string; code?: string } =>
              typeof e === 'object' &&
              e !== null &&
              typeof (e as ApiFieldError).message === 'string'
          )
          .map((e) => ({
            field: e.field,
            message: e.message,
            code: e.code,
          }))
      }
    }

    return new ApiError(status, serverMessage, fieldErrors)
  }

  // Network or other errors
  const message = error instanceof Error ? error.message : 'Request failed'
  return new ApiError(0, message)
}

/**
 * Axios-backed API client that wraps the SDK's Axios instance.
 *
 * Preserves the same interface (get, post, put, patch, delete) used by
 * all existing UI components, while routing all requests through the
 * SDK's Axios pipeline (auth, retry, interceptors).
 */
export class ApiClient {
  private axios: AxiosInstance

  constructor(axiosInstance: AxiosInstance) {
    this.axios = axiosInstance
  }

  /**
   * GET request
   */
  async get<T = unknown>(url: string): Promise<T> {
    try {
      const response = await this.axios.get<T>(url)
      return response.data
    } catch (error) {
      throw parseAxiosError(error)
    }
  }

  /**
   * POST request
   */
  async post<T = unknown>(url: string, data?: unknown): Promise<T> {
    try {
      const response = await this.axios.post<T>(url, data)
      return response.data
    } catch (error) {
      throw parseAxiosError(error)
    }
  }

  /**
   * POST request with multipart form data (e.g. file uploads)
   */
  async postFormData<T = unknown>(url: string, formData: FormData): Promise<T> {
    try {
      const response = await this.axios.post<T>(url, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return response.data
    } catch (error) {
      throw parseAxiosError(error)
    }
  }

  /**
   * PUT request
   */
  async put<T = unknown>(url: string, data?: unknown): Promise<T> {
    try {
      const response = await this.axios.put<T>(url, data)
      return response.data
    } catch (error) {
      throw parseAxiosError(error)
    }
  }

  /**
   * PATCH request
   */
  async patch<T = unknown>(url: string, data?: unknown): Promise<T> {
    try {
      const response = await this.axios.patch<T>(url, data)
      return response.data
    } catch (error) {
      throw parseAxiosError(error)
    }
  }

  /**
   * DELETE request
   */
  async delete<T = unknown>(url: string): Promise<T> {
    try {
      const response = await this.axios.delete<T>(url)
      return response.data
    } catch (error) {
      throw parseAxiosError(error)
    }
  }
}
