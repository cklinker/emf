/**
 * Toast Notification System
 *
 * Provides toast notifications with success, error, warning, and info variants.
 * Includes ToastProvider context and useToast hook for managing notifications.
 *
 * Requirements:
 * - 18.1: Display appropriate error messages when API requests fail
 * - 18.3: Display success messages after successful operations
 *
 * Features:
 * - Toast container positioned at top-right of screen
 * - Multiple toasts can be displayed simultaneously
 * - Auto-dismiss after configurable duration (default 5000ms)
 * - Manual close button
 * - Accessible with ARIA live regions
 * - Smooth enter/exit animations (respecting reduced motion)
 */

import React, {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
  useEffect,
  useRef,
} from 'react'
import { CheckCircle, XCircle, AlertTriangle, Info } from 'lucide-react'
import styles from './Toast.module.css'

/**
 * Toast type variants
 */
export type ToastType = 'success' | 'error' | 'warning' | 'info'

/**
 * Individual toast data
 */
export interface ToastData {
  /** Unique identifier for the toast */
  id: string
  /** Message to display */
  message: string
  /** Type of toast (determines styling and icon) */
  type: ToastType
  /** Duration in milliseconds before auto-dismiss (0 = no auto-dismiss) */
  duration: number
  /** Timestamp when toast was created */
  createdAt: number
}

/**
 * Props for the Toast component
 */
export interface ToastProps {
  /** Message to display */
  message: string
  /** Type of toast (determines styling and icon) */
  type: ToastType
  /** Duration in milliseconds before auto-dismiss */
  duration?: number
  /** Callback when toast is closed */
  onClose: () => void
}

/**
 * Toast context value interface
 */
export interface ToastContextValue {
  /** Show a new toast notification */
  showToast: (message: string, type: ToastType, duration?: number) => string
  /** Hide a specific toast by ID */
  hideToast: (id: string) => void
  /** Hide all toasts */
  hideAllToasts: () => void
  /** Current list of active toasts */
  toasts: ToastData[]
}

/**
 * Props for the ToastProvider component
 */
export interface ToastProviderProps {
  /** Child components to render */
  children: React.ReactNode
  /** Default duration for toasts in milliseconds (default: 5000) */
  defaultDuration?: number
  /** Maximum number of toasts to display at once (default: 5) */
  maxToasts?: number
  /** Position of the toast container */
  position?:
    | 'top-right'
    | 'top-left'
    | 'bottom-right'
    | 'bottom-left'
    | 'top-center'
    | 'bottom-center'
}

// Default values
const DEFAULT_DURATION = 5000
const DEFAULT_MAX_TOASTS = 5
const DEFAULT_POSITION = 'top-right'

// Create the context with undefined default
const ToastContext = createContext<ToastContextValue | undefined>(undefined)

/**
 * Generate a unique ID for toasts
 */
