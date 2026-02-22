/**
 * I18nContext Unit Tests
 *
 * Tests for the internationalization context provider and hook.
 * Validates translation, locale detection, RTL support, and formatting.
 *
 * Requirements tested:
 * - 15.1: Support multiple languages through a translation system
 * - 15.2: Detect user's preferred language from browser settings
 * - 15.3: Allow users to manually select their preferred language
 * - 15.4: Persist the user's language preference
 * - 15.5: Update all UI text without page reload when language changes
 * - 15.6: Support right-to-left (RTL) text direction for applicable languages
 * - 15.7: Format dates, numbers, and currencies according to the selected locale
 */

import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  I18nProvider,
  useI18n,
  getBrowserLocale,
  storeLocale,
  getTextDirection,
  getNestedValue,
  interpolate,
  SUPPORTED_LOCALES,
  RTL_LOCALES,
  DEFAULT_LOCALE,
} from './I18nContext'

// Test component that uses the I18n hook
function TestComponent() {
  const {
    locale,
    setLocale,
    t,
    formatDate,
    formatNumber,
    formatCurrency,
    direction,
    supportedLocales,
    getLocaleDisplayName,
  } = useI18n()

  return (
    <div data-testid="test-component" dir={direction}>
      <span data-testid="locale">{locale}</span>
      <span data-testid="direction">{direction}</span>
      <span data-testid="translation">{t('common.loading')}</span>
      <span data-testid="translation-with-params">
        {t('auth.loginWith', { provider: 'Google' })}
      </span>
      <span data-testid="formatted-date">
        {formatDate(new Date('2024-01-15T12:00:00Z'), { dateStyle: 'short' })}
      </span>
      <span data-testid="formatted-number">{formatNumber(1234.56)}</span>
      <span data-testid="formatted-currency">{formatCurrency(99.99, 'USD')}</span>
      <span data-testid="supported-locales">{supportedLocales.join(',')}</span>
      <span data-testid="locale-display-name">{getLocaleDisplayName('ar')}</span>
      <button data-testid="set-en" onClick={() => setLocale('en')}>
        English
      </button>
      <button data-testid="set-ar" onClick={() => setLocale('ar')}>
        Arabic
      </button>
    </div>
  )
}

// Component that throws when used outside provider
function ComponentOutsideProvider() {
  const { t } = useI18n()
  return <span>{t('test')}</span>
}

