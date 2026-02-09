/**
 * Custom React Hooks
 *
 * This module exports all custom React hooks used throughout the application.
 * Hooks include: useAuth, useConfig, useTheme, useI18n, usePlugins, useToast, etc.
 */

// Keyboard shortcuts hook for accessibility
export {
  useKeyboardShortcuts,
  useEscapeKey,
  formatShortcut,
  type KeyboardShortcut,
  type KeyboardModifiers,
  type UseKeyboardShortcutsOptions,
} from './useKeyboardShortcuts'

// Recent records hook for tracking user activity
export {
  useRecentRecords,
  type RecentRecord,
  type UseRecentRecordsReturn,
} from './useRecentRecords'

// Favorites hook for starred/pinned items
export { useFavorites, type FavoriteItem, type UseFavoritesReturn } from './useFavorites'

// Global keyboard shortcuts hook
export { useGlobalShortcuts } from './useGlobalShortcuts'
