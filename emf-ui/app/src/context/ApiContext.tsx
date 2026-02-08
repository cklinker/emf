/**
 * API Context
 *
 * Provides an authenticated API client throughout the application.
 * Automatically includes Bearer tokens in all requests.
 */

import React, { createContext, useContext, useMemo } from 'react'
import { ApiClient, createApiClient } from '../services/apiClient'
import { useAuth } from './AuthContext'

interface ApiContextValue {
  apiClient: ApiClient
}

const ApiContext = createContext<ApiContextValue | undefined>(undefined)

export interface ApiProviderProps {
  children: React.ReactNode
  baseUrl?: string
}

/**
 * API Provider Component
 *
 * Wraps the application to provide an authenticated API client.
 */
export function ApiProvider({ children, baseUrl = '' }: ApiProviderProps): React.ReactElement {
  const { getAccessToken, login } = useAuth()

  const apiClient = useMemo(
    () =>
      createApiClient({
        baseUrl,
        getAccessToken,
        onUnauthorized: () => {
          // On 401, trigger re-authentication
          console.warn('[API] Received 401 Unauthorized, triggering re-authentication')
          login()
        },
      }),
    [baseUrl, getAccessToken, login]
  )

  const contextValue = useMemo<ApiContextValue>(
    () => ({
      apiClient,
    }),
    [apiClient]
  )

  return <ApiContext.Provider value={contextValue}>{children}</ApiContext.Provider>
}

/**
 * Hook to access the API client
 *
 * @throws Error if used outside of ApiProvider
 */
export function useApi(): ApiContextValue {
  const context = useContext(ApiContext)
  if (context === undefined) {
    throw new Error('useApi must be used within an ApiProvider')
  }
  return context
}
