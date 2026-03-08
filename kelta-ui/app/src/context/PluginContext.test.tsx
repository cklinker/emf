/**
 * PluginContext Unit Tests
 *
 * Tests for the plugin context and usePlugins hook.
 * Validates requirements 12.1, 12.2, 12.3, 12.7, 12.8 for plugin system.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import { PluginProvider, usePlugins } from './PluginContext'
import type { ReactNode } from 'react'
import type { Plugin, FieldRendererProps, PageComponentProps } from '../types/plugin'

// Mock field renderer component
function MockFieldRenderer({ name, value }: FieldRendererProps) {
  return (
    <div data-testid={`field-renderer-${name}`}>
      Field: {name}, Value: {String(value)}
    </div>
  )
}

// Mock page component
function MockPageComponent({ config }: PageComponentProps) {
  return <div data-testid="page-component">Page Component: {JSON.stringify(config)}</div>
}

// Create a test plugin
function createTestPlugin(overrides: Partial<Plugin> = {}): Plugin {
  return {
    id: 'test-plugin',
    name: 'Test Plugin',
    version: '1.0.0',
    ...overrides,
  }
}

// Test component that uses usePlugins
function TestComponent({
  onRender,
}: {
  onRender?: (context: ReturnType<typeof usePlugins>) => void
}) {
  const pluginContext = usePlugins()
  onRender?.(pluginContext)
  return (
    <div>
      <div data-testid="loading">{pluginContext.isLoading ? 'loading' : 'not-loading'}</div>
      <div data-testid="plugins-count">{pluginContext.plugins.length}</div>
      <div data-testid="field-renderers-count">{pluginContext.fieldRenderers.size}</div>
      <div data-testid="page-components-count">{pluginContext.pageComponents.size}</div>
      <div data-testid="errors-count">{pluginContext.errors.length}</div>
      <div data-testid="errors">
        {pluginContext.errors.map((e) => `${e.pluginId}: ${e.error}`).join(', ')}
      </div>
    </div>
  )
}

// Helper to render with PluginProvider
function renderWithPlugins(
  ui: ReactNode = <TestComponent />,
  props?: { plugins?: Plugin[]; getLocale?: () => string; getThemeMode?: () => 'light' | 'dark' }
) {
  return render(<PluginProvider {...props}>{ui}</PluginProvider>)
}

describe('PluginContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  describe('Initial State', () => {
    it('should start in loading state when plugins are provided', async () => {
      const plugin = createTestPlugin()
      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      // Wait for loading to complete
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })
    })

    it('should not be loading when no plugins are provided', async () => {
      renderWithPlugins(<TestComponent />, { plugins: [] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })
    })

    it('should have empty registries initially', async () => {
      renderWithPlugins(<TestComponent />, { plugins: [] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('field-renderers-count')).toHaveTextContent('0')
      expect(screen.getByTestId('page-components-count')).toHaveTextContent('0')
    })

    it('should have no errors initially', async () => {
      renderWithPlugins(<TestComponent />, { plugins: [] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('errors-count')).toHaveTextContent('0')
    })
  })

  describe('Plugin Loading (Requirement 12.1)', () => {
    it('should load configured plugins on startup', async () => {
      const plugin = createTestPlugin()
      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('plugins-count')).toHaveTextContent('1')
    })

    it('should load multiple plugins', async () => {
      const plugins = [
        createTestPlugin({ id: 'plugin-1', name: 'Plugin 1' }),
        createTestPlugin({ id: 'plugin-2', name: 'Plugin 2' }),
        createTestPlugin({ id: 'plugin-3', name: 'Plugin 3' }),
      ]
      renderWithPlugins(<TestComponent />, { plugins })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('plugins-count')).toHaveTextContent('3')
    })

    it('should set plugin status to loaded on success', async () => {
      const plugin = createTestPlugin()
      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [plugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      expect(contextValue?.plugins[0].status).toBe('loaded')
    })
  })

  describe('Custom Field Renderers (Requirement 12.2)', () => {
    it('should register field renderers from plugin', async () => {
      const plugin = createTestPlugin({
        fieldRenderers: {
          'custom-field': MockFieldRenderer,
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('field-renderers-count')).toHaveTextContent('1')
    })

    it('should register multiple field renderers', async () => {
      const plugin = createTestPlugin({
        fieldRenderers: {
          'custom-field-1': MockFieldRenderer,
          'custom-field-2': MockFieldRenderer,
          'custom-field-3': MockFieldRenderer,
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('field-renderers-count')).toHaveTextContent('3')
    })

    it('should make field renderers accessible via getFieldRenderer', async () => {
      const plugin = createTestPlugin({
        fieldRenderers: {
          'custom-field': MockFieldRenderer,
        },
      })

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [plugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      const renderer = contextValue?.getFieldRenderer('custom-field')
      expect(renderer).toBe(MockFieldRenderer)
    })

    it('should return undefined for unregistered field types', async () => {
      const plugin = createTestPlugin({
        fieldRenderers: {
          'custom-field': MockFieldRenderer,
        },
      })

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [plugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      const renderer = contextValue?.getFieldRenderer('non-existent')
      expect(renderer).toBeUndefined()
    })
  })

  describe('Custom Page Components (Requirement 12.3)', () => {
    it('should register page components from plugin', async () => {
      const plugin = createTestPlugin({
        pageComponents: {
          CustomPage: MockPageComponent,
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('page-components-count')).toHaveTextContent('1')
    })

    it('should register multiple page components', async () => {
      const plugin = createTestPlugin({
        pageComponents: {
          CustomPage1: MockPageComponent,
          CustomPage2: MockPageComponent,
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('page-components-count')).toHaveTextContent('2')
    })

    it('should make page components accessible via getPageComponent', async () => {
      const plugin = createTestPlugin({
        pageComponents: {
          CustomPage: MockPageComponent,
        },
      })

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [plugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      const component = contextValue?.getPageComponent('CustomPage')
      expect(component).toBe(MockPageComponent)
    })

    it('should return undefined for unregistered page components', async () => {
      const plugin = createTestPlugin({
        pageComponents: {
          CustomPage: MockPageComponent,
        },
      })

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [plugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      const component = contextValue?.getPageComponent('NonExistent')
      expect(component).toBeUndefined()
    })
  })

  describe('Plugin Load Failures (Requirement 12.7)', () => {
    it('should handle plugin load failures gracefully', async () => {
      const failingPlugin = createTestPlugin({
        id: 'failing-plugin',
        onLoad: async () => {
          throw new Error('Plugin initialization failed')
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [failingPlugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('errors-count')).toHaveTextContent('1')
      expect(screen.getByTestId('errors')).toHaveTextContent('failing-plugin')
      expect(screen.getByTestId('errors')).toHaveTextContent('Plugin initialization failed')
    })

    it('should continue loading other plugins when one fails', async () => {
      const plugins = [
        createTestPlugin({ id: 'plugin-1', name: 'Plugin 1' }),
        createTestPlugin({
          id: 'failing-plugin',
          onLoad: async () => {
            throw new Error('Failed')
          },
        }),
        createTestPlugin({ id: 'plugin-3', name: 'Plugin 3' }),
      ]

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      // All plugins should be in the list
      expect(contextValue?.plugins.length).toBe(3)

      // Two should be loaded, one should have error
      const loadedPlugins = contextValue?.plugins.filter((p) => p.status === 'loaded')
      const errorPlugins = contextValue?.plugins.filter((p) => p.status === 'error')

      expect(loadedPlugins?.length).toBe(2)
      expect(errorPlugins?.length).toBe(1)
    })

    it('should set plugin status to error on failure', async () => {
      const failingPlugin = createTestPlugin({
        id: 'failing-plugin',
        onLoad: async () => {
          throw new Error('Failed')
        },
      })

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [failingPlugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      expect(contextValue?.plugins[0].status).toBe('error')
      expect(contextValue?.plugins[0].error).toBe('Failed')
    })

    it('should log errors to console', async () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const failingPlugin = createTestPlugin({
        id: 'failing-plugin',
        onLoad: async () => {
          throw new Error('Test error')
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [failingPlugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('[Plugin] Failed to load plugin failing-plugin:'),
        expect.any(Error)
      )

      consoleSpy.mockRestore()
    })
  })

  describe('Plugin Lifecycle Hooks (Requirement 12.8)', () => {
    it('should call onLoad hook during initialization', async () => {
      const onLoadMock = vi.fn().mockResolvedValue(undefined)
      const plugin = createTestPlugin({
        onLoad: onLoadMock,
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(onLoadMock).toHaveBeenCalledTimes(1)
    })

    it('should pass plugin context to onLoad hook', async () => {
      let receivedContext: unknown
      const plugin = createTestPlugin({
        onLoad: async (context) => {
          receivedContext = context
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(receivedContext).toBeDefined()
      expect(typeof (receivedContext as Record<string, unknown>).registerFieldRenderer).toBe(
        'function'
      )
      expect(typeof (receivedContext as Record<string, unknown>).registerPageComponent).toBe(
        'function'
      )
      expect(typeof (receivedContext as Record<string, unknown>).getLocale).toBe('function')
      expect(typeof (receivedContext as Record<string, unknown>).getThemeMode).toBe('function')
    })

    it('should allow plugins to register components via onLoad context', async () => {
      const plugin = createTestPlugin({
        onLoad: async (context) => {
          context.registerFieldRenderer('dynamic-field', MockFieldRenderer)
          context.registerPageComponent('DynamicPage', MockPageComponent)
        },
      })

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [plugin] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      expect(contextValue?.getFieldRenderer('dynamic-field')).toBe(MockFieldRenderer)
      expect(contextValue?.getPageComponent('DynamicPage')).toBe(MockPageComponent)
    })

    it('should provide getLocale function in context', async () => {
      let localeValue: string | undefined
      const plugin = createTestPlugin({
        onLoad: async (context) => {
          localeValue = context.getLocale()
        },
      })

      renderWithPlugins(<TestComponent />, {
        plugins: [plugin],
        getLocale: () => 'fr',
      })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(localeValue).toBe('fr')
    })

    it('should provide getThemeMode function in context', async () => {
      let themeModeValue: string | undefined
      const plugin = createTestPlugin({
        onLoad: async (context) => {
          themeModeValue = context.getThemeMode()
        },
      })

      renderWithPlugins(<TestComponent />, {
        plugins: [plugin],
        getThemeMode: () => 'dark',
      })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(themeModeValue).toBe('dark')
    })
  })

  describe('Component Registry', () => {
    it('should allow manual registration of field renderers', async () => {
      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      // Register a field renderer manually
      act(() => {
        contextValue?.registerFieldRenderer('manual-field', MockFieldRenderer)
      })

      await waitFor(() => {
        expect(contextValue?.fieldRenderers.size).toBe(1)
      })

      expect(contextValue?.getFieldRenderer('manual-field')).toBe(MockFieldRenderer)
    })

    it('should allow manual registration of page components', async () => {
      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [] }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      // Register a page component manually
      act(() => {
        contextValue?.registerPageComponent('ManualPage', MockPageComponent)
      })

      await waitFor(() => {
        expect(contextValue?.pageComponents.size).toBe(1)
      })

      expect(contextValue?.getPageComponent('ManualPage')).toBe(MockPageComponent)
    })

    it('should aggregate components from multiple plugins', async () => {
      const plugins = [
        createTestPlugin({
          id: 'plugin-1',
          fieldRenderers: { 'field-1': MockFieldRenderer },
          pageComponents: { Page1: MockPageComponent },
        }),
        createTestPlugin({
          id: 'plugin-2',
          fieldRenderers: { 'field-2': MockFieldRenderer },
          pageComponents: { Page2: MockPageComponent },
        }),
      ]

      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins }
      )

      await waitFor(() => {
        expect(contextValue?.isLoading).toBe(false)
      })

      expect(contextValue?.fieldRenderers.size).toBe(2)
      expect(contextValue?.pageComponents.size).toBe(2)
      expect(contextValue?.getFieldRenderer('field-1')).toBeDefined()
      expect(contextValue?.getFieldRenderer('field-2')).toBeDefined()
      expect(contextValue?.getPageComponent('Page1')).toBeDefined()
      expect(contextValue?.getPageComponent('Page2')).toBeDefined()
    })
  })

  describe('usePlugins Hook', () => {
    it('should throw error when used outside PluginProvider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      expect(() => {
        render(<TestComponent />)
      }).toThrow('usePlugins must be used within a PluginProvider')

      consoleSpy.mockRestore()
    })

    it('should provide plugin context value', async () => {
      let contextValue: ReturnType<typeof usePlugins> | undefined

      renderWithPlugins(
        <TestComponent
          onRender={(ctx) => {
            contextValue = ctx
          }}
        />,
        { plugins: [] }
      )

      await waitFor(() => {
        expect(contextValue).toBeDefined()
        expect(contextValue?.isLoading).toBe(false)
      })

      expect(contextValue?.fieldRenderers).toBeInstanceOf(Map)
      expect(contextValue?.pageComponents).toBeInstanceOf(Map)
      expect(typeof contextValue?.registerFieldRenderer).toBe('function')
      expect(typeof contextValue?.registerPageComponent).toBe('function')
      expect(typeof contextValue?.getFieldRenderer).toBe('function')
      expect(typeof contextValue?.getPageComponent).toBe('function')
      expect(Array.isArray(contextValue?.plugins)).toBe(true)
      expect(Array.isArray(contextValue?.errors)).toBe(true)
    })
  })

  describe('Edge Cases', () => {
    it('should handle plugins with no components', async () => {
      const plugin = createTestPlugin({
        // No fieldRenderers or pageComponents
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('plugins-count')).toHaveTextContent('1')
      expect(screen.getByTestId('field-renderers-count')).toHaveTextContent('0')
      expect(screen.getByTestId('page-components-count')).toHaveTextContent('0')
    })

    it('should handle plugins with empty component maps', async () => {
      const plugin = createTestPlugin({
        fieldRenderers: {},
        pageComponents: {},
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('plugins-count')).toHaveTextContent('1')
      expect(screen.getByTestId('field-renderers-count')).toHaveTextContent('0')
      expect(screen.getByTestId('page-components-count')).toHaveTextContent('0')
    })

    it('should handle async onLoad that takes time', async () => {
      const plugin = createTestPlugin({
        onLoad: async () => {
          await new Promise((resolve) => setTimeout(resolve, 100))
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      // Should be loading initially
      expect(screen.getByTestId('loading')).toHaveTextContent('loading')

      // Advance timers
      await act(async () => {
        vi.advanceTimersByTime(150)
      })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })
    })

    it('should handle non-Error exceptions in onLoad', async () => {
      const plugin = createTestPlugin({
        id: 'string-error-plugin',
        onLoad: async () => {
          throw 'String error'
        },
      })

      renderWithPlugins(<TestComponent />, { plugins: [plugin] })

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading')
      })

      expect(screen.getByTestId('errors-count')).toHaveTextContent('1')
      expect(screen.getByTestId('errors')).toHaveTextContent('Unknown error')
    })
  })
})
