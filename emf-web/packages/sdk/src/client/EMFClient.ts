import type { AxiosInstance } from 'axios';
import axios from 'axios';
import type { EMFClientConfig, CacheConfig, RetryConfig } from './types';
import type { ResourceMetadata } from '../types';
import { ResourceClient } from '../resources/ResourceClient';
import { AdminClient } from '../admin/AdminClient';
import { TokenManager } from '../auth/TokenManager';
import { DiscoveryResponseSchema } from '../validation/schemas';
import { ValidationError, mapAxiosError } from '../errors';

/**
 * Default cache configuration
 */
const DEFAULT_CACHE_CONFIG: Required<CacheConfig> = {
  discoveryTTL: 300000, // 5 minutes
};

/**
 * Default retry configuration
 */
const DEFAULT_RETRY_CONFIG: Required<RetryConfig> = {
  maxAttempts: 3,
  backoffMultiplier: 2,
  maxDelay: 10000,
};

/**
 * Main EMF client for interacting with EMF services
 */
export class EMFClient {
  private readonly baseUrl: string;
  private readonly axiosInstance: AxiosInstance;
  private readonly tokenManager?: TokenManager;
  private readonly cacheConfig: Required<CacheConfig>;
  private readonly retryConfig: Required<RetryConfig>;
  private readonly validationEnabled: boolean;

  // Discovery cache
  private discoveryCache: ResourceMetadata[] | null = null;
  private discoveryCacheTime: number = 0;

  /**
   * Admin client for control plane operations
   */
  public readonly admin: AdminClient;

  constructor(config: EMFClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, ''); // Remove trailing slash
    this.cacheConfig = { ...DEFAULT_CACHE_CONFIG, ...config.cache };
    this.retryConfig = { ...DEFAULT_RETRY_CONFIG, ...config.retry };
    this.validationEnabled = config.validation ?? true;

    // Initialize Axios instance
    this.axiosInstance = axios.create({
      baseURL: this.baseUrl,
      headers: {
        'Content-Type': 'application/json',
      },
      ...config.axiosConfig,
    });

    // Initialize token manager if provider is configured
    if (config.tokenProvider) {
      this.tokenManager = new TokenManager(config.tokenProvider);
      this.setupAuthInterceptor();
    }

    // Setup retry interceptor
    this.setupRetryInterceptor();

    // Initialize admin client
    this.admin = new AdminClient(this.axiosInstance);
  }

  /**
   * Get the base URL
   */
  getBaseUrl(): string {
    return this.baseUrl;
  }

  /**
   * Get the Axios instance (for internal use)
   */
  getAxiosInstance(): AxiosInstance {
    return this.axiosInstance;
  }

  /**
   * Check if validation is enabled
   */
  isValidationEnabled(): boolean {
    return this.validationEnabled;
  }

  /**
   * Discover available resources from the control plane
   * 
   * Fetches resource metadata from the Discovery_Endpoint (/api/_meta/resources)
   * and caches the results for the configured TTL period.
   * 
   * @returns Promise<ResourceMetadata[]> - Array of resource metadata objects
   * @throws ValidationError - If the response doesn't match the expected schema
   * @throws EMFError - If the API request fails
   */
  async discover(): Promise<ResourceMetadata[]> {
    // Check cache - return cached data if within TTL
    const now = Date.now();
    if (this.discoveryCache && now - this.discoveryCacheTime < this.cacheConfig.discoveryTTL) {
      return this.discoveryCache;
    }

    try {
      // Fetch fresh data from discovery endpoint
      const response = await this.axiosInstance.get('/api/_meta/resources');

      // Validate response with Zod schema if validation is enabled
      let resources: ResourceMetadata[];
      if (this.validationEnabled) {
        const parseResult = DiscoveryResponseSchema.safeParse(response.data);
        if (!parseResult.success) {
          const errorMessages = parseResult.error.errors.map(
            (e) => `${e.path.join('.')}: ${e.message}`
          );
          throw new ValidationError(
            `Invalid discovery response: ${errorMessages.join(', ')}`,
            { schema: errorMessages }
          );
        }
        resources = parseResult.data.resources;
      } else {
        // Skip validation - use raw response
        resources = response.data.resources ?? [];
      }

      // Update cache with fresh data
      this.discoveryCache = resources;
      this.discoveryCacheTime = now;

      return resources;
    } catch (error) {
      // Re-throw EMF errors as-is
      if (error instanceof ValidationError) {
        throw error;
      }
      // Map other errors to appropriate EMF error types
      throw mapAxiosError(error);
    }
  }

  /**
   * Get a resource client for the specified resource
   */
  resource<T = unknown>(name: string): ResourceClient<T> {
    return new ResourceClient<T>(this, name);
  }

  /**
   * Build a full URL from an endpoint path
   */
  buildUrl(endpoint: string): string {
    const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    return `${this.baseUrl}${cleanEndpoint}`;
  }

  /**
   * Clear the discovery cache
   */
  clearDiscoveryCache(): void {
    this.discoveryCache = null;
    this.discoveryCacheTime = 0;
  }

  /**
   * Setup authentication interceptor
   */
  private setupAuthInterceptor(): void {
    this.axiosInstance.interceptors.request.use(async (config) => {
      if (this.tokenManager) {
        const token = await this.tokenManager.getValidToken();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
      }
      return config;
    });
  }

  /**
   * Setup retry interceptor with exponential backoff
   */
  private setupRetryInterceptor(): void {
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      async (error) => {
        const config = error.config;

        // Initialize retry count
        config.__retryCount = config.__retryCount || 0;

        // Check if we should retry
        if (!this.shouldRetry(error) || config.__retryCount >= this.retryConfig.maxAttempts) {
          return Promise.reject(error);
        }

        // Increment retry count
        config.__retryCount += 1;

        // Calculate delay with exponential backoff
        const delay = Math.min(
          1000 * Math.pow(this.retryConfig.backoffMultiplier, config.__retryCount - 1),
          this.retryConfig.maxDelay
        );

        // Wait and retry
        await new Promise((resolve) => setTimeout(resolve, delay));
        return this.axiosInstance(config);
      }
    );
  }

  /**
   * Determine if an error should trigger a retry
   */
  private shouldRetry(error: unknown): boolean {
    if (!axios.isAxiosError(error)) {
      return false;
    }

    // Retry on network errors
    if (!error.response) {
      return true;
    }

    // Retry on specific status codes
    const status = error.response.status;
    return status === 429 || (status >= 500 && status < 600);
  }
}
