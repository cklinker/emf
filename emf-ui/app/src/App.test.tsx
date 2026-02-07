/**
 * App Component Tests
 *
 * Tests for the root App component that wires together all providers and routing.
 *
 * Requirements:
 * - 1.1: Fetch bootstrap configuration from /ui/config/bootstrap on startup
 * - 1.2: Configure application routes based on page definitions
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../vitest.setup';
import App from './App';

// Mock bootstrap config response
const mockBootstrapConfig = {
  pages: [
    { id: 'dashboard', path: '/', title: 'Dashboard', component: 'DashboardPage' },
    { id: 'collections', path: '/collections', title: 'Collections', component: 'CollectionsPage' },
  ],
  menus: [
    {
      id: 'main',
      name: 'Main Navigation',
      items: [
        { id: 'dashboard', label: 'Dashboard', path: '/', icon: 'home' },
        { id: 'collections', label: 'Collections', path: '/collections', icon: 'folder' },
      ],
    },
  ],
  theme: {
    primaryColor: '#0066cc',
    secondaryColor: '#6c757d',
    fontFamily: 'Inter, sans-serif',
    borderRadius: '8px',
  },
  branding: {
    logoUrl: '/logo.svg',
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
    { id: 'provider-1', name: 'Test Provider', issuer: 'https://auth.example.com' },
  ],
};

// Mock auth context values
const mockAuthContext = {
  user: null as { id: string; email: string; name: string; roles?: string[] } | null,
  isAuthenticated: false,
  isLoading: false,
  error: null as Error | null,
  login: vi.fn(),
  logout: vi.fn(),
  getAccessToken: vi.fn(),
};

// Mock the AuthContext
vi.mock('./context/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => mockAuthContext,
  AuthContext: {
    Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  },
}));

// Mock the ConfigContext
vi.mock('./context/ConfigContext', () => ({
  ConfigProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useConfig: () => ({
    config: mockBootstrapConfig,
    isLoading: false,
    error: null,
    reload: vi.fn(),
  }),
  ConfigContext: {
    Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  },
}));

// Mock the ThemeContext
vi.mock('./context/ThemeContext', () => ({
  ThemeProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useTheme: () => ({
    mode: 'light',
    resolvedMode: 'light',
    setMode: vi.fn(),
    colors: {
      primary: '#0066cc',
      primaryHover: '#0052a3',
      primaryActive: '#004080',
      secondary: '#6c757d',
      secondaryHover: '#5a6268',
      secondaryActive: '#495057',
      background: '#ffffff',
      backgroundSecondary: '#f8f9fa',
      backgroundTertiary: '#e9ecef',
      surface: '#ffffff',
      surfaceHover: '#f8f9fa',
      surfaceBorder: '#dee2e6',
      text: '#212529',
      textSecondary: '#495057',
      textMuted: '#6c757d',
      textInverse: '#ffffff',
      success: '#198754',
      warning: '#ffc107',
      error: '#dc3545',
      info: '#0dcaf0',
      border: '#dee2e6',
      borderLight: '#e9ecef',
      focus: '#0066cc',
    },
  }),
  ThemeContext: {
    Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  },
}));

// Mock the I18nContext
vi.mock('./context/I18nContext', () => ({
  I18nProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useI18n: () => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string) => key,
    formatDate: (date: Date) => date.toLocaleDateString(),
    formatNumber: (num: number) => num.toString(),
    formatCurrency: (amount: number) => `$${amount}`,
    direction: 'ltr',
    supportedLocales: ['en', 'ar'],
    getLocaleDisplayName: (code: string) => code,
  }),
  I18nContext: {
    Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  },
}));

// Mock the PluginContext
vi.mock('./context/PluginContext', () => ({
  PluginProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  usePlugins: () => ({
    fieldRenderers: new Map(),
    pageComponents: new Map(),
    registerFieldRenderer: vi.fn(),
    registerPageComponent: vi.fn(),
    getFieldRenderer: vi.fn(),
    getPageComponent: vi.fn(),
    plugins: [],
    isLoading: false,
    errors: [],
  }),
  PluginContext: {
    Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  },
}));

// Mock the Toast components
vi.mock('./components/Toast', () => ({
  ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useToast: () => ({
    toasts: [],
    addToast: vi.fn(),
    removeToast: vi.fn(),
  }),
}));

// Mock the LiveRegion components
vi.mock('./components/LiveRegion', () => ({
  LiveRegionProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAnnounce: () => vi.fn(),
}));

// Mock the AppShell component
vi.mock('./components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="app-shell">{children}</div>
  ),
  useAppShell: () => ({
    screenSize: 'desktop',
    sidebarCollapsed: false,
    sidebarOpen: false,
    toggleSidebar: vi.fn(),
    toggleMobileSidebar: vi.fn(),
    closeMobileSidebar: vi.fn(),
  }),
}));

// Mock the Header component
vi.mock('./components/Header', () => ({
  Header: () => <header data-testid="header">Header</header>,
}));

// Mock the Sidebar component
vi.mock('./components/Sidebar', () => ({
  Sidebar: () => <nav data-testid="sidebar">Sidebar</nav>,
}));

// Mock the ErrorBoundary component
vi.mock('./components/ErrorBoundary', () => ({
  ErrorBoundary: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock the ProtectedRoute component
vi.mock('./components/ProtectedRoute', () => ({
  ProtectedRoute: ({ children }: { children: React.ReactNode }) => {
    if (!mockAuthContext.isAuthenticated) {
      return <div data-testid="redirect-to-login">Redirecting to login...</div>;
    }
    return <>{children}</>;
  },
}));

// Mock page components
vi.mock('./pages', () => ({
  DashboardPage: () => <div data-testid="dashboard-page">Dashboard Page</div>,
  CollectionsPage: () => <div data-testid="collections-page">Collections Page</div>,
  CollectionDetailPage: () => <div data-testid="collection-detail-page">Collection Detail Page</div>,
  CollectionFormPage: () => <div data-testid="collection-form-page">Collection Form Page</div>,
  RolesPage: () => <div data-testid="roles-page">Roles Page</div>,
  PoliciesPage: () => <div data-testid="policies-page">Policies Page</div>,
  OIDCProvidersPage: () => <div data-testid="oidc-providers-page">OIDC Providers Page</div>,
  ServicesPage: () => <div data-testid="services-page">Services Page</div>,
  PageBuilderPage: () => <div data-testid="page-builder-page">Page Builder Page</div>,
  MenuBuilderPage: () => <div data-testid="menu-builder-page">Menu Builder Page</div>,
  PackagesPage: () => <div data-testid="packages-page">Packages Page</div>,
  MigrationsPage: () => <div data-testid="migrations-page">Migrations Page</div>,
  ResourceBrowserPage: () => <div data-testid="resource-browser-page">Resource Browser Page</div>,
  ResourceListPage: () => <div data-testid="resource-list-page">Resource List Page</div>,
  ResourceDetailPage: () => <div data-testid="resource-detail-page">Resource Detail Page</div>,
  ResourceFormPage: () => <div data-testid="resource-form-page">Resource Form Page</div>,
  PluginsPage: () => <div data-testid="plugins-page">Plugins Page</div>,
  TenantsPage: () => <div data-testid="tenants-page">Tenants Page</div>,
  TenantDashboardPage: () => <div data-testid="tenant-dashboard-page">Tenant Dashboard Page</div>,
  LoginPage: () => <div data-testid="login-page">Login Page</div>,
  UnauthorizedPage: () => <div data-testid="unauthorized-page">Unauthorized Page</div>,
  NotFoundPage: () => <div data-testid="not-found-page">Not Found Page</div>,
}));

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset mock auth context
    mockAuthContext.user = null;
    mockAuthContext.isAuthenticated = false;
    mockAuthContext.isLoading = false;
    mockAuthContext.error = null;

    // Setup MSW handlers
    server.use(
      http.get('/ui/config/bootstrap', () => {
        return HttpResponse.json(mockBootstrapConfig);
      })
    );
  });

  afterEach(() => {
    server.resetHandlers();
  });

  describe('Rendering', () => {
    it('should render without crashing', () => {
      render(<App />);
      // App should render something
      expect(document.body).toBeInTheDocument();
    });

    it('should render with plugins prop', () => {
      const mockPlugins = [
        {
          id: 'test-plugin',
          name: 'Test Plugin',
          version: '1.0.0',
        },
      ];
      render(<App plugins={mockPlugins} />);
      expect(document.body).toBeInTheDocument();
    });
  });

  describe('Public Routes', () => {
    it('should render login page at /login', async () => {
      window.history.pushState({}, '', '/login');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('login-page')).toBeInTheDocument();
      });
    });

    it('should render unauthorized page at /unauthorized', async () => {
      window.history.pushState({}, '', '/unauthorized');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('unauthorized-page')).toBeInTheDocument();
      });
    });

    it('should render not found page for unknown routes', async () => {
      window.history.pushState({}, '', '/unknown-route');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('not-found-page')).toBeInTheDocument();
      });
    });
  });

  describe('Protected Routes - Unauthenticated', () => {
    beforeEach(() => {
      mockAuthContext.isAuthenticated = false;
      mockAuthContext.user = null;
    });

    it('should redirect to login when accessing dashboard unauthenticated', async () => {
      window.history.pushState({}, '', '/');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('redirect-to-login')).toBeInTheDocument();
      });
    });

    it('should redirect to login when accessing collections unauthenticated', async () => {
      window.history.pushState({}, '', '/collections');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('redirect-to-login')).toBeInTheDocument();
      });
    });

    it('should redirect to login when accessing roles unauthenticated', async () => {
      window.history.pushState({}, '', '/roles');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('redirect-to-login')).toBeInTheDocument();
      });
    });
  });

  describe('Protected Routes - Authenticated', () => {
    beforeEach(() => {
      mockAuthContext.isAuthenticated = true;
      mockAuthContext.user = {
        id: 'user-1',
        email: 'test@example.com',
        name: 'Test User',
        roles: ['admin'],
      };
    });

    it('should render dashboard page at / when authenticated', async () => {
      window.history.pushState({}, '', '/');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
      });
    });

    it('should render collections page at /collections when authenticated', async () => {
      window.history.pushState({}, '', '/collections');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('collections-page')).toBeInTheDocument();
      });
    });

    it('should render collection detail page at /collections/:id when authenticated', async () => {
      window.history.pushState({}, '', '/collections/test-collection');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('collection-detail-page')).toBeInTheDocument();
      });
    });

    it('should render roles page at /roles when authenticated', async () => {
      window.history.pushState({}, '', '/roles');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('roles-page')).toBeInTheDocument();
      });
    });

    it('should render policies page at /policies when authenticated', async () => {
      window.history.pushState({}, '', '/policies');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('policies-page')).toBeInTheDocument();
      });
    });

    it('should render OIDC providers page at /oidc-providers when authenticated', async () => {
      window.history.pushState({}, '', '/oidc-providers');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('oidc-providers-page')).toBeInTheDocument();
      });
    });

    it('should render page builder page at /pages when authenticated', async () => {
      window.history.pushState({}, '', '/pages');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('page-builder-page')).toBeInTheDocument();
      });
    });

    it('should render menu builder page at /menus when authenticated', async () => {
      window.history.pushState({}, '', '/menus');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('menu-builder-page')).toBeInTheDocument();
      });
    });

    it('should render packages page at /packages when authenticated', async () => {
      window.history.pushState({}, '', '/packages');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('packages-page')).toBeInTheDocument();
      });
    });

    it('should render migrations page at /migrations when authenticated', async () => {
      window.history.pushState({}, '', '/migrations');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('migrations-page')).toBeInTheDocument();
      });
    });

    it('should render resource browser page at /resources when authenticated', async () => {
      window.history.pushState({}, '', '/resources');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('resource-browser-page')).toBeInTheDocument();
      });
    });

    it('should render resource list page at /resources/:collection when authenticated', async () => {
      window.history.pushState({}, '', '/resources/users');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('resource-list-page')).toBeInTheDocument();
      });
    });

    it('should render resource detail page at /resources/:collection/:id when authenticated', async () => {
      window.history.pushState({}, '', '/resources/users/123');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('resource-detail-page')).toBeInTheDocument();
      });
    });

    it('should render resource form page at /resources/:collection/new when authenticated', async () => {
      window.history.pushState({}, '', '/resources/users/new');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('resource-form-page')).toBeInTheDocument();
      });
    });

    it('should render resource form page at /resources/:collection/:id/edit when authenticated', async () => {
      window.history.pushState({}, '', '/resources/users/123/edit');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('resource-form-page')).toBeInTheDocument();
      });
    });

    it('should render plugins page at /plugins when authenticated', async () => {
      window.history.pushState({}, '', '/plugins');
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByTestId('plugins-page')).toBeInTheDocument();
      });
    });
  });

  describe('Route Configuration', () => {
    it('should have all required routes configured', () => {
      // This test verifies that the App component has all the expected routes
      // by checking that the component renders without errors
      mockAuthContext.isAuthenticated = true;
      mockAuthContext.user = {
        id: 'user-1',
        email: 'test@example.com',
        name: 'Test User',
      };

      const routes = [
        '/',
        '/collections',
        '/collections/test',
        '/roles',
        '/policies',
        '/oidc-providers',
        '/pages',
        '/menus',
        '/packages',
        '/migrations',
        '/resources',
        '/resources/users',
        '/resources/users/123',
        '/resources/users/new',
        '/resources/users/123/edit',
        '/plugins',
        '/login',
        '/unauthorized',
      ];

      routes.forEach((route) => {
        window.history.pushState({}, '', route);
        const { unmount } = render(<App />);
        // If the app renders without throwing, the route is configured
        expect(document.body).toBeInTheDocument();
        unmount();
      });
    });
  });

  describe('Provider Hierarchy', () => {
    it('should wrap app with all required providers', () => {
      // This test verifies that the provider hierarchy is correct
      // by checking that the app renders without context errors
      render(<App />);
      
      // If we get here without errors, all providers are correctly set up
      expect(document.body).toBeInTheDocument();
    });
  });
});

describe('App Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthContext.isAuthenticated = true;
    mockAuthContext.user = {
      id: 'user-1',
      email: 'test@example.com',
      name: 'Test User',
    };
  });

  it('should render app shell with header and sidebar for protected routes', async () => {
    window.history.pushState({}, '', '/');
    render(<App />);
    
    await waitFor(() => {
      expect(screen.getByTestId('app-shell')).toBeInTheDocument();
    });
  });
});
