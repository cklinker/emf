import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { TokenManager } from './TokenManager';
import type { TokenProvider } from '../client/types';
import { AuthenticationError } from '../errors';

/**
 * Helper to create a valid JWT token with a specific expiration time
 */
function createJwtToken(expiresAt: number): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ 
    sub: 'user123', 
    exp: Math.floor(expiresAt / 1000) // JWT exp is in seconds
  }));
  const signature = btoa('fake-signature');
  return `${header}.${payload}.${signature}`;
}

/**
 * Helper to create a JWT token without expiration
 */
function createJwtTokenWithoutExp(): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ sub: 'user123' }));
  const signature = btoa('fake-signature');
  return `${header}.${payload}.${signature}`;
}

describe('TokenManager', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  describe('Token caching (Requirement 9.1)', () => {
    it('should call token provider getToken method', async () => {
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue('test-token'),
      };

      const manager = new TokenManager(mockProvider);
      await manager.getValidToken();

      expect(mockProvider.getToken).toHaveBeenCalledTimes(1);
    });

    it('should return the token from provider', async () => {
      const expectedToken = 'my-auth-token';
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(expectedToken),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      expect(token).toBe(expectedToken);
    });

    it('should return null when provider returns null', async () => {
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(null),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      expect(token).toBeNull();
    });

    it('should cache the token state internally', async () => {
      const now = Date.now();
      const futureExpiration = now + 3600000; // 1 hour from now
      const jwtToken = createJwtToken(futureExpiration);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(jwtToken),
      };

      const manager = new TokenManager(mockProvider);
      
      // First call
      const token1 = await manager.getValidToken();
      expect(token1).toBe(jwtToken);
      
      // Second call - provider is still called but token is cached
      const token2 = await manager.getValidToken();
      expect(token2).toBe(jwtToken);
      expect(mockProvider.getToken).toHaveBeenCalledTimes(2);
    });
  });

  describe('Token expiration checking (Requirement 9.3)', () => {
    it('should parse JWT expiration time correctly', async () => {
      const now = Date.now();
      const futureExpiration = now + 3600000; // 1 hour from now
      const jwtToken = createJwtToken(futureExpiration);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(jwtToken),
        refreshToken: vi.fn().mockResolvedValue(jwtToken),
      };

      const manager = new TokenManager(mockProvider);
      await manager.getValidToken();

      // Token is not expired, so refresh should not be called
      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
    });

    it('should consider token expired 30 seconds before actual expiration', async () => {
      const now = Date.now();
      // Token expires in 20 seconds (less than 30 second buffer)
      const nearExpiration = now + 20000;
      const expiredToken = createJwtToken(nearExpiration);
      const newToken = createJwtToken(now + 3600000);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(expiredToken)
          .mockResolvedValue(newToken),
        refreshToken: vi.fn().mockResolvedValue(newToken),
      };

      const manager = new TokenManager(mockProvider);
      
      // First call to cache the token
      await manager.getValidToken();
      
      // Second call - token is within 30 second buffer, should trigger refresh
      await manager.getValidToken();
      
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
    });

    it('should not consider token expired when no expiration is set', async () => {
      const tokenWithoutExp = createJwtTokenWithoutExp();
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(tokenWithoutExp),
        refreshToken: vi.fn(),
      };

      const manager = new TokenManager(mockProvider);
      
      // Multiple calls should not trigger refresh
      await manager.getValidToken();
      await manager.getValidToken();
      await manager.getValidToken();

      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
    });

    it('should handle non-JWT tokens gracefully', async () => {
      const nonJwtToken = 'simple-api-key-token';
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(nonJwtToken),
        refreshToken: vi.fn(),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      expect(token).toBe(nonJwtToken);
      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
    });

    it('should handle malformed JWT tokens gracefully', async () => {
      const malformedToken = 'not.a.valid.jwt.token';
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(malformedToken),
        refreshToken: vi.fn(),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      expect(token).toBe(malformedToken);
      // Should not throw, just treat as no expiration
      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
    });

    it('should handle JWT with invalid base64 payload', async () => {
      const invalidBase64Token = 'header.!!!invalid-base64!!!.signature';
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(invalidBase64Token),
        refreshToken: vi.fn(),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      expect(token).toBe(invalidBase64Token);
      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
    });
  });

  describe('Automatic token refresh (Requirement 9.3)', () => {
    it('should automatically refresh expired token when refreshToken is available', async () => {
      const now = Date.now();
      const expiredToken = createJwtToken(now - 1000); // Already expired
      const newToken = createJwtToken(now + 3600000); // Valid for 1 hour
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(expiredToken)
          .mockResolvedValue(newToken),
        refreshToken: vi.fn().mockResolvedValue(newToken),
      };

      const manager = new TokenManager(mockProvider);
      
      // First call to cache the expired token
      await manager.getValidToken();
      
      // Second call should trigger refresh
      const token = await manager.getValidToken();
      
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
      expect(token).toBe(newToken);
    });

    it('should not attempt refresh when refreshToken method is not available', async () => {
      const now = Date.now();
      const expiredToken = createJwtToken(now - 1000); // Already expired
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(expiredToken),
        // No refreshToken method
      };

      const manager = new TokenManager(mockProvider);
      
      // First call
      await manager.getValidToken();
      
      // Second call - should not throw even with expired token
      const token = await manager.getValidToken();
      
      expect(token).toBe(expiredToken);
    });

    it('should update cached state after successful refresh', async () => {
      const now = Date.now();
      const expiredToken = createJwtToken(now - 1000);
      const newToken = createJwtToken(now + 3600000);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(expiredToken)
          .mockResolvedValue(newToken),
        refreshToken: vi.fn().mockResolvedValue(newToken),
      };

      const manager = new TokenManager(mockProvider);
      
      // Cache expired token
      await manager.getValidToken();
      
      // Trigger refresh
      await manager.getValidToken();
      
      // Advance time but stay within new token's validity
      vi.advanceTimersByTime(1800000); // 30 minutes
      
      // Should not refresh again since new token is still valid
      await manager.getValidToken();
      
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
    });
  });

  describe('Token refresh failure (Requirement 9.4)', () => {
    it('should throw AuthenticationError when refresh fails', async () => {
      const now = Date.now();
      // Token that expires in 10 seconds (within 30 second buffer, so considered expired)
      const expiredToken = createJwtToken(now + 10000);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(expiredToken),
        refreshToken: vi.fn().mockRejectedValue(new Error('Refresh failed')),
      };

      const manager = new TokenManager(mockProvider);
      
      // First call caches the token and sets expiration
      await manager.getValidToken();
      
      // Second call - token is within 30 second buffer, triggers refresh which fails
      await expect(manager.getValidToken()).rejects.toThrow(AuthenticationError);
    });

    it('should clear token state when refresh fails', async () => {
      const now = Date.now();
      // Token that expires in 10 seconds (within 30 second buffer)
      const expiredToken = createJwtToken(now + 10000);
      const newToken = createJwtToken(now + 3600000);
      
      // After refresh failure, the error is thrown before getToken is called
      // So the call sequence is:
      // 1. First getValidToken: getToken called (returns expiredToken)
      // 2. Second getValidToken: refresh fails, throws before getToken
      // 3. Third getValidToken: state cleared, getToken called (returns newToken)
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(expiredToken) // First call - cache token
          .mockResolvedValue(newToken),        // Third call - returns new token (second call throws before getToken)
        refreshToken: vi.fn()
          .mockRejectedValueOnce(new Error('Refresh failed')),
      };

      const manager = new TokenManager(mockProvider);
      
      // Cache token (within 30 second buffer, so considered expired on next call)
      await manager.getValidToken();
      expect(mockProvider.getToken).toHaveBeenCalledTimes(1);
      
      // Refresh fails - throws before getToken is called
      try {
        await manager.getValidToken();
      } catch {
        // Expected to throw
      }
      // getToken was NOT called because refresh threw first
      expect(mockProvider.getToken).toHaveBeenCalledTimes(1);
      
      // After failure, state should be cleared (expiresAt is null)
      // Next call won't trigger refresh (no expiration set), just calls getToken
      const token = await manager.getValidToken();
      expect(mockProvider.getToken).toHaveBeenCalledTimes(2);
      expect(token).toBe(newToken);
    });

    it('should preserve original error type in AuthenticationError', async () => {
      const now = Date.now();
      const expiredToken = createJwtToken(now - 1000);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(expiredToken),
        refreshToken: vi.fn().mockRejectedValue(new TypeError('Network error')),
      };

      const manager = new TokenManager(mockProvider);
      
      // Cache expired token
      await manager.getValidToken();
      
      // Verify the error is AuthenticationError regardless of original error type
      try {
        await manager.getValidToken();
        expect.fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(AuthenticationError);
        expect((error as AuthenticationError).message).toBe('Token refresh failed');
        expect((error as AuthenticationError).statusCode).toBe(401);
      }
    });
  });

  describe('clearToken method', () => {
    it('should clear the cached token state', async () => {
      const now = Date.now();
      const token = createJwtToken(now + 3600000);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(token),
        refreshToken: vi.fn().mockResolvedValue(token),
      };

      const manager = new TokenManager(mockProvider);
      
      // Cache token
      await manager.getValidToken();
      
      // Clear token
      manager.clearToken();
      
      // Next call should get fresh token from provider
      await manager.getValidToken();
      
      expect(mockProvider.getToken).toHaveBeenCalledTimes(2);
    });

    it('should allow clearing token even when no token is cached', () => {
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(null),
      };

      const manager = new TokenManager(mockProvider);
      
      // Should not throw
      expect(() => manager.clearToken()).not.toThrow();
    });
  });

  describe('Edge cases', () => {
    it('should handle provider that throws on getToken', async () => {
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockRejectedValue(new Error('Provider error')),
      };

      const manager = new TokenManager(mockProvider);
      
      await expect(manager.getValidToken()).rejects.toThrow('Provider error');
    });

    it('should handle concurrent getValidToken calls', async () => {
      const now = Date.now();
      const token = createJwtToken(now + 3600000);
      
      let callCount = 0;
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockImplementation(async () => {
          callCount++;
          // No delay - just return immediately
          return token;
        }),
      };

      const manager = new TokenManager(mockProvider);
      
      // Make concurrent calls
      const [result1, result2, result3] = await Promise.all([
        manager.getValidToken(),
        manager.getValidToken(),
        manager.getValidToken(),
      ]);

      expect(result1).toBe(token);
      expect(result2).toBe(token);
      expect(result3).toBe(token);
      // Each call invokes getToken independently
      expect(callCount).toBe(3);
    });

    it('should handle token with exp as string (invalid)', async () => {
      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const payload = btoa(JSON.stringify({ sub: 'user123', exp: 'not-a-number' }));
      const signature = btoa('fake-signature');
      const invalidExpToken = `${header}.${payload}.${signature}`;
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(invalidExpToken),
        refreshToken: vi.fn(),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      expect(token).toBe(invalidExpToken);
      // Should treat as no expiration
      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
    });

    it('should handle empty string token', async () => {
      const mockProvider: TokenProvider = {
        getToken: vi.fn().mockResolvedValue(''),
      };

      const manager = new TokenManager(mockProvider);
      const token = await manager.getValidToken();

      // Empty string is falsy, so state won't be updated
      expect(token).toBe('');
    });

    it('should handle token expiring exactly at current time', async () => {
      const now = Date.now();
      // Token expires exactly now (within 30 second buffer)
      const expiringToken = createJwtToken(now);
      const newToken = createJwtToken(now + 3600000);
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(expiringToken)
          .mockResolvedValue(newToken),
        refreshToken: vi.fn().mockResolvedValue(newToken),
      };

      const manager = new TokenManager(mockProvider);
      
      // Cache token
      await manager.getValidToken();
      
      // Should trigger refresh since token is within 30 second buffer
      await manager.getValidToken();
      
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
    });
  });

  describe('Integration scenarios', () => {
    it('should handle full token lifecycle: get -> expire -> refresh -> get', async () => {
      const now = Date.now();
      const shortLivedToken = createJwtToken(now + 60000); // Expires in 1 minute
      const refreshedToken = createJwtToken(now + 3660000); // Expires in 1 hour + 1 minute
      
      // The implementation always calls getToken after refresh
      // So we need to mock getToken to return the refreshed token after refresh happens
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(shortLivedToken)  // First call - initial fetch
          .mockResolvedValue(refreshedToken),       // All subsequent calls return refreshed token
        refreshToken: vi.fn().mockResolvedValue(refreshedToken),
      };

      const manager = new TokenManager(mockProvider);
      
      // Initial token fetch
      const token1 = await manager.getValidToken();
      expect(token1).toBe(shortLivedToken);
      expect(mockProvider.refreshToken).not.toHaveBeenCalled();
      
      // Advance time to within 30 seconds of expiration (60000 - 35000 = 25000ms left, which is < 30000)
      vi.advanceTimersByTime(35000); // 35 seconds
      
      // Token should trigger refresh because we're within 30 second buffer
      const token2 = await manager.getValidToken();
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
      // After refresh, getToken is called and returns refreshedToken
      expect(token2).toBe(refreshedToken);
      
      // Advance time but stay within new token's validity
      vi.advanceTimersByTime(1800000); // 30 minutes
      
      // Should not refresh again since new token is still valid
      const token3 = await manager.getValidToken();
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
      expect(token3).toBe(refreshedToken);
    });

    it('should handle multiple refresh cycles', async () => {
      const now = Date.now();
      const token1 = createJwtToken(now + 60000); // 1 minute
      const token2 = createJwtToken(now + 120000); // 2 minutes
      const token3 = createJwtToken(now + 180000); // 3 minutes
      
      const mockProvider: TokenProvider = {
        getToken: vi.fn()
          .mockResolvedValueOnce(token1)
          .mockResolvedValueOnce(token1)
          .mockResolvedValueOnce(token2)
          .mockResolvedValue(token3),
        refreshToken: vi.fn()
          .mockResolvedValueOnce(token2)
          .mockResolvedValue(token3),
      };

      const manager = new TokenManager(mockProvider);
      
      // Get initial token
      await manager.getValidToken();
      
      // Advance to trigger first refresh
      vi.advanceTimersByTime(35000);
      await manager.getValidToken();
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(1);
      
      // Advance to trigger second refresh
      vi.advanceTimersByTime(60000);
      await manager.getValidToken();
      expect(mockProvider.refreshToken).toHaveBeenCalledTimes(2);
    });
  });
});
