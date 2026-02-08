/**
 * Authentication Context
 *
 * Provides authentication state and OIDC authentication flow for the application.
 * Implements redirect-based OIDC authentication with token storage and refresh.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login
 * - 2.2: Display provider selection page for multiple providers
 * - 2.3: Store access token securely and redirect after auth
 * - 2.4: Attempt silent token refresh when token expires
 * - 2.5: Redirect to login if refresh fails
 * - 2.6: Clear tokens and redirect on logout
 * - 2.7: Include access token in all API requests
 * - 2.8: Trigger token refresh on 401 responses
 */

import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react'
import type {
  User,
  AuthContextValue,
  AuthProviderProps,
  StoredTokens,
  TokenResponse,
  OIDCDiscoveryDocument,
} from '../types/auth'
import type { OIDCProviderSummary } from '../types/config'

// Storage keys
const STORAGE_KEYS = {
  TOKENS: 'emf_auth_tokens',
  STATE: 'emf_auth_state',
  NONCE: 'emf_auth_nonce',
  CODE_VERIFIER: 'emf_auth_code_verifier',
  REDIRECT_PATH: 'emf_auth_redirect_path',
  REDIRECT_URI: 'emf_auth_redirect_uri',
  PROVIDER_ID: 'emf_auth_provider_id',
  CALLBACK_PROCESSED: 'emf_auth_callback_processed',
  LOGIN_ERROR: 'emf_auth_login_error',
} as const

// Token refresh buffer (refresh 5 minutes before expiry)
const TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000

/**
 * Generate a random string for state/nonce/code_verifier
 */
