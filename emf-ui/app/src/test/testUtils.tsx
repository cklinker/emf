/**
 * Test Utilities
 *
 * Shared utilities for testing React components with all required providers.
 */

import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nProvider } from '../context/I18nContext';
import { ToastProvider } from '../components/Toast';
import { ApiProvider } from '../context/ApiContext';
import { AuthProvider } from '../context/AuthContext';
import { PluginProvider } from '../context/PluginContext';

/**
 * Mock bootstrap config response
 */
export const mockBootstrapConfig = {
  oidcProviders: [
    {
      id: 'test-provider',
      name: 'Test Provider',
      issuer: 'https://test.example.com',
      clientId: 'test-client-id',
    },
  ],
};

/**
 * Mock tokens for authenticated state
 */
export const mockTokens = {
  accessToken: 'mock-access-token',
  idToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJlbWFpbCI6InRlc3RAdGVzdC5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c',
  refreshToken: 'mock-refresh-token',
  expiresAt: Date.now() + 3600000, // 1 hour from now
};

// Store the original fetch globally so tests can access it
let originalFetch: typeof fetch;
let bootstrapFetchWrapper: typeof fetch;

/**
 * Create a fetch wrapper that always handles bootstrap config
 */
function createBootstrapFetchWrapper(baseFetch: typeof fetch): typeof fetch {
  return ((url: string | URL | Request, ...args: unknown[]) => {
    const urlString = typeof url === 'string' ? url : url instanceof URL ? url.toString() : url.url;
    
    // Always intercept bootstrap config requests
    if (urlString.includes('/ui/config/bootstrap')) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockBootstrapConfig),
        text: () => Promise.resolve(JSON.stringify(mockBootstrapConfig)),
        clone: function() { return this; },
        headers: new Headers(),
        redirected: false,
        statusText: 'OK',
        type: 'basic' as ResponseType,
        url: urlString,
        body: null,
        bodyUsed: false,
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
        blob: () => Promise.resolve(new Blob()),
        formData: () => Promise.resolve(new FormData()),
        bytes: () => Promise.resolve(new Uint8Array()),
      } as Response);
    }
    
    // For all other requests, use the base fetch (which might be a test mock)
    return baseFetch(url as RequestInfo | URL, ...(args as [RequestInit?]));
  }) as typeof fetch;
}

/**
 * Setup mock fetch for bootstrap config and authentication
 * This should be called once at the start of each test file
 */
export function setupAuthMocks() {
  // Store original fetch if not already stored
  if (!originalFetch) {
    originalFetch = global.fetch;
  }
  
  // Mock sessionStorage
  const mockSessionStorage: Record<string, string> = {
    emf_auth_tokens: JSON.stringify(mockTokens),
  };

  Object.defineProperty(window, 'sessionStorage', {
    value: {
      getItem: (key: string) => mockSessionStorage[key] || null,
      setItem: (key: string, value: string) => {
        mockSessionStorage[key] = value;
      },
      removeItem: (key: string) => {
        delete mockSessionStorage[key];
      },
      clear: () => {
        Object.keys(mockSessionStorage).forEach((key) => delete mockSessionStorage[key]);
      },
      get length() {
        return Object.keys(mockSessionStorage).length;
      },
      key: (index: number) => Object.keys(mockSessionStorage)[index] || null,
    },
    writable: true,
  });

  // Create the bootstrap wrapper
  bootstrapFetchWrapper = createBootstrapFetchWrapper(originalFetch);
  global.fetch = bootstrapFetchWrapper;

  // Return a function that wraps any test's fetch mock with bootstrap handling
  return () => {
    global.fetch = originalFetch;
  };
}

/**
 * Wrap a test's fetch mock to also handle bootstrap config
 * Call this after setting up your test's fetch mock
 */
export function wrapFetchMock(testFetchMock: typeof fetch) {
  global.fetch = createBootstrapFetchWrapper(testFetchMock);
}

/**
 * Create a test wrapper with all required providers
 */
export function createTestWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return function TestWrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nProvider>
            <AuthProvider>
              <ApiProvider>
                <PluginProvider>
                  <ToastProvider>{children}</ToastProvider>
                </PluginProvider>
              </ApiProvider>
            </AuthProvider>
          </I18nProvider>
        </BrowserRouter>
      </QueryClientProvider>
    );
  };
}

/**
 * Auth wrapper component for tests that need custom routing or query client setup
 * Wraps children with AuthProvider, ApiProvider, and PluginProvider only
 */
export function AuthWrapper({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <ApiProvider>
        <PluginProvider>
          <I18nProvider>
            <ToastProvider>{children}</ToastProvider>
          </I18nProvider>
        </PluginProvider>
      </ApiProvider>
    </AuthProvider>
  );
}


