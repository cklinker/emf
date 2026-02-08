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

// Export custom hooks as they are implemented
// Example exports:
// export { useAuth } from './useAuth';
// export { useConfig } from './useConfig';
// export { useTheme } from './useTheme';
// export { useI18n } from './useI18n';
// export { usePlugins } from './usePlugins';
// export { useToast } from './useToast';
