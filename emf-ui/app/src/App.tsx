/**
 * App Component
 *
 * The root component that initializes all providers and routing.
 * Wires together all context providers and configures React Router.
 *
 * Requirements:
 * - 1.1: Fetch bootstrap configuration from /control/ui-bootstrap on startup
 * - 1.2: Configure application routes based on page definitions
 * - 1.3: Configure navigation menus based on menu definitions
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 */

import React from 'react'
import { BrowserRouter, Routes, Route, Navigate, useNavigate, useLocation, Link } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'

// Context Providers
import { AuthProvider } from './context/AuthContext'
import { ApiProvider } from './context/ApiContext'
import { ConfigProvider, useConfig } from './context/ConfigContext'
import { ThemeProvider } from './context/ThemeContext'
import { I18nProvider } from './context/I18nContext'
import { PluginProvider } from './context/PluginContext'
import { TenantProvider, useTenant } from './context/TenantContext'
import { AppContextProvider } from './context/AppContext'
import { useAuth } from './context/AuthContext'

// Hooks
import { useGlobalShortcuts } from './hooks/useGlobalShortcuts'

// Components
import { ErrorBoundary } from './components/ErrorBoundary'
import { KeyboardShortcutsHelp } from './components/KeyboardShortcutsHelp/KeyboardShortcutsHelp'
import { ToastProvider } from './components/Toast'
import { LiveRegionProvider } from './components/LiveRegion'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Header } from './components/Header'
import { PageTransition } from './components/PageTransition'
import { PageLoader } from './components/PageLoader'
import {
  Breadcrumb,
  BreadcrumbList,
  BreadcrumbItem,
  BreadcrumbLink,
} from './components/ui/breadcrumb'

// Pages
import {
  DashboardPage,
  CollectionsPage,
  CollectionDetailPage,
  CollectionFormPage,
  CollectionWizardPage,
  OIDCProvidersPage,
  PageBuilderPage,
  MenuBuilderPage,
  PackagesPage,
  MigrationsPage,
  ResourceBrowserPage,
  ResourceListPage,
  ResourceDetailPage,
  ResourceFormPage,
  PluginsPage,
  UsersPage,
  UserDetailPage,
  SetupAuditTrailPage,
  GovernorLimitsPage,
  TenantsPage,
  TenantDashboardPage,
  PicklistsPage,
  PageLayoutsPage,
  ListViewsPage,
  ReportsPage,
  DashboardsPage,
  WorkflowRulesPage,
  ApprovalProcessesPage,
  FlowsPage,
  ScheduledJobsPage,
  EmailTemplatesPage,
  ScriptsPage,
  WebhooksPage,
  ConnectedAppsPage,
  BulkJobsPage,
  SetupHomePage,
  WorkersPage,
  LoginPage,
  UnauthorizedPage,
  NotFoundPage,
} from './pages'
import { NoTenantPage } from './pages/NoTenantPage/NoTenantPage'

// End-User Shell & Pages (lazy-loaded for code splitting)
import { EndUserShell } from './shells/EndUserShell'

const AppHomePage = React.lazy(() =>
  import('./pages/app/AppHomePage/AppHomePage').then((m) => ({ default: m.AppHomePage }))
)
const EndUserObjectListPage = React.lazy(() =>
  import('./pages/app/ObjectListPage/ObjectListPage').then((m) => ({ default: m.ObjectListPage }))
)
const EndUserObjectDetailPage = React.lazy(() =>
  import('./pages/app/ObjectDetailPage/ObjectDetailPage').then((m) => ({
    default: m.ObjectDetailPage,
  }))
)
const EndUserObjectFormPage = React.lazy(() =>
  import('./pages/app/ObjectFormPage/ObjectFormPage').then((m) => ({ default: m.ObjectFormPage }))
)
const GlobalSearchPage = React.lazy(() =>
  import('./pages/app/GlobalSearchPage/GlobalSearchPage').then((m) => ({
    default: m.GlobalSearchPage,
  }))
)
const EndUserCustomPage = React.lazy(() =>
  import('./pages/app/CustomPage/CustomPage').then((m) => ({ default: m.CustomPage }))
)

