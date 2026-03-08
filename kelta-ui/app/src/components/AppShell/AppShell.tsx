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
import { Menu, X } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext'
import { useEscapeKey } from '../../hooks/useKeyboardShortcuts'
import { SkipLinks } from '../SkipLinks'
import { cn } from '@/lib/utils'
import { BREAKPOINTS, type ScreenSize } from './constants'

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
// eslint-disable-next-line react-refresh/only-export-components
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
        className={cn(
          'flex flex-col h-screen w-full overflow-hidden bg-background text-foreground',
          resolvedMode === 'dark'
            ? '[--sidebar-bg:var(--color-background-secondary,#1e1e1e)] [--sidebar-border:var(--color-border,#3d3d3d)]'
            : '[--sidebar-bg:var(--color-background-secondary,#f8f9fa)] [--sidebar-border:var(--color-border,#dee2e6)]'
        )}
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
          <header
            className="sticky top-0 z-[100] h-[60px] shrink-0 bg-[var(--app-shell-surface,var(--color-surface,#ffffff))] border-b border-[var(--app-shell-border,var(--color-border,#e0e0e0))] flex items-center print:border-b print:border-black forced-colors:border-b-2 forced-colors:border-current"
            role="banner"
          >
            {header}
          </header>
        )}

        {/* Main content area with sidebar and content */}
        <div className="flex flex-1 relative overflow-hidden">
          {/* Mobile overlay */}
          {screenSize === 'mobile' && sidebarOpen && (
            <div
              className="fixed inset-0 bg-black/50 z-[150] transition-opacity duration-200 ease-in-out motion-reduce:transition-none"
              onClick={closeMobileSidebar}
              aria-hidden="true"
              data-testid="sidebar-overlay"
            />
          )}

          {/* Sidebar slot */}
          <aside
            id="main-navigation"
            className={cn(
              'shrink-0 bg-[var(--sidebar-bg,var(--color-background-secondary,#f5f5f5))] border-r border-[var(--sidebar-border,var(--color-border,#e0e0e0))] flex flex-col overflow-hidden transition-[width,transform] duration-200 ease-in-out motion-reduce:transition-none print:hidden forced-colors:border-r-2 forced-colors:border-current',
              // Desktop sizing
              screenSize === 'desktop' && !sidebarCollapsed && 'w-[250px]',
              screenSize === 'desktop' && sidebarCollapsed && 'w-16',
              // Tablet sizing
              screenSize === 'tablet' && !sidebarCollapsed && 'w-[220px]',
              screenSize === 'tablet' && sidebarCollapsed && 'w-16',
              // Mobile: fixed position overlay sidebar
              screenSize === 'mobile' &&
                'fixed top-[60px] left-0 bottom-0 w-[280px] max-w-[85vw] z-[200] border-r-0 shadow-none -translate-x-full',
              screenSize === 'mobile' &&
                sidebarOpen &&
                'translate-x-0 shadow-[4px_0_16px_rgba(0,0,0,0.1)]'
            )}
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
                className="flex items-center justify-center w-8 h-8 m-2 p-0 bg-transparent border border-[var(--app-shell-border,var(--color-border,#e0e0e0))] rounded cursor-pointer text-[var(--app-shell-text,var(--color-text,#1a1a1a))] transition-[background-color,border-color] duration-150 ease-in-out hover:bg-[var(--color-surface-hover,rgba(0,0,0,0.05))] focus:outline-2 focus:outline-[var(--color-focus,#0066cc)] focus:outline-offset-2 focus-visible:outline-2 focus-visible:outline-[var(--color-focus,#0066cc)] focus-visible:outline-offset-2 [&:focus:not(:focus-visible)]:outline-none motion-reduce:transition-none print:hidden forced-colors:border-2 forced-colors:border-current"
                data-testid="sidebar-toggle"
              >
                <span className="text-base leading-none" aria-hidden="true">
                  {sidebarCollapsed ? '\u203A' : '\u2039'}
                </span>
              </button>
            )}

            {/* Sidebar content */}
            <div
              className={cn(
                'flex-1 overflow-y-auto overflow-x-hidden',
                sidebarCollapsed && screenSize !== 'mobile' && 'overflow-hidden'
              )}
            >
              {sidebar}
            </div>
          </aside>

          {/* Main content */}
          <main
            ref={mainContentRef}
            className={cn(
              'flex-1 overflow-y-auto overflow-x-hidden bg-[var(--app-shell-background,var(--color-background,#ffffff))] focus:outline-none focus-visible:outline-2 focus-visible:outline-[var(--color-focus,#0066cc)] focus-visible:outline-offset-[-2px] print:p-0 print:overflow-visible',
              screenSize === 'desktop' && 'p-8',
              screenSize === 'tablet' && 'p-6',
              screenSize === 'mobile' && 'p-4 w-full'
            )}
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
            className="fixed bottom-6 right-6 z-[250] flex items-center justify-center w-14 h-14 p-0 bg-[var(--app-shell-primary,var(--color-primary,#0066cc))] border-none rounded-full cursor-pointer text-[var(--color-text-inverse,#ffffff)] shadow-[0_4px_12px_rgba(0,0,0,0.15)] transition-[background-color,transform,box-shadow] duration-150 ease-in-out hover:bg-[var(--color-primary-hover,#0052a3)] hover:scale-105 focus:outline-2 focus:outline-[var(--color-focus,#0066cc)] focus:outline-offset-2 focus-visible:outline-2 focus-visible:outline-[var(--color-focus,#0066cc)] focus-visible:outline-offset-2 [&:focus:not(:focus-visible)]:outline-none active:scale-95 motion-reduce:transition-none motion-reduce:hover:transform-none motion-reduce:active:transform-none print:hidden forced-colors:border-2 forced-colors:border-current"
            data-testid="mobile-menu-toggle"
          >
            <span className="text-2xl leading-none" aria-hidden="true">
              {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
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