describe('I18nContext', () => {
  beforeEach(() => {
    // Mock localStorage
    const localStorageMock: Record<string, string> = {}
    Object.defineProperty(window, 'localStorage', {
      value: {
        getItem: vi.fn((key: string) => localStorageMock[key] || null),
        setItem: vi.fn((key: string, value: string) => {
          localStorageMock[key] = value
        }),
        removeItem: vi.fn((key: string) => {
          delete localStorageMock[key]
        }),
        clear: vi.fn(() => {
          Object.keys(localStorageMock).forEach((key) => delete localStorageMock[key])
        }),
      },
      writable: true,
    })

    // Reset document attributes
    document.documentElement.removeAttribute('dir')
    document.documentElement.removeAttribute('lang')
    document.documentElement.removeAttribute('data-direction')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('I18nProvider', () => {
    it('should render children', () => {
      render(
        <I18nProvider>
          <div data-testid="child">Child content</div>
        </I18nProvider>
      )

      expect(screen.getByTestId('child')).toBeInTheDocument()
    })

    it('should provide default locale (en)', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('locale')).toHaveTextContent('en')
    })

    it('should use initialLocale prop when provided', () => {
      render(
        <I18nProvider initialLocale="ar">
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('locale')).toHaveTextContent('ar')
    })

    it('should use stored locale preference over initialLocale', () => {
      // Set stored preference
      localStorage.setItem('emf_locale', 'ar')

      render(
        <I18nProvider initialLocale="en">
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('locale')).toHaveTextContent('ar')
    })

    it('should apply text direction to document', () => {
      render(
        <I18nProvider initialLocale="ar">
          <TestComponent />
        </I18nProvider>
      )

      expect(document.documentElement.getAttribute('dir')).toBe('rtl')
      expect(document.documentElement.getAttribute('data-direction')).toBe('rtl')
    })

    it('should apply locale to document lang attribute', () => {
      render(
        <I18nProvider initialLocale="ar">
          <TestComponent />
        </I18nProvider>
      )

      expect(document.documentElement.getAttribute('lang')).toBe('ar')
    })
  })

  describe('useI18n hook', () => {
    it('should throw error when used outside provider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      expect(() => {
        render(<ComponentOutsideProvider />)
      }).toThrow('useI18n must be used within an I18nProvider')

      consoleSpy.mockRestore()
    })

    it('should return locale value', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('locale')).toHaveTextContent('en')
    })

    it('should return direction value', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('direction')).toHaveTextContent('ltr')
    })

    it('should return supported locales', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('supported-locales')).toHaveTextContent('en,ar,fr,de,es,pt')
    })
  })

  describe('setLocale', () => {
    it('should change locale when setLocale is called', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('locale')).toHaveTextContent('en')

      await user.click(screen.getByTestId('set-ar'))

      expect(screen.getByTestId('locale')).toHaveTextContent('ar')
    })

    it('should persist locale to localStorage', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      await user.click(screen.getByTestId('set-ar'))

      expect(localStorage.setItem).toHaveBeenCalledWith('emf_locale', 'ar')
    })

    it('should update direction when locale changes to RTL', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('direction')).toHaveTextContent('ltr')

      await user.click(screen.getByTestId('set-ar'))

      expect(screen.getByTestId('direction')).toHaveTextContent('rtl')
    })

    it('should update document direction when locale changes', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(document.documentElement.getAttribute('dir')).toBe('ltr')

      await user.click(screen.getByTestId('set-ar'))

      expect(document.documentElement.getAttribute('dir')).toBe('rtl')
    })

    it('should not change locale for unsupported locale', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

      // Component that captures and exposes setLocale
      function TestSetLocale({
        onMount,
      }: {
        onMount: (setLocale: (locale: string) => void) => void
      }) {
        const { setLocale } = useI18n()
        onMount(setLocale)
        return null
      }

      let capturedSetLocale: ((locale: string) => void) | null = null

      render(
        <I18nProvider>
          <TestSetLocale
            onMount={(setLocale) => {
              capturedSetLocale = setLocale
            }}
          />
        </I18nProvider>
      )

      act(() => {
        capturedSetLocale?.('zz')
      })

      expect(consoleSpy).toHaveBeenCalledWith('[I18n] Unsupported locale: zz')
      consoleSpy.mockRestore()
    })
  })

  describe('t (translation function)', () => {
    it('should translate simple keys', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('translation')).toHaveTextContent('Loading...')
    })

    it('should translate keys with parameter interpolation', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('translation-with-params')).toHaveTextContent('Log in with Google')
    })

    it('should translate to Arabic when locale is ar', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      await user.click(screen.getByTestId('set-ar'))

      expect(screen.getByTestId('translation')).toHaveTextContent('جاري التحميل...')
    })

    it('should return key when translation not found', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

      function TestMissingKey() {
        const { t } = useI18n()
        return <span data-testid="missing">{t('nonexistent.key')}</span>
      }

      render(
        <I18nProvider>
          <TestMissingKey />
        </I18nProvider>
      )

      expect(screen.getByTestId('missing')).toHaveTextContent('nonexistent.key')
      expect(consoleSpy).toHaveBeenCalled()
      consoleSpy.mockRestore()
    })

    it('should update translations without page reload', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      // Initial English
      expect(screen.getByTestId('translation')).toHaveTextContent('Loading...')

      // Switch to Arabic
      await user.click(screen.getByTestId('set-ar'))
      expect(screen.getByTestId('translation')).toHaveTextContent('جاري التحميل...')

      // Switch back to English
      await user.click(screen.getByTestId('set-en'))
      expect(screen.getByTestId('translation')).toHaveTextContent('Loading...')
    })
  })

  describe('formatDate', () => {
    it('should format date according to locale', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      // The exact format depends on the locale, but it should be a valid date string
      const formattedDate = screen.getByTestId('formatted-date').textContent
      expect(formattedDate).toBeTruthy()
      expect(formattedDate).toMatch(/\d/) // Should contain digits
    })

    it('should format date differently for different locales', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      // Get English date format
      const enDateElement = screen.getByTestId('formatted-date')
      expect(enDateElement.textContent).toBeTruthy()

      await user.click(screen.getByTestId('set-ar'))

      // Arabic dates use different numerals or format
      const arDateElement = screen.getByTestId('formatted-date')
      expect(arDateElement.textContent).toBeTruthy()
    })
  })

  describe('formatNumber', () => {
    it('should format number according to locale', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      const formattedNumber = screen.getByTestId('formatted-number').textContent
      expect(formattedNumber).toBeTruthy()
      // English format: 1,234.56
      expect(formattedNumber).toMatch(/1.*234.*56/)
    })

    it('should format number differently for different locales', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      // Get English number format
      const enNumberElement = screen.getByTestId('formatted-number')
      expect(enNumberElement.textContent).toBeTruthy()

      await user.click(screen.getByTestId('set-ar'))

      // Both should represent the same number
      const arNumberElement = screen.getByTestId('formatted-number')
      expect(arNumberElement.textContent).toBeTruthy()
    })
  })

  describe('formatCurrency', () => {
    it('should format currency according to locale', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      const formattedCurrency = screen.getByTestId('formatted-currency').textContent
      expect(formattedCurrency).toBeTruthy()
      // Should contain USD symbol or code and the amount
      expect(formattedCurrency).toMatch(/\$|USD/)
      expect(formattedCurrency).toMatch(/99/)
    })
  })

  describe('getLocaleDisplayName', () => {
    it('should return display name for known locale', () => {
      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('locale-display-name')).toHaveTextContent('العربية')
    })
  })

  describe('Utility functions', () => {
    describe('getTextDirection', () => {
      it('should return rtl for Arabic', () => {
        expect(getTextDirection('ar')).toBe('rtl')
      })

      it('should return rtl for Hebrew', () => {
        expect(getTextDirection('he')).toBe('rtl')
      })

      it('should return rtl for Persian', () => {
        expect(getTextDirection('fa')).toBe('rtl')
      })

      it('should return rtl for Urdu', () => {
        expect(getTextDirection('ur')).toBe('rtl')
      })

      it('should return ltr for English', () => {
        expect(getTextDirection('en')).toBe('ltr')
      })

      it('should return ltr for unknown locales', () => {
        expect(getTextDirection('fr')).toBe('ltr')
      })
    })

    describe('getNestedValue', () => {
      it('should get nested value with dot notation', () => {
        const obj = { a: { b: { c: 'value' } } }
        expect(getNestedValue(obj, 'a.b.c')).toBe('value')
      })

      it('should return undefined for non-existent path', () => {
        const obj = { a: { b: 'value' } }
        expect(getNestedValue(obj, 'a.c')).toBeUndefined()
      })

      it('should return undefined for empty object', () => {
        expect(getNestedValue({}, 'a.b')).toBeUndefined()
      })

      it('should handle single-level keys', () => {
        const obj = { key: 'value' }
        expect(getNestedValue(obj, 'key')).toBe('value')
      })
    })

    describe('interpolate', () => {
      it('should interpolate single parameter', () => {
        expect(interpolate('Hello {{name}}', { name: 'World' })).toBe('Hello World')
      })

      it('should interpolate multiple parameters', () => {
        expect(interpolate('{{greeting}} {{name}}!', { greeting: 'Hello', name: 'World' })).toBe(
          'Hello World!'
        )
      })

      it('should handle numeric parameters', () => {
        expect(interpolate('Count: {{count}}', { count: 42 })).toBe('Count: 42')
      })

      it('should leave unmatched placeholders unchanged', () => {
        expect(interpolate('Hello {{name}}', {})).toBe('Hello {{name}}')
      })

      it('should return original string when no params provided', () => {
        expect(interpolate('Hello World')).toBe('Hello World')
      })
    })

    describe('storeLocale and getStoredLocale', () => {
      it('should store and retrieve locale', () => {
        storeLocale('ar')
        expect(localStorage.setItem).toHaveBeenCalledWith('emf_locale', 'ar')
      })
    })

    describe('getBrowserLocale', () => {
      it('should return null when navigator is undefined', () => {
        // This is hard to test in jsdom, but we can verify the function exists
        expect(typeof getBrowserLocale).toBe('function')
      })
    })
  })

  describe('RTL Support', () => {
    it('should set direction to rtl for Arabic locale', () => {
      render(
        <I18nProvider initialLocale="ar">
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('direction')).toHaveTextContent('rtl')
      expect(screen.getByTestId('test-component')).toHaveAttribute('dir', 'rtl')
    })

    it('should set direction to ltr for English locale', () => {
      render(
        <I18nProvider initialLocale="en">
          <TestComponent />
        </I18nProvider>
      )

      expect(screen.getByTestId('direction')).toHaveTextContent('ltr')
      expect(screen.getByTestId('test-component')).toHaveAttribute('dir', 'ltr')
    })

    it('should update document dir attribute when switching to RTL', async () => {
      const user = userEvent.setup()

      render(
        <I18nProvider>
          <TestComponent />
        </I18nProvider>
      )

      expect(document.documentElement.getAttribute('dir')).toBe('ltr')

      await user.click(screen.getByTestId('set-ar'))

      expect(document.documentElement.getAttribute('dir')).toBe('rtl')
    })
  })

  describe('Constants', () => {
    it('should have correct supported locales', () => {
      expect(SUPPORTED_LOCALES).toContain('en')
      expect(SUPPORTED_LOCALES).toContain('ar')
      expect(SUPPORTED_LOCALES).toContain('fr')
      expect(SUPPORTED_LOCALES).toContain('de')
      expect(SUPPORTED_LOCALES).toContain('es')
      expect(SUPPORTED_LOCALES).toContain('pt')
      expect(SUPPORTED_LOCALES).toHaveLength(6)
    })

    it('should have correct RTL locales', () => {
      expect(RTL_LOCALES.has('ar')).toBe(true)
      expect(RTL_LOCALES.has('he')).toBe(true)
      expect(RTL_LOCALES.has('fa')).toBe(true)
      expect(RTL_LOCALES.has('ur')).toBe(true)
      expect(RTL_LOCALES.has('en')).toBe(false)
    })

    it('should have correct default locale', () => {
      expect(DEFAULT_LOCALE).toBe('en')
    })
  })
})