// Types
import type { Plugin } from './types/plugin'

// Create a QueryClient instance for TanStack Query
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      retry: 3,
      refetchOnWindowFocus: false,
    },
  },
})

/**
 * Props for the App component
 */
export interface AppProps {
  /** Optional plugins to load */
  plugins?: Plugin[]
}

/**
 * Auth callback page - shows loading while processing callback, error if it fails
 */
function AuthCallbackPage(): React.ReactElement {
  const { isLoading, error } = useAuth()
  const navigate = useNavigate()
  const { tenantBasePath } = useTenant()

  if (isLoading) {
    return <PageLoader fullPage message="Completing authentication..." />
  }

  if (error) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          padding: '2rem',
          textAlign: 'center',
          fontFamily: 'system-ui, -apple-system, sans-serif',
        }}
        role="alert"
      >
        <h1 style={{ margin: '0 0 1rem', fontSize: '1.5rem', fontWeight: 600 }}>
          Authentication Failed
        </h1>
        <p style={{ margin: '0 0 0.5rem', color: '#666', maxWidth: '500px' }}>{error.message}</p>
        <button
          onClick={() => {
            sessionStorage.removeItem('emf_auth_login_error')
            navigate(`${tenantBasePath}/login`)
          }}
          style={{
            marginTop: '1.5rem',
            padding: '0.75rem 1.5rem',
            fontSize: '1rem',
            fontWeight: 500,
            color: '#fff',
            backgroundColor: '#0066cc',
            border: 'none',
            borderRadius: '0.375rem',
            cursor: 'pointer',
          }}
        >
          Try Again
        </button>
      </div>
    )
  }

  return <PageLoader fullPage message="Completing authentication..." />
}

/**
 * Bootstrap error display component
 * Shows when the bootstrap API call fails with retry option
 */
function BootstrapError({
  error,
  onRetry,
}: {
  error: Error
  onRetry: () => void
}): React.ReactElement {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        padding: '2rem',
        textAlign: 'center',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
      role="alert"
      aria-live="assertive"
    >
      <div
        style={{
          marginBottom: '1rem',
        }}
        aria-hidden="true"
      >
        <AlertTriangle size={48} />
      </div>
      <h1 style={{ margin: '0 0 1rem', fontSize: '1.5rem', fontWeight: 600 }}>
        Unable to Load Application
      </h1>
      <p style={{ margin: '0 0 0.5rem', color: '#666', maxWidth: '400px' }}>
        Failed to connect to the server. Please check your connection and try again.
      </p>
      <p style={{ margin: '0 0 1.5rem', color: '#999', fontSize: '0.875rem' }}>{error.message}</p>
      <button
        onClick={onRetry}
        style={{
          padding: '0.75rem 1.5rem',
          fontSize: '1rem',
          fontWeight: 500,
          color: '#fff',
          backgroundColor: '#0066cc',
          border: 'none',
          borderRadius: '0.375rem',
          cursor: 'pointer',
        }}
      >
        Retry
      </button>
    </div>
  )
}

/**
 * Admin layout without sidebar — the Setup page acts as the navigation hub.
 * Uses a simple flex layout instead of AppShell to avoid the sidebar aside element.
 */
