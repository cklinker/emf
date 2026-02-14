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
import { fetchBootstrapConfig } from '../utils/bootstrapCache'
import { getTenantSlug, setResolvedTenantId } from './TenantContext'

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
  JUST_LOGGED_OUT: 'emf_auth_just_logged_out',
} as const

// Token refresh buffer (refresh 30 seconds before expiry)
const TOKEN_REFRESH_BUFFER_MS = 30 * 1000

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
 * Apply role mapping from OIDC provider configuration.
 * Maps raw IdP groups (e.g., "authentik Admins", "emf-admins") to
 * application roles (e.g., "PLATFORM_ADMIN", "ADMIN") using the
 * rolesMapping JSON from the provider config.
 */
function applyRoleMapping(
  rawGroups: string[] | undefined,
  oidcProviders?: OIDCProviderSummary[]
): string[] | undefined {
  if (!rawGroups || rawGroups.length === 0) return rawGroups
  if (!oidcProviders || oidcProviders.length === 0) return rawGroups

  // Find the first provider with a rolesMapping configured
  const provider = oidcProviders.find((p) => p.rolesMapping)
  if (!provider?.rolesMapping) return rawGroups

  try {
    const mapping = JSON.parse(provider.rolesMapping) as Record<string, string>
    const mappedRoles: string[] = []
    for (const group of rawGroups) {
      const role = mapping[group]
      if (role) {
        mappedRoles.push(role)
      }
    }
    return mappedRoles.length > 0 ? mappedRoles : rawGroups
  } catch {
    console.warn('[Auth] Failed to parse rolesMapping:', provider.rolesMapping)
    return rawGroups
  }
}

/**
 * Extract user information from ID token or access token.
 * When OIDC providers are supplied, applies role mapping to convert
 * raw IdP groups to application roles.
 */
