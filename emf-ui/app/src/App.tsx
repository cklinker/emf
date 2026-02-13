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

import React, { useCallback } from 'react'
import { BrowserRouter, Routes, Route, useNavigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'

// Context Providers
import { AuthProvider } from './context/AuthContext'
import { ApiProvider } from './context/ApiContext'
import { ConfigProvider, useConfig } from './context/ConfigContext'
import { ThemeProvider } from './context/ThemeContext'
import { I18nProvider } from './context/I18nContext'
import { PluginProvider } from './context/PluginContext'
import { useAuth } from './context/AuthContext'

// Hooks
import { useGlobalShortcuts } from './hooks/useGlobalShortcuts'

// Components
import { ErrorBoundary } from './components/ErrorBoundary'
import { KeyboardShortcutsHelp } from './components/KeyboardShortcutsHelp/KeyboardShortcutsHelp'
import { ToastProvider } from './components/Toast'
import { LiveRegionProvider } from './components/LiveRegion'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AppShell, useAppShell } from './components/AppShell'
import { Header } from './components/Header'
import { Sidebar } from './components/Sidebar'
import { PageTransition } from './components/PageTransition'
import { PageLoader } from './components/PageLoader'

// Pages
import {
  HomePage,
  DashboardPage,
  CollectionsPage,
  CollectionDetailPage,
  CollectionFormPage,
  CollectionWizardPage,
  RolesPage,
  PoliciesPage,
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
  ProfilesPage,
  PermissionSetsPage,
  SharingSettingsPage,
  RoleHierarchyPage,
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

// Types
import type { Plugin } from './types/plugin'
import type { MenuConfig } from './types/config'

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
            navigate('/login')
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
 * Main application layout with AppShell, Header, and Sidebar
 * Wraps content with PageTransition for smooth page changes
 */
function AppLayout({ children }: { children: React.ReactNode }): React.ReactElement {
  const { user, logout } = useAuth()
  const { config, isLoading: configLoading, error, reload } = useConfig()
  const { helpOpen, setHelpOpen } = useGlobalShortcuts()

  // Show loading state while config is loading
  if (configLoading) {
    return <PageLoader fullPage message="Loading application..." />
  }

  // Show error state if bootstrap failed
  if (error) {
    return <BootstrapError error={error} onRetry={reload} />
  }

  // Extract branding and menus from config
  const branding = config?.branding ?? {
    logoUrl: '',
    applicationName: 'EMF Admin',
    faviconUrl: '',
  }

  const menus = config?.menus ?? []

  return (
    <AppShell
      header={<Header branding={branding} user={user} onLogout={logout} />}
      sidebar={<SidebarWithContext menus={menus} />}
    >
      <PageTransition type="fade" duration={200}>
        {children}
      </PageTransition>
      <KeyboardShortcutsHelp isOpen={helpOpen} onClose={() => setHelpOpen(false)} />
    </AppShell>
  )
}

/**
 * Sidebar wrapper that uses AppShell context
 */
function SidebarWithContext({ menus }: { menus: MenuConfig[] }): React.ReactElement {
  const { sidebarCollapsed, toggleSidebar, closeMobileSidebar, screenSize } = useAppShell()

  const handleItemClick = useCallback(() => {
    if (screenSize === 'mobile') {
      closeMobileSidebar()
    }
  }, [screenSize, closeMobileSidebar])

  return (
    <Sidebar
      menus={menus}
      collapsed={sidebarCollapsed}
      onToggle={toggleSidebar}
      onItemClick={handleItemClick}
    />
  )
}

/**
 * Protected route wrapper that includes the AppLayout
 */
function ProtectedPageRoute({
  children,
  requiredRoles,
  requiredPolicies,
}: {
  children: React.ReactNode
  requiredRoles?: string[]
  requiredPolicies?: string[]
}): React.ReactElement {
  return (
    <ProtectedRoute requiredRoles={requiredRoles} requiredPolicies={requiredPolicies}>
      <AppLayout>{children}</AppLayout>
    </ProtectedRoute>
  )
}

/**
 * App Component
 *
 * The root component that sets up all providers and routing.
 *
 * Provider hierarchy:
 * 1. ErrorBoundary - Catches and displays unexpected errors
 * 2. QueryClientProvider - TanStack Query for server state
 * 3. AuthProvider - Authentication state and OIDC flow
 * 4. ApiProvider - Authenticated API client
 * 5. ConfigProvider - Bootstrap configuration
 * 6. ThemeProvider - Theme state and CSS custom properties
 * 7. I18nProvider - Internationalization
 * 8. PluginProvider - Plugin system
 * 9. ToastProvider - Toast notifications
 * 10. LiveRegionProvider - Screen reader announcements
 * 11. BrowserRouter - React Router
 */
function App({ plugins = [] }: AppProps): React.ReactElement {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <ApiProvider baseUrl={import.meta.env.VITE_API_BASE_URL || ''}>
            <ConfigProvider>
              <ThemeProvider>
                <I18nProvider>
                  <PluginProvider plugins={plugins}>
                    <ToastProvider>
                      <LiveRegionProvider>
                        <BrowserRouter>
                          <Routes>
                            {/* Public routes */}
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/unauthorized" element={<UnauthorizedPage />} />

                            {/* OAuth callback route - shows loading or error while AuthContext processes the callback */}
                            <Route path="/auth/callback" element={<AuthCallbackPage />} />

                            {/* Home Page - default landing page */}
                            <Route
                              path="/"
                              element={
                                <ProtectedPageRoute>
                                  <HomePage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* System Health Dashboard (moved from /) */}
                            <Route
                              path="/system-health"
                              element={
                                <ProtectedPageRoute>
                                  <DashboardPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Collections routes */}
                            <Route
                              path="/collections"
                              element={
                                <ProtectedPageRoute>
                                  <CollectionsPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/collections/new"
                              element={
                                <ProtectedPageRoute>
                                  <CollectionWizardPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/collections/:id"
                              element={
                                <ProtectedPageRoute>
                                  <CollectionDetailPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/collections/:id/edit"
                              element={
                                <ProtectedPageRoute>
                                  <CollectionFormPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Authorization routes */}
                            <Route
                              path="/roles"
                              element={
                                <ProtectedPageRoute>
                                  <RolesPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/policies"
                              element={
                                <ProtectedPageRoute>
                                  <PoliciesPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* OIDC Providers route */}
                            <Route
                              path="/oidc-providers"
                              element={
                                <ProtectedPageRoute>
                                  <OIDCProvidersPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Workers route */}
                            <Route
                              path="/workers"
                              element={
                                <ProtectedPageRoute>
                                  <WorkersPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* UI Builder routes */}
                            <Route
                              path="/pages"
                              element={
                                <ProtectedPageRoute>
                                  <PageBuilderPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/menus"
                              element={
                                <ProtectedPageRoute>
                                  <MenuBuilderPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Package Management route */}
                            <Route
                              path="/packages"
                              element={
                                <ProtectedPageRoute>
                                  <PackagesPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Migrations route */}
                            <Route
                              path="/migrations"
                              element={
                                <ProtectedPageRoute>
                                  <MigrationsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Resource Browser routes */}
                            <Route
                              path="/resources"
                              element={
                                <ProtectedPageRoute>
                                  <ResourceBrowserPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/resources/:collection"
                              element={
                                <ProtectedPageRoute>
                                  <ResourceListPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/resources/:collection/new"
                              element={
                                <ProtectedPageRoute>
                                  <ResourceFormPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/resources/:collection/:id"
                              element={
                                <ProtectedPageRoute>
                                  <ResourceDetailPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/resources/:collection/:id/edit"
                              element={
                                <ProtectedPageRoute>
                                  <ResourceFormPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Picklists route */}
                            <Route
                              path="/picklists"
                              element={
                                <ProtectedPageRoute>
                                  <PicklistsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Page Layouts route */}
                            <Route
                              path="/layouts"
                              element={
                                <ProtectedPageRoute>
                                  <PageLayoutsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* List Views route */}
                            <Route
                              path="/listviews"
                              element={
                                <ProtectedPageRoute>
                                  <ListViewsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Reports route */}
                            <Route
                              path="/reports"
                              element={
                                <ProtectedPageRoute>
                                  <ReportsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Dashboards route */}
                            <Route
                              path="/dashboards"
                              element={
                                <ProtectedPageRoute>
                                  <DashboardsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Workflow Rules route */}
                            <Route
                              path="/workflow-rules"
                              element={
                                <ProtectedPageRoute>
                                  <WorkflowRulesPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Approval Processes route */}
                            <Route
                              path="/approvals"
                              element={
                                <ProtectedPageRoute>
                                  <ApprovalProcessesPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Flows route */}
                            <Route
                              path="/flows"
                              element={
                                <ProtectedPageRoute>
                                  <FlowsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Scheduled Jobs route */}
                            <Route
                              path="/scheduled-jobs"
                              element={
                                <ProtectedPageRoute>
                                  <ScheduledJobsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Email Templates route */}
                            <Route
                              path="/email-templates"
                              element={
                                <ProtectedPageRoute>
                                  <EmailTemplatesPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Plugins route */}
                            <Route
                              path="/plugins"
                              element={
                                <ProtectedPageRoute>
                                  <PluginsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* User Management routes */}
                            <Route
                              path="/users"
                              element={
                                <ProtectedPageRoute>
                                  <UsersPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/users/:id"
                              element={
                                <ProtectedPageRoute>
                                  <UserDetailPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Profile & Permission Set routes */}
                            <Route
                              path="/profiles"
                              element={
                                <ProtectedPageRoute>
                                  <ProfilesPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/permission-sets"
                              element={
                                <ProtectedPageRoute>
                                  <PermissionSetsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Sharing Settings routes */}
                            <Route
                              path="/sharing"
                              element={
                                <ProtectedPageRoute>
                                  <SharingSettingsPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/role-hierarchy"
                              element={
                                <ProtectedPageRoute>
                                  <RoleHierarchyPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Audit Trail route */}
                            <Route
                              path="/audit-trail"
                              element={
                                <ProtectedPageRoute>
                                  <SetupAuditTrailPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Governor Limits route */}
                            <Route
                              path="/governor-limits"
                              element={
                                <ProtectedPageRoute>
                                  <GovernorLimitsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Tenant Management routes (platform admin) */}
                            <Route
                              path="/tenants"
                              element={
                                <ProtectedPageRoute requiredRoles={['PLATFORM_ADMIN']}>
                                  <TenantsPage />
                                </ProtectedPageRoute>
                              }
                            />
                            <Route
                              path="/tenant-dashboard"
                              element={
                                <ProtectedPageRoute>
                                  <TenantDashboardPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Scripts route */}
                            <Route
                              path="/scripts"
                              element={
                                <ProtectedPageRoute>
                                  <ScriptsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Webhooks route */}
                            <Route
                              path="/webhooks"
                              element={
                                <ProtectedPageRoute>
                                  <WebhooksPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Connected Apps route */}
                            <Route
                              path="/connected-apps"
                              element={
                                <ProtectedPageRoute>
                                  <ConnectedAppsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Bulk Jobs route */}
                            <Route
                              path="/bulk-jobs"
                              element={
                                <ProtectedPageRoute>
                                  <BulkJobsPage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* Setup Home Page */}
                            <Route
                              path="/setup"
                              element={
                                <ProtectedPageRoute>
                                  <SetupHomePage />
                                </ProtectedPageRoute>
                              }
                            />

                            {/* 404 Not Found - catch all */}
                            <Route path="*" element={<NotFoundPage />} />
                          </Routes>
                        </BrowserRouter>
                      </LiveRegionProvider>
                    </ToastProvider>
                  </PluginProvider>
                </I18nProvider>
              </ThemeProvider>
            </ConfigProvider>
          </ApiProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