function AdminLayout({ children }: { children: React.ReactNode }): React.ReactElement {
  const { user, logout } = useAuth()
  const { config, isLoading: configLoading, error, reload } = useConfig()
  const { helpOpen, setHelpOpen } = useGlobalShortcuts()
  const location = useLocation()
  const { tenantSlug } = useTenant()

  const isSetupPage = location.pathname.endsWith('/setup')

  if (configLoading) {
    return <PageLoader fullPage message="Loading application..." />
  }

  if (error) {
    return <BootstrapError error={error} onRetry={reload} />
  }

  const branding = config?.branding ?? {
    logoUrl: '',
    applicationName: 'EMF Admin',
    faviconUrl: '',
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          height: '60px',
          flexShrink: 0,
          backgroundColor: 'var(--color-surface, #ffffff)',
          borderBottom: '1px solid var(--color-border, #e0e0e0)',
          display: 'flex',
          alignItems: 'center',
        }}
      >
        <Header branding={branding} user={user} onLogout={logout} />
      </div>
      {!isSetupPage && (
        <div
          style={{
            padding: '8px 24px',
            borderBottom: '1px solid var(--color-border, #e0e0e0)',
            backgroundColor: 'var(--color-surface, #ffffff)',
            flexShrink: 0,
          }}
        >
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link to={`/${tenantSlug}/setup`}>← Setup</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        </div>
      )}
      <main
        style={{
          flex: 1,
          overflowY: 'auto',
          overflowX: 'hidden',
          padding: '24px',
          backgroundColor: 'var(--color-background, #ffffff)',
        }}
      >
        <PageTransition type="fade" duration={200}>
          {children}
        </PageTransition>
      </main>
      <KeyboardShortcutsHelp isOpen={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  )
}

/**
 * Protected route wrapper for admin pages (no sidebar)
 */
function AdminPageRoute({
  children,
  requiredRoles,
  requiredPolicies,
}: {
  children: React.ReactNode
  requiredRoles?: string[]
  requiredPolicies?: string[]
}): React.ReactElement {
  const { tenantBasePath } = useTenant()

  return (
    <ProtectedRoute
      requiredRoles={requiredRoles}
      requiredPolicies={requiredPolicies}
      loginPath={`${tenantBasePath}/login`}
      unauthorizedPath={`${tenantBasePath}/unauthorized`}
    >
      <AdminLayout>{children}</AdminLayout>
    </ProtectedRoute>
  )
}

/**
 * Tenant-scoped application wrapper.
 * Wraps all providers that depend on the tenant slug from the URL.
 *
 * Provider hierarchy:
 * 1. TenantProvider - Tenant identity from URL slug
 * 2. AuthProvider - Authentication state and OIDC flow
 * 3. ApiProvider - Authenticated API client (slug-prefixed base URL)
 * 4. ConfigProvider - Bootstrap configuration
 * 5. ThemeProvider - Theme state and CSS custom properties
 * 6. I18nProvider - Internationalization
 * 7. PluginProvider - Plugin system
 * 8. ToastProvider - Toast notifications
 * 9. LiveRegionProvider - Screen reader announcements
 */
function TenantScopedApp({ plugins = [] }: { plugins?: Plugin[] }): React.ReactElement {
  const { tenantSlug, tenantBasePath } = useTenant()
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

  return (
    <AuthProvider
      redirectUri={window.location.origin + tenantBasePath + '/auth/callback'}
      postLogoutRedirectUri={window.location.origin + tenantBasePath}
    >
      <ApiProvider baseUrl={`${apiBaseUrl}/${tenantSlug}`}>
        <ConfigProvider>
          <ThemeProvider>
            <I18nProvider>
              <PluginProvider plugins={plugins}>
                <AppContextProvider>
                  <ToastProvider>
                    <LiveRegionProvider>
                      <TenantRoutes />
                    </LiveRegionProvider>
                  </ToastProvider>
                </AppContextProvider>
              </PluginProvider>
            </I18nProvider>
          </ThemeProvider>
        </ConfigProvider>
      </ApiProvider>
    </AuthProvider>
  )
}

/**
 * All tenant-scoped routes. Paths are relative to /:tenantSlug/.
 */
