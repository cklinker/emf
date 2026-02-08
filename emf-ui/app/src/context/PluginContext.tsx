/**
 * Plugin Context
 *
 * Provides plugin system state and management for the application.
 * Loads and initializes configured plugins, manages the ComponentRegistry
 * for custom field renderers and page components, and handles plugin lifecycle.
 *
 * Requirements:
 * - 12.1: Plugin system loads configured plugins on startup
 * - 12.2: Plugin system supports custom field renderers via ComponentRegistry
 * - 12.3: Plugin system supports custom page components via ComponentRegistry
 * - 12.7: Plugin system handles plugin load failures gracefully
 * - 12.8: Plugin system calls plugin lifecycle hooks (onLoad, onUnload)
 */

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
  useRef,
  type ComponentType,
} from 'react'
import type {
  Plugin,
  PluginInitContext,
  LoadedPlugin,
  PluginStatus,
  FieldRendererProps,
  PageComponentProps,
} from '../types/plugin'

/**
 * Plugin context value interface
 */
export interface PluginContextValue {
  /** Map of field type to renderer component */
  fieldRenderers: Map<string, ComponentType<FieldRendererProps>>
  /** Map of component name to page component */
  pageComponents: Map<string, ComponentType<PageComponentProps>>
  /** Register a custom field renderer */
  registerFieldRenderer: (type: string, renderer: ComponentType<FieldRendererProps>) => void
  /** Register a custom page component */
  registerPageComponent: (name: string, component: ComponentType<PageComponentProps>) => void
  /** Get a field renderer by type */
  getFieldRenderer: (type: string) => ComponentType<FieldRendererProps> | undefined
  /** Get a page component by name */
  getPageComponent: (name: string) => ComponentType<PageComponentProps> | undefined
  /** List of loaded plugins with their status */
  plugins: LoadedPlugin[]
  /** Whether plugins are currently being loaded */
  isLoading: boolean
  /** Errors that occurred during plugin loading */
  errors: Array<{ pluginId: string; error: string }>
}

/**
 * Props for the PluginProvider component
 */
export interface PluginProviderProps {
  /** Child components to render */
  children: React.ReactNode
  /** Array of plugins to load */
  plugins?: Plugin[]
  /** Optional function to get current locale (for plugin context) */
  getLocale?: () => string
  /** Optional function to get current theme mode (for plugin context) */
  getThemeMode?: () => 'light' | 'dark'
}

// Create the context with undefined default
const PluginContext = createContext<PluginContextValue | undefined>(undefined)

/**
 * Plugin Provider Component
 *
 * Wraps the application to provide plugin system state and methods.
 * Loads and initializes plugins on mount, managing their lifecycle.
 *
 * @example
 * ```tsx
 * const plugins = [myCustomPlugin, anotherPlugin];
 *
 * <PluginProvider plugins={plugins}>
 *   <App />
 * </PluginProvider>
 * ```
 */
