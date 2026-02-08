/**
 * Internationalization (I18n) Context
 *
 * Provides internationalization state and methods for the application.
 * Supports multiple languages, RTL text direction, and locale-aware formatting.
 *
 * Requirements:
 * - 15.1: Support multiple languages through a translation system
 * - 15.2: Detect user's preferred language from browser settings
 * - 15.3: Allow users to manually select their preferred language
 * - 15.4: Persist the user's language preference
 * - 15.5: Update all UI text without page reload when language changes
 * - 15.6: Support right-to-left (RTL) text direction for applicable languages
 * - 15.7: Format dates, numbers, and currencies according to the selected locale
 */

import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react'

// Import translation files
import enTranslations from '../i18n/translations/en.json'
import arTranslations from '../i18n/translations/ar.json'

/**
 * Text direction type
 */
export type TextDirection = 'ltr' | 'rtl'

/**
 * Supported locale codes
 */
export type SupportedLocale = 'en' | 'ar'

/**
 * Translation dictionary type (nested object with string values)
 */
export type TranslationDictionary = {
  [key: string]: string | TranslationDictionary
}

/**
 * I18n context value interface
 */
export interface I18nContextValue {
  /** Current locale code (e.g., 'en', 'ar') */
  locale: string
  /** Set the current locale */
  setLocale: (locale: string) => void
  /** Translate a key with optional parameter interpolation */
  t: (key: string, params?: Record<string, string | number>) => string
  /** Format a date according to the current locale */
  formatDate: (date: Date, options?: Intl.DateTimeFormatOptions) => string
  /** Format a number according to the current locale */
  formatNumber: (num: number, options?: Intl.NumberFormatOptions) => string
  /** Format a currency value according to the current locale */
  formatCurrency: (amount: number, currency?: string, options?: Intl.NumberFormatOptions) => string
  /** Current text direction (ltr or rtl) */
  direction: TextDirection
  /** List of supported locales */
  supportedLocales: SupportedLocale[]
  /** Get the display name for a locale */
  getLocaleDisplayName: (localeCode: string) => string
}

/**
 * Props for the I18nProvider component
 */
export interface I18nProviderProps {
  /** Child components to render */
  children: React.ReactNode
  /** Optional initial locale (defaults to detected or stored preference) */
  initialLocale?: string
  /** Optional default currency code (defaults to 'USD') */
  defaultCurrency?: string
}

// Storage key for locale preference
const LOCALE_STORAGE_KEY = 'emf_locale'

// Default locale if none detected or stored
const DEFAULT_LOCALE: SupportedLocale = 'en'

// Default currency
const DEFAULT_CURRENCY = 'USD'

// Supported locales configuration
const SUPPORTED_LOCALES: SupportedLocale[] = ['en', 'ar']

// RTL languages
const RTL_LOCALES: Set<string> = new Set(['ar', 'he', 'fa', 'ur'])

// Locale display names
const LOCALE_DISPLAY_NAMES: Record<string, string> = {
  en: 'English',
  ar: 'العربية',
  he: 'עברית',
  fa: 'فارسی',
  ur: 'اردو',
}

// Translation dictionaries by locale
const TRANSLATIONS: Record<string, TranslationDictionary> = {
  en: enTranslations as TranslationDictionary,
  ar: arTranslations as TranslationDictionary,
}

/**
 * Get the browser's preferred language
 * Requirement 15.2: Detect user's preferred language from browser settings
 */
function getBrowserLocale(): string | null {
  if (typeof navigator === 'undefined') {
    return null
  }

  // Try navigator.language first (most specific)
  const browserLang = navigator.language
  if (browserLang) {
    // Extract the language code (e.g., 'en-US' -> 'en')
    const langCode = browserLang.split('-')[0].toLowerCase()
    if (SUPPORTED_LOCALES.includes(langCode as SupportedLocale)) {
      return langCode
    }
  }

  // Try navigator.languages array
  if (navigator.languages && navigator.languages.length > 0) {
    for (const lang of navigator.languages) {
      const langCode = lang.split('-')[0].toLowerCase()
      if (SUPPORTED_LOCALES.includes(langCode as SupportedLocale)) {
        return langCode
      }
    }
  }

  return null
}

/**
 * Get stored locale preference from localStorage
 * Requirement 15.4: Persist the user's language preference
 */
