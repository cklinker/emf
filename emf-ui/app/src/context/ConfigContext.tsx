/**
 * Configuration Context
 *
 * Provides bootstrap configuration state and management for the application.
 * Fetches configuration from /ui/config/bootstrap and handles loading, error, and reload states.
 *
 * Requirements:
 * - 1.1: Fetch bootstrap configuration from /ui/config/bootstrap on startup
 * - 1.2: Configure application routes based on page definitions
 * - 1.3: Configure navigation menus based on menu definitions
 * - 1.4: Apply theme settings including colors, fonts, and spacing
 * - 1.5: Apply branding including logo, application name, and favicon
 * - 1.6: Display error page with retry option if bootstrap endpoint unavailable
 * - 1.7: Display error page with diagnostic information if config invalid
 * - 1.8: Offer to reload when config changes detected
 */

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
  useRef,
} from 'react'
import type { BootstrapConfig } from '../types/config'

/**
 * Configuration context value interface
 */
export interface ConfigContextValue {
  /** The bootstrap configuration, null if not loaded */
  config: BootstrapConfig | null
  /** Whether the configuration is currently being loaded */
  isLoading: boolean
  /** Error that occurred during configuration fetch, null if no error */
  error: Error | null
  /** Reload the configuration from the server */
  reload: () => Promise<void>
}

/**
 * Props for the ConfigProvider component
 */
export interface ConfigProviderProps {
  /** Child components to render */
  children: React.ReactNode
  /** Optional custom endpoint URL for bootstrap config (defaults to /ui/config/bootstrap) */
  bootstrapEndpoint?: string
  /** Optional interval in ms to check for config changes (defaults to 0 = disabled) */
  pollInterval?: number
}

// Bootstrap endpoint URL — prefixed with VITE_API_BASE_URL for production (e.g. https://emf.rzware.com)
const DEFAULT_BOOTSTRAP_ENDPOINT = `${import.meta.env.VITE_API_BASE_URL || ''}/ui/config/bootstrap`

/**
 * Validate that the bootstrap config has required fields
 */
function validateBootstrapConfig(config: unknown): config is BootstrapConfig {
  if (!config || typeof config !== 'object') {
    return false
  }

  const c = config as Record<string, unknown>

  // Check required arrays exist
  if (!Array.isArray(c.pages)) {
    return false
  }
  if (!Array.isArray(c.menus)) {
    return false
  }

  // Check required objects exist
  if (!c.theme || typeof c.theme !== 'object') {
    return false
  }
  if (!c.branding || typeof c.branding !== 'object') {
    return false
  }
  if (!c.features || typeof c.features !== 'object') {
    return false
  }

  // Validate theme has required fields
  const theme = c.theme as Record<string, unknown>
  if (typeof theme.primaryColor !== 'string') {
    return false
  }
  if (typeof theme.secondaryColor !== 'string') {
    return false
  }
  if (typeof theme.fontFamily !== 'string') {
    return false
  }
  if (typeof theme.borderRadius !== 'string') {
    return false
  }

  // Validate branding has required fields
  const branding = c.branding as Record<string, unknown>
  if (typeof branding.logoUrl !== 'string') {
    return false
  }
  if (typeof branding.applicationName !== 'string') {
    return false
  }
  if (typeof branding.faviconUrl !== 'string') {
    return false
  }

  // Validate features has required fields
  const features = c.features as Record<string, unknown>
  if (typeof features.enableBuilder !== 'boolean') {
    return false
  }
  if (typeof features.enableResourceBrowser !== 'boolean') {
    return false
  }
  if (typeof features.enablePackages !== 'boolean') {
    return false
  }
  if (typeof features.enableMigrations !== 'boolean') {
    return false
  }
  if (typeof features.enableDashboard !== 'boolean') {
    return false
  }

  return true
}

/**
 * Create a configuration validation error with diagnostic information
 */
function createValidationError(config: unknown): Error {
  const diagnostics: string[] = []

  if (!config || typeof config !== 'object') {
    diagnostics.push('Configuration is not an object')
  } else {
    const c = config as Record<string, unknown>

    if (!Array.isArray(c.pages)) {
      diagnostics.push('Missing or invalid "pages" array')
    }
    if (!Array.isArray(c.menus)) {
      diagnostics.push('Missing or invalid "menus" array')
    }
    if (!c.theme || typeof c.theme !== 'object') {
      diagnostics.push('Missing or invalid "theme" object')
    }
    if (!c.branding || typeof c.branding !== 'object') {
      diagnostics.push('Missing or invalid "branding" object')
    }
    if (!c.features || typeof c.features !== 'object') {
      diagnostics.push('Missing or invalid "features" object')
    }
  }

  const error = new Error(`Invalid bootstrap configuration: ${diagnostics.join(', ')}`)
  error.name = 'ConfigValidationError'
  return error
}

// Create the context with undefined default
const ConfigContext = createContext<ConfigContextValue | undefined>(undefined)

