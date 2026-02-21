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
import styles from './Header.module.css'

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
      <header className={styles.header} data-testid="header" role="banner">
        {/* Branding section */}
        <div className={styles.branding}>
          {branding.logoUrl && (
            <img
              src={branding.logoUrl}
              alt={`${branding.applicationName} logo`}
              className={styles.logo}
              data-testid="header-logo"
            />
          )}
          {!isMobile && (
            <h1 className={styles.appName} data-testid="header-app-name">
              {branding.applicationName}
            </h1>
          )}
          <button
            type="button"
            className={styles.backToApp}
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
          className={styles.searchTrigger}
          onClick={() => setSearchOpen(true)}
          aria-label="Search (Cmd+K)"
          data-testid="search-trigger"
        >
          <span className={styles.searchTriggerIcon} aria-hidden="true">
            <Search size={16} />
          </span>
          {!isMobile && (
            <span className={styles.searchTriggerText}>
              Search...
              <kbd className={styles.searchKbd}>&#x2318;K</kbd>
            </span>
          )}
        </button>

        {/* Spacer to push items to the right */}
        <div className={styles.spacer} aria-hidden="true" />

        {/* Recent items + User menu */}
        {user && (
          <div className={styles.headerActions}>
            <RecentItemsDropdown />

            {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
            <div className={styles.userSection} onKeyDown={handleKeyDown}>
              <button
                ref={buttonRef}
                type="button"
                className={styles.userButton}
                onClick={toggleMenu}
                aria-expanded={isMenuOpen}
                aria-haspopup="menu"
                aria-controls={isMenuOpen ? 'user-dropdown-menu' : undefined}
                aria-label={`User menu for ${getDisplayName(user)}`}
                data-testid="user-menu-button"
              >
                <div className={styles.avatar} aria-hidden="true">
                  {avatarImageUrl ? (
                    <img
                      src={avatarImageUrl}
                      alt=""
                      className={styles.avatarImage}
                      onError={() => setGravatarFailed(true)}
                      data-testid="user-avatar-image"
                    />
                  ) : (
                    <span className={styles.avatarInitials} data-testid="user-avatar-initials">
                      {getUserInitials(user.name, user.email)}
                    </span>
                  )}
                </div>

                {!isMobile && (
                  <span className={styles.userName} data-testid="user-name">
                    {getDisplayName(user)}
                  </span>
                )}

                <span className={styles.dropdownIcon} aria-hidden="true">
                  {isMenuOpen ? '\u25B2' : '\u25BC'}
                </span>
              </button>

              {isMenuOpen && (
                <div
                  ref={menuRef}
                  id="user-dropdown-menu"
                  className={styles.dropdownMenu}
                  role="menu"
                  aria-label="User menu"
                  data-testid="user-dropdown-menu"
                >
                  <div className={styles.menuUserInfo} role="presentation">
                    <span className={styles.menuUserName}>{getDisplayName(user)}</span>
                    <span className={styles.menuUserEmail}>{user.email}</span>
                  </div>

                  <div className={styles.menuDivider} role="separator" aria-hidden="true" />

                  <button
                    type="button"
                    className={styles.menuItem}
                    onClick={handleLogout}
                    role="menuitem"
                    data-testid="logout-button"
                  >
                    <span className={styles.menuItemIcon} aria-hidden="true">
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