function getStoredLocale(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
    if (stored && SUPPORTED_LOCALES.includes(stored as SupportedLocale)) {
      return stored
    }
    return null
  } catch {
    // localStorage may not be available
    return null
  }
}

/**
 * Store locale preference to localStorage
 * Requirement 15.4: Persist the user's language preference
 */
function storeLocale(locale: string): void {
  if (typeof window === 'undefined') {
    return
  }
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  } catch {
    // localStorage may not be available
    console.warn('[I18n] Failed to persist locale preference')
  }
}

/**
 * Determine text direction for a locale
 * Requirement 15.6: Support right-to-left (RTL) text direction
 */
function getTextDirection(locale: string): TextDirection {
  return RTL_LOCALES.has(locale) ? 'rtl' : 'ltr'
}

/**
 * Get a nested value from an object using dot notation
 * e.g., getNestedValue({ a: { b: 'value' } }, 'a.b') => 'value'
 */
function getNestedValue(obj: TranslationDictionary, path: string): string | undefined {
  const keys = path.split('.')
  let current: TranslationDictionary | string | undefined = obj

  for (const key of keys) {
    if (current === undefined || typeof current === 'string') {
      return undefined
    }
    current = current[key]
  }

  return typeof current === 'string' ? current : undefined
}

/**
 * Interpolate parameters into a translation string
 * Supports {{param}} syntax
 */
function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template
  }

  return template.replace(/\{\{(\w+)\}\}/g, (match, key) => {
    const value = params[key]
    return value !== undefined ? String(value) : match
  })
}

/**
 * Apply text direction to document
 * Requirement 15.6: Support right-to-left (RTL) text direction
 */
function applyTextDirection(direction: TextDirection): void {
  if (typeof document === 'undefined') {
    return
  }

  const root = document.documentElement
  root.setAttribute('dir', direction)
  root.setAttribute('lang', direction === 'rtl' ? 'ar' : 'en')

  // Also set data attribute for CSS selectors
  root.setAttribute('data-direction', direction)
}

/**
 * Apply locale to document
 */
function applyLocaleToDocument(locale: string): void {
  if (typeof document === 'undefined') {
    return
  }

  const root = document.documentElement
  root.setAttribute('lang', locale)
}

// Create the context with undefined default
const I18nContext = createContext<I18nContextValue | undefined>(undefined)

/**
 * I18n Provider Component
 *
 * Wraps the application to provide internationalization state and methods.
 * Handles locale detection, persistence, and text direction.
 *
 * @example
 * ```tsx
 * <I18nProvider>
 *   <App />
 * </I18nProvider>
 * ```
 */
