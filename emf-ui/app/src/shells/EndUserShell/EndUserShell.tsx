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
import { useAuth } from '@/context/AuthContext'
import { useConfig } from '@/context/ConfigContext'
import { PageLoader } from '@/components/PageLoader'
import { SkipLinks } from '@/components/SkipLinks'
import type { NavTab } from './TopNavBar'
import type { MenuConfig } from '@/types/config'

/**
 * Extract collection-based navigation tabs from menu config.
 * Pure function — safe for React compiler optimization.
 */
function buildNavTabs(menus: MenuConfig[] | undefined): NavTab[] {
  if (!menus) return []

  const collectionTabs: NavTab[] = []
  for (const menu of menus) {
    if (menu.items) {
      for (const item of menu.items) {
        if (item.path && item.path.startsWith('/resources/')) {
          const collectionName = item.path.replace('/resources/', '').split('/')[0]
          if (collectionName) {
            collectionTabs.push({
              collectionName,
              label: item.label || collectionName,
              icon: item.icon,
            })
          }
        }
      }
    }
  }
  return collectionTabs
}

export function EndUserShell(): React.ReactElement {
  const { user, logout } = useAuth()
  const { config, isLoading: configLoading } = useConfig()
  const [searchOpen, setSearchOpen] = useState(false)

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

  const appName = config?.branding?.applicationName || 'EMF'
  const userName = user?.name || user?.email || ''
  const userEmail = user?.email || ''

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
        userName={userName}
        userEmail={userEmail}
        onLogout={handleLogout}
        onSearchOpen={() => setSearchOpen(true)}
        notificationCount={0}
      />
      <main id="main-content" className="flex-1 overflow-auto" role="main" tabIndex={-1}>
        <Outlet />
      </main>
      <GlobalSearch open={searchOpen} onOpenChange={setSearchOpen} />
    </div>
  )
}
