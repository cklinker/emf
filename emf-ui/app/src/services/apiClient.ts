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

export interface ApiClientConfig {
  baseUrl?: string;
  getAccessToken: () => Promise<string | null>;
  onUnauthorized?: () => void;
}

export class ApiClient {
  private config: ApiClientConfig;

  constructor(config: ApiClientConfig) {
    this.config = {
      baseUrl: config.baseUrl || '',
      ...config,
    };
  }

  /**
   * Make an authenticated API request
   */
  async fetch(url: string, options: RequestInit = {}): Promise<Response> {
    // Get access token
    const token = await this.config.getAccessToken();

    // Prepare headers
    const headers = new Headers(options.headers);
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    if (!headers.has('Content-Type') && options.body && typeof options.body === 'string') {
      headers.set('Content-Type', 'application/json');
    }

    // Make request
    const fullUrl = url.startsWith('http') ? url : `${this.config.baseUrl}${url}`;
    const response = await fetch(fullUrl, {
      ...options,
      headers,
    });

    // Handle 401 Unauthorized
    if (response.status === 401 && this.config.onUnauthorized) {
      this.config.onUnauthorized();
    }

    return response;
  }

  /**
   * GET request
   */
  async get<T = unknown>(url: string, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'GET',
    });

    if (!response.ok) {
      throw new Error(`API request failed: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * POST request
   */
  async post<T = unknown>(url: string, data?: unknown, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    });

    if (!response.ok) {
      throw new Error(`API request failed: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * PUT request
   */
  async put<T = unknown>(url: string, data?: unknown, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    });

    if (!response.ok) {
      throw new Error(`API request failed: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * PATCH request
   */
  async patch<T = unknown>(url: string, data?: unknown, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'PATCH',
      body: data ? JSON.stringify(data) : undefined,
    });

    if (!response.ok) {
      throw new Error(`API request failed: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * DELETE request
   */
  async delete<T = unknown>(url: string, options: RequestInit = {}): Promise<T> {
    const response = await this.fetch(url, {
      ...options,
      method: 'DELETE',
    });

    if (!response.ok) {
      throw new Error(`API request failed: ${response.statusText}`);
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return undefined as T;
    }

    return response.json();
  }
}

/**
 * Create an API client instance
 */
export function createApiClient(config: ApiClientConfig): ApiClient {
  return new ApiClient(config);
}