export function I18nProvider({
  children,
  initialLocale,
  defaultCurrency = DEFAULT_CURRENCY,
}: I18nProviderProps): React.ReactElement {
  // Initialize locale from stored preference, browser setting, initial prop, or default
  const [locale, setLocaleState] = useState<string>(() => {
    // Priority: stored preference > initial prop > browser detection > default
    const stored = getStoredLocale()
    if (stored) {
      return stored
    }
    if (initialLocale && SUPPORTED_LOCALES.includes(initialLocale as SupportedLocale)) {
      return initialLocale
    }
    const browserLocale = getBrowserLocale()
    if (browserLocale) {
      return browserLocale
    }
    return DEFAULT_LOCALE
  })

  // Calculate text direction based on locale
  const direction = useMemo(() => getTextDirection(locale), [locale])

  // Get translations for current locale
  const translations = useMemo(() => {
    return TRANSLATIONS[locale] || TRANSLATIONS[DEFAULT_LOCALE]
  }, [locale])

  /**
   * Set locale and persist to localStorage
   * Requirement 15.3: Allow users to manually select their preferred language
   * Requirement 15.4: Persist the user's language preference
   */
  const setLocale = useCallback((newLocale: string): void => {
    if (!SUPPORTED_LOCALES.includes(newLocale as SupportedLocale)) {
      console.warn(`[I18n] Unsupported locale: ${newLocale}`)
      return
    }
    setLocaleState(newLocale)
    storeLocale(newLocale)
  }, [])

  /**
   * Translate a key with optional parameter interpolation
   * Requirement 15.1: Support multiple languages through a translation system
   * Requirement 15.5: Update all UI text without page reload when language changes
   */
  const t = useCallback(
    (key: string, params?: Record<string, string | number>): string => {
      const translation = getNestedValue(translations, key)

      if (translation === undefined) {
        // Fallback to English if translation not found
        const fallback = getNestedValue(TRANSLATIONS[DEFAULT_LOCALE], key)
        if (fallback !== undefined) {
          console.warn(`[I18n] Missing translation for key "${key}" in locale "${locale}"`)
          return interpolate(fallback, params)
        }

        // Return the key itself if no translation found
        console.warn(`[I18n] Translation key not found: "${key}"`)
        return key
      }

      return interpolate(translation, params)
    },
    [translations, locale]
  )

  /**
   * Format a date according to the current locale
   * Requirement 15.7: Format dates according to the selected locale
   */
  const formatDate = useCallback(
    (date: Date, options?: Intl.DateTimeFormatOptions): string => {
      try {
        const formatter = new Intl.DateTimeFormat(locale, options)
        return formatter.format(date)
      } catch (error) {
        console.error('[I18n] Error formatting date:', error)
        return date.toLocaleDateString()
      }
    },
    [locale]
  )

  /**
   * Format a number according to the current locale
   * Requirement 15.7: Format numbers according to the selected locale
   */
  const formatNumber = useCallback(
    (num: number, options?: Intl.NumberFormatOptions): string => {
      try {
        const formatter = new Intl.NumberFormat(locale, options)
        return formatter.format(num)
      } catch (error) {
        console.error('[I18n] Error formatting number:', error)
        return num.toString()
      }
    },
    [locale]
  )

  /**
   * Format a currency value according to the current locale
   * Requirement 15.7: Format currencies according to the selected locale
   */
  const formatCurrency = useCallback(
    (
      amount: number,
      currency: string = defaultCurrency,
      options?: Intl.NumberFormatOptions
    ): string => {
      try {
        const formatter = new Intl.NumberFormat(locale, {
          style: 'currency',
          currency,
          ...options,
        })
        return formatter.format(amount)
      } catch (error) {
        console.error('[I18n] Error formatting currency:', error)
        return `${currency} ${amount.toFixed(2)}`
      }
    },
    [locale, defaultCurrency]
  )

  /**
   * Get the display name for a locale code
   */
  const getLocaleDisplayName = useCallback((localeCode: string): string => {
    return LOCALE_DISPLAY_NAMES[localeCode] || localeCode
  }, [])

  /**
   * Apply text direction when locale changes
   * Requirement 15.6: Support right-to-left (RTL) text direction
   */
  useEffect(() => {
    applyTextDirection(direction)
    applyLocaleToDocument(locale)
  }, [direction, locale])

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale,
      t,
      formatDate,
      formatNumber,
      formatCurrency,
      direction,
      supportedLocales: SUPPORTED_LOCALES,
      getLocaleDisplayName,
    }),
    [
      locale,
      setLocale,
      t,
      formatDate,
      formatNumber,
      formatCurrency,
      direction,
      getLocaleDisplayName,
    ]
  )

  return <I18nContext.Provider value={contextValue}>{children}</I18nContext.Provider>
}

/**
 * Hook to access I18n context
 *
 * @throws Error if used outside of I18nProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { t, locale, setLocale, formatDate, formatNumber, direction } = useI18n();
 *
 *   return (
 *     <div dir={direction}>
 *       <h1>{t('common.title')}</h1>
 *       <p>{t('greeting', { name: 'John' })}</p>
 *       <p>{formatDate(new Date())}</p>
 *       <p>{formatNumber(1234.56)}</p>
 *       <select value={locale} onChange={(e) => setLocale(e.target.value)}>
 *         <option value="en">English</option>
 *         <option value="ar">العربية</option>
 *       </select>
 *     </div>
 *   );
 * }
 * ```
 */
export function useI18n(): I18nContextValue {
  const context = useContext(I18nContext)
  if (context === undefined) {
    throw new Error('useI18n must be used within an I18nProvider')
  }
  return context
}

// Export the context for testing purposes
export { I18nContext }

// Export utility functions for testing
export {
  getBrowserLocale,
  getStoredLocale,
  storeLocale,
  getTextDirection,
  getNestedValue,
  interpolate,
  SUPPORTED_LOCALES,
  RTL_LOCALES,
  DEFAULT_LOCALE,
}
