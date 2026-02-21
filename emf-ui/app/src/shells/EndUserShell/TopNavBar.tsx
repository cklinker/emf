/**
 * TopNavBar Component
 *
 * Horizontal top navigation bar for the end-user runtime.
 * Contains: App launcher, app name, object tabs, search trigger,
 * notifications bell, user menu.
 *
 * Responsive: On mobile (<768px), collection tabs collapse into a
 * hamburger menu that opens a Sheet drawer.
 *
 * Uses shadcn/ui components with Tailwind CSS.
 */

import React, { useState, useCallback } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Search,
  Bell,
  Sun,
  Moon,
  Monitor,
  LogOut,
  Settings,
  User,
  LayoutGrid,
  ChevronDown,
  Menu,
  Home,
  Database,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Separator } from '@/components/ui/separator'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useTheme } from '@/context/ThemeContext'
import type { ThemeMode } from '@/context/ThemeContext'

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
  /** Current user display name */
  userName?: string
  /** Current user email */
  userEmail?: string
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
  userName = '',
  userEmail = '',
  onLogout,
  onSearchOpen,
  notificationCount = 0,
  onNotificationsOpen,
  userRoles = [],
}: TopNavBarProps): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const { mode, setMode, resolvedMode } = useTheme()
  const [activeTabIndex, setActiveTabIndex] = useState<number | null>(null)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const isAdmin = userRoles.some((role) => role === 'ADMIN' || role === 'PLATFORM_ADMIN')

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

  const handleThemeChange = useCallback(
    (newMode: ThemeMode) => {
      setMode(newMode)
    },
    [setMode]
  )

  const userInitials = userName
    ? userName
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .slice(0, 2)
    : 'U'

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
              {isAdmin && (
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
        {isAdmin && (
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

        {/* Theme toggle (hidden on mobile to save space) */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="hidden h-8 w-8 md:inline-flex"
              aria-label="Toggle theme"
            >
              {resolvedMode === 'dark' ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => handleThemeChange('light')}>
              <Sun className="mr-2 h-4 w-4" />
              Light
              {mode === 'light' && <span className="ml-auto text-primary">&#10003;</span>}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => handleThemeChange('dark')}>
              <Moon className="mr-2 h-4 w-4" />
              Dark
              {mode === 'dark' && <span className="ml-auto text-primary">&#10003;</span>}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => handleThemeChange('system')}>
              <Monitor className="mr-2 h-4 w-4" />
              System
              {mode === 'system' && <span className="ml-auto text-primary">&#10003;</span>}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* User menu */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="flex h-8 items-center gap-2 px-2">
              <Avatar className="h-6 w-6">
                <AvatarFallback className="bg-primary text-[10px] text-primary-foreground">
                  {userInitials}
                </AvatarFallback>
              </Avatar>
              <ChevronDown className="hidden h-3 w-3 text-muted-foreground md:block" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <div className="flex flex-col space-y-1">
                <p className="text-sm font-medium leading-none">{userName || 'User'}</p>
                {userEmail && (
                  <p className="text-xs leading-none text-muted-foreground">{userEmail}</p>
                )}
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {/* Theme toggle in user menu for mobile */}
            <DropdownMenuItem
              className="md:hidden"
              onClick={() => handleThemeChange(resolvedMode === 'dark' ? 'light' : 'dark')}
            >
              {resolvedMode === 'dark' ? (
                <Sun className="mr-2 h-4 w-4" />
              ) : (
                <Moon className="mr-2 h-4 w-4" />
              )}
              {resolvedMode === 'dark' ? 'Light Mode' : 'Dark Mode'}
            </DropdownMenuItem>
            {isAdmin && (
              <DropdownMenuItem onClick={() => navigate(`/${tenantSlug}/setup`)}>
                <Settings className="mr-2 h-4 w-4" />
                Switch to Setup
              </DropdownMenuItem>
            )}
            <DropdownMenuItem>
              <User className="mr-2 h-4 w-4" />
              Profile
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onLogout}>
              <LogOut className="mr-2 h-4 w-4" />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
