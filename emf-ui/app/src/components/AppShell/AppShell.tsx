/**
 * AppShell Component
 *
 * The main layout wrapper providing consistent structure for the application.
 * Includes header, sidebar, and main content areas with responsive behavior.
 *
 * Requirements:
 * - 14.2: All interactive elements are keyboard accessible
 * - 17.1: Adapt layout for desktop screens (1024px and above)
 * - 17.2: Adapt layout for tablet screens (768px to 1023px)
 * - 17.3: Adapt layout for mobile screens (below 768px)
 */

import {
  useState,
  useEffect,
  useCallback,
  createContext,
  useContext,
  useRef,
  type ReactNode,
} from 'react'
import { useTheme } from '../../context/ThemeContext'
import { useEscapeKey } from '../../hooks/useKeyboardShortcuts'
import { SkipLinks } from '../SkipLinks'
import { BREAKPOINTS, type ScreenSize } from './constants'
import styles from './AppShell.module.css'

/**
 * AppShell context value for child components
 */
export interface AppShellContextValue {
  /** Current screen size category */
  screenSize: ScreenSize
  /** Whether the sidebar is collapsed (desktop/tablet) */
  sidebarCollapsed: boolean
  /** Whether the mobile sidebar is open */
  sidebarOpen: boolean
  /** Toggle sidebar collapsed state (desktop/tablet) */
  toggleSidebar: () => void
  /** Toggle mobile sidebar open state */
  toggleMobileSidebar: () => void
  /** Close mobile sidebar */
  closeMobileSidebar: () => void
}

/**
 * Props for the AppShell component
 */
export interface AppShellProps {
  /** Main content to render in the content area */
  children: ReactNode
  /** Optional header component to render */
  header?: ReactNode
  /** Optional sidebar component to render */
  sidebar?: ReactNode
  /** Initial collapsed state for sidebar (default: false) */
  initialSidebarCollapsed?: boolean
}

// Create context for AppShell state
const AppShellContext = createContext<AppShellContextValue | undefined>(undefined)

/**
 * Hook to access AppShell context
 *
 * @throws Error if used outside of AppShell
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { screenSize, sidebarCollapsed, toggleSidebar } = useAppShell();
 *   return <button onClick={toggleSidebar}>Toggle</button>;
 * }
 * ```
 */
export function useAppShell(): AppShellContextValue {
  const context = useContext(AppShellContext)
  if (context === undefined) {
    throw new Error('useAppShell must be used within an AppShell')
  }
  return context
}

/**
 * Determine screen size category based on window width
 */
function getScreenSize(width: number): ScreenSize {
  if (width < BREAKPOINTS.mobile) {
    return 'mobile'
  }
  if (width < BREAKPOINTS.tablet) {
    return 'tablet'
  }
  return 'desktop'
}

/**
 * AppShell provides the main application layout structure.
 * It handles responsive layout changes and sidebar collapse state.
 *
 * Features:
 * - Responsive layout with desktop, tablet, and mobile breakpoints
 * - Collapsible sidebar for desktop/tablet
 * - Slide-out sidebar for mobile with overlay
 * - Integration with ThemeContext for styling
 * - Accessible navigation with proper ARIA attributes
 *
 * @example
 * ```tsx
 * <AppShell
 *   header={<Header />}
 *   sidebar={<Sidebar />}
 * >
 *   <MainContent />
 * </AppShell>
 * ```
 */