function extractUserFromToken(
  idToken?: string,
  accessToken?: string,
  oidcProviders?: OIDCProviderSummary[]
): User | null {
  const token = idToken || accessToken
  if (!token) return null

  const claims = parseJwt(token)
  if (!claims) return null

  // Determine the roles claim name from the provider config (default: "groups")
  const provider = oidcProviders?.find((p) => p.rolesClaim)
  const rolesClaim = provider?.rolesClaim || 'groups'

  // Extract raw groups from the configured claim
  const rawGroups =
    (claims.roles as string[] | undefined) || (claims[rolesClaim] as string[] | undefined)

  // Apply role mapping if providers are available
  const roles = applyRoleMapping(rawGroups, oidcProviders)

  return {
    id: (claims.sub as string) || '',
    email: (claims.email as string) || '',
    name: (claims.name as string) || (claims.preferred_username as string),
    picture: claims.picture as string | undefined,
    roles,
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

// Module-level guard to prevent concurrent login() calls within the same page load.
// Using a module variable (not sessionStorage) ensures it resets on page reload,
// so the callback page gets a clean slate after returning from the IdP.
let loginInProgress = false

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
   * Fetch available OIDC providers from bootstrap config.
   * Uses shared cache to avoid duplicate request (ConfigContext also reads bootstrap).
   */
  const fetchProviders = useCallback(async (): Promise<OIDCProviderSummary[]> => {
    try {
      const config = (await fetchBootstrapConfig()) as Record<string, unknown>
      // Store resolved tenant ID from bootstrap response
      if (config.tenantId) {
        setResolvedTenantId(config.tenantId as string)
      }
      return (config.oidcProviders as OIDCProviderSummary[]) || []
    } catch (err) {
      // Don't throw - just return empty. ConfigProvider will handle the error display.
      console.warn('[Auth] Failed to fetch providers:', err)
      return []
    }
  }, [])

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
      const newUser = extractUserFromToken(newTokens.idToken, newTokens.accessToken, providers)
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
      // Guard against concurrent login calls within the same page load.
      // Uses a module-level variable (not sessionStorage) so it resets on page reload,
      // ensuring the callback page gets a clean slate after returning from the IdP.
      if (loginInProgress) {
        console.log('[Auth] Login already in progress, skipping duplicate call')
        return
      }
      loginInProgress = true

      setError(null)

      // If no provider specified and multiple providers available, redirect to selection
      if (!providerId && providers.length > 1) {
        // Store current path for redirect after auth
        sessionStorage.setItem(STORAGE_KEYS.REDIRECT_PATH, window.location.pathname)
        loginInProgress = false
        const slug = getTenantSlug()
        window.location.href = `/${slug}/auth/select-provider`
        return
      }

      // Use specified provider or the only available one
      const selectedProviderId = providerId || providers[0]?.id
      if (!selectedProviderId) {
        loginInProgress = false
        throw new Error('No OIDC providers configured')
      }

      const provider = providers.find((p) => p.id === selectedProviderId)
      if (!provider) {
        loginInProgress = false
        throw new Error(`Provider not found: ${selectedProviderId}`)
      }

      try {
        // Clear any previous login error
        sessionStorage.removeItem(STORAGE_KEYS.LOGIN_ERROR)

        // Generate all auth parameters BEFORE any async operations to minimize
        // the window for race conditions
        const codeVerifier = generateRandomString(64)
        const state = generateRandomString(32)
        const nonce = generateRandomString(32)

        // Store auth parameters immediately (before async calls)
        sessionStorage.setItem(STORAGE_KEYS.CODE_VERIFIER, codeVerifier)
        sessionStorage.setItem(STORAGE_KEYS.STATE, state)
        sessionStorage.setItem(STORAGE_KEYS.NONCE, nonce)
        sessionStorage.setItem(STORAGE_KEYS.PROVIDER_ID, selectedProviderId)
        sessionStorage.setItem(STORAGE_KEYS.REDIRECT_PATH, window.location.pathname)
        sessionStorage.setItem(STORAGE_KEYS.REDIRECT_URI, redirectUri)

        // Now do async operations (these are what caused the race condition)
        const discovery = await fetchDiscoveryDocument(provider.issuer)
        const codeChallenge = await generateCodeChallenge(codeVerifier)

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

        // If user just logged out, force the IdP to show the login form
        // instead of silently re-authenticating with the existing session
        if (sessionStorage.getItem(STORAGE_KEYS.JUST_LOGGED_OUT) === 'true') {
          authUrl.searchParams.set('prompt', 'login')
          sessionStorage.removeItem(STORAGE_KEYS.JUST_LOGGED_OUT)
        }

        // Redirect to authorization endpoint
        window.location.href = authUrl.toString()
      } catch (err) {
        loginInProgress = false
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

    // Set logout flag BEFORE clearing storage so LoginPage knows to skip auto-login.
    // This survives clearAuthStorage() because it's set after the clear,
    // and it's more reliable than URL params which the IdP may strip.
    sessionStorage.setItem(STORAGE_KEYS.JUST_LOGGED_OUT, 'true')

    // Clear local auth state
    clearAuthStorage()
    setUser(null)
    setError(null)

    // Re-set the flag since clearAuthStorage just cleared it
    sessionStorage.setItem(STORAGE_KEYS.JUST_LOGGED_OUT, 'true')

    // Build logout redirect URL (keep ?logged_out=true as belt-and-suspenders with sessionStorage)
    const logoutRedirect = new URL(postLogoutRedirectUri)
    logoutRedirect.searchParams.set('logged_out', 'true')
    const logoutRedirectStr = logoutRedirect.toString()

    // If we have provider info, try to do OIDC logout
    if (providerId && storedTokens?.idToken) {
      const provider = providers.find((p) => p.id === providerId)
      if (provider) {
        try {
          const discovery = await fetchDiscoveryDocument(provider.issuer)
          if (discovery.end_session_endpoint) {
            const logoutUrl = new URL(discovery.end_session_endpoint)
            logoutUrl.searchParams.set('id_token_hint', storedTokens.idToken)
            logoutUrl.searchParams.set('post_logout_redirect_uri', logoutRedirectStr)
            window.location.href = logoutUrl.toString()
            return
          }
        } catch (err) {
          console.error('[Auth] OIDC logout failed:', err)
        }
      }
    }

    // Fallback: redirect to home with logged_out flag
    window.location.href = logoutRedirectStr
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

      if (errorParam) {
        throw new Error(errorDescription || errorParam)
      }

      // Validate state
      const storedState = sessionStorage.getItem(STORAGE_KEYS.STATE)
      if (!state || state !== storedState) {
        throw new Error('Invalid state parameter')
      }

      // Get stored parameters
      const codeVerifier = sessionStorage.getItem(STORAGE_KEYS.CODE_VERIFIER)
      const providerId = sessionStorage.getItem(STORAGE_KEYS.PROVIDER_ID)

      if (!code || !codeVerifier || !providerId) {
        throw new Error('Missing authentication parameters')
      }

      // Get provider info
      const provider = availableProviders.find((p) => p.id === providerId)
      if (!provider) {
        throw new Error(`Provider not found: ${providerId}`)
      }

      // Get discovery document
      const discovery = await fetchDiscoveryDocument(provider.issuer)

      // Use the stored redirect_uri (exact match with authorization request)
      const storedRedirectUri = sessionStorage.getItem(STORAGE_KEYS.REDIRECT_URI) || redirectUri

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
        throw new Error(
          `Token exchange failed: ${errorData.error_description || errorData.error || response.statusText}`
        )
      }

      const tokenResponse = (await response.json()) as TokenResponse

      const tokens: StoredTokens = {
        accessToken: tokenResponse.access_token,
        idToken: tokenResponse.id_token,
        refreshToken: tokenResponse.refresh_token,
        expiresAt: Date.now() + tokenResponse.expires_in * 1000,
      }

      storeTokens(tokens)

      const newUser = extractUserFromToken(tokens.idToken, tokens.accessToken, availableProviders)
      if (newUser) {
        setUser(newUser)
      } else {
        throw new Error('Failed to extract user information from tokens')
      }

      // Clean up temporary storage
      sessionStorage.removeItem(STORAGE_KEYS.STATE)
      sessionStorage.removeItem(STORAGE_KEYS.NONCE)
      sessionStorage.removeItem(STORAGE_KEYS.CODE_VERIFIER)

      const slug = getTenantSlug()
      const tenantBase = `/${slug}`
      let redirectPath = sessionStorage.getItem(STORAGE_KEYS.REDIRECT_PATH) || tenantBase
      sessionStorage.removeItem(STORAGE_KEYS.REDIRECT_PATH)

      // Redirect to tenant home if path points to login/callback pages
      if (
        redirectPath === '/login' ||
        redirectPath === '/auth/callback' ||
        redirectPath === `${tenantBase}/login` ||
        redirectPath === `${tenantBase}/auth/callback` ||
        redirectPath === '/'
      ) {
        redirectPath = tenantBase
      }

      console.log('[Auth] Callback complete, redirecting to:', redirectPath)
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
          setError(new Error('Authentication configuration unavailable. Please contact support.'))
          // Clear URL to prevent loop
          window.history.replaceState({}, document.title, window.location.pathname)
          clearAuthStorage()
          setIsLoading(false)
          return
        }

        // Handle OAuth callback
        if (isCallback) {
          try {
            await handleCallback(availableProviders)
            // Clear the processed code marker after successful processing
            sessionStorage.removeItem(STORAGE_KEYS.CALLBACK_PROCESSED)
          } catch (callbackError) {
            console.error('[Auth] Callback failed:', callbackError)
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

        if (storedTokens) {
          // Check if tokens are still valid
          if (!isTokenExpired(storedTokens.expiresAt)) {
            const existingUser = extractUserFromToken(
              storedTokens.idToken,
              storedTokens.accessToken,
              availableProviders
            )
            if (existingUser) {
              setUser(existingUser)
            }
          } else {
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
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

// Export the context for testing purposes
export { AuthContext }
