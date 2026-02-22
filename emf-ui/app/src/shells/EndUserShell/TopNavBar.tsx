/**
 * TopNavBar Component
 *
 * Horizontal top navigation bar for the end-user runtime.
 * Contains: App launcher, app name, object tabs, search trigger,
 * notifications bell, shared UserMenu.
 *
 * Responsive: On mobile (<768px), collection tabs collapse into a
 * hamburger menu that opens a Sheet drawer.
 *
 * Uses shadcn/ui components with Tailwind CSS.
 */

import React, { useState, useCallback } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Search, Bell, Settings, LayoutGrid, Menu, Home, Database } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useSystemPermissions } from '@/hooks/useSystemPermissions'
import { UserMenu } from '@/components/UserMenu'
import type { User } from '@/types/auth'

/**
 * Navigation tab definition — represents a collection in the top nav.
 */
export interface NavTab {
  /** Collection API name */
  collectionName: string
  /** Display label */
  label: string
  /** Lucide icon name (optional) */
  icon?: string
}

interface TopNavBarProps {
  /** Application display name */
  appName?: string
  /** Navigation tabs (collections) */
  tabs?: NavTab[]
  /** Full User object for the shared UserMenu */
  user?: User | null
  /** Callback when logout is requested */
  onLogout?: () => void
  /** Callback when search is triggered */
  onSearchOpen?: () => void
  /** Notification count */
  notificationCount?: number
  /** Callback when notifications bell is clicked */
  onNotificationsOpen?: () => void
  /** Current user roles for role-gated UI */
  userRoles?: string[]
}

export function TopNavBar({
  appName = 'EMF',
  tabs = [],
  user,
  onLogout,
  onSearchOpen,
  notificationCount = 0,
  onNotificationsOpen,
}: TopNavBarProps): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const { hasPermission } = useSystemPermissions()
  const [activeTabIndex, setActiveTabIndex] = useState<number | null>(null)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const canAccessSetup = hasPermission('VIEW_SETUP')

  const basePath = `/${tenantSlug}/app`

  const handleTabClick = useCallback(
    (tab: NavTab, index: number) => {
      setActiveTabIndex(index)
      navigate(`${basePath}/o/${tab.collectionName}`)
    },
    [navigate, basePath]
  )

  const handleMobileNavClick = useCallback(
    (path: string) => {
      setMobileMenuOpen(false)
      navigate(path)
    },
    [navigate]
  )

  return (
    <header className="flex h-14 items-center border-b border-border bg-card px-4 shadow-sm">
      {/* Mobile hamburger menu (visible below md) */}
      <div className="md:hidden">
        <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Open menu">
              <Menu className="h-4 w-4" />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-72">
            <SheetHeader>
              <SheetTitle className="text-left">{appName}</SheetTitle>
            </SheetHeader>
            <nav className="mt-4 flex flex-col gap-1" aria-label="Mobile navigation">
              <button
                onClick={() => handleMobileNavClick(`${basePath}/home`)}
                className="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent"
              >
                <Home className="h-4 w-4 text-muted-foreground" />
                Home
              </button>
              {tabs.length > 0 && (
                <>
                  <Separator className="my-2" />
                  <p className="px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Collections
                  </p>
                </>
              )}
              {tabs.map((tab) => (
                <button
                  key={tab.collectionName}
                  onClick={() => handleMobileNavClick(`${basePath}/o/${tab.collectionName}`)}
                  className="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent"
                >
                  <Database className="h-4 w-4 text-muted-foreground" />
                  {tab.label}
                </button>
              ))}
              {canAccessSetup && (
                <>
                  <Separator className="my-2" />
                  <button
                    onClick={() => handleMobileNavClick(`/${tenantSlug}/setup`)}
                    className="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent"
                  >
                    <Settings className="h-4 w-4" />
                    Switch to Setup
                  </button>
                </>
              )}
            </nav>
          </SheetContent>
        </Sheet>
      </div>

      {/* Left section: App launcher + name (hidden on mobile for space) */}
      <div className="hidden items-center gap-3 md:flex">
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          aria-label="App launcher"
          onClick={() => navigate(`${basePath}/home`)}
        >
          <LayoutGrid className="h-4 w-4" />
        </Button>
        <Link
          to={`${basePath}/home`}
          className="text-sm font-semibold text-foreground no-underline hover:text-primary"
        >
          {appName}
        </Link>
        <Separator orientation="vertical" className="h-6" />
      </div>

      {/* Mobile: App name (visible on mobile only) */}
      <Link
        to={`${basePath}/home`}
        className="ml-2 text-sm font-semibold text-foreground no-underline hover:text-primary md:hidden"
      >
        {appName}
      </Link>

      {/* Center section: Object tabs (hidden on mobile) */}
      <nav
        id="main-navigation"
        className="ml-2 hidden flex-1 items-center gap-1 overflow-x-auto md:flex"
        role="navigation"
        aria-label="Object navigation"
      >
        {tabs.map((tab, index) => (
          <button
            key={tab.collectionName}
            onClick={() => handleTabClick(tab, index)}
            className={`inline-flex items-center whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              activeTabIndex === index
                ? 'bg-accent text-accent-foreground'
                : 'text-muted-foreground'
            }`}
            aria-current={activeTabIndex === index ? 'page' : undefined}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {/* Spacer for mobile */}
      <div className="flex-1 md:hidden" />

      {/* Right section: Admin mode, Search, notifications, user menu */}
      <div className="flex items-center gap-1">
        {/* Admin Mode button (visible only for admins on desktop) */}
        {canAccessSetup && (
          <>
            <Button
              variant="outline"
              size="sm"
              className="hidden items-center gap-1.5 text-xs md:inline-flex"
              onClick={() => navigate(`/${tenantSlug}/setup`)}
              aria-label="Switch to admin mode"
            >
              <Settings className="h-3.5 w-3.5" />
              Admin
            </Button>
            <Separator orientation="vertical" className="mx-1 hidden h-5 md:block" />
          </>
        )}

        {/* Search trigger */}
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          aria-label="Search (Cmd+K)"
          onClick={onSearchOpen}
        >
          <Search className="h-4 w-4" />
        </Button>

        {/* Notifications bell */}
        <Button
          variant="ghost"
          size="icon"
          className="relative h-8 w-8"
          aria-label={`Notifications${notificationCount > 0 ? ` (${notificationCount} unread)` : ''}`}
          onClick={onNotificationsOpen}
        >
          <Bell className="h-4 w-4" />
          {notificationCount > 0 && (
            <Badge
              variant="destructive"
              className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center px-1 text-[10px]"
            >
              {notificationCount > 99 ? '99+' : notificationCount}
            </Badge>
          )}
        </Button>

        {/* Shared User Menu with avatar, language, theme, and context navigation */}
        {user && onLogout && <UserMenu user={user} onLogout={onLogout} variant="app" />}
      </div>
    </header>
  )
}