export function AppShell({
  children,
  header,
  sidebar,
  initialSidebarCollapsed = false,
}: AppShellProps) {
  // Get theme context for styling integration
  const { resolvedMode, colors } = useTheme()

  // Ref for focus management
  const mainContentRef = useRef<HTMLElement>(null)

  // Screen size state
  const [screenSize, setScreenSize] = useState<ScreenSize>(() => {
    if (typeof window === 'undefined') {
      return 'desktop'
    }
    return getScreenSize(window.innerWidth)
  })

  // Sidebar state
  const [sidebarCollapsed, setSidebarCollapsed] = useState(initialSidebarCollapsed)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  /**
   * Handle window resize to update screen size
   * Requirement 17.1, 17.2, 17.3: Adapt layout for different screen sizes
   */
  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }

    const handleResize = () => {
      const newScreenSize = getScreenSize(window.innerWidth)
      setScreenSize(newScreenSize)

      // Close mobile sidebar when resizing to larger screen
      if (newScreenSize !== 'mobile' && sidebarOpen) {
        setSidebarOpen(false)
      }
    }

    // Add resize listener
    window.addEventListener('resize', handleResize)

    // Initial check
    handleResize()

    return () => {
      window.removeEventListener('resize', handleResize)
    }
  }, [sidebarOpen])

  /**
   * Toggle sidebar collapsed state (desktop/tablet)
   */
  const toggleSidebar = useCallback(() => {
    setSidebarCollapsed((prev) => !prev)
  }, [])

  /**
   * Toggle mobile sidebar open state
   */
  const toggleMobileSidebar = useCallback(() => {
    setSidebarOpen((prev) => !prev)
  }, [])

  /**
   * Close mobile sidebar
   */
  const closeMobileSidebar = useCallback(() => {
    setSidebarOpen(false)
  }, [])

  /**
   * Handle Escape key to close mobile sidebar
   * Requirement 14.2: Keyboard accessibility
   */
  useEscapeKey(closeMobileSidebar, sidebarOpen)

  /**
   * Prevent body scroll when mobile sidebar is open
   */
  useEffect(() => {
    if (typeof document === 'undefined') {
      return
    }

    if (sidebarOpen && screenSize === 'mobile') {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }

    return () => {
      document.body.style.overflow = ''
    }
  }, [sidebarOpen, screenSize])

  // Build class names
  const appShellClasses = [
    styles.appShell,
    styles[`appShell--${screenSize}`],
    styles[`appShell--${resolvedMode}`],
  ]
    .filter(Boolean)
    .join(' ')

  const sidebarClasses = [
    styles.sidebar,
    sidebarCollapsed && screenSize !== 'mobile' ? styles['sidebar--collapsed'] : '',
    sidebarOpen && screenSize === 'mobile' ? styles['sidebar--open'] : '',
  ]
    .filter(Boolean)
    .join(' ')

  const contentClasses = [
    styles.content,
    sidebarCollapsed && screenSize !== 'mobile' ? styles['content--expanded'] : '',
  ]
    .filter(Boolean)
    .join(' ')

  // Context value
  const contextValue: AppShellContextValue = {
    screenSize,
    sidebarCollapsed,
    sidebarOpen,
    toggleSidebar,
    toggleMobileSidebar,
    closeMobileSidebar,
  }

  return (
    <AppShellContext.Provider value={contextValue}>
      <div
        className={appShellClasses}
        data-screen-size={screenSize}
        data-theme={resolvedMode}
        style={
          {
            '--app-shell-primary': colors.primary,
            '--app-shell-background': colors.background,
            '--app-shell-surface': colors.surface,
            '--app-shell-text': colors.text,
            '--app-shell-border': colors.border,
          } as React.CSSProperties
        }
      >
        {/* Skip links for keyboard navigation - must be first focusable elements */}
        <SkipLinks />

        {/* Header slot */}
        {header && (
          <header className={styles.header} role="banner">
            {header}
          </header>
        )}

        {/* Main content area with sidebar and content */}
        <div className={styles.main}>
          {/* Mobile overlay */}
          {screenSize === 'mobile' && sidebarOpen && (
            <div
              className={styles.overlay}
              onClick={closeMobileSidebar}
              aria-hidden="true"
              data-testid="sidebar-overlay"
            />
          )}

          {/* Sidebar slot */}
          <aside
            id="main-navigation"
            className={sidebarClasses}
            aria-label="Main navigation"
            aria-hidden={screenSize === 'mobile' && !sidebarOpen}
          >
            {/* Sidebar toggle button (desktop/tablet) */}
            {screenSize !== 'mobile' && (
              <button
                type="button"
                onClick={toggleSidebar}
                aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
                aria-expanded={!sidebarCollapsed}
                className={styles.toggleButton}
                data-testid="sidebar-toggle"
              >
                <span className={styles.toggleIcon} aria-hidden="true">
                  {sidebarCollapsed ? '›' : '‹'}
                </span>
              </button>
            )}

            {/* Sidebar content */}
            <div className={styles.sidebarContent}>{sidebar}</div>
          </aside>

          {/* Main content */}
          <main
            ref={mainContentRef}
            className={contentClasses}
            role="main"
            id="main-content"
            tabIndex={-1}
          >
            {children}
          </main>
        </div>

        {/* Mobile menu toggle button (fixed position) */}
        {screenSize === 'mobile' && (
          <button
            type="button"
            onClick={toggleMobileSidebar}
            aria-label={sidebarOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={sidebarOpen}
            aria-controls="mobile-sidebar"
            className={styles.mobileToggleButton}
            data-testid="mobile-menu-toggle"
          >
            <span className={styles.hamburgerIcon} aria-hidden="true">
              {sidebarOpen ? '✕' : '☰'}
            </span>
          </button>
        )}
      </div>
    </AppShellContext.Provider>
  )
}

// Export context for testing
export { AppShellContext }

export default AppShell
