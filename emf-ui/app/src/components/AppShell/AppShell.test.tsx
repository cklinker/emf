/**
 * AppShell Component Unit Tests
 *
 * Tests for the main application shell layout component.
 * Validates requirements 14.2 (keyboard accessibility), 17.1, 17.2, 17.3 for responsive design.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppShell, useAppShell } from './AppShell';
import { BREAKPOINTS } from './constants';
import { ThemeProvider } from '../../context/ThemeContext';

// Mock window.matchMedia for ThemeContext
const mockMatchMedia = vi.fn((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}));

// Store original window properties
let originalInnerWidth: number;
let originalMatchMedia: typeof window.matchMedia;

// Helper to render with ThemeProvider
function renderWithTheme(ui: React.ReactNode) {
  return render(
    <ThemeProvider initialMode="light">
      {ui}
    </ThemeProvider>
  );
}

// Helper to set window width and trigger resize
function setWindowWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', {
    writable: true,
    configurable: true,
    value: width,
  });
  
  // Trigger resize event
  act(() => {
    window.dispatchEvent(new Event('resize'));
  });
}

// Test component that uses useAppShell hook
function TestConsumer({ onRender }: { onRender?: (context: ReturnType<typeof useAppShell>) => void }) {
  const context = useAppShell();
  onRender?.(context);
  return (
    <div>
      <div data-testid="screen-size">{context.screenSize}</div>
      <div data-testid="sidebar-collapsed">{String(context.sidebarCollapsed)}</div>
      <div data-testid="sidebar-open">{String(context.sidebarOpen)}</div>
      <button onClick={context.toggleSidebar} data-testid="toggle-sidebar">Toggle Sidebar</button>
      <button onClick={context.toggleMobileSidebar} data-testid="toggle-mobile">Toggle Mobile</button>
      <button onClick={context.closeMobileSidebar} data-testid="close-mobile">Close Mobile</button>
    </div>
  );
}

describe('AppShell', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    
    // Store original values
    originalInnerWidth = window.innerWidth;
    originalMatchMedia = window.matchMedia;
    
    // Setup matchMedia mock
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: mockMatchMedia,
    });
    
    // Default to desktop width
    setWindowWidth(1200);
    
    // Clear body styles
    document.body.style.overflow = '';
  });

  afterEach(() => {
    // Restore original values
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: originalMatchMedia,
    });
    
    document.body.style.overflow = '';
  });

  describe('Basic Rendering', () => {
    it('should render children content', () => {
      renderWithTheme(
        <AppShell>
          <div data-testid="content">Main Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('content')).toBeInTheDocument();
      expect(screen.getByText('Main Content')).toBeInTheDocument();
    });

    it('should render header slot when provided', () => {
      renderWithTheme(
        <AppShell header={<div data-testid="header">Header Content</div>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('header')).toBeInTheDocument();
      expect(screen.getByText('Header Content')).toBeInTheDocument();
    });

    it('should render sidebar slot when provided', () => {
      renderWithTheme(
        <AppShell sidebar={<div data-testid="sidebar">Sidebar Content</div>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('sidebar')).toBeInTheDocument();
      expect(screen.getByText('Sidebar Content')).toBeInTheDocument();
    });

    it('should render all slots together', () => {
      renderWithTheme(
        <AppShell
          header={<div data-testid="header">Header</div>}
          sidebar={<div data-testid="sidebar">Sidebar</div>}
        >
          <div data-testid="content">Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('header')).toBeInTheDocument();
      expect(screen.getByTestId('sidebar')).toBeInTheDocument();
      expect(screen.getByTestId('content')).toBeInTheDocument();
    });

    it('should not render header when not provided', () => {
      renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.queryByRole('banner')).not.toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels on sidebar', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Navigation</nav>}>
          <div>Content</div>
        </AppShell>
      );

      const sidebar = screen.getByLabelText('Main navigation');
      expect(sidebar).toBeInTheDocument();
    });

    it('should have proper role on main content', () => {
      renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByRole('main')).toBeInTheDocument();
    });

    it('should have proper role on header', () => {
      renderWithTheme(
        <AppShell header={<div>Header</div>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByRole('banner')).toBeInTheDocument();
    });

    it('should have skip links for accessibility', () => {
      renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('skip-links')).toBeInTheDocument();
      expect(screen.getByText('Skip to main content')).toBeInTheDocument();
      expect(screen.getByText('Skip to navigation')).toBeInTheDocument();
    });

    it('should have skip link that targets main content', () => {
      renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const skipLink = screen.getByText('Skip to main content');
      expect(skipLink).toHaveAttribute('href', '#main-content');
      
      // Main content should have the matching id
      const mainContent = screen.getByRole('main');
      expect(mainContent).toHaveAttribute('id', 'main-content');
    });

    it('should have skip link that targets navigation', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      const skipLink = screen.getByText('Skip to navigation');
      expect(skipLink).toHaveAttribute('href', '#main-navigation');
      
      // Sidebar should have the matching id
      const sidebar = screen.getByLabelText('Main navigation');
      expect(sidebar).toHaveAttribute('id', 'main-navigation');
    });

    it('should have main content focusable for skip link', () => {
      renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const mainContent = screen.getByRole('main');
      expect(mainContent).toHaveAttribute('tabindex', '-1');
    });

    it('should have aria-expanded on sidebar toggle button', () => {
      setWindowWidth(1200); // Desktop
      
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      const toggleButton = screen.getByTestId('sidebar-toggle');
      expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    });
  });

  describe('Desktop Layout (Requirement 17.1)', () => {
    beforeEach(() => {
      setWindowWidth(1200); // Desktop width (>= 1024px)
    });

    it('should apply desktop screen size', () => {
      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('screen-size')).toHaveTextContent('desktop');
    });

    it('should show sidebar toggle button on desktop', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-toggle')).toBeInTheDocument();
    });

    it('should not show mobile toggle button on desktop', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.queryByTestId('mobile-menu-toggle')).not.toBeInTheDocument();
    });

    it('should have data-screen-size attribute set to desktop', () => {
      const { container } = renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const appShell = container.querySelector('[data-screen-size="desktop"]');
      expect(appShell).toBeInTheDocument();
    });
  });

  describe('Tablet Layout (Requirement 17.2)', () => {
    beforeEach(() => {
      setWindowWidth(900); // Tablet width (768px - 1023px)
    });

    it('should apply tablet screen size', () => {
      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('screen-size')).toHaveTextContent('tablet');
    });

    it('should show sidebar toggle button on tablet', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-toggle')).toBeInTheDocument();
    });

    it('should not show mobile toggle button on tablet', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.queryByTestId('mobile-menu-toggle')).not.toBeInTheDocument();
    });

    it('should have data-screen-size attribute set to tablet', () => {
      const { container } = renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const appShell = container.querySelector('[data-screen-size="tablet"]');
      expect(appShell).toBeInTheDocument();
    });
  });

  describe('Mobile Layout (Requirement 17.3)', () => {
    beforeEach(() => {
      setWindowWidth(500); // Mobile width (< 768px)
    });

    it('should apply mobile screen size', () => {
      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('screen-size')).toHaveTextContent('mobile');
    });

    it('should show mobile toggle button on mobile', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.getByTestId('mobile-menu-toggle')).toBeInTheDocument();
    });

    it('should not show desktop sidebar toggle on mobile', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.queryByTestId('sidebar-toggle')).not.toBeInTheDocument();
    });

    it('should have data-screen-size attribute set to mobile', () => {
      const { container } = renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const appShell = container.querySelector('[data-screen-size="mobile"]');
      expect(appShell).toBeInTheDocument();
    });

    it('should have aria-hidden on sidebar when closed on mobile', () => {
      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      const sidebar = screen.getByLabelText('Main navigation');
      expect(sidebar).toHaveAttribute('aria-hidden', 'true');
    });
  });

  describe('Sidebar Toggle Functionality', () => {
    it('should toggle sidebar collapsed state on desktop', async () => {
      const user = userEvent.setup();
      setWindowWidth(1200);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-collapsed')).toHaveTextContent('false');

      await user.click(screen.getByTestId('sidebar-toggle'));

      expect(screen.getByTestId('sidebar-collapsed')).toHaveTextContent('true');
    });

    it('should toggle mobile sidebar open state', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('false');

      await user.click(screen.getByTestId('mobile-menu-toggle'));

      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('true');
    });

    it('should show overlay when mobile sidebar is open', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(screen.queryByTestId('sidebar-overlay')).not.toBeInTheDocument();

      await user.click(screen.getByTestId('mobile-menu-toggle'));

      expect(screen.getByTestId('sidebar-overlay')).toBeInTheDocument();
    });

    it('should close mobile sidebar when clicking overlay', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <TestConsumer />
        </AppShell>
      );

      // Open sidebar
      await user.click(screen.getByTestId('mobile-menu-toggle'));
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('true');

      // Click overlay
      await user.click(screen.getByTestId('sidebar-overlay'));
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('false');
    });

    it('should close mobile sidebar on Escape key', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <TestConsumer />
        </AppShell>
      );

      // Open sidebar
      await user.click(screen.getByTestId('mobile-menu-toggle'));
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('true');

      // Press Escape
      await user.keyboard('{Escape}');
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('false');
    });

    it('should update aria-expanded when toggling sidebar', async () => {
      const user = userEvent.setup();
      setWindowWidth(1200);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      const toggleButton = screen.getByTestId('sidebar-toggle');
      expect(toggleButton).toHaveAttribute('aria-expanded', 'true');

      await user.click(toggleButton);
      expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    });
  });

  describe('Responsive Transitions', () => {
    it('should close mobile sidebar when resizing to larger screen', async () => {
      const user = userEvent.setup();
      setWindowWidth(500); // Start mobile

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <TestConsumer />
        </AppShell>
      );

      // Open mobile sidebar
      await user.click(screen.getByTestId('mobile-menu-toggle'));
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('true');

      // Resize to desktop
      setWindowWidth(1200);

      await waitFor(() => {
        expect(screen.getByTestId('sidebar-open')).toHaveTextContent('false');
      });
    });

    it('should update screen size on window resize', async () => {
      setWindowWidth(1200); // Start desktop

      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('screen-size')).toHaveTextContent('desktop');

      // Resize to tablet
      setWindowWidth(900);

      await waitFor(() => {
        expect(screen.getByTestId('screen-size')).toHaveTextContent('tablet');
      });

      // Resize to mobile
      setWindowWidth(500);

      await waitFor(() => {
        expect(screen.getByTestId('screen-size')).toHaveTextContent('mobile');
      });
    });
  });

  describe('useAppShell Hook', () => {
    it('should throw error when used outside AppShell', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        renderWithTheme(<TestConsumer />);
      }).toThrow('useAppShell must be used within an AppShell');

      consoleSpy.mockRestore();
    });

    it('should provide context values', () => {
      let contextValue: ReturnType<typeof useAppShell> | undefined;

      renderWithTheme(
        <AppShell>
          <TestConsumer onRender={(ctx) => { contextValue = ctx; }} />
        </AppShell>
      );

      expect(contextValue).toBeDefined();
      expect(contextValue?.screenSize).toBeDefined();
      expect(typeof contextValue?.sidebarCollapsed).toBe('boolean');
      expect(typeof contextValue?.sidebarOpen).toBe('boolean');
      expect(typeof contextValue?.toggleSidebar).toBe('function');
      expect(typeof contextValue?.toggleMobileSidebar).toBe('function');
      expect(typeof contextValue?.closeMobileSidebar).toBe('function');
    });

    it('should allow toggling sidebar via context', async () => {
      const user = userEvent.setup();
      setWindowWidth(1200);

      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-collapsed')).toHaveTextContent('false');

      await user.click(screen.getByTestId('toggle-sidebar'));

      expect(screen.getByTestId('sidebar-collapsed')).toHaveTextContent('true');
    });

    it('should allow toggling mobile sidebar via context', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('false');

      await user.click(screen.getByTestId('toggle-mobile'));

      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('true');
    });

    it('should allow closing mobile sidebar via context', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      // Open first
      await user.click(screen.getByTestId('toggle-mobile'));
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('true');

      // Close via context
      await user.click(screen.getByTestId('close-mobile'));
      expect(screen.getByTestId('sidebar-open')).toHaveTextContent('false');
    });
  });

  describe('Initial State', () => {
    it('should respect initialSidebarCollapsed prop', () => {
      setWindowWidth(1200);

      renderWithTheme(
        <AppShell initialSidebarCollapsed={true}>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-collapsed')).toHaveTextContent('true');
    });

    it('should default to sidebar expanded', () => {
      setWindowWidth(1200);

      renderWithTheme(
        <AppShell>
          <TestConsumer />
        </AppShell>
      );

      expect(screen.getByTestId('sidebar-collapsed')).toHaveTextContent('false');
    });
  });

  describe('Theme Integration', () => {
    it('should have data-theme attribute', () => {
      const { container } = renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const appShell = container.querySelector('[data-theme]');
      expect(appShell).toBeInTheDocument();
      expect(appShell).toHaveAttribute('data-theme', 'light');
    });

    it('should apply theme CSS custom properties', () => {
      const { container } = renderWithTheme(
        <AppShell>
          <div>Content</div>
        </AppShell>
      );

      const appShell = container.firstChild as HTMLElement;
      expect(appShell.style.getPropertyValue('--app-shell-primary')).toBeTruthy();
      expect(appShell.style.getPropertyValue('--app-shell-background')).toBeTruthy();
    });
  });

  describe('Body Scroll Lock', () => {
    it('should lock body scroll when mobile sidebar is open', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      expect(document.body.style.overflow).toBe('');

      await user.click(screen.getByTestId('mobile-menu-toggle'));

      expect(document.body.style.overflow).toBe('hidden');
    });

    it('should unlock body scroll when mobile sidebar is closed', async () => {
      const user = userEvent.setup();
      setWindowWidth(500);

      renderWithTheme(
        <AppShell sidebar={<nav>Nav</nav>}>
          <div>Content</div>
        </AppShell>
      );

      // Open sidebar
      await user.click(screen.getByTestId('mobile-menu-toggle'));
      expect(document.body.style.overflow).toBe('hidden');

      // Close sidebar
      await user.click(screen.getByTestId('mobile-menu-toggle'));
      expect(document.body.style.overflow).toBe('');
    });
  });

  describe('BREAKPOINTS constant', () => {
    it('should export correct breakpoint values', () => {
      expect(BREAKPOINTS.mobile).toBe(768);
      expect(BREAKPOINTS.tablet).toBe(1024);
    });
  });
});
