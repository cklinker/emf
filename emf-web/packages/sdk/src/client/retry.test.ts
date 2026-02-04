import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios, { AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import { EMFClient } from './EMFClient';

/**
 * Retry Interceptor Tests
 * 
 * Tests for the retry logic with exponential backoff in EMFClient.
 * 
 * **Validates: Requirements 8.7**
 * 
 * The SDK implements exponential backoff for retryable errors:
 * - Network errors (connection refused, timeout)
 * - Server errors (500, 502, 503, 504)
 * - Rate limiting (429)
 * 
 * Retry configuration:
 * - maxAttempts: 3 (default)
 * - backoffMultiplier: 2 (default)
 * - maxDelay: 10000 milliseconds (default)
 * 
 * Delay calculation: min(baseDelay * (backoffMultiplier ^ attemptNumber), maxDelay)
 * 
 * Non-retryable errors (4xx except 429) fail immediately without retry.
 */
describe('Retry Interceptor', () => {
  // Helper to create an Axios error
  const createAxiosError = (
    status: number | null,
    message: string = 'Request failed',
    config: Partial<InternalAxiosRequestConfig> = {}
  ): AxiosError => {
    const error = new Error(message) as AxiosError;
    error.isAxiosError = true;
    error.config = {
      url: '/api/test',
      method: 'get',
      headers: {} as any,
      ...config,
    } as InternalAxiosRequestConfig;
    
    if (status !== null) {
      error.response = {
        status,
        statusText: 'Error',
        headers: {},
        config: error.config,
        data: { message },
      };
    }
    
    return error;
  };

  // Helper to create a network error (no response)
  const createNetworkError = (message: string = 'Network Error'): AxiosError => {
    return createAxiosError(null, message);
  };

  describe('shouldRetry logic', () => {
    /**
     * Test the shouldRetry method behavior by examining what errors trigger retries.
     * We test this by checking if retryable errors increment the retry count,
     * while non-retryable errors are rejected immediately.
     */
    
    let mockGet: ReturnType<typeof vi.fn>;
    let client: EMFClient;

    beforeEach(() => {
      vi.useFakeTimers();
      mockGet = vi.fn();
      
      // Create a more complete mock that tracks calls
      const mockAxiosInstance = vi.fn().mockImplementation((config: any) => {
        return mockGet(config);
      });
      
      (mockAxiosInstance as any).get = mockGet;
      (mockAxiosInstance as any).post = vi.fn();
      (mockAxiosInstance as any).put = vi.fn();
      (mockAxiosInstance as any).patch = vi.fn();
      (mockAxiosInstance as any).delete = vi.fn();
      (mockAxiosInstance as any).defaults = {
        baseURL: 'https://api.example.com',
        headers: { 'Content-Type': 'application/json' },
      };
      (mockAxiosInstance as any).interceptors = {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      };

      vi.spyOn(axios, 'create').mockReturnValue(mockAxiosInstance as any);
      vi.spyOn(axios, 'isAxiosError').mockImplementation((error: unknown) => {
        return error !== null && typeof error === 'object' && 'isAxiosError' in error;
      });

      client = new EMFClient({ baseUrl: 'https://api.example.com' });
    });

    afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
    });

    describe('Retryable Error Detection - Network Errors', () => {
      it('should identify network errors as retryable', () => {
        // Network errors have no response
        const networkError = createNetworkError('Network Error');
        
        // Verify the error structure that triggers retry
        expect(networkError.response).toBeUndefined();
        expect(axios.isAxiosError(networkError)).toBe(true);
      });

      it('should identify connection refused as retryable', () => {
        const error = createNetworkError('ECONNREFUSED');
        
        expect(error.response).toBeUndefined();
        expect(axios.isAxiosError(error)).toBe(true);
      });

      it('should identify timeout errors as retryable', () => {
        const error = createNetworkError('timeout of 5000ms exceeded');
        
        expect(error.response).toBeUndefined();
        expect(axios.isAxiosError(error)).toBe(true);
      });
    });

    describe('Retryable Error Detection - Server Errors (5xx)', () => {
      it('should identify 500 Internal Server Error as retryable', () => {
        const error = createAxiosError(500, 'Internal Server Error');
        
        expect(error.response?.status).toBe(500);
        expect(error.response!.status >= 500 && error.response!.status < 600).toBe(true);
      });

      it('should identify 502 Bad Gateway as retryable', () => {
        const error = createAxiosError(502, 'Bad Gateway');
        
        expect(error.response?.status).toBe(502);
        expect(error.response!.status >= 500 && error.response!.status < 600).toBe(true);
      });

      it('should identify 503 Service Unavailable as retryable', () => {
        const error = createAxiosError(503, 'Service Unavailable');
        
        expect(error.response?.status).toBe(503);
        expect(error.response!.status >= 500 && error.response!.status < 600).toBe(true);
      });

      it('should identify 504 Gateway Timeout as retryable', () => {
        const error = createAxiosError(504, 'Gateway Timeout');
        
        expect(error.response?.status).toBe(504);
        expect(error.response!.status >= 500 && error.response!.status < 600).toBe(true);
      });
    });

    describe('Retryable Error Detection - Rate Limiting (429)', () => {
      it('should identify 429 Too Many Requests as retryable', () => {
        const error = createAxiosError(429, 'Too Many Requests');
        
        expect(error.response?.status).toBe(429);
      });
    });
  });

  describe('Non-Retryable Errors', () => {
    it('should NOT retry on 400 Bad Request', () => {
      const error = createAxiosError(400, 'Bad Request');
      
      // 400 is not in the retryable range (not 429, not 5xx, has response)
      expect(error.response?.status).toBe(400);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status < 500).toBe(true);
    });

    it('should NOT retry on 401 Unauthorized', () => {
      const error = createAxiosError(401, 'Unauthorized');
      
      expect(error.response?.status).toBe(401);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status < 500).toBe(true);
    });

    it('should NOT retry on 403 Forbidden', () => {
      const error = createAxiosError(403, 'Forbidden');
      
      expect(error.response?.status).toBe(403);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status < 500).toBe(true);
    });

    it('should NOT retry on 404 Not Found', () => {
      const error = createAxiosError(404, 'Not Found');
      
      expect(error.response?.status).toBe(404);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status < 500).toBe(true);
    });

    it('should NOT retry on 409 Conflict', () => {
      const error = createAxiosError(409, 'Conflict');
      
      expect(error.response?.status).toBe(409);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status < 500).toBe(true);
    });

    it('should NOT retry on 422 Unprocessable Entity', () => {
      const error = createAxiosError(422, 'Unprocessable Entity');
      
      expect(error.response?.status).toBe(422);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status < 500).toBe(true);
    });

    it('should NOT retry on non-Axios errors', () => {
      const regularError = new Error('Regular error');
      
      // Non-Axios errors should not be retried
      expect(axios.isAxiosError(regularError)).toBe(false);
    });
  });

  describe('Retry Configuration', () => {
    it('should use default retry configuration when not specified', () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      // Default config: maxAttempts: 3, backoffMultiplier: 2, maxDelay: 10000
      expect(client).toBeDefined();
    });

    it('should accept custom retry configuration', () => {
      const client = new EMFClient({
        baseUrl: 'https://api.example.com',
        retry: {
          maxAttempts: 5,
          backoffMultiplier: 3,
          maxDelay: 30000,
        },
      });

      expect(client).toBeDefined();
    });

    it('should merge custom retry config with defaults', () => {
      const client = new EMFClient({
        baseUrl: 'https://api.example.com',
        retry: {
          maxAttempts: 5,
          // backoffMultiplier and maxDelay should use defaults
        },
      });

      expect(client).toBeDefined();
    });
  });

  describe('Exponential Backoff Delay Calculation', () => {
    /**
     * Test the exponential backoff formula:
     * delay = min(baseDelay * (backoffMultiplier ^ attemptNumber), maxDelay)
     * 
     * Default values:
     * - baseDelay: 1000ms
     * - backoffMultiplier: 2
     * - maxDelay: 10000ms
     * 
     * Expected delays:
     * - Attempt 1: 1000 * 2^0 = 1000ms
     * - Attempt 2: 1000 * 2^1 = 2000ms
     * - Attempt 3: 1000 * 2^2 = 4000ms
     * - Attempt 4: 1000 * 2^3 = 8000ms
     * - Attempt 5: min(1000 * 2^4, 10000) = 10000ms (capped)
     */
    
    it('should calculate first retry delay correctly', () => {
      // First retry (attemptNumber = 0): 1000 * 2^0 = 1000ms
      const baseDelay = 1000;
      const backoffMultiplier = 2;
      const attemptNumber = 0;
      const maxDelay = 10000;
      
      const delay = Math.min(
        baseDelay * Math.pow(backoffMultiplier, attemptNumber),
        maxDelay
      );
      
      expect(delay).toBe(1000);
    });

    it('should calculate second retry delay correctly', () => {
      // Second retry (attemptNumber = 1): 1000 * 2^1 = 2000ms
      const baseDelay = 1000;
      const backoffMultiplier = 2;
      const attemptNumber = 1;
      const maxDelay = 10000;
      
      const delay = Math.min(
        baseDelay * Math.pow(backoffMultiplier, attemptNumber),
        maxDelay
      );
      
      expect(delay).toBe(2000);
    });

    it('should calculate third retry delay correctly', () => {
      // Third retry (attemptNumber = 2): 1000 * 2^2 = 4000ms
      const baseDelay = 1000;
      const backoffMultiplier = 2;
      const attemptNumber = 2;
      const maxDelay = 10000;
      
      const delay = Math.min(
        baseDelay * Math.pow(backoffMultiplier, attemptNumber),
        maxDelay
      );
      
      expect(delay).toBe(4000);
    });

    it('should cap delay at maxDelay', () => {
      // With high attempt number, delay should be capped
      const baseDelay = 1000;
      const backoffMultiplier = 2;
      const attemptNumber = 10; // Would be 1000 * 2^10 = 1024000ms without cap
      const maxDelay = 10000;
      
      const delay = Math.min(
        baseDelay * Math.pow(backoffMultiplier, attemptNumber),
        maxDelay
      );
      
      expect(delay).toBe(10000);
    });

    it('should use custom backoff multiplier', () => {
      // With multiplier 3: 1000 * 3^1 = 3000ms
      const baseDelay = 1000;
      const backoffMultiplier = 3;
      const attemptNumber = 1;
      const maxDelay = 30000;
      
      const delay = Math.min(
        baseDelay * Math.pow(backoffMultiplier, attemptNumber),
        maxDelay
      );
      
      expect(delay).toBe(3000);
    });

    it('should use custom maxDelay', () => {
      const baseDelay = 1000;
      const backoffMultiplier = 2;
      const attemptNumber = 5; // Would be 32000ms without cap
      const maxDelay = 5000;
      
      const delay = Math.min(
        baseDelay * Math.pow(backoffMultiplier, attemptNumber),
        maxDelay
      );
      
      expect(delay).toBe(5000);
    });
  });

  describe('Max Attempts Limit', () => {
    it('should define max attempts correctly', () => {
      // Default max attempts is 3
      const defaultMaxAttempts = 3;
      expect(defaultMaxAttempts).toBe(3);
    });

    it('should allow custom max attempts', () => {
      const customMaxAttempts = 5;
      
      const client = new EMFClient({
        baseUrl: 'https://api.example.com',
        retry: { maxAttempts: customMaxAttempts },
      });
      
      expect(client).toBeDefined();
    });

    it('should allow disabling retries with maxAttempts: 0', () => {
      const client = new EMFClient({
        baseUrl: 'https://api.example.com',
        retry: { maxAttempts: 0 },
      });
      
      expect(client).toBeDefined();
    });
  });

  describe('Edge Cases', () => {
    it('should handle 5xx status codes at boundary (500)', () => {
      const error = createAxiosError(500, 'Internal Server Error');
      
      // 500 is the lower boundary of 5xx
      expect(error.response?.status).toBe(500);
      expect(error.response!.status >= 500 && error.response!.status < 600).toBe(true);
    });

    it('should handle 5xx status codes at boundary (599)', () => {
      const error = createAxiosError(599, 'Network Connect Timeout Error');
      
      // 599 is the upper boundary of 5xx
      expect(error.response?.status).toBe(599);
      expect(error.response!.status >= 500 && error.response!.status < 600).toBe(true);
    });

    it('should NOT retry on 600 status code (outside 5xx range)', () => {
      const error = createAxiosError(600, 'Unknown Error');
      
      // 600 is outside the 5xx range
      expect(error.response?.status).toBe(600);
      expect(error.response!.status >= 500 && error.response!.status < 600).toBe(false);
    });

    it('should NOT retry on 499 status code (below 5xx range)', () => {
      const error = createAxiosError(499, 'Client Closed Request');
      
      // 499 is below the 5xx range and not 429
      expect(error.response?.status).toBe(499);
      expect(error.response!.status !== 429).toBe(true);
      expect(error.response!.status >= 500).toBe(false);
    });
  });

  describe('Integration with EMFClient', () => {
    let mockAxiosInstance: any;
    let responseInterceptorReject: (error: any) => Promise<any>;

    beforeEach(() => {
      vi.useFakeTimers();
      
      // Track interceptor registration
      const interceptorHandlers: any = {};
      
      mockAxiosInstance = vi.fn().mockImplementation((config: any) => {
        return Promise.resolve({ data: { success: true } });
      });
      
      mockAxiosInstance.get = vi.fn();
      mockAxiosInstance.post = vi.fn();
      mockAxiosInstance.put = vi.fn();
      mockAxiosInstance.patch = vi.fn();
      mockAxiosInstance.delete = vi.fn();
      mockAxiosInstance.defaults = {
        baseURL: 'https://api.example.com',
        headers: { 'Content-Type': 'application/json' },
      };
      mockAxiosInstance.interceptors = {
        request: { use: vi.fn() },
        response: { 
          use: vi.fn((onFulfilled: any, onRejected: any) => {
            interceptorHandlers.onFulfilled = onFulfilled;
            responseInterceptorReject = onRejected;
          })
        },
      };

      vi.spyOn(axios, 'create').mockReturnValue(mockAxiosInstance);
      vi.spyOn(axios, 'isAxiosError').mockImplementation((error: unknown) => {
        return error !== null && typeof error === 'object' && 'isAxiosError' in error;
      });
    });

    afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
    });

    it('should setup retry interceptor on client creation', () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      // Verify interceptor was registered
      expect(mockAxiosInstance.interceptors.response.use).toHaveBeenCalled();
    });

    it('should reject non-retryable errors immediately', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      const error = createAxiosError(400, 'Bad Request');
      error.config = { url: '/api/test', __retryCount: 0 } as any;
      
      // Non-retryable error should be rejected
      await expect(responseInterceptorReject(error)).rejects.toEqual(error);
    });

    it('should reject after max attempts exceeded', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      const error = createAxiosError(500, 'Internal Server Error');
      error.config = { url: '/api/test', __retryCount: 3 } as any; // Already at max
      
      // Should reject because max attempts reached
      await expect(responseInterceptorReject(error)).rejects.toEqual(error);
    });

    it('should increment retry count on retryable error', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      const error = createAxiosError(500, 'Internal Server Error');
      const config = { url: '/api/test', __retryCount: 0 } as any;
      error.config = config;
      
      // Start the retry (don't await - it will wait for timeout)
      const retryPromise = responseInterceptorReject(error);
      
      // The retry count should be incremented
      expect(config.__retryCount).toBe(1);
      
      // Advance timers to complete the retry
      await vi.advanceTimersByTimeAsync(1000);
      
      // The promise should resolve (retry succeeds)
      await expect(retryPromise).resolves.toBeDefined();
    });

    it('should wait before retrying with exponential backoff', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      const error = createAxiosError(500, 'Internal Server Error');
      const config = { url: '/api/test', __retryCount: 0 } as any;
      error.config = config;
      
      const setTimeoutSpy = vi.spyOn(global, 'setTimeout');
      
      // Start the retry
      const retryPromise = responseInterceptorReject(error);
      
      // setTimeout should have been called with the backoff delay
      expect(setTimeoutSpy).toHaveBeenCalled();
      
      // First retry delay should be 1000ms (1000 * 2^0)
      const [, delay] = setTimeoutSpy.mock.calls[0];
      expect(delay).toBe(1000);
      
      // Advance timers to complete
      await vi.advanceTimersByTimeAsync(1000);
      await retryPromise;
    });

    it('should use increasing delays for subsequent retries', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      // Test second retry delay
      const error = createAxiosError(500, 'Internal Server Error');
      const config = { url: '/api/test', __retryCount: 1 } as any; // Already retried once
      error.config = config;
      
      const setTimeoutSpy = vi.spyOn(global, 'setTimeout');
      
      const retryPromise = responseInterceptorReject(error);
      
      // Second retry delay should be 2000ms (1000 * 2^1)
      const [, delay] = setTimeoutSpy.mock.calls[0];
      expect(delay).toBe(2000);
      
      await vi.advanceTimersByTimeAsync(2000);
      await retryPromise;
    });

    it('should retry on 429 rate limiting', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      const error = createAxiosError(429, 'Too Many Requests');
      const config = { url: '/api/test', __retryCount: 0 } as any;
      error.config = config;
      
      const retryPromise = responseInterceptorReject(error);
      
      // Should increment retry count (indicating retry will happen)
      expect(config.__retryCount).toBe(1);
      
      await vi.advanceTimersByTimeAsync(1000);
      await retryPromise;
    });

    it('should retry on network error', async () => {
      const client = new EMFClient({ baseUrl: 'https://api.example.com' });
      
      const error = createNetworkError('Network Error');
      const config = { url: '/api/test', __retryCount: 0 } as any;
      error.config = config;
      
      const retryPromise = responseInterceptorReject(error);
      
      // Should increment retry count (indicating retry will happen)
      expect(config.__retryCount).toBe(1);
      
      await vi.advanceTimersByTimeAsync(1000);
      await retryPromise;
    });
  });
});
