/**
 * TopNavBar Component
 *
 * Horizontal top navigation bar for the end-user runtime.
 * Contains: App launcher, app name, object tabs, search trigger,
 * notifications bell, user menu.
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
}: TopNavBarProps): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const { mode, setMode, resolvedMode } = useTheme()
  const [activeTabIndex, setActiveTabIndex] = useState<number | null>(null)

  const basePath = `/${tenantSlug}/app`

  const handleTabClick = useCallback(
    (tab: NavTab, index: number) => {
      setActiveTabIndex(index)
      navigate(`${basePath}/o/${tab.collectionName}`)
    },
    [navigate, basePath]
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
      {/* Left section: App launcher + name */}
      <div className="flex items-center gap-3">
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

      {/* Center section: Object tabs */}
      <nav
        className="ml-2 flex flex-1 items-center gap-1 overflow-x-auto"
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

      {/* Right section: Search, notifications, user menu */}
      <div className="flex items-center gap-1">
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

        {/* Theme toggle */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Toggle theme">
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
              <ChevronDown className="h-3 w-3 text-muted-foreground" />
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
            <DropdownMenuItem onClick={() => navigate(`/${tenantSlug}/setup`)}>
              <Settings className="mr-2 h-4 w-4" />
              Switch to Setup
            </DropdownMenuItem>
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
