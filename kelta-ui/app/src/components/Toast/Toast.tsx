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
import { cn } from '@/lib/utils'

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

/** Toast type variant styles */
const toastTypeStyles: Record<ToastType, string> = {
  success:
    'bg-emerald-500 text-white border-l-4 border-l-emerald-600 dark:bg-emerald-800 dark:border-l-emerald-500',
  error: 'bg-red-500 text-white border-l-4 border-l-red-600 dark:bg-red-900 dark:border-l-red-500',
  warning:
    'bg-amber-500 text-gray-800 border-l-4 border-l-amber-600 dark:bg-amber-800 dark:text-amber-50 dark:border-l-amber-500',
  info: 'bg-blue-500 text-white border-l-4 border-l-blue-600 dark:bg-blue-900 dark:border-l-blue-500',
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
    }, 200) // Match animation duration
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
      className={cn(
        'flex items-center gap-3 py-3.5 px-4 rounded-lg shadow-[0_4px_12px_rgba(0,0,0,0.15),0_2px_4px_rgba(0,0,0,0.1)] min-w-[280px] max-w-[420px] pointer-events-auto transition-[opacity,transform] duration-200 ease-out',
        '[animation:toast-slide-in-right_0.3s_ease-out]',
        isExiting && '[animation:toast-slide-out-right_0.2s_ease-in_forwards]',
        'motion-reduce:[animation:toast-fade-in_0.15s_ease-out] motion-reduce:transition-[opacity] motion-reduce:duration-150',
        isExiting && 'motion-reduce:[animation:toast-fade-out_0.15s_ease-in_forwards]',
        'max-[480px]:min-w-0 max-[480px]:max-w-none max-[480px]:w-full max-[480px]:rounded-md',
        'forced-colors:border-2 forced-colors:border-current',
        toastTypeStyles[type]
      )}
      role={role}
      aria-live={ariaLive}
      aria-atomic="true"
      data-testid={`toast-${type}`}
      onKeyDown={handleKeyDown}
    >
      <span
        className="flex items-center justify-center w-6 h-6 text-base font-bold shrink-0 rounded-full bg-white/20"
        aria-hidden="true"
        data-testid="toast-icon"
      >
        {icon}
      </span>
      <span
        className="flex-1 text-[0.9375rem] leading-[1.4] break-words"
        data-testid="toast-message"
      >
        {message}
      </span>
      <button
        type="button"
        className="flex items-center justify-center w-7 h-7 p-0 border-none rounded bg-transparent text-inherit text-xl leading-none cursor-pointer opacity-70 transition-[opacity,background-color] duration-150 ease-linear shrink-0 hover:opacity-100 hover:bg-black/10 focus:outline-2 focus:outline-current focus:outline-offset-2 focus:opacity-100 focus-visible:outline-2 focus-visible:outline-current focus-visible:outline-offset-2 focus-visible:opacity-100 focus:[&:not(:focus-visible)]:outline-none"
        onClick={handleClose}
        aria-label="Close notification"
        data-testid="toast-close-button"
      >
        <span aria-hidden="true">&times;</span>
      </button>
    </div>
  )
}

/** Position variant styles for the toast container */
const positionStyles: Record<string, string> = {
  topright: 'top-0 right-0 items-end',
  topleft: 'top-0 left-0 items-start',
  bottomright: 'bottom-0 right-0 items-end flex-col-reverse',
  bottomleft: 'bottom-0 left-0 items-start flex-col-reverse',
  topcenter: 'top-0 left-1/2 -translate-x-1/2 items-center',
  bottomcenter: 'bottom-0 left-1/2 -translate-x-1/2 items-center flex-col-reverse',
}

/** Animation overrides for left-positioned toasts */
const leftAnimationStyle =
  '[&>div]:[animation-name:toast-slide-in-left] [&>div.exiting]:[animation-name:toast-slide-out-left]'
/** Animation overrides for center-positioned toasts */
const centerAnimationStyle =
  '[&>div]:[animation-name:toast-slide-in-center] [&>div.exiting]:[animation-name:toast-slide-out-center]'

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

  const positionKey = position.replace(/-/g, '')
  const isLeft = positionKey === 'topleft' || positionKey === 'bottomleft'
  const isCenter = positionKey === 'topcenter' || positionKey === 'bottomcenter'

  return (
    <div
      className={cn(
        'fixed z-[9999] flex flex-col gap-3 p-4 pointer-events-none max-h-screen overflow-hidden',
        positionStyles[positionKey],
        isLeft && leftAnimationStyle,
        isCenter && centerAnimationStyle,
        'max-[480px]:p-2 max-[480px]:left-0 max-[480px]:right-0',
        (positionKey === 'topright' || positionKey === 'topleft' || positionKey === 'topcenter') &&
          'max-[480px]:top-0 max-[480px]:left-0 max-[480px]:right-0 max-[480px]:translate-x-0 max-[480px]:items-stretch',
        (positionKey === 'bottomright' ||
          positionKey === 'bottomleft' ||
          positionKey === 'bottomcenter') &&
          'max-[480px]:bottom-0 max-[480px]:left-0 max-[480px]:right-0 max-[480px]:translate-x-0 max-[480px]:items-stretch'
      )}
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
