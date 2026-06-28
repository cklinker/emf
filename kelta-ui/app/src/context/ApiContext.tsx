/**
 * API Context
 *
 * Provides an authenticated API client throughout the application.
 * Uses the SDK's KeltaClient as the single HTTP transport layer, which provides:
 * - Auth token injection via TokenProvider
 * - Retry with exponential backoff
 * - Consistent error handling
 *
 * The ApiClient wrapper preserves the thin get/post/put/patch/delete interface
 * used by existing UI components, while routing all requests through the
 * SDK's Axios pipeline.
 */

import React, { createContext, useContext, useMemo } from 'react'
import { KeltaClient } from '@kelta/sdk'
import type { TokenProvider } from '@kelta/sdk'
import { ApiClient } from '../services/apiClient'
import { useAuth } from './AuthContext'
import { getTenantSlug, isCustomDomainHost } from './TenantContext'

interface ApiContextValue {
  apiClient: ApiClient
  keltaClient: KeltaClient
}

const ApiContext = createContext<ApiContextValue | undefined>(undefined)

export interface ApiProviderProps {
  children: React.ReactNode
  baseUrl?: string
}

/**
 * API Provider Component
 *
 * Wraps the application to provide an authenticated API client backed
 * by the SDK's KeltaClient Axios instance.
 */
export function ApiProvider({ children, baseUrl = '' }: ApiProviderProps): React.ReactElement {
  const { getAccessToken, login } = useAuth()

  const keltaClient = useMemo(() => {
    // Adapt AuthContext's getAccessToken to the SDK's TokenProvider interface
    const tokenProvider: TokenProvider = {
      getToken: async () => {
        try {
          return await getAccessToken()
        } catch {
          return null
        }
      },
    }

    const client = new KeltaClient({
      baseUrl,
      tokenProvider,
      // Disable SDK-level response validation in the UI — the UI has its own
      // error handling through ApiError / ApiClient.
      validation: false,
      // Disable retry for 401 — we handle it via the response interceptor below
      retry: { maxAttempts: 0 },
    })

    // Add a 401 response interceptor: attempt token refresh + retry before redirecting
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(client.getAxiosInstance() as any).interceptors.response.use(
      (response: unknown) => response,
      async (error: unknown) => {
        const err = error as {
          response?: { status?: number }
          config?: Record<string, unknown> & { headers?: Record<string, unknown> }
        }
        if (
          err &&
          typeof err === 'object' &&
          err.response &&
          typeof err.response === 'object' &&
          err.response.status === 401 &&
          err.config &&
          !err.config.__retried
        ) {
          // Try refreshing the token and retrying the request once
          try {
            const newToken = await getAccessToken()
            const retryConfig = {
              ...err.config,
              __retried: true,
              headers: { ...err.config.headers, Authorization: `Bearer ${newToken}` },
            }
            return await client.getAxiosInstance().request(retryConfig)
          } catch {
            // Refresh failed — clear tokens and redirect to login page.
            // We intentionally do NOT call login() here because login() without
            // a provider ID triggers a redirect to /login when multiple providers
            // are configured, which causes an infinite redirect loop if the 401
            // originated from a component that fires on the login page.
            console.warn('[API] Token refresh failed on 401, redirecting to login')
            sessionStorage.removeItem('kelta_auth_tokens')
            const loginPath = isCustomDomainHost() ? '/login' : `/${getTenantSlug()}/login`
            window.location.assign(loginPath)
          }
        }
        return Promise.reject(error)
      }
    )

    return client
  }, [baseUrl, getAccessToken, login])

  const apiClient = useMemo(
    () =>
      new ApiClient(
        // The SDK has its own copy of axios in its node_modules; ours is structurally
        // identical so we cast through unknown to bridge the duplicate-types divide.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        keltaClient.getAxiosInstance() as any
      ),
    [keltaClient]
  )

  const contextValue = useMemo<ApiContextValue>(
    () => ({
      apiClient,
      keltaClient,
    }),
    [apiClient, keltaClient]
  )

  return <ApiContext.Provider value={contextValue}>{children}</ApiContext.Provider>
}

/**
 * Hook to access the API client
 *
 * @throws Error if used outside of ApiProvider
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useApi(): ApiContextValue {
  const context = useContext(ApiContext)
  if (context === undefined) {
    throw new Error('useApi must be used within an ApiProvider')
  }
  return context
}