function generateRandomString(length: number = 32): string {
  const array = new Uint8Array(length)
  crypto.getRandomValues(array)
  return Array.from(array, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/**
 * Generate code challenge from code verifier (PKCE)
 */
async function generateCodeChallenge(codeVerifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(codeVerifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

/**
 * Parse JWT token to extract claims
 */
function parseJwt(token: string): Record<string, unknown> | null {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}

/**
 * Extract user information from ID token or access token
 */
function extractUserFromToken(idToken?: string, accessToken?: string): User | null {
  const token = idToken || accessToken
  if (!token) return null

  const claims = parseJwt(token)
  if (!claims) return null

  return {
    id: (claims.sub as string) || '',
    email: (claims.email as string) || '',
    name: (claims.name as string) || (claims.preferred_username as string),
    picture: claims.picture as string | undefined,
    roles: (claims.roles as string[] | undefined) || (claims.groups as string[] | undefined),
    claims,
  }
}

/**
 * Check if tokens are expired or about to expire
 */
function isTokenExpired(expiresAt: number): boolean {
  return Date.now() >= expiresAt - TOKEN_REFRESH_BUFFER_MS
}

/**
 * Store tokens in sessionStorage (more secure than localStorage)
 */
function storeTokens(tokens: StoredTokens): void {
  sessionStorage.setItem(STORAGE_KEYS.TOKENS, JSON.stringify(tokens))
}

/**
 * Retrieve stored tokens
 */
function getStoredTokens(): StoredTokens | null {
  const stored = sessionStorage.getItem(STORAGE_KEYS.TOKENS)
  if (!stored) return null
  try {
    return JSON.parse(stored) as StoredTokens
  } catch {
    return null
  }
}

/**
 * Clear all stored auth data
 */
function clearAuthStorage(): void {
  Object.values(STORAGE_KEYS).forEach((key) => {
    sessionStorage.removeItem(key)
  })
}

// Create the context with undefined default
const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/**
 * Authentication Provider Component
 *
 * Wraps the application to provide authentication state and methods.
 */
export function AuthProvider({
  children,
  redirectUri = window.location.origin + '/auth/callback',
  postLogoutRedirectUri = window.location.origin,
}: AuthProviderProps): React.ReactElement {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [providers, setProviders] = useState<OIDCProviderSummary[]>([])
  const [discoveryDocs, setDiscoveryDocs] = useState<Map<string, OIDCDiscoveryDocument>>(new Map())

  const isAuthenticated = user !== null

  /**
   * Fetch OIDC discovery document for a provider
   */
  const fetchDiscoveryDocument = useCallback(
    async (issuer: string): Promise<OIDCDiscoveryDocument> => {
      // Check cache first
      const cached = discoveryDocs.get(issuer)
      if (cached) return cached

      const wellKnownUrl = `${issuer.replace(/\/$/, '')}/.well-known/openid-configuration`
      const response = await fetch(wellKnownUrl)
      if (!response.ok) {
        throw new Error(`Failed to fetch OIDC discovery document: ${response.statusText}`)
      }
      const doc = (await response.json()) as OIDCDiscoveryDocument

      // Cache the document
      setDiscoveryDocs((prev) => new Map(prev).set(issuer, doc))
      return doc
    },
    [discoveryDocs]
  )

  /**
   * Fetch available OIDC providers from bootstrap config
   * Note: This is a one-time fetch on init. If it fails, we return empty array
   * and the app will show the config error from ConfigProvider.
   */
  const fetchProviders = useCallback(async (): Promise<OIDCProviderSummary[]> => {
    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL || ''}/control/ui-bootstrap`
      )
      if (!response.ok) {
        // Don't throw - just return empty. ConfigProvider will handle the error display.
        console.warn('[Auth] Bootstrap config unavailable, OIDC providers not loaded')
        return []
      }
      const config = await response.json()
      return config.oidcProviders || []
    } catch (err) {
      // Don't throw - just return empty. ConfigProvider will handle the error display.
      console.warn('[Auth] Failed to fetch providers:', err)
      return []
    }
  }, [])

  /**
   * Exchange authorization code for tokens
   */
  const exchangeCodeForTokens = useCallback(
    async (code: string, providerId: string, codeVerifier: string): Promise<TokenResponse> => {
      // Get provider info
      const provider = providers.find((p) => p.id === providerId)
      if (!provider) {
        throw new Error(`Provider not found: ${providerId}`)
      }

      // Get discovery document
      const discovery = await fetchDiscoveryDocument(provider.issuer)

      // Exchange code for tokens
      const tokenUrl = discovery.token_endpoint
      const params = new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: provider.clientId,
        code,
        redirect_uri: redirectUri,
        code_verifier: codeVerifier,
      })

      const response = await fetch(tokenUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString(),
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(
          `Token exchange failed: ${errorData.error_description || response.statusText}`
        )
      }

      return response.json()
    },
    [providers, fetchDiscoveryDocument, redirectUri]
  )

  /**
   * Refresh the access token using the refresh token
   */
  const refreshAccessToken = useCallback(async (): Promise<StoredTokens | null> => {
    const storedTokens = getStoredTokens()
    if (!storedTokens?.refreshToken) {
      return null
    }

    const providerId = sessionStorage.getItem(STORAGE_KEYS.PROVIDER_ID)
    if (!providerId) {
      return null
    }

    const provider = providers.find((p) => p.id === providerId)
    if (!provider) {
      return null
    }

    try {
      const discovery = await fetchDiscoveryDocument(provider.issuer)
      const tokenUrl = discovery.token_endpoint

      const params = new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: storedTokens.refreshToken,
      })

      const response = await fetch(tokenUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString(),
      })

      if (!response.ok) {
        console.error('[Auth] Token refresh failed:', response.statusText)
        return null
      }

      const tokenResponse: TokenResponse = await response.json()
      const newTokens: StoredTokens = {
        accessToken: tokenResponse.access_token,
        idToken: tokenResponse.id_token,
        refreshToken: tokenResponse.refresh_token || storedTokens.refreshToken,
        expiresAt: Date.now() + tokenResponse.expires_in * 1000,
      }

      storeTokens(newTokens)
      const newUser = extractUserFromToken(newTokens.idToken, newTokens.accessToken)
      if (newUser) {
        setUser(newUser)
      }

      return newTokens
    } catch (err) {
      console.error('[Auth] Token refresh error:', err)
      return null
    }
  }, [providers, fetchDiscoveryDocument])

  /**
   * Get the current access token, refreshing if necessary
   * Requirement 2.4: Attempt silent token refresh when token expires
   * Requirement 2.7: Include access token in all API requests
   */
  const getAccessToken = useCallback(async (): Promise<string> => {
    const storedTokens = getStoredTokens()

    if (!storedTokens) {
      throw new Error('No tokens available. Please log in.')
    }

    // Check if token is expired or about to expire
    if (isTokenExpired(storedTokens.expiresAt)) {
      // Attempt to refresh
      const refreshedTokens = await refreshAccessToken()
      if (refreshedTokens) {
        return refreshedTokens.accessToken
      }
      // Refresh failed - clear auth and throw
      // Requirement 2.5: Redirect to login if refresh fails
      clearAuthStorage()
      setUser(null)
      throw new Error('Session expired. Please log in again.')
    }

    return storedTokens.accessToken
  }, [refreshAccessToken])

  /**
   * Initiate login flow
   * Requirement 2.1: Redirect unauthenticated users to OIDC provider login
   * Requirement 2.2: Display provider selection page for multiple providers
   */
  const login = useCallback(
    async (providerId?: string): Promise<void> => {
      setError(null)

      // If no provider specified and multiple providers available, redirect to selection
      if (!providerId && providers.length > 1) {
        // Store current path for redirect after auth
        sessionStorage.setItem(STORAGE_KEYS.REDIRECT_PATH, window.location.pathname)
        window.location.href = '/auth/select-provider'
        return
      }

      // Use specified provider or the only available one
      const selectedProviderId = providerId || providers[0]?.id
      if (!selectedProviderId) {
        throw new Error('No OIDC providers configured')
      }

      const provider = providers.find((p) => p.id === selectedProviderId)
      if (!provider) {
        throw new Error(`Provider not found: ${selectedProviderId}`)
      }

      try {
        // Clear any previous login error
        sessionStorage.removeItem(STORAGE_KEYS.LOGIN_ERROR)

        // Fetch discovery document
        const discovery = await fetchDiscoveryDocument(provider.issuer)

        // Generate PKCE code verifier and challenge
        const codeVerifier = generateRandomString(64)
        const codeChallenge = await generateCodeChallenge(codeVerifier)

        // Generate state and nonce
        const state = generateRandomString(32)
        const nonce = generateRandomString(32)

        // Store auth parameters (including redirect_uri for exact match during exchange)
        sessionStorage.setItem(STORAGE_KEYS.CODE_VERIFIER, codeVerifier)
        sessionStorage.setItem(STORAGE_KEYS.STATE, state)
        sessionStorage.setItem(STORAGE_KEYS.NONCE, nonce)
        sessionStorage.setItem(STORAGE_KEYS.PROVIDER_ID, selectedProviderId)
        sessionStorage.setItem(STORAGE_KEYS.REDIRECT_PATH, window.location.pathname)
        sessionStorage.setItem(STORAGE_KEYS.REDIRECT_URI, redirectUri)

        // Build authorization URL
        const authUrl = new URL(discovery.authorization_endpoint)
        authUrl.searchParams.set('response_type', 'code')
        authUrl.searchParams.set('client_id', provider.clientId)
        authUrl.searchParams.set('scope', 'openid profile email')
        authUrl.searchParams.set('redirect_uri', redirectUri)
        authUrl.searchParams.set('state', state)
        authUrl.searchParams.set('nonce', nonce)
        authUrl.searchParams.set('code_challenge', codeChallenge)
        authUrl.searchParams.set('code_challenge_method', 'S256')

        console.log('[Auth] PKCE parameters:', {
          codeVerifierLength: codeVerifier.length,
          codeChallengeLength: codeChallenge.length,
          codeChallenge,
          redirectUri,
        })

        // Redirect to authorization endpoint
        window.location.href = authUrl.toString()
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Login failed')
        setError(error)
        throw error
      }
    },
    [providers, fetchDiscoveryDocument, redirectUri]
  )

  /**
   * Logout and clear tokens
   * Requirement 2.6: Clear tokens and redirect on logout
   */
  const logout = useCallback(async (): Promise<void> => {
    const providerId = sessionStorage.getItem(STORAGE_KEYS.PROVIDER_ID)
    const storedTokens = getStoredTokens()

    // Clear local auth state
    clearAuthStorage()
    setUser(null)
    setError(null)

    // If we have provider info, try to do OIDC logout
    if (providerId && storedTokens?.idToken) {
      const provider = providers.find((p) => p.id === providerId)
      if (provider) {
        try {
          const discovery = await fetchDiscoveryDocument(provider.issuer)
          if (discovery.end_session_endpoint) {
            const logoutUrl = new URL(discovery.end_session_endpoint)
            logoutUrl.searchParams.set('id_token_hint', storedTokens.idToken)
            logoutUrl.searchParams.set('post_logout_redirect_uri', postLogoutRedirectUri)
            window.location.href = logoutUrl.toString()
            return
          }
        } catch (err) {
          console.error('[Auth] OIDC logout failed:', err)
        }
      }
    }

    // Fallback: redirect to home
    window.location.href = postLogoutRedirectUri
  }, [providers, fetchDiscoveryDocument, postLogoutRedirectUri])

  /**
   * Handle the OAuth callback
   * Requirement 2.3: Store access token securely and redirect after auth
   */
  const handleCallback = useCallback(
    async (availableProviders: OIDCProviderSummary[]): Promise<void> => {
      const urlParams = new URLSearchParams(window.location.search)
      const code = urlParams.get('code')
      const state = urlParams.get('state')
      const errorParam = urlParams.get('error')
      const errorDescription = urlParams.get('error_description')

      console.log('[Auth] Handling callback with params:', {
        hasCode: !!code,
        hasState: !!state,
        hasError: !!errorParam,
      })

      // Check for error response
      if (errorParam) {
        throw new Error(errorDescription || errorParam)
      }

      // Validate state
      const storedState = sessionStorage.getItem(STORAGE_KEYS.STATE)
      console.log('[Auth] State validation:', {
        urlState: state,
        storedState: storedState,
        match: state === storedState,
      })

      if (!state || state !== storedState) {
        throw new Error('Invalid state parameter')
      }

      // Get stored parameters
      const codeVerifier = sessionStorage.getItem(STORAGE_KEYS.CODE_VERIFIER)
      const providerId = sessionStorage.getItem(STORAGE_KEYS.PROVIDER_ID)

      console.log('[Auth] Retrieved stored parameters:', {
        hasCodeVerifier: !!codeVerifier,
        providerId: providerId,
      })

      if (!code || !codeVerifier || !providerId) {
        console.error('[Auth] Missing authentication parameters:', {
          hasCode: !!code,
          hasCodeVerifier: !!codeVerifier,
          hasProviderId: !!providerId,
        })
        throw new Error('Missing authentication parameters')
      }

      // Get provider info from the passed providers array
      const provider = availableProviders.find((p) => p.id === providerId)
      if (!provider) {
        console.error(
          '[Auth] Provider not found:',
          providerId,
          'Available:',
          availableProviders.map((p) => p.id)
        )
        throw new Error(`Provider not found: ${providerId}`)
      }

      console.log('[Auth] Found provider:', provider.name, 'Issuer:', provider.issuer)

      // Get discovery document
      const discovery = await fetchDiscoveryDocument(provider.issuer)
      console.log('[Auth] Discovery document loaded, token endpoint:', discovery.token_endpoint)

      // Use the stored redirect_uri (exact match with authorization request)
      const storedRedirectUri = sessionStorage.getItem(STORAGE_KEYS.REDIRECT_URI) || redirectUri

      console.log('[Auth] Token exchange parameters:', {
        tokenUrl: discovery.token_endpoint,
        clientId: provider.clientId,
        codeLength: code.length,
        redirectUri: storedRedirectUri,
        codeVerifierLength: codeVerifier.length,
      })

      // Exchange code for tokens
      const tokenUrl = discovery.token_endpoint
      const params = new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: provider.clientId,
        code,
        redirect_uri: storedRedirectUri,
        code_verifier: codeVerifier,
      })

      const response = await fetch(tokenUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString(),
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        const errorMsg = `Token exchange failed: ${errorData.error_description || errorData.error || response.statusText}`
        console.error('[Auth] Token exchange error:', {
          status: response.status,
          error: errorData.error,
          errorDescription: errorData.error_description,
          redirectUri: storedRedirectUri,
        })
        throw new Error(errorMsg)
      }

      const tokenResponse = (await response.json()) as TokenResponse

      // Store tokens
      const tokens: StoredTokens = {
        accessToken: tokenResponse.access_token,
        idToken: tokenResponse.id_token,
        refreshToken: tokenResponse.refresh_token,
        expiresAt: Date.now() + tokenResponse.expires_in * 1000,
      }

      console.log('[Auth] Token exchange successful, storing tokens')
      console.log('[Auth] Token response:', {
        hasAccessToken: !!tokenResponse.access_token,
        hasIdToken: !!tokenResponse.id_token,
        hasRefreshToken: !!tokenResponse.refresh_token,
        expiresIn: tokenResponse.expires_in,
      })

      storeTokens(tokens)

      // Extract user from token
      const newUser = extractUserFromToken(tokens.idToken, tokens.accessToken)
      console.log('[Auth] Extracted user:', newUser)

      if (newUser) {
        setUser(newUser)
        console.log('[Auth] User set successfully:', {
          id: newUser.id,
          email: newUser.email,
          name: newUser.name,
        })
      } else {
        console.error('[Auth] Failed to extract user from tokens')
        console.error('[Auth] ID Token present:', !!tokens.idToken)
        console.error('[Auth] Access Token present:', !!tokens.accessToken)
        throw new Error('Failed to extract user information from tokens')
      }

      // Clean up temporary storage
      sessionStorage.removeItem(STORAGE_KEYS.STATE)
      sessionStorage.removeItem(STORAGE_KEYS.NONCE)
      sessionStorage.removeItem(STORAGE_KEYS.CODE_VERIFIER)

      // Redirect to original path
      let redirectPath = sessionStorage.getItem(STORAGE_KEYS.REDIRECT_PATH) || '/'
      sessionStorage.removeItem(STORAGE_KEYS.REDIRECT_PATH)

      // Don't redirect back to login page
      if (redirectPath === '/login' || redirectPath === '/auth/callback') {
        redirectPath = '/'
      }

      console.log('[Auth] Callback complete, redirecting to:', redirectPath)

      // Use window.location.replace to navigate without adding to history
      // This will reload the page at the new path, and the auth context will
      // restore the user from the stored tokens
      window.location.replace(window.location.origin + redirectPath)
    },
    [fetchDiscoveryDocument, redirectUri]
  )

  /**
   * Initialize authentication state on mount
   */
  useEffect(() => {
    const initAuth = async () => {
      setIsLoading(true)
      setError(null)

      try {
        // Check for previous login error (persisted in sessionStorage)
        const previousLoginError = sessionStorage.getItem(STORAGE_KEYS.LOGIN_ERROR)
        if (previousLoginError) {
          console.log('[Auth] Found previous login error:', previousLoginError)
          setError(new Error(previousLoginError))
        }

        // Check if this is a callback FIRST, before fetching providers
        const urlParams = new URLSearchParams(window.location.search)
        const code = urlParams.get('code')
        const isCallback = urlParams.has('code') || urlParams.has('error')

        // If this is a callback, check if we've already processed this specific code
        if (isCallback && code) {
          const processedCode = sessionStorage.getItem(STORAGE_KEYS.CALLBACK_PROCESSED)
          if (processedCode === code) {
            console.log(
              '[Auth] Callback already processed for this code, skipping duplicate (React StrictMode)'
            )
            setIsLoading(false)
            return
          }
          // Mark this code as being processed
          sessionStorage.setItem(STORAGE_KEYS.CALLBACK_PROCESSED, code)
        }

        // Fetch available providers
        const availableProviders = await fetchProviders()
        setProviders(availableProviders)

        // If no providers available and this is a callback, we're in a bad state
        if (isCallback && availableProviders.length === 0) {
          console.error('[Auth] Callback received but no providers configured')
          setError(new Error('Authentication configuration unavailable. Please contact support.'))
          // Clear URL to prevent loop
          window.history.replaceState({}, document.title, window.location.pathname)
          clearAuthStorage()
          setIsLoading(false)
          return
        }

        // Handle OAuth callback
        if (isCallback) {
          console.log('[Auth] Processing callback...')
          try {
            await handleCallback(availableProviders)
            console.log('[Auth] Callback processed successfully')
            // Clear the processed code marker after successful processing
            sessionStorage.removeItem(STORAGE_KEYS.CALLBACK_PROCESSED)
          } catch (callbackError) {
            // Log the error for debugging
            console.error('[Auth] Callback handling failed:', callbackError)
            const authErr =
              callbackError instanceof Error ? callbackError : new Error('Authentication failed')
            setError(authErr)

            // Store the error in sessionStorage so it persists across page loads
            // and prevents the auto-login loop
            sessionStorage.setItem(STORAGE_KEYS.LOGIN_ERROR, authErr.message)

            // Clear URL parameters and state to prevent infinite loop
            window.history.replaceState({}, document.title, window.location.pathname)
            // Clear auth storage but keep the LOGIN_ERROR flag
            sessionStorage.removeItem(STORAGE_KEYS.STATE)
            sessionStorage.removeItem(STORAGE_KEYS.NONCE)
            sessionStorage.removeItem(STORAGE_KEYS.CODE_VERIFIER)
            sessionStorage.removeItem(STORAGE_KEYS.REDIRECT_URI)
            sessionStorage.removeItem(STORAGE_KEYS.TOKENS)
            sessionStorage.removeItem(STORAGE_KEYS.CALLBACK_PROCESSED)
            sessionStorage.removeItem(STORAGE_KEYS.PROVIDER_ID)
            sessionStorage.removeItem(STORAGE_KEYS.REDIRECT_PATH)
          }
          setIsLoading(false)
          return
        }

        // Check for existing tokens
        const storedTokens = getStoredTokens()
        console.log('[Auth] Checking for existing tokens:', {
          hasTokens: !!storedTokens,
          isExpired: storedTokens ? isTokenExpired(storedTokens.expiresAt) : null,
          expiresAt: storedTokens?.expiresAt,
          now: Date.now(),
        })

        if (storedTokens) {
          console.log('[Auth] Found stored tokens, checking expiration...')
          // Check if tokens are still valid
          if (!isTokenExpired(storedTokens.expiresAt)) {
            console.log('[Auth] Tokens are valid, extracting user...')
            const existingUser = extractUserFromToken(
              storedTokens.idToken,
              storedTokens.accessToken
            )
            console.log('[Auth] Restored user from tokens:', existingUser)
            if (existingUser) {
              setUser(existingUser)
              console.log('[Auth] User restored successfully')
            } else {
              console.error('[Auth] Failed to extract user from stored tokens')
            }
          } else {
            console.log('[Auth] Tokens are expired')
            if (storedTokens.refreshToken) {
              // Try to refresh
              const refreshedTokens = await refreshAccessToken()
              if (!refreshedTokens) {
                // Refresh failed, clear tokens
                clearAuthStorage()
              }
            } else {
              // No refresh token and expired, clear
              clearAuthStorage()
            }
          }
        }
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Authentication initialization failed')
        setError(error)
        console.error('[Auth] Initialization error:', error)
      } finally {
        setIsLoading(false)
      }
    }

    initAuth()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // Run only once on mount

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated,
      isLoading,
      error,
      login,
      logout,
      getAccessToken,
    }),
    [user, isAuthenticated, isLoading, error, login, logout, getAccessToken]
  )

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
}

/**
 * Hook to access authentication context
 *
 * @throws Error if used outside of AuthProvider
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

// Export the context for testing purposes
export { AuthContext }
