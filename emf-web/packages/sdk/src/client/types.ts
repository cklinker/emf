/**
 * EMF Client configuration types
 */

/**
 * Token provider interface for authentication
 */
export interface TokenProvider {
  /**
   * Get the current authentication token
   */
  getToken(): Promise<string | null>;

  /**
   * Refresh the authentication token (optional)
   */
  refreshToken?(): Promise<string>;
}

/**
 * Cache configuration options
 */
export interface CacheConfig {
  /**
   * Time-to-live for discovery cache in milliseconds
   * @default 300000 (5 minutes)
   */
  discoveryTTL?: number;
}

/**
 * Retry configuration options
 */
export interface RetryConfig {
  /**
   * Maximum number of retry attempts
   * @default 3
   */
  maxAttempts?: number;

  /**
   * Multiplier for exponential backoff
   * @default 2
   */
  backoffMultiplier?: number;

  /**
   * Maximum delay between retries in milliseconds
   * @default 10000
   */
  maxDelay?: number;
}

/**
 * EMF Client configuration
 */
export interface EMFClientConfig {
  /**
   * Base URL for the EMF API
   */
  baseUrl: string;

  /**
   * Token provider for authentication (optional)
   */
  tokenProvider?: TokenProvider;

  /**
   * Cache configuration (optional)
   */
  cache?: CacheConfig;

  /**
   * Retry configuration (optional)
   */
  retry?: RetryConfig;

  /**
   * Enable response validation with Zod schemas
   * @default true
   */
  validation?: boolean;

  /**
   * Custom Axios configuration options (optional)
   */
  axiosConfig?: Record<string, unknown>;
}
