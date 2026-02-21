/**
 * Header Component
 *
 * Top navigation bar with branding, user menu, and global actions.
 * Displays logo and application name from config, and user menu with logout option.
 * Includes global search trigger (Cmd+K), recent items dropdown, and user menu.
 *
 * Requirements:
 * - 1.5: Apply branding including logo, application name, and favicon
 * - 2.6: Clear tokens and redirect on logout (via onLogout callback)
 */

import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, ArrowLeft } from 'lucide-react'
import type { BrandingConfig } from '../../types/config'
import type { User } from '../../types/auth'
import { useAppShell } from '../AppShell'
import { SearchModal } from '../SearchModal'
import { RecentItemsDropdown } from '../RecentItemsDropdown'
import { getGravatarUrl } from '../../utils/gravatar'
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
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [gravatarFailed, setGravatarFailed] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

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

  const toggleMenu = useCallback(() => {
    setIsMenuOpen((prev) => !prev)
  }, [])

  const closeMenu = useCallback(() => {
    setIsMenuOpen(false)
  }, [])

  const handleLogout = useCallback(() => {
    closeMenu()
    onLogout()
  }, [closeMenu, onLogout])

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeMenu()
        buttonRef.current?.focus()
      }
    },
    [closeMenu]
  )

  useEffect(() => {
    if (!isMenuOpen) return

    const handleClickOutside = (event: MouseEvent) => {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        buttonRef.current &&
        !buttonRef.current.contains(event.target as Node)
      ) {
        closeMenu()
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isMenuOpen, closeMenu])

  const getUserInitials = (userName?: string, email?: string): string => {
    if (userName) {
      const parts = userName.split(' ')
      if (parts.length >= 2) {
        return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
      }
      return userName.substring(0, 2).toUpperCase()
    }
    if (email) {
      return email.substring(0, 2).toUpperCase()
    }
    return 'U'
  }

  const getDisplayName = (userData: User): string => {
    return userData.name || userData.email || 'User'
  }

  // Compute Gravatar URL once (only when email changes)
  const gravatarUrl = useMemo(() => getGravatarUrl(user?.email, 64), [user?.email])

  // Resolve the avatar image URL: OIDC picture > Gravatar > initials fallback
  const avatarImageUrl = user?.picture || (!gravatarFailed && gravatarUrl) || null

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

            {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
            <div className="relative flex items-center" onKeyDown={handleKeyDown}>
              <button
                ref={buttonRef}
                type="button"
                className="flex items-center gap-2 py-1 px-2 bg-transparent border border-transparent rounded cursor-pointer text-[var(--app-shell-text,var(--color-text,#1a1a1a))] transition-[background-color,border-color] duration-150 ease-in-out hover:bg-[var(--color-surface-hover,rgba(0,0,0,0.05))] hover:border-[var(--app-shell-border,var(--color-border,#e0e0e0))] focus:outline-2 focus:outline-[var(--color-focus,#0066cc)] focus:outline-offset-2 [&:focus:not(:focus-visible)]:outline-none motion-reduce:transition-none forced-colors:border forced-colors:border-current"
                onClick={toggleMenu}
                aria-expanded={isMenuOpen}
                aria-haspopup="menu"
                aria-controls={isMenuOpen ? 'user-dropdown-menu' : undefined}
                aria-label={`User menu for ${getDisplayName(user)}`}
                data-testid="user-menu-button"
              >
                <div
                  className="flex items-center justify-center w-8 h-8 rounded-full bg-[var(--app-shell-primary,var(--color-primary,#0066cc))] text-[var(--color-text-inverse,#ffffff)] text-sm font-semibold overflow-hidden shrink-0 forced-colors:border forced-colors:border-current"
                  aria-hidden="true"
                >
                  {avatarImageUrl ? (
                    <img
                      src={avatarImageUrl}
                      alt=""
                      className="w-full h-full object-cover"
                      onError={() => setGravatarFailed(true)}
                      data-testid="user-avatar-image"
                    />
                  ) : (
                    <span className="leading-none" data-testid="user-avatar-initials">
                      {getUserInitials(user.name, user.email)}
                    </span>
                  )}
                </div>

                {!isMobile && (
                  <span
                    className="text-sm font-medium max-w-[150px] overflow-hidden text-ellipsis whitespace-nowrap md:max-lg:max-w-[100px]"
                    data-testid="user-name"
                  >
                    {getDisplayName(user)}
                  </span>
                )}

                <span
                  className="text-[0.625rem] opacity-70 transition-transform duration-150 ease-in-out motion-reduce:transition-none"
                  aria-hidden="true"
                >
                  {isMenuOpen ? '\u25B2' : '\u25BC'}
                </span>
              </button>

              {isMenuOpen && (
                <div
                  ref={menuRef}
                  id="user-dropdown-menu"
                  className="absolute top-[calc(100%+4px)] right-0 min-w-[220px] bg-[var(--app-shell-surface,var(--color-surface,#ffffff))] border border-[var(--app-shell-border,var(--color-border,#e0e0e0))] rounded shadow-[0_4px_12px_rgba(0,0,0,0.15)] z-[1000] overflow-hidden max-md:min-w-[200px] max-md:-right-2 forced-colors:border-2 forced-colors:border-current"
                  role="menu"
                  aria-label="User menu"
                  data-testid="user-dropdown-menu"
                >
                  <div className="flex flex-col p-4 gap-1" role="presentation">
                    <span className="text-sm font-semibold text-[var(--app-shell-text,var(--color-text,#1a1a1a))]">
                      {getDisplayName(user)}
                    </span>
                    <span className="text-xs text-[var(--color-text-secondary,#666666)] overflow-hidden text-ellipsis whitespace-nowrap">
                      {user.email}
                    </span>
                  </div>

                  <div
                    className="h-px bg-[var(--app-shell-border,var(--color-border,#e0e0e0))] m-0"
                    role="separator"
                    aria-hidden="true"
                  />

                  <button
                    type="button"
                    className="flex items-center gap-2 w-full py-2 px-4 bg-transparent border-none cursor-pointer text-[var(--app-shell-text,var(--color-text,#1a1a1a))] text-sm text-left transition-[background-color] duration-150 ease-in-out hover:bg-[var(--color-surface-hover,rgba(0,0,0,0.05))] focus:outline-2 focus:outline-[var(--color-focus,#0066cc)] focus:outline-offset-[-2px] focus:bg-[var(--color-surface-hover,rgba(0,0,0,0.05))] [&:focus:not(:focus-visible)]:outline-none motion-reduce:transition-none forced-colors:focus:outline-2 forced-colors:focus:outline-current"
                    onClick={handleLogout}
                    role="menuitem"
                    data-testid="logout-button"
                  >
                    <span className="text-base opacity-70" aria-hidden="true">
                      &#x238B;
                    </span>
                    <span>Logout</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </header>

      {/* Global Search Modal */}
      <SearchModal open={searchOpen} onClose={() => setSearchOpen(false)} />
    </>
  )
}

export default Header