function TenantRoutes(): React.ReactElement {
  const { tenantSlug } = useTenant()
  return (
    <Routes>
      {/* Public routes */}
      <Route path="login" element={<LoginPage />} />
      <Route path="unauthorized" element={<UnauthorizedPage />} />

      {/* OAuth callback route */}
      <Route path="auth/callback" element={<AuthCallbackPage />} />

      {/* Root redirects to end-user app */}
      <Route
        path=""
        element={
          <ProtectedRoute
            loginPath={`/${tenantSlug}/login`}
            unauthorizedPath={`/${tenantSlug}/unauthorized`}
          >
            <Navigate to="app" replace />
          </ProtectedRoute>
        }
      />

      {/* System Health Dashboard */}
      <Route
        path="system-health"
        element={
          <AdminPageRoute>
            <DashboardPage />
          </AdminPageRoute>
        }
      />

      {/* Collections routes */}
      <Route
        path="collections"
        element={
          <AdminPageRoute>
            <CollectionsPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="collections/new"
        element={
          <AdminPageRoute>
            <CollectionWizardPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="collections/:id"
        element={
          <AdminPageRoute>
            <CollectionDetailPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="collections/:id/edit"
        element={
          <AdminPageRoute>
            <CollectionFormPage />
          </AdminPageRoute>
        }
      />

      {/* OIDC Providers route */}
      <Route
        path="oidc-providers"
        element={
          <AdminPageRoute>
            <OIDCProvidersPage />
          </AdminPageRoute>
        }
      />

      {/* Workers route */}
      <Route
        path="workers"
        element={
          <AdminPageRoute>
            <WorkersPage />
          </AdminPageRoute>
        }
      />

      {/* UI Builder routes */}
      <Route
        path="pages"
        element={
          <AdminPageRoute>
            <PageBuilderPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="menus"
        element={
          <AdminPageRoute>
            <MenuBuilderPage />
          </AdminPageRoute>
        }
      />

      {/* Package Management route */}
      <Route
        path="packages"
        element={
          <AdminPageRoute>
            <PackagesPage />
          </AdminPageRoute>
        }
      />

      {/* Migrations route */}
      <Route
        path="migrations"
        element={
          <AdminPageRoute>
            <MigrationsPage />
          </AdminPageRoute>
        }
      />

      {/* Resource Browser routes */}
      <Route
        path="resources"
        element={
          <AdminPageRoute>
            <ResourceBrowserPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="resources/:collection"
        element={
          <AdminPageRoute>
            <ResourceListPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="resources/:collection/new"
        element={
          <AdminPageRoute>
            <ResourceFormPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="resources/:collection/:id"
        element={
          <AdminPageRoute>
            <ResourceDetailPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="resources/:collection/:id/edit"
        element={
          <AdminPageRoute>
            <ResourceFormPage />
          </AdminPageRoute>
        }
      />

      {/* Picklists route */}
      <Route
        path="picklists"
        element={
          <AdminPageRoute>
            <PicklistsPage />
          </AdminPageRoute>
        }
      />

      {/* Page Layouts route */}
      <Route
        path="layouts"
        element={
          <AdminPageRoute>
            <PageLayoutsPage />
          </AdminPageRoute>
        }
      />

      {/* List Views route */}
      <Route
        path="listviews"
        element={
          <AdminPageRoute>
            <ListViewsPage />
          </AdminPageRoute>
        }
      />

      {/* Reports route */}
      <Route
        path="reports"
        element={
          <AdminPageRoute>
            <ReportsPage />
          </AdminPageRoute>
        }
      />

      {/* Dashboards route */}
      <Route
        path="dashboards"
        element={
          <AdminPageRoute>
            <DashboardsPage />
          </AdminPageRoute>
        }
      />

      {/* Workflow Rules route */}
      <Route
        path="workflow-rules"
        element={
          <AdminPageRoute>
            <WorkflowRulesPage />
          </AdminPageRoute>
        }
      />

      {/* Approval Processes route */}
      <Route
        path="approvals"
        element={
          <AdminPageRoute>
            <ApprovalProcessesPage />
          </AdminPageRoute>
        }
      />

      {/* Flows route */}
      <Route
        path="flows"
        element={
          <AdminPageRoute>
            <FlowsPage />
          </AdminPageRoute>
        }
      />

      {/* Scheduled Jobs route */}
      <Route
        path="scheduled-jobs"
        element={
          <AdminPageRoute>
            <ScheduledJobsPage />
          </AdminPageRoute>
        }
      />

      {/* Email Templates route */}
      <Route
        path="email-templates"
        element={
          <AdminPageRoute>
            <EmailTemplatesPage />
          </AdminPageRoute>
        }
      />

      {/* Plugins route */}
      <Route
        path="plugins"
        element={
          <AdminPageRoute>
            <PluginsPage />
          </AdminPageRoute>
        }
      />

      {/* User Management routes */}
      <Route
        path="users"
        element={
          <AdminPageRoute>
            <UsersPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="users/:id"
        element={
          <AdminPageRoute>
            <UserDetailPage />
          </AdminPageRoute>
        }
      />

      {/* Audit Trail route */}
      <Route
        path="audit-trail"
        element={
          <AdminPageRoute>
            <SetupAuditTrailPage />
          </AdminPageRoute>
        }
      />

      {/* Governor Limits route */}
      <Route
        path="governor-limits"
        element={
          <AdminPageRoute>
            <GovernorLimitsPage />
          </AdminPageRoute>
        }
      />

      {/* Tenant Management routes (platform admin) */}
      <Route
        path="tenants"
        element={
          <AdminPageRoute requiredRoles={['PLATFORM_ADMIN']}>
            <TenantsPage />
          </AdminPageRoute>
        }
      />
      <Route
        path="tenant-dashboard"
        element={
          <AdminPageRoute>
            <TenantDashboardPage />
          </AdminPageRoute>
        }
      />

      {/* Scripts route */}
      <Route
        path="scripts"
        element={
          <AdminPageRoute>
            <ScriptsPage />
          </AdminPageRoute>
        }
      />

      {/* Webhooks route */}
      <Route
        path="webhooks"
        element={
          <AdminPageRoute>
            <WebhooksPage />
          </AdminPageRoute>
        }
      />

      {/* Connected Apps route */}
      <Route
        path="connected-apps"
        element={
          <AdminPageRoute>
            <ConnectedAppsPage />
          </AdminPageRoute>
        }
      />

      {/* Bulk Jobs route */}
      <Route
        path="bulk-jobs"
        element={
          <AdminPageRoute>
            <BulkJobsPage />
          </AdminPageRoute>
        }
      />

      {/* Setup Home Page */}
      <Route
        path="setup"
        element={
          <AdminPageRoute>
            <SetupHomePage />
          </AdminPageRoute>
        }
      />

      {/* ============================================
       * END-USER RUNTIME (shadcn/Tailwind)
       * All routes under /app use the EndUserShell
       * with horizontal top nav bar.
       * ============================================ */}
      <Route
        path="app"
        element={
          <ProtectedRoute
            loginPath={`/${tenantSlug}/login`}
            unauthorizedPath={`/${tenantSlug}/unauthorized`}
          >
            <EndUserShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="home" replace />} />
        <Route
          path="home"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <AppHomePage />
            </React.Suspense>
          }
        />
        <Route
          path="o/:collection"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <EndUserObjectListPage />
            </React.Suspense>
          }
        />
        <Route
          path="o/:collection/new"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <EndUserObjectFormPage />
            </React.Suspense>
          }
        />
        <Route
          path="o/:collection/:id"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <EndUserObjectDetailPage />
            </React.Suspense>
          }
        />
        <Route
          path="o/:collection/:id/edit"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <EndUserObjectFormPage />
            </React.Suspense>
          }
        />
        <Route
          path="search"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <GlobalSearchPage />
            </React.Suspense>
          }
        />
        <Route
          path="p/:pageSlug"
          element={
            <React.Suspense fallback={<PageLoader message="Loading..." />}>
              <EndUserCustomPage />
            </React.Suspense>
          }
        />
      </Route>

      {/* 404 Not Found - catch all within tenant scope */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

/**
 * App Component
 *
 * The root component that sets up routing with tenant slug prefix.
 * All routes are scoped under /:tenantSlug/.
 *
 * Route structure:
 * - /:tenantSlug/* → TenantScopedApp (all providers + routes)
 * - / → NoTenantPage (error: tenant slug required)
 * - * → NoTenantPage (catch-all)
 */
function App({ plugins = [] }: AppProps): React.ReactElement {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            <Route
              path="/:tenantSlug/*"
              element={
                <TenantProvider>
                  <TenantScopedApp plugins={plugins} />
                </TenantProvider>
              }
            />
            <Route path="/" element={<NoTenantPage />} />
            <Route path="*" element={<NoTenantPage />} />
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
