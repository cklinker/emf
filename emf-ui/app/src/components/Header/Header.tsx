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
import { Search } from 'lucide-react'
import type { BrandingConfig } from '../../types/config'
import type { User } from '../../types/auth'
import { useAppShell } from '../AppShell'
import { SearchModal } from '../SearchModal'
import { RecentItemsDropdown } from '../RecentItemsDropdown'
import { getGravatarUrl } from '../../utils/gravatar'

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
        className="flex items-center gap-3 h-full px-4 bg-card border-b border-border"
        data-testid="header"
        role="banner"
      >
        {/* Branding section */}
        <div className="flex items-center gap-2 shrink-0">
          {branding.logoUrl && (
            <img
              src={branding.logoUrl}
              alt={`${branding.applicationName} logo`}
              className="h-8 w-8 object-contain"
              data-testid="header-logo"
            />
          )}
          {!isMobile && (
            <h1
              className="text-base font-semibold text-foreground truncate max-w-[200px]"
              data-testid="header-app-name"
            >
              {branding.applicationName}
            </h1>
          )}
        </div>

        {/* Search trigger */}
        <button
          type="button"
          className="flex items-center gap-2 px-3 py-1.5 rounded-md border border-border bg-muted/50 text-muted-foreground text-sm cursor-pointer hover:bg-muted transition-colors"
          onClick={() => setSearchOpen(true)}
          aria-label="Search (Cmd+K)"
          data-testid="search-trigger"
        >
          <span className="flex items-center" aria-hidden="true">
            <Search size={16} />
          </span>
          {!isMobile && (
            <span className="flex items-center gap-2">
              Search...
              <kbd className="text-xs px-1.5 py-0.5 rounded border border-border bg-background font-mono">
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
            <div className="relative" onKeyDown={handleKeyDown}>
              <button
                ref={buttonRef}
                type="button"
                className="flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-accent cursor-pointer transition-colors border-0 bg-transparent"
                onClick={toggleMenu}
                aria-expanded={isMenuOpen}
                aria-haspopup="menu"
                aria-controls={isMenuOpen ? 'user-dropdown-menu' : undefined}
                aria-label={`User menu for ${getDisplayName(user)}`}
                data-testid="user-menu-button"
              >
                <div
                  className="w-8 h-8 rounded-full overflow-hidden bg-primary/10 flex items-center justify-center shrink-0"
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
                    <span
                      className="text-xs font-semibold text-primary"
                      data-testid="user-avatar-initials"
                    >
                      {getUserInitials(user.name, user.email)}
                    </span>
                  )}
                </div>

                {!isMobile && (
                  <span
                    className="text-sm text-foreground truncate max-w-[120px]"
                    data-testid="user-name"
                  >
                    {getDisplayName(user)}
                  </span>
                )}

                <span className="text-[10px] text-muted-foreground" aria-hidden="true">
                  {isMenuOpen ? '\u25B2' : '\u25BC'}
                </span>
              </button>

              {isMenuOpen && (
                <div
                  ref={menuRef}
                  id="user-dropdown-menu"
                  className="absolute right-0 top-full mt-1 w-64 bg-card border border-border rounded-lg shadow-lg z-50 py-1"
                  role="menu"
                  aria-label="User menu"
                  data-testid="user-dropdown-menu"
                >
                  <div className="px-4 py-3" role="presentation">
                    <span className="block text-sm font-medium text-foreground">
                      {getDisplayName(user)}
                    </span>
                    <span className="block text-xs text-muted-foreground mt-0.5">{user.email}</span>
                  </div>

                  <div className="h-px bg-border mx-2 my-1" role="separator" aria-hidden="true" />

                  <button
                    type="button"
                    className="flex items-center gap-2 w-full px-4 py-2 text-sm text-foreground hover:bg-accent cursor-pointer border-0 bg-transparent text-left"
                    onClick={handleLogout}
                    role="menuitem"
                    data-testid="logout-button"
                  >
                    <span className="text-muted-foreground" aria-hidden="true">
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
