/**
 * EndUserShell Component
 *
 * The top-level layout for the end-user runtime. Provides a horizontal
 * top navigation bar and a full-width content area. This is the shadcn/Tailwind
 * equivalent of the existing AppShell used for admin pages.
 *
 * Uses React Router's <Outlet> to render child route content.
 */

import React, { useState, useCallback, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { TopNavBar } from './TopNavBar'
import { GlobalSearch } from './GlobalSearch'
import { OfflineIndicator } from './OfflineIndicator'
import { OfflineProvider } from '@/offline'
import { RealtimeProvider } from '@/realtime'
import { buildNavTabs } from './navTabs'
import { useAuth } from '@/context/AuthContext'
import { useApi } from '@/context/ApiContext'
import { useConfig } from '@/context/ConfigContext'
import { initPushNotifications } from '@/push/deviceRegistration'
import { PageLoader } from '@/components/PageLoader'
import { SkipLinks } from '@/components/SkipLinks'

export function EndUserShell(): React.ReactElement {
  const { user, logout } = useAuth()
  const { apiClient } = useApi()
  const { config, isLoading: configLoading } = useConfig()
  const [searchOpen, setSearchOpen] = useState(false)

  // Register the device's push token when running in the Capacitor native shell.
  // No-op on the web (does not touch any Capacitor code).
  useEffect(() => {
    void initPushNotifications(apiClient)
  }, [apiClient])

  // Build navigation tabs from bootstrap config menus
  const tabs = buildNavTabs(config?.menus)

  // Global keyboard shortcut: Cmd+K / Ctrl+K → open search
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setSearchOpen(true)
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [])

  const handleLogout = useCallback(() => {
    logout()
  }, [logout])

  if (configLoading) {
    return <PageLoader fullPage message="Loading application..." />
  }

  const appName = config?.branding?.applicationName || 'Kelta'

  return (
    <div className="flex h-full flex-col overflow-hidden bg-background">
      {/* Skip links for keyboard navigation — must be first focusable elements */}
      <SkipLinks
        targets={[
          { id: 'main-content', label: 'Skip to main content' },
          { id: 'main-navigation', label: 'Skip to navigation' },
        ]}
      />
      <TopNavBar
        appName={appName}
        tabs={tabs}
        user={user}
        onLogout={handleLogout}
        onSearchOpen={() => setSearchOpen(true)}
        notificationCount={0}
      />
      <OfflineIndicator />
      <main id="main-content" className="flex-1 overflow-auto" role="main" tabIndex={-1}>
        <OfflineProvider>
          <RealtimeProvider>
            <Outlet />
          </RealtimeProvider>
        </OfflineProvider>
      </main>
      <GlobalSearch open={searchOpen} onOpenChange={setSearchOpen} />
    </div>
  )
}