/**
 * Configuration Provider Component
 *
 * Wraps the application to provide bootstrap configuration state and methods.
 * Fetches configuration from the bootstrap endpoint on mount.
 *
 * @example
 * ```tsx
 * <ConfigProvider>
 *   <App />
 * </ConfigProvider>
 * ```
 */
export function ConfigProvider({
  children,
  bootstrapEndpoint = DEFAULT_BOOTSTRAP_ENDPOINT,
  pollInterval = 0,
}: ConfigProviderProps): React.ReactElement {
  const [config, setConfig] = useState<BootstrapConfig | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  // Track the last config hash for change detection
  const lastConfigHashRef = useRef<string | null>(null)
  // Track if initial load has been attempted
  const initialLoadAttemptedRef = useRef(false)

  /**
   * Compute a simple hash of the config for change detection
   */
  const computeConfigHash = useCallback((cfg: BootstrapConfig): string => {
    return JSON.stringify(cfg)
  }, [])

  /**
   * Fetch bootstrap configuration from the server
   * Requirements: 1.1, 1.6, 1.7
   */
  const fetchConfig = useCallback(async (): Promise<BootstrapConfig> => {
    const response = await fetch(bootstrapEndpoint)

    // Requirement 1.6: Handle unavailable endpoint
    if (!response.ok) {
      const error = new Error(
        `Failed to fetch bootstrap configuration: ${response.status} ${response.statusText}`
      )
      error.name = 'ConfigFetchError'
      throw error
    }

    let data: unknown
    try {
      data = await response.json()
    } catch {
      const error = new Error('Failed to parse bootstrap configuration: Invalid JSON response')
      error.name = 'ConfigParseError'
      throw error
    }

    // Requirement 1.7: Validate configuration
    if (!validateBootstrapConfig(data)) {
      throw createValidationError(data)
    }

    return data
  }, [bootstrapEndpoint])

  /**
   * Load or reload the configuration
   * Requirements: 1.1, 1.6, 1.7, 1.8
   */
  const loadConfig = useCallback(
    async (isReload: boolean = false): Promise<void> => {
      if (!isReload) {
        setIsLoading(true)
      }
      setError(null)

      try {
        const newConfig = await fetchConfig()
        const newHash = computeConfigHash(newConfig)

        // Requirement 1.8: Detect config changes
        if (isReload && lastConfigHashRef.current !== null) {
          if (lastConfigHashRef.current !== newHash) {
            console.info('[Config] Configuration has changed')
          }
        }

        lastConfigHashRef.current = newHash
        setConfig(newConfig)
      } catch (err) {
        const configError =
          err instanceof Error ? err : new Error('Unknown error loading configuration')
        setError(configError)
        console.error('[Config] Failed to load configuration:', configError)
      } finally {
        setIsLoading(false)
      }
    },
    [fetchConfig, computeConfigHash]
  )

  /**
   * Reload the configuration from the server
   * Requirement 1.8: Offer to reload when config changes detected
   */
  const reload = useCallback(async (): Promise<void> => {
    await loadConfig(true)
  }, [loadConfig])

  /**
   * Initialize configuration on mount
   * Requirement 1.1: Fetch bootstrap configuration on startup
   */
  useEffect(() => {
    // Only attempt initial load once to prevent infinite loops on failure
    if (initialLoadAttemptedRef.current) {
      return
    }
    initialLoadAttemptedRef.current = true
    loadConfig(false)
  }, [loadConfig])

  /**
   * Set up polling for config changes if enabled
   * Requirement 1.8: Detect config changes
   */
  useEffect(() => {
    if (pollInterval <= 0) {
      return
    }

    const intervalId = setInterval(() => {
      // Only poll if we have a valid config (don't poll during error state)
      if (config && !error) {
        reload().catch((err) => {
          console.warn('[Config] Polling failed:', err)
        })
      }
    }, pollInterval)

    return () => {
      clearInterval(intervalId)
    }
  }, [pollInterval, config, error, reload])

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo<ConfigContextValue>(
    () => ({
      config,
      isLoading,
      error,
      reload,
    }),
    [config, isLoading, error, reload]
  )

  return <ConfigContext.Provider value={contextValue}>{children}</ConfigContext.Provider>
}

/**
 * Hook to access configuration context
 *
 * @throws Error if used outside of ConfigProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { config, isLoading, error, reload } = useConfig();
 *
 *   if (isLoading) return <LoadingSpinner />;
 *   if (error) return <ErrorMessage error={error} onRetry={reload} />;
 *
 *   return <div>{config?.branding.applicationName}</div>;
 * }
 * ```
 */
export function useConfig(): ConfigContextValue {
  const context = useContext(ConfigContext)
  if (context === undefined) {
    throw new Error('useConfig must be used within a ConfigProvider')
  }
  return context
}

// Export the context for testing purposes
export { ConfigContext }
