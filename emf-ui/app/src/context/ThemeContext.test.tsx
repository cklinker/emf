/**
 * ThemeContext Unit Tests
 *
 * Tests for the theme context and useTheme hook.
 * Validates requirements 16.1-16.7 for theme support.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  ThemeProvider,
  useTheme,
  getSystemTheme,
  getStoredTheme,
  storeTheme,
  resolveTheme,
  getThemeColors,
} from './ThemeContext'
import type { ThemeMode } from './ThemeContext'
import type { ThemeConfig } from '../types/config'

// Mock localStorage
const mockLocalStorage: Record<string, string> = {}
const localStorageMock = {
  getItem: vi.fn((key: string) => mockLocalStorage[key] || null),
  setItem: vi.fn((key: string, value: string) => {
    mockLocalStorage[key] = value
  }),
  removeItem: vi.fn((key: string) => {
    delete mockLocalStorage[key]
  }),
  clear: vi.fn(() => {
    Object.keys(mockLocalStorage).forEach((key) => delete mockLocalStorage[key])
  }),
  length: 0,
  key: vi.fn(),
}

// Mock matchMedia
let mockMatchMediaMatches = false
let mockMatchMediaListeners: Array<(event: MediaQueryListEvent) => void> = []

const mockMatchMedia = vi.fn((query: string) => ({
  matches: mockMatchMediaMatches,
  media: query,
  onchange: null,
  addListener: vi.fn((listener: (event: MediaQueryListEvent) => void) => {
    mockMatchMediaListeners.push(listener)
  }),
  removeListener: vi.fn((listener: (event: MediaQueryListEvent) => void) => {
    mockMatchMediaListeners = mockMatchMediaListeners.filter((l) => l !== listener)
  }),
  addEventListener: vi.fn((event: string, listener: (event: MediaQueryListEvent) => void) => {
    if (event === 'change') {
      mockMatchMediaListeners.push(listener)
    }
  }),
  removeEventListener: vi.fn((event: string, listener: (event: MediaQueryListEvent) => void) => {
    if (event === 'change') {
      mockMatchMediaListeners = mockMatchMediaListeners.filter((l) => l !== listener)
    }
  }),
  dispatchEvent: vi.fn(),
}))

// Test component that uses useTheme
function TestComponent({ onRender }: { onRender?: (theme: ReturnType<typeof useTheme>) => void }) {
  const theme = useTheme()
  onRender?.(theme)
  return (
    <div>
      <div data-testid="mode">{theme.mode}</div>
      <div data-testid="resolved-mode">{theme.resolvedMode}</div>
      <div data-testid="primary-color">{theme.colors.primary}</div>
      <button onClick={() => theme.setMode('light')}>Light</button>
      <button onClick={() => theme.setMode('dark')}>Dark</button>
      <button onClick={() => theme.setMode('system')}>System</button>
    </div>
  )
}

// Helper to render with ThemeProvider
function renderWithTheme(
  ui: React.ReactNode = <TestComponent />,
  props?: { initialMode?: ThemeMode; themeConfig?: ThemeConfig }
) {
  return render(<ThemeProvider {...props}>{ui}</ThemeProvider>)
}

describe('ThemeContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()

    // Clear mock storage
    Object.keys(mockLocalStorage).forEach((key) => delete mockLocalStorage[key])

    // Reset matchMedia mock
    mockMatchMediaMatches = false
    mockMatchMediaListeners = []

    // Setup localStorage mock
    Object.defineProperty(window, 'localStorage', {
      value: localStorageMock,
      writable: true,
    })

    // Setup matchMedia mock
    Object.defineProperty(window, 'matchMedia', {
      value: mockMatchMedia,
      writable: true,
    })

    // Clear document styles
    document.documentElement.style.cssText = ''
    document.documentElement.className = ''
    document.documentElement.removeAttribute('data-theme')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Initial State', () => {
    it('should default to system mode when no preference stored', () => {
      renderWithTheme()

      expect(screen.getByTestId('mode')).toHaveTextContent('system')
    })

    it('should use stored preference from localStorage', () => {
      mockLocalStorage['emf_theme_mode'] = 'dark'

      renderWithTheme()

      expect(screen.getByTestId('mode')).toHaveTextContent('dark')
      expect(screen.getByTestId('resolved-mode')).toHaveTextContent('dark')
    })

    it('should use initialMode prop when no stored preference', () => {
      renderWithTheme(<TestComponent />, { initialMode: 'light' })

      expect(screen.getByTestId('mode')).toHaveTextContent('light')
    })

    it('should prefer stored preference over initialMode prop', () => {
      mockLocalStorage['emf_theme_mode'] = 'dark'

      renderWithTheme(<TestComponent />, { initialMode: 'light' })

      expect(screen.getByTestId('mode')).toHaveTextContent('dark')
    })
  })

  describe('useTheme Hook', () => {
    it('should throw error when used outside ThemeProvider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      expect(() => {
        render(<TestComponent />)
      }).toThrow('useTheme must be used within a ThemeProvider')

      consoleSpy.mockRestore()
    })

    it('should provide theme context value', () => {
      let themeValue: ReturnType<typeof useTheme> | undefined

      renderWithTheme(
        <TestComponent
          onRender={(theme) => {
            themeValue = theme
          }}
        />
      )

      expect(themeValue).toBeDefined()
      expect(themeValue?.mode).toBeDefined()
      expect(themeValue?.resolvedMode).toBeDefined()
      expect(typeof themeValue?.setMode).toBe('function')
      expect(themeValue?.colors).toBeDefined()
    })
  })

  describe('Theme Mode Selection (Requirement 16.3)', () => {
    it('should allow setting light mode', async () => {
      const user = userEvent.setup()
      renderWithTheme()

      await user.click(screen.getByText('Light'))

      expect(screen.getByTestId('mode')).toHaveTextContent('light')
      expect(screen.getByTestId('resolved-mode')).toHaveTextContent('light')
    })

    it('should allow setting dark mode', async () => {
      const user = userEvent.setup()
      renderWithTheme()

      await user.click(screen.getByText('Dark'))

      expect(screen.getByTestId('mode')).toHaveTextContent('dark')
      expect(screen.getByTestId('resolved-mode')).toHaveTextContent('dark')
    })

    it('should allow setting system mode', async () => {
      const user = userEvent.setup()
      renderWithTheme(<TestComponent />, { initialMode: 'dark' })

      await user.click(screen.getByText('System'))

      expect(screen.getByTestId('mode')).toHaveTextContent('system')
    })
  })

  describe('Theme Persistence (Requirement 16.4)', () => {
    it('should persist theme preference to localStorage', async () => {
      const user = userEvent.setup()
      renderWithTheme()

      await user.click(screen.getByText('Dark'))

      expect(localStorageMock.setItem).toHaveBeenCalledWith('emf_theme_mode', 'dark')
    })

    it('should restore theme preference from localStorage on mount', () => {
      mockLocalStorage['emf_theme_mode'] = 'dark'

      renderWithTheme()

      expect(screen.getByTestId('mode')).toHaveTextContent('dark')
    })
  })

  describe('System Theme Detection (Requirement 16.2)', () => {
    it('should detect light system preference', () => {
      mockMatchMediaMatches = false

      renderWithTheme(<TestComponent />, { initialMode: 'system' })

      expect(screen.getByTestId('resolved-mode')).toHaveTextContent('light')
    })

    it('should detect dark system preference', () => {
      mockMatchMediaMatches = true

      renderWithTheme(<TestComponent />, { initialMode: 'system' })

      expect(screen.getByTestId('resolved-mode')).toHaveTextContent('dark')
    })

    it('should update when system preference changes', async () => {
      mockMatchMediaMatches = false

      renderWithTheme(<TestComponent />, { initialMode: 'system' })

      expect(screen.getByTestId('resolved-mode')).toHaveTextContent('light')

      // Simulate system theme change
      act(() => {
        mockMatchMediaListeners.forEach((listener) => {
          listener({ matches: true } as MediaQueryListEvent)
        })
      })

      await waitFor(() => {
        expect(screen.getByTestId('resolved-mode')).toHaveTextContent('dark')
      })
    })
  })

  describe('CSS Custom Properties (Requirement 16.5)', () => {
    it('should apply CSS custom properties to document root', () => {
      renderWithTheme(<TestComponent />, { initialMode: 'light' })

      const root = document.documentElement
      expect(root.style.getPropertyValue('--color-primary')).toBeTruthy()
      expect(root.style.getPropertyValue('--color-background')).toBeTruthy()
      expect(root.style.getPropertyValue('--color-text')).toBeTruthy()
    })

    it('should update CSS custom properties when theme changes', async () => {
      const user = userEvent.setup()
      renderWithTheme(<TestComponent />, { initialMode: 'light' })

      const root = document.documentElement
      const lightBackground = root.style.getPropertyValue('--color-background')

      await user.click(screen.getByText('Dark'))

      const darkBackground = root.style.getPropertyValue('--color-background')
      expect(darkBackground).not.toBe(lightBackground)
    })

    it('should apply theme class to document root', () => {
      renderWithTheme(<TestComponent />, { initialMode: 'light' })

      const root = document.documentElement
      expect(root.classList.contains('theme-light')).toBe(true)
      expect(root.getAttribute('data-theme')).toBe('light')
    })

    it('should update theme class when theme changes', async () => {
      const user = userEvent.setup()
      renderWithTheme(<TestComponent />, { initialMode: 'light' })

      const root = document.documentElement
      expect(root.classList.contains('theme-light')).toBe(true)

      await user.click(screen.getByText('Dark'))

      expect(root.classList.contains('theme-dark')).toBe(true)
      expect(root.classList.contains('theme-light')).toBe(false)
      expect(root.getAttribute('data-theme')).toBe('dark')
    })
  })

  describe('Bootstrap Theme Configuration (Requirement 16.6)', () => {
    it('should apply theme colors from bootstrap configuration', () => {
      const themeConfig: ThemeConfig = {
        primaryColor: '#ff0000',
        secondaryColor: '#00ff00',
        fontFamily: 'Arial, sans-serif',
        borderRadius: '8px',
      }

      renderWithTheme(<TestComponent />, { themeConfig, initialMode: 'light' })

      expect(screen.getByTestId('primary-color')).toHaveTextContent('#ff0000')

      const root = document.documentElement
      expect(root.style.getPropertyValue('--color-primary')).toBe('#ff0000')
      expect(root.style.getPropertyValue('--font-family')).toBe('Arial, sans-serif')
      expect(root.style.getPropertyValue('--border-radius')).toBe('8px')
    })
  })

  describe('Theme Colors', () => {
    it('should provide light theme colors', () => {
      let themeValue: ReturnType<typeof useTheme> | undefined

      renderWithTheme(
        <TestComponent
          onRender={(theme) => {
            themeValue = theme
          }}
        />,
        { initialMode: 'light' }
      )

      expect(themeValue?.colors.background).toBe('#ffffff')
      expect(themeValue?.colors.text).toBe('#212529')
    })

    it('should provide dark theme colors', () => {
      let themeValue: ReturnType<typeof useTheme> | undefined

      renderWithTheme(
        <TestComponent
          onRender={(theme) => {
            themeValue = theme
          }}
        />,
        { initialMode: 'dark' }
      )

      expect(themeValue?.colors.background).toBe('#121212')
      expect(themeValue?.colors.text).toBe('#f8f9fa')
    })
  })

  describe('Utility Functions', () => {
    describe('getSystemTheme', () => {
      it('should return light when system prefers light', () => {
        mockMatchMediaMatches = false
        expect(getSystemTheme()).toBe('light')
      })

      it('should return dark when system prefers dark', () => {
        mockMatchMediaMatches = true
        expect(getSystemTheme()).toBe('dark')
      })
    })

    describe('getStoredTheme', () => {
      it('should return null when no theme stored', () => {
        expect(getStoredTheme()).toBeNull()
      })

      it('should return stored theme', () => {
        mockLocalStorage['emf_theme_mode'] = 'dark'
        expect(getStoredTheme()).toBe('dark')
      })

      it('should return null for invalid stored value', () => {
        mockLocalStorage['emf_theme_mode'] = 'invalid'
        expect(getStoredTheme()).toBeNull()
      })
    })

    describe('storeTheme', () => {
      it('should store theme to localStorage', () => {
        storeTheme('dark')
        expect(localStorageMock.setItem).toHaveBeenCalledWith('emf_theme_mode', 'dark')
      })
    })

    describe('resolveTheme', () => {
      it('should return light for light mode', () => {
        expect(resolveTheme('light')).toBe('light')
      })

      it('should return dark for dark mode', () => {
        expect(resolveTheme('dark')).toBe('dark')
      })

      it('should return system preference for system mode', () => {
        mockMatchMediaMatches = true
        expect(resolveTheme('system')).toBe('dark')

        mockMatchMediaMatches = false
        expect(resolveTheme('system')).toBe('light')
      })
    })

    describe('getThemeColors', () => {
      it('should return light theme colors for light mode', () => {
        const colors = getThemeColors('light')
        expect(colors.background).toBe('#ffffff')
      })

      it('should return dark theme colors for dark mode', () => {
        const colors = getThemeColors('dark')
        expect(colors.background).toBe('#121212')
      })

      it('should apply theme config overrides', () => {
        const themeConfig: ThemeConfig = {
          primaryColor: '#ff0000',
          secondaryColor: '#00ff00',
          fontFamily: 'Arial',
          borderRadius: '4px',
        }

        const colors = getThemeColors('light', themeConfig)
        expect(colors.primary).toBe('#ff0000')
        expect(colors.secondary).toBe('#00ff00')
      })
    })
  })

  describe('Accessibility (Requirement 16.7)', () => {
    it('should have sufficient contrast in light theme', () => {
      let themeValue: ReturnType<typeof useTheme> | undefined

      renderWithTheme(
        <TestComponent
          onRender={(theme) => {
            themeValue = theme
          }}
        />,
        { initialMode: 'light' }
      )

      // Light theme should have dark text on light background
      // #212529 on #ffffff has contrast ratio > 4.5:1
      expect(themeValue?.colors.text).toBe('#212529')
      expect(themeValue?.colors.background).toBe('#ffffff')
    })

    it('should have sufficient contrast in dark theme', () => {
      let themeValue: ReturnType<typeof useTheme> | undefined

      renderWithTheme(
        <TestComponent
          onRender={(theme) => {
            themeValue = theme
          }}
        />,
        { initialMode: 'dark' }
      )

      // Dark theme should have light text on dark background
      // #f8f9fa on #121212 has contrast ratio > 4.5:1
      expect(themeValue?.colors.text).toBe('#f8f9fa')
      expect(themeValue?.colors.background).toBe('#121212')
    })
  })
})