export function PluginProvider({
  children,
  plugins = [],
  getLocale = () => 'en',
  getThemeMode = () => 'light',
}: PluginProviderProps): React.ReactElement {
  // Component registry state
  const [fieldRenderers, setFieldRenderers] = useState<
    Map<string, ComponentType<FieldRendererProps>>
  >(new Map())
  const [pageComponents, setPageComponents] = useState<
    Map<string, ComponentType<PageComponentProps>>
  >(new Map())

  // Plugin loading state
  const [loadedPlugins, setLoadedPlugins] = useState<LoadedPlugin[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errors, setErrors] = useState<Array<{ pluginId: string; error: string }>>([])

  // Track if we've already initialized to prevent double loading
  const initializedRef = useRef(false)
  // Track loaded plugin IDs for cleanup
  const loadedPluginIdsRef = useRef<Set<string>>(new Set())

  /**
   * Register a custom field renderer
   * Requirement 12.2: Support custom field renderers via ComponentRegistry
   */
  const registerFieldRenderer = useCallback(
    (type: string, renderer: ComponentType<FieldRendererProps>) => {
      setFieldRenderers((prev) => {
        const next = new Map(prev)
        next.set(type, renderer)
        return next
      })
      console.info(`[Plugin] Registered field renderer for type: ${type}`)
    },
    []
  )

  /**
   * Register a custom page component
   * Requirement 12.3: Support custom page components via ComponentRegistry
   */
  const registerPageComponent = useCallback(
    (name: string, component: ComponentType<PageComponentProps>) => {
      setPageComponents((prev) => {
        const next = new Map(prev)
        next.set(name, component)
        return next
      })
      console.info(`[Plugin] Registered page component: ${name}`)
    },
    []
  )

  /**
   * Get a field renderer by type
   */
  const getFieldRenderer = useCallback(
    (type: string): ComponentType<FieldRendererProps> | undefined => {
      return fieldRenderers.get(type)
    },
    [fieldRenderers]
  )

  /**
   * Get a page component by name
   */
  const getPageComponent = useCallback(
    (name: string): ComponentType<PageComponentProps> | undefined => {
      return pageComponents.get(name)
    },
    [pageComponents]
  )

  /**
   * Load a single plugin
   * Requirements: 12.1, 12.7, 12.8
   */
  const loadPlugin = useCallback(
    async (plugin: Plugin): Promise<LoadedPlugin> => {
      const loadedPlugin: LoadedPlugin = {
        plugin,
        status: 'loading' as PluginStatus,
      }

      try {
        console.info(`[Plugin] Loading plugin: ${plugin.id} (${plugin.name} v${plugin.version})`)

        // Register static field renderers if provided
        if (plugin.fieldRenderers) {
          for (const [type, renderer] of Object.entries(plugin.fieldRenderers)) {
            registerFieldRenderer(type, renderer)
          }
        }

        // Register static page components if provided
        if (plugin.pageComponents) {
          for (const [name, component] of Object.entries(plugin.pageComponents)) {
            registerPageComponent(name, component)
          }
        }

        // Call onLoad lifecycle hook if provided
        // Requirement 12.8: Call plugin lifecycle hooks
        if (plugin.onLoad) {
          const context: PluginInitContext = {
            registerFieldRenderer,
            registerPageComponent,
            getLocale,
            getThemeMode,
          }
          await plugin.onLoad(context)
        }

        loadedPlugin.status = 'loaded'
        loadedPluginIdsRef.current.add(plugin.id)
        console.info(`[Plugin] Successfully loaded plugin: ${plugin.id}`)
      } catch (err) {
        // Requirement 12.7: Handle plugin load failures gracefully
        const errorMessage = err instanceof Error ? err.message : 'Unknown error'
        loadedPlugin.status = 'error'
        loadedPlugin.error = errorMessage
        console.error(`[Plugin] Failed to load plugin ${plugin.id}:`, err)
      }

      return loadedPlugin
    },
    [registerFieldRenderer, registerPageComponent, getLocale, getThemeMode]
  )

  /**
   * Unload a plugin
   * Requirement 12.8: Call plugin lifecycle hooks (onUnload)
   */
  const unloadPlugin = useCallback(async (plugin: Plugin): Promise<void> => {
    try {
      if (plugin.onUnload) {
        console.info(`[Plugin] Unloading plugin: ${plugin.id}`)
        await plugin.onUnload()
        console.info(`[Plugin] Successfully unloaded plugin: ${plugin.id}`)
      }
    } catch (err) {
      console.error(`[Plugin] Error unloading plugin ${plugin.id}:`, err)
    }
  }, [])

  /**
   * Initialize all plugins
   * Requirement 12.1: Load configured plugins on startup
   * Requirement 12.7: Continue loading other plugins if one fails
   */
  const initializePlugins = useCallback(async () => {
    if (plugins.length === 0) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setErrors([])

    const results: LoadedPlugin[] = []
    const loadErrors: Array<{ pluginId: string; error: string }> = []

    // Load plugins sequentially to maintain order and prevent race conditions
    for (const plugin of plugins) {
      const result = await loadPlugin(plugin)
      results.push(result)

      if (result.status === 'error' && result.error) {
        loadErrors.push({ pluginId: plugin.id, error: result.error })
      }
    }

    setLoadedPlugins(results)
    setErrors(loadErrors)
    setIsLoading(false)

    const successCount = results.filter((r) => r.status === 'loaded').length
    const failCount = results.filter((r) => r.status === 'error').length
    console.info(
      `[Plugin] Plugin initialization complete: ${successCount} loaded, ${failCount} failed`
    )
  }, [plugins, loadPlugin])

  /**
   * Initialize plugins on mount
   */
  useEffect(() => {
    // Prevent double initialization in React StrictMode
    if (initializedRef.current) {
      return
    }
    initializedRef.current = true

    initializePlugins()

    // Cleanup: unload plugins on unmount
    return () => {
      const cleanup = async () => {
        for (const loadedPlugin of loadedPlugins) {
          if (loadedPlugin.status === 'loaded') {
            await unloadPlugin(loadedPlugin.plugin)
          }
        }
      }
      cleanup()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo<PluginContextValue>(
    () => ({
      fieldRenderers,
      pageComponents,
      registerFieldRenderer,
      registerPageComponent,
      getFieldRenderer,
      getPageComponent,
      plugins: loadedPlugins,
      isLoading,
      errors,
    }),
    [
      fieldRenderers,
      pageComponents,
      registerFieldRenderer,
      registerPageComponent,
      getFieldRenderer,
      getPageComponent,
      loadedPlugins,
      isLoading,
      errors,
    ]
  )

  return <PluginContext.Provider value={contextValue}>{children}</PluginContext.Provider>
}

/**
 * Hook to access plugin context
 *
 * @throws Error if used outside of PluginProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { fieldRenderers, getFieldRenderer, isLoading } = usePlugins();
 *
 *   if (isLoading) return <LoadingSpinner />;
 *
 *   const CustomRenderer = getFieldRenderer('custom-type');
 *   if (CustomRenderer) {
 *     return <CustomRenderer name="field" value={value} onChange={onChange} />;
 *   }
 *
 *   return <DefaultRenderer />;
 * }
 * ```
 */
export function usePlugins(): PluginContextValue {
  const context = useContext(PluginContext)
  if (context === undefined) {
    throw new Error('usePlugins must be used within a PluginProvider')
  }
  return context
}

// Export the context for testing purposes
export { PluginContext }
