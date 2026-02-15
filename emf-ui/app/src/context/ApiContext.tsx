/**
 * API Context
 *
 * Provides an authenticated API client throughout the application.
 * Uses the SDK's EMFClient as the single HTTP transport layer, which provides:
 * - Auth token injection via TokenProvider
 * - Retry with exponential backoff
 * - Consistent error handling
 *
 * The ApiClient wrapper preserves the thin get/post/put/patch/delete interface
 * used by existing UI components, while routing all requests through the
 * SDK's Axios pipeline.
 */

import React, { createContext, useContext, useMemo } from 'react'
import { EMFClient } from '@emf/sdk'
import type { TokenProvider } from '@emf/sdk'
import { ApiClient } from '../services/apiClient'
import { useAuth } from './AuthContext'

interface ApiContextValue {
  apiClient: ApiClient
  emfClient: EMFClient
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
 * by the SDK's EMFClient Axios instance.
 */
export function ApiProvider({ children, baseUrl = '' }: ApiProviderProps): React.ReactElement {
  const { getAccessToken, login } = useAuth()

  const emfClient = useMemo(() => {
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

    const client = new EMFClient({
      baseUrl,
      tokenProvider,
      // Disable SDK-level response validation in the UI — the UI has its own
      // error handling through ApiError / ApiClient.
      validation: false,
      // Disable retry for 401 — we handle it via the response interceptor below
      retry: { maxAttempts: 0 },
    })

    // Add a 401 response interceptor to trigger re-authentication
    client.getAxiosInstance().interceptors.response.use(
      (response) => response,
      (error) => {
        if (
          error &&
          typeof error === 'object' &&
          'response' in error &&
          error.response &&
          typeof error.response === 'object' &&
          'status' in error.response &&
          error.response.status === 401
        ) {
          console.warn('[API] Received 401 Unauthorized, triggering re-authentication')
          login()
        }
        return Promise.reject(error)
      }
    )

    return client
  }, [baseUrl, getAccessToken, login])

  const apiClient = useMemo(() => new ApiClient(emfClient.getAxiosInstance()), [emfClient])

  const contextValue = useMemo<ApiContextValue>(
    () => ({
      apiClient,
      emfClient,
    }),
    [apiClient, emfClient]
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
