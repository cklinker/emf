/**
 * Header Component
 *
 * Top navigation bar with branding, user menu, and global actions.
 * Displays logo and application name from config, and user menu with logout option.
 * Includes global search trigger (Cmd+K), recent items dropdown, and shared UserMenu.
 *
 * Requirements:
 * - 1.5: Apply branding including logo, application name, and favicon
 * - 2.6: Clear tokens and redirect on logout (via onLogout callback)
 */

import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, ArrowLeft } from 'lucide-react'
import type { BrandingConfig } from '../../types/config'
import type { User } from '../../types/auth'
import { useAppShell } from '../AppShell'
import { SearchModal } from '../SearchModal'
import { RecentItemsDropdown } from '../RecentItemsDropdown'
import { UserMenu } from '../UserMenu'
import { getTenantSlug } from '../../context/TenantContext'

/**
 * Props for the Header component
 */
export interface HeaderProps {
  /** Branding configuration with logo, app name */
  branding: BrandingConfig
  /** Current authenticated user, null if not authenticated */
  user: User | null
  /** Callback when user clicks logout */
  onLogout: () => void
}

/**
 * Header component provides the top navigation bar with branding and user menu.
 */
export function Header({ branding, user, onLogout }: HeaderProps): JSX.Element {
  const navigate = useNavigate()
  const tenantSlug = getTenantSlug()
  const [searchOpen, setSearchOpen] = useState(false)

  // Get screen size from AppShell context for responsive behavior
  let screenSize: 'mobile' | 'tablet' | 'desktop' = 'desktop'
  try {
    const appShell = useAppShell()
    screenSize = appShell.screenSize
  } catch {
    // AppShell context not available, use default
  }

  // Global Cmd+K / Ctrl+K shortcut
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setSearchOpen(true)
      }
    }
    document.addEventListener('keydown', handleGlobalKeyDown)
    return () => document.removeEventListener('keydown', handleGlobalKeyDown)
  }, [])

  const isMobile = screenSize === 'mobile'

  return (
    <>
      <header
        className="flex items-center w-full h-full px-6 gap-4 md:max-lg:px-4 max-md:px-4 print:border-b print:border-black"
        data-testid="header"
        role="banner"
      >
        {/* Branding section */}
        <div className="flex items-center gap-2 shrink-0">
          {branding.logoUrl && (
            <img
              src={branding.logoUrl}
              alt={`${branding.applicationName} logo`}
              className="h-9 w-auto object-contain max-md:h-7"
              data-testid="header-logo"
            />
          )}
          {!isMobile && (
            <h1
              className="m-0 text-xl font-semibold text-[var(--app-shell-text,var(--color-text,#1a1a1a))] whitespace-nowrap overflow-hidden text-ellipsis max-w-[300px] md:max-lg:max-w-[200px]"
              data-testid="header-app-name"
            >
              {branding.applicationName}
            </h1>
          )}
          <button
            type="button"
            className="flex items-center gap-1 py-1 px-2 bg-transparent border border-[var(--app-shell-border,var(--color-border,#e0e0e0))] rounded cursor-pointer text-[var(--color-text-secondary,#666666)] text-[0.8125rem] font-medium whitespace-nowrap transition-[background-color,border-color] duration-150 ease-in-out hover:bg-[var(--color-surface-hover,rgba(0,0,0,0.05))] hover:border-[var(--color-text-secondary,#999999)] hover:text-[var(--app-shell-text,var(--color-text,#1a1a1a))] focus:outline-2 focus:outline-[var(--color-focus,#0066cc)] focus:outline-offset-2 [&:focus:not(:focus-visible)]:outline-none"
            onClick={() => navigate(`/${tenantSlug}/app`)}
            aria-label="Back to application"
            data-testid="back-to-app-button"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            {!isMobile && <span>Back to App</span>}
          </button>
        </div>

        {/* Search trigger */}
        <button
          type="button"
          className="flex items-center gap-2 py-1 px-4 bg-[var(--color-surface-hover,rgba(0,0,0,0.03))] border border-[var(--app-shell-border,var(--color-border,#e0e0e0))] rounded cursor-pointer text-[var(--color-text-secondary,#666666)] text-sm min-w-[200px] transition-[border-color] duration-150 ease-in-out hover:border-[var(--color-text-secondary,#999999)] focus:outline-2 focus:outline-[var(--color-focus,#0066cc)] focus:outline-offset-2 [&:focus:not(:focus-visible)]:outline-none max-md:min-w-0 max-md:py-1 max-md:px-2 motion-reduce:transition-none"
          onClick={() => setSearchOpen(true)}
          aria-label="Search (Cmd+K)"
          data-testid="search-trigger"
        >
          <span className="text-sm opacity-60" aria-hidden="true">
            <Search size={16} />
          </span>
          {!isMobile && (
            <span className="flex items-center gap-4 flex-1">
              Search...
              <kbd className="ml-auto inline-flex items-center py-px px-[5px] text-[0.6875rem] font-[inherit] text-[var(--color-text-secondary,#999999)] bg-[var(--app-shell-surface,var(--color-surface,#ffffff))] border border-[var(--app-shell-border,var(--color-border,#e0e0e0))] rounded-[3px]">
                &#x2318;K
              </kbd>
            </span>
          )}
        </button>

        {/* Spacer to push items to the right */}
        <div className="flex-1" aria-hidden="true" />

        {/* Recent items + User menu */}
        {user && (
          <div className="flex items-center gap-2">
            <RecentItemsDropdown />
            <UserMenu user={user} onLogout={onLogout} variant="admin" compact={isMobile} />
          </div>
        )}
      </header>

      {/* Global Search Modal */}
      <SearchModal open={searchOpen} onClose={() => setSearchOpen(false)} />
    </>
  )
}

export default Header
