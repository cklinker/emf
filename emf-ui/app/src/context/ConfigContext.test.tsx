/**
 * ConfigContext Unit Tests
 *
 * Tests for the configuration context and useConfig hook.
 * Validates requirements 1.1-1.8 for bootstrap configuration.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfigProvider, useConfig } from './ConfigContext';
import type { ReactNode } from 'react';
import type { BootstrapConfig } from '../types/config';

// Store original fetch
const originalFetch = global.fetch;

// Valid mock bootstrap config
const mockBootstrapConfig: BootstrapConfig = {
  pages: [
    {
      id: 'page-1',
      path: '/dashboard',
      title: 'Dashboard',
      component: 'DashboardPage',
    },
    {
      id: 'page-2',
      path: '/collections',
      title: 'Collections',
      component: 'CollectionsPage',
      policies: ['admin'],
    },
  ],
  menus: [
    {
      id: 'main-menu',
      name: 'Main Menu',
      items: [
        { id: 'item-1', label: 'Dashboard', path: '/dashboard', icon: 'home' },
        { id: 'item-2', label: 'Collections', path: '/collections', icon: 'folder' },
      ],
    },
  ],
  theme: {
    primaryColor: '#1976d2',
    secondaryColor: '#dc004e',
    fontFamily: 'Roboto, sans-serif',
    borderRadius: '4px',
  },
  branding: {
    logoUrl: '/logo.png',
    applicationName: 'EMF Admin',
    faviconUrl: '/favicon.ico',
  },
  features: {
    enableBuilder: true,
    enableResourceBrowser: true,
    enablePackages: true,
    enableMigrations: true,
    enableDashboard: true,
  },
  oidcProviders: [
    {
      id: 'provider-1',
      name: 'Test Provider',
      issuer: 'https://auth.example.com',
    },
  ],
};

// Create mock fetch function
function createMockFetch(options: {
  config?: BootstrapConfig | Partial<BootstrapConfig>;
  shouldFail?: boolean;
  failStatus?: number;
  failStatusText?: string;
  invalidJson?: boolean;
  delay?: number;
} = {}) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : (input as Request).url;

    if (options.delay) {
      await new Promise((resolve) => setTimeout(resolve, options.delay));
    }

    if (url.includes('/ui/config/bootstrap')) {
      if (options.shouldFail) {
        return {
          ok: false,
          status: options.failStatus || 500,
          statusText: options.failStatusText || 'Internal Server Error',
        } as Response;
      }

      if (options.invalidJson) {
        return {
          ok: true,
          json: async () => {
            throw new SyntaxError('Unexpected token');
          },
        } as unknown as Response;
      }

      return {
        ok: true,
        json: async () => options.config || mockBootstrapConfig,
      } as Response;
    }

    return {
      ok: false,
      status: 404,
      statusText: 'Not Found',
    } as Response;
  });
}

// Test component that uses useConfig
function TestComponent({
  onRender,
}: {
  onRender?: (config: ReturnType<typeof useConfig>) => void;
}) {
  const configContext = useConfig();
  onRender?.(configContext);
  return (
    <div>
      <div data-testid="loading">{configContext.isLoading ? 'loading' : 'not-loading'}</div>
      <div data-testid="has-config">{configContext.config ? 'has-config' : 'no-config'}</div>
      <div data-testid="error">{configContext.error ? configContext.error.message : 'no-error'}</div>
      <div data-testid="app-name">
        {configContext.config?.branding.applicationName || 'no-app-name'}
      </div>
      <div data-testid="pages-count">
        {configContext.config?.pages.length ?? 'no-pages'}
      </div>
      <div data-testid="menus-count">
        {configContext.config?.menus.length ?? 'no-menus'}
      </div>
      <button onClick={() => configContext.reload()}>Reload</button>
    </div>
  );
}

// Helper to render with ConfigProvider
function renderWithConfig(
  ui: ReactNode = <TestComponent />,
  props?: { bootstrapEndpoint?: string; pollInterval?: number }
) {
  return render(<ConfigProvider {...props}>{ui}</ConfigProvider>);
}

describe('ConfigContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    global.fetch = createMockFetch();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    global.fetch = originalFetch;
  });

  describe('Initial State', () => {
    it('should start in loading state', async () => {
      renderWithConfig();

      // Initially loading
      expect(screen.getByTestId('loading')).toHaveTextContent('loading');

      // Wait for initialization to complete
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });
    });

    it('should have no config initially before fetch completes', () => {
      // Use a delayed fetch to capture initial state
      global.fetch = createMockFetch({ delay: 1000 });
      renderWithConfig();

      expect(screen.getByTestId('has-config')).toHaveTextContent('no-config');
    });

    it('should have no error initially', () => {
      global.fetch = createMockFetch({ delay: 1000 });
      renderWithConfig();

      expect(screen.getByTestId('error')).toHaveTextContent('no-error');
    });
  });

  describe('Fetch Bootstrap Configuration (Requirement 1.1)', () => {
    it('should fetch bootstrap config from /ui/config/bootstrap on mount', async () => {
      const mockFetch = createMockFetch();
      global.fetch = mockFetch;

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      // Verify fetch was called with correct endpoint
      expect(mockFetch).toHaveBeenCalledWith('/ui/config/bootstrap');
    });

    it('should use custom bootstrap endpoint when provided', async () => {
      const mockFetch = createMockFetch();
      global.fetch = mockFetch;

      renderWithConfig(<TestComponent />, {
        bootstrapEndpoint: '/custom/bootstrap',
      });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(mockFetch).toHaveBeenCalledWith('/custom/bootstrap');
    });

    it('should load config successfully', async () => {
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('has-config')).toHaveTextContent('has-config');
      expect(screen.getByTestId('error')).toHaveTextContent('no-error');
    });
  });

  describe('Configure Routes (Requirement 1.2)', () => {
    it('should provide page configurations for routing', async () => {
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('pages-count')).toHaveTextContent('2');
    });

    it('should include page path, title, and component in config', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const pages = configValue?.config?.pages;
      expect(pages).toBeDefined();
      expect(pages?.[0]).toMatchObject({
        id: 'page-1',
        path: '/dashboard',
        title: 'Dashboard',
        component: 'DashboardPage',
      });
    });
  });

  describe('Configure Menus (Requirement 1.3)', () => {
    it('should provide menu configurations for navigation', async () => {
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('menus-count')).toHaveTextContent('1');
    });

    it('should include menu items with labels and paths', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const menus = configValue?.config?.menus;
      expect(menus).toBeDefined();
      expect(menus?.[0].items).toHaveLength(2);
      expect(menus?.[0].items[0]).toMatchObject({
        label: 'Dashboard',
        path: '/dashboard',
        icon: 'home',
      });
    });
  });

  describe('Apply Theme (Requirement 1.4)', () => {
    it('should provide theme configuration', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const theme = configValue?.config?.theme;
      expect(theme).toBeDefined();
      expect(theme).toMatchObject({
        primaryColor: '#1976d2',
        secondaryColor: '#dc004e',
        fontFamily: 'Roboto, sans-serif',
        borderRadius: '4px',
      });
    });
  });

  describe('Apply Branding (Requirement 1.5)', () => {
    it('should provide branding configuration', async () => {
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('app-name')).toHaveTextContent('EMF Admin');
    });

    it('should include logo, app name, and favicon in branding', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const branding = configValue?.config?.branding;
      expect(branding).toBeDefined();
      expect(branding).toMatchObject({
        logoUrl: '/logo.png',
        applicationName: 'EMF Admin',
        faviconUrl: '/favicon.ico',
      });
    });
  });

  describe('Error Handling - Unavailable Endpoint (Requirement 1.6)', () => {
    it('should set error when bootstrap endpoint returns error status', async () => {
      global.fetch = createMockFetch({
        shouldFail: true,
        failStatus: 500,
        failStatusText: 'Internal Server Error',
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent(
        'Failed to fetch bootstrap configuration: 500 Internal Server Error'
      );
      expect(screen.getByTestId('has-config')).toHaveTextContent('no-config');
    });

    it('should set error when bootstrap endpoint returns 404', async () => {
      global.fetch = createMockFetch({
        shouldFail: true,
        failStatus: 404,
        failStatusText: 'Not Found',
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent(
        'Failed to fetch bootstrap configuration: 404 Not Found'
      );
    });

    it('should provide reload function for retry', async () => {
      let reloadCalled = false;
      global.fetch = createMockFetch({ shouldFail: true });

      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      expect(typeof configValue?.reload).toBe('function');

      // Now make fetch succeed and reload
      global.fetch = createMockFetch();

      await act(async () => {
        await configValue?.reload();
        reloadCalled = true;
      });

      expect(reloadCalled).toBe(true);
      expect(screen.getByTestId('has-config')).toHaveTextContent('has-config');
      expect(screen.getByTestId('error')).toHaveTextContent('no-error');
    });
  });

  describe('Error Handling - Invalid Config (Requirement 1.7)', () => {
    it('should set error when config is invalid JSON', async () => {
      global.fetch = createMockFetch({ invalidJson: true });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent(
        'Failed to parse bootstrap configuration: Invalid JSON response'
      );
    });

    it('should set error when config is missing pages array', async () => {
      global.fetch = createMockFetch({
        config: {
          menus: [],
          theme: mockBootstrapConfig.theme,
          branding: mockBootstrapConfig.branding,
          features: mockBootstrapConfig.features,
        } as unknown as BootstrapConfig,
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent('Invalid bootstrap configuration');
      expect(screen.getByTestId('error')).toHaveTextContent('pages');
    });

    it('should set error when config is missing theme', async () => {
      global.fetch = createMockFetch({
        config: {
          pages: [],
          menus: [],
          branding: mockBootstrapConfig.branding,
          features: mockBootstrapConfig.features,
        } as unknown as BootstrapConfig,
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent('Invalid bootstrap configuration');
      expect(screen.getByTestId('error')).toHaveTextContent('theme');
    });

    it('should set error when config is missing branding', async () => {
      global.fetch = createMockFetch({
        config: {
          pages: [],
          menus: [],
          theme: mockBootstrapConfig.theme,
          features: mockBootstrapConfig.features,
        } as unknown as BootstrapConfig,
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent('Invalid bootstrap configuration');
      expect(screen.getByTestId('error')).toHaveTextContent('branding');
    });

    it('should set error when config is missing features', async () => {
      global.fetch = createMockFetch({
        config: {
          pages: [],
          menus: [],
          theme: mockBootstrapConfig.theme,
          branding: mockBootstrapConfig.branding,
        } as unknown as BootstrapConfig,
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      expect(screen.getByTestId('error')).toHaveTextContent('Invalid bootstrap configuration');
      expect(screen.getByTestId('error')).toHaveTextContent('features');
    });

    it('should provide diagnostic information in error message', async () => {
      global.fetch = createMockFetch({
        config: {} as unknown as BootstrapConfig,
      });

      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      const errorText = screen.getByTestId('error').textContent;
      expect(errorText).toContain('Invalid bootstrap configuration');
      // Should list multiple missing fields
      expect(errorText).toContain('pages');
      expect(errorText).toContain('menus');
      expect(errorText).toContain('theme');
      expect(errorText).toContain('branding');
      expect(errorText).toContain('features');
    });
  });

  describe('Reload Configuration (Requirement 1.8)', () => {
    it('should provide reload function', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      expect(typeof configValue?.reload).toBe('function');
    });

    it('should reload config when reload is called', async () => {
      const mockFetch = createMockFetch();
      global.fetch = mockFetch;

      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      // Initial fetch
      expect(mockFetch).toHaveBeenCalledTimes(1);

      // Click reload button
      await user.click(screen.getByText('Reload'));

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledTimes(2);
      });
    });

    it('should update config after reload with new data', async () => {
      // Start with initial config
      global.fetch = createMockFetch();

      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('app-name')).toHaveTextContent('EMF Admin');
      });

      // Update fetch to return different config
      const updatedConfig: BootstrapConfig = {
        ...mockBootstrapConfig,
        branding: {
          ...mockBootstrapConfig.branding,
          applicationName: 'Updated App Name',
        },
      };
      global.fetch = createMockFetch({ config: updatedConfig });

      // Click reload
      await user.click(screen.getByText('Reload'));

      await waitFor(() => {
        expect(screen.getByTestId('app-name')).toHaveTextContent('Updated App Name');
      });
    });

    it('should clear error after successful reload', async () => {
      // Start with error
      global.fetch = createMockFetch({ shouldFail: true });

      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderWithConfig();

      await waitFor(() => {
        expect(screen.getByTestId('error')).not.toHaveTextContent('no-error');
      });

      // Make fetch succeed
      global.fetch = createMockFetch();

      // Click reload
      await user.click(screen.getByText('Reload'));

      await waitFor(() => {
        expect(screen.getByTestId('error')).toHaveTextContent('no-error');
        expect(screen.getByTestId('has-config')).toHaveTextContent('has-config');
      });
    });
  });

  describe('useConfig Hook', () => {
    it('should throw error when used outside ConfigProvider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        render(<TestComponent />);
      }).toThrow('useConfig must be used within a ConfigProvider');

      consoleSpy.mockRestore();
    });

    it('should provide config context value', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue).toBeDefined();
        expect(configValue?.isLoading).toBe(false);
      });

      expect(configValue?.config).not.toBeNull();
      expect(configValue?.error).toBeNull();
      expect(typeof configValue?.reload).toBe('function');
    });
  });

  describe('Feature Flags', () => {
    it('should provide feature flags in config', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const features = configValue?.config?.features;
      expect(features).toBeDefined();
      expect(features?.enableBuilder).toBe(true);
      expect(features?.enableResourceBrowser).toBe(true);
      expect(features?.enablePackages).toBe(true);
      expect(features?.enableMigrations).toBe(true);
      expect(features?.enableDashboard).toBe(true);
    });

    it('should handle disabled features', async () => {
      const configWithDisabledFeatures: BootstrapConfig = {
        ...mockBootstrapConfig,
        features: {
          enableBuilder: false,
          enableResourceBrowser: false,
          enablePackages: false,
          enableMigrations: false,
          enableDashboard: false,
        },
      };
      global.fetch = createMockFetch({ config: configWithDisabledFeatures });

      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const features = configValue?.config?.features;
      expect(features?.enableBuilder).toBe(false);
      expect(features?.enableResourceBrowser).toBe(false);
    });
  });

  describe('OIDC Providers', () => {
    it('should provide OIDC providers in config', async () => {
      let configValue: ReturnType<typeof useConfig> | undefined;

      renderWithConfig(
        <TestComponent
          onRender={(config) => {
            configValue = config;
          }}
        />
      );

      await waitFor(() => {
        expect(configValue?.isLoading).toBe(false);
      });

      const providers = configValue?.config?.oidcProviders;
      expect(providers).toBeDefined();
      expect(providers).toHaveLength(1);
      expect(providers?.[0]).toMatchObject({
        id: 'provider-1',
        name: 'Test Provider',
        issuer: 'https://auth.example.com',
      });
    });
  });

  describe('Polling for Config Changes', () => {
    it('should poll for config changes when pollInterval is set', async () => {
      const mockFetch = createMockFetch();
      global.fetch = mockFetch;

      renderWithConfig(<TestComponent />, { pollInterval: 5000 });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      // Initial fetch
      expect(mockFetch).toHaveBeenCalledTimes(1);

      // Advance time by poll interval
      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledTimes(2);
      });

      // Advance again
      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledTimes(3);
      });
    });

    it('should not poll when pollInterval is 0', async () => {
      const mockFetch = createMockFetch();
      global.fetch = mockFetch;

      renderWithConfig(<TestComponent />, { pollInterval: 0 });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      // Initial fetch
      expect(mockFetch).toHaveBeenCalledTimes(1);

      // Advance time
      await act(async () => {
        vi.advanceTimersByTime(10000);
      });

      // Should still be 1 call
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it('should not poll when in error state', async () => {
      const mockFetch = createMockFetch({ shouldFail: true });
      global.fetch = mockFetch;

      renderWithConfig(<TestComponent />, { pollInterval: 5000 });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('not-loading');
      });

      // Initial fetch (failed)
      expect(mockFetch).toHaveBeenCalledTimes(1);

      // Advance time
      await act(async () => {
        vi.advanceTimersByTime(10000);
      });

      // Should still be 1 call (no polling during error)
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });
});
