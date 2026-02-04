/**
 * Header Component
 * 
 * Top navigation bar with branding, user menu, and global actions.
 * Displays logo and application name from config, and user menu with logout option.
 * 
 * Requirements:
 * - 1.5: Apply branding including logo, application name, and favicon
 * - 2.6: Clear tokens and redirect on logout (via onLogout callback)
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import type { BrandingConfig } from '../../types/config';
import type { User } from '../../types/auth';
import { useAppShell } from '../AppShell';
import styles from './Header.module.css';

/**
 * Props for the Header component
 */
export interface HeaderProps {
  /** Branding configuration with logo, app name */
  branding: BrandingConfig;
  /** Current authenticated user, null if not authenticated */
  user: User | null;
  /** Callback when user clicks logout */
  onLogout: () => void;
}

/**
 * Header component provides the top navigation bar with branding and user menu.
 * 
 * Features:
 * - Displays application logo and name from branding config
 * - Shows user avatar/initials and name when authenticated
 * - Provides dropdown menu with logout option
 * - Responsive behavior for mobile screens
 * - Accessible with keyboard navigation and ARIA attributes
 * 
 * @example
 * ```tsx
 * <Header
 *   branding={{ logoUrl: '/logo.png', applicationName: 'EMF Admin', faviconUrl: '/favicon.ico' }}
 *   user={{ id: '1', email: 'user@example.com', name: 'John Doe' }}
 *   onLogout={() => auth.logout()}
 * />
 * ```
 */
export function Header({ branding, user, onLogout }: HeaderProps): JSX.Element {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  
  // Get screen size from AppShell context for responsive behavior
  let screenSize: 'mobile' | 'tablet' | 'desktop' = 'desktop';
  try {
    const appShell = useAppShell();
    screenSize = appShell.screenSize;
  } catch {
    // AppShell context not available, use default
  }

  /**
   * Toggle user menu open/closed
   */
  const toggleMenu = useCallback(() => {
    setIsMenuOpen((prev) => !prev);
  }, []);

  /**
   * Close the user menu
   */
  const closeMenu = useCallback(() => {
    setIsMenuOpen(false);
  }, []);

  /**
   * Handle logout click
   */
  const handleLogout = useCallback(() => {
    closeMenu();
    onLogout();
  }, [closeMenu, onLogout]);

  /**
   * Handle keyboard navigation in the menu
   */
  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      closeMenu();
      buttonRef.current?.focus();
    }
  }, [closeMenu]);

  /**
   * Close menu when clicking outside
   */
  useEffect(() => {
    if (!isMenuOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        buttonRef.current &&
        !buttonRef.current.contains(event.target as Node)
      ) {
        closeMenu();
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isMenuOpen, closeMenu]);

  /**
   * Get user initials for avatar fallback
   */
  const getUserInitials = (userName?: string, email?: string): string => {
    if (userName) {
      const parts = userName.split(' ');
      if (parts.length >= 2) {
        return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
      }
      return userName.substring(0, 2).toUpperCase();
    }
    if (email) {
      return email.substring(0, 2).toUpperCase();
    }
    return 'U';
  };

  /**
   * Get display name for user
   */
  const getDisplayName = (userData: User): string => {
    return userData.name || userData.email || 'User';
  };

  const isMobile = screenSize === 'mobile';

  return (
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
      </div>

      {/* Spacer to push user menu to the right */}
      <div className={styles.spacer} aria-hidden="true" />

      {/* User menu section */}
      {user && (
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
            {/* User avatar */}
            <div className={styles.avatar} aria-hidden="true">
              {user.picture ? (
                <img
                  src={user.picture}
                  alt=""
                  className={styles.avatarImage}
                  data-testid="user-avatar-image"
                />
              ) : (
                <span className={styles.avatarInitials} data-testid="user-avatar-initials">
                  {getUserInitials(user.name, user.email)}
                </span>
              )}
            </div>
            
            {/* User name (hidden on mobile) */}
            {!isMobile && (
              <span className={styles.userName} data-testid="user-name">
                {getDisplayName(user)}
              </span>
            )}
            
            {/* Dropdown indicator */}
            <span className={styles.dropdownIcon} aria-hidden="true">
              {isMenuOpen ? '▲' : '▼'}
            </span>
          </button>

          {/* Dropdown menu */}
          {isMenuOpen && (
            <div
              ref={menuRef}
              id="user-dropdown-menu"
              className={styles.dropdownMenu}
              role="menu"
              aria-label="User menu"
              data-testid="user-dropdown-menu"
            >
              {/* User info in dropdown */}
              <div className={styles.menuUserInfo} role="presentation">
                <span className={styles.menuUserName}>{getDisplayName(user)}</span>
                <span className={styles.menuUserEmail}>{user.email}</span>
              </div>
              
              <div className={styles.menuDivider} role="separator" aria-hidden="true" />
              
              {/* Logout option */}
              <button
                type="button"
                className={styles.menuItem}
                onClick={handleLogout}
                role="menuitem"
                data-testid="logout-button"
              >
                <span className={styles.menuItemIcon} aria-hidden="true">
                  ⎋
                </span>
                <span>Logout</span>
              </button>
            </div>
          )}
        </div>
      )}
    </header>
  );
}

export default Header;