function generateToastId(): string {
  return `toast-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
}

/**
 * Get the appropriate icon for a toast type
 */
function getToastIcon(type: ToastType): React.ReactNode {
  switch (type) {
    case 'success':
      return <CheckCircle size={18} />
    case 'error':
      return <XCircle size={18} />
    case 'warning':
      return <AlertTriangle size={18} />
    case 'info':
      return <Info size={18} />
    default:
      return <Info size={18} />
  }
}

/**
 * Get the appropriate ARIA role for a toast type
 */
function getAriaRole(type: ToastType): 'alert' | 'status' {
  // Use 'alert' for error and warning (more urgent)
  // Use 'status' for success and info (less urgent)
  return type === 'error' || type === 'warning' ? 'alert' : 'status'
}

/**
 * Get the appropriate aria-live value for a toast type
 */
function getAriaLive(type: ToastType): 'assertive' | 'polite' {
  // Use 'assertive' for error and warning (interrupt user)
  // Use 'polite' for success and info (wait for user to finish)
  return type === 'error' || type === 'warning' ? 'assertive' : 'polite'
}

/**
 * Individual Toast Component
 *
 * Displays a single toast notification with icon, message, and close button.
 * Supports auto-dismiss and manual close.
 */
export function Toast({
  message,
  type,
  duration = DEFAULT_DURATION,
  onClose,
}: ToastProps): React.ReactElement {
  const [isExiting, setIsExiting] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Handle close with exit animation
  const handleClose = useCallback(() => {
    setIsExiting(true)
    // Wait for exit animation to complete before calling onClose
    setTimeout(() => {
      onClose()
    }, 200) // Match CSS animation duration
  }, [onClose])

  // Set up auto-dismiss timer
  useEffect(() => {
    if (duration > 0) {
      timerRef.current = setTimeout(() => {
        handleClose()
      }, duration)
    }

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [duration, handleClose])

  // Handle keyboard events
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'Escape') {
        handleClose()
      }
    },
    [handleClose]
  )

  const icon = getToastIcon(type)
  const role = getAriaRole(type)
  const ariaLive = getAriaLive(type)

  return (
    <div
      className={`${styles.toast} ${styles[type]} ${isExiting ? styles.exiting : ''}`}
      role={role}
      aria-live={ariaLive}
      aria-atomic="true"
      data-testid={`toast-${type}`}
      onKeyDown={handleKeyDown}
    >
      <span className={styles.icon} aria-hidden="true" data-testid="toast-icon">
        {icon}
      </span>
      <span className={styles.message} data-testid="toast-message">
        {message}
      </span>
      <button
        type="button"
        className={styles.closeButton}
        onClick={handleClose}
        aria-label="Close notification"
        data-testid="toast-close-button"
      >
        <span aria-hidden="true">×</span>
      </button>
    </div>
  )
}

/**
 * Toast Container Component
 *
 * Renders all active toasts in a positioned container.
 * Uses ARIA live region for accessibility.
 */
function ToastContainer({
  toasts,
  onClose,
  position = DEFAULT_POSITION,
}: {
  toasts: ToastData[]
  onClose: (id: string) => void
  position?: ToastProviderProps['position']
}): React.ReactElement | null {
  if (toasts.length === 0) {
    return null
  }

  return (
    <div
      className={`${styles.container} ${styles[position.replace('-', '')]}`}
      data-testid="toast-container"
      aria-label="Notifications"
    >
      {toasts.map((toast) => (
        <Toast
          key={toast.id}
          message={toast.message}
          type={toast.type}
          duration={toast.duration}
          onClose={() => onClose(toast.id)}
        />
      ))}
    </div>
  )
}

/**
 * Toast Provider Component
 *
 * Wraps the application to provide toast notification functionality.
 * Manages toast state and renders the toast container.
 *
 * @example
 * ```tsx
 * <ToastProvider>
 *   <App />
 * </ToastProvider>
 * ```
 */
export function ToastProvider({
  children,
  defaultDuration = DEFAULT_DURATION,
  maxToasts = DEFAULT_MAX_TOASTS,
  position = DEFAULT_POSITION,
}: ToastProviderProps): React.ReactElement {
  const [toasts, setToasts] = useState<ToastData[]>([])

  /**
   * Show a new toast notification
   * Returns the toast ID for programmatic dismissal
   */
  const showToast = useCallback(
    (message: string, type: ToastType, duration?: number): string => {
      const id = generateToastId()
      const newToast: ToastData = {
        id,
        message,
        type,
        duration: duration ?? defaultDuration,
        createdAt: Date.now(),
      }

      setToasts((currentToasts) => {
        // Add new toast and limit to maxToasts
        const updatedToasts = [...currentToasts, newToast]
        if (updatedToasts.length > maxToasts) {
          // Remove oldest toasts to stay within limit
          return updatedToasts.slice(-maxToasts)
        }
        return updatedToasts
      })

      return id
    },
    [defaultDuration, maxToasts]
  )

  /**
   * Hide a specific toast by ID
   */
  const hideToast = useCallback((id: string): void => {
    setToasts((currentToasts) => currentToasts.filter((toast) => toast.id !== id))
  }, [])

  /**
   * Hide all toasts
   */
  const hideAllToasts = useCallback((): void => {
    setToasts([])
  }, [])

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo<ToastContextValue>(
    () => ({
      showToast,
      hideToast,
      hideAllToasts,
      toasts,
    }),
    [showToast, hideToast, hideAllToasts, toasts]
  )

  return (
    <ToastContext.Provider value={contextValue}>
      {children}
      <ToastContainer toasts={toasts} onClose={hideToast} position={position} />
    </ToastContext.Provider>
  )
}

/**
 * Hook to access Toast context
 *
 * @throws Error if used outside of ToastProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { showToast } = useToast();
 *
 *   const handleSuccess = () => {
 *     showToast('Operation completed successfully!', 'success');
 *   };
 *
 *   const handleError = () => {
 *     showToast('An error occurred. Please try again.', 'error');
 *   };
 *
 *   return (
 *     <div>
 *       <button onClick={handleSuccess}>Success</button>
 *       <button onClick={handleError}>Error</button>
 *     </div>
 *   );
 * }
 * ```
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext)
  if (context === undefined) {
    throw new Error('useToast must be used within a ToastProvider')
  }
  return context
}

// Export the context for testing purposes
export { ToastContext }

// Export default duration for testing
export { DEFAULT_DURATION, DEFAULT_MAX_TOASTS }
