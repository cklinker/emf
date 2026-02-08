/**
 * ConfirmDialog Component
 *
 * A reusable confirmation dialog for destructive or important actions.
 * Provides modal overlay with confirm/cancel actions and accessibility support.
 *
 * Requirements:
 * - 3.10: Display confirmation dialog before collection deletion
 * - 4.8: Display confirmation dialog before field deletion
 * - 5.5: Display confirmation dialog before role deletion
 * - 6.7: Display confirmation dialog before OIDC provider deletion
 *
 * Features:
 * - Modal overlay with centered dialog
 * - Title and message display
 * - Confirm and Cancel buttons
 * - Danger variant with red confirm button for destructive actions
 * - Focus trap (focus stays within dialog)
 * - Escape key closes dialog
 * - Click outside closes dialog
 * - Accessible with ARIA dialog role
 * - Reduced motion support
 */

import React, { useEffect, useRef, useCallback } from 'react'
import { useI18n } from '../../context/I18nContext'
import styles from './ConfirmDialog.module.css'

/**
 * ConfirmDialog variant type
 */
export type ConfirmDialogVariant = 'default' | 'danger'

/**
 * Props for the ConfirmDialog component
 */
export interface ConfirmDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Dialog title */
  title: string
  /** Dialog message/description */
  message: string
  /** Label for the confirm button (defaults to "Confirm") */
  confirmLabel?: string
  /** Label for the cancel button (defaults to "Cancel") */
  cancelLabel?: string
  /** Callback when confirm button is clicked */
  onConfirm: () => void
  /** Callback when cancel button is clicked or dialog is dismissed */
  onCancel: () => void
  /** Visual variant - 'danger' shows red confirm button for destructive actions */
  variant?: ConfirmDialogVariant
  /** Whether clicking outside the dialog closes it (default: true) */
  closeOnOverlayClick?: boolean
  /** Whether pressing Escape closes the dialog (default: true) */
  closeOnEscape?: boolean
  /** ID for the dialog element (for accessibility) */
  id?: string
}

/**
 * Get all focusable elements within a container
 */
function getFocusableElements(container: HTMLElement): HTMLElement[] {
  const focusableSelectors = [
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    'a[href]',
    '[tabindex]:not([tabindex="-1"])',
  ].join(', ')

  return Array.from(container.querySelectorAll<HTMLElement>(focusableSelectors))
}

/**
 * ConfirmDialog Component
 *
 * Displays a modal confirmation dialog with customizable title, message,
 * and action buttons. Supports danger variant for destructive actions.
 *
 * @example
 * ```tsx
 * <ConfirmDialog
 *   open={isOpen}
 *   title="Delete Collection"
 *   message="Are you sure you want to delete this collection? This action cannot be undone."
 *   confirmLabel="Delete"
 *   cancelLabel="Cancel"
 *   onConfirm={handleDelete}
 *   onCancel={() => setIsOpen(false)}
 *   variant="danger"
 * />
 * ```
 */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
  variant = 'default',
  closeOnOverlayClick = true,
  closeOnEscape = true,
  id,
}: ConfirmDialogProps): React.ReactElement | null {
  const { t } = useI18n()
  const dialogRef = useRef<HTMLDivElement>(null)
  const previousActiveElement = useRef<HTMLElement | null>(null)

  // Default labels with i18n support
  const resolvedConfirmLabel = confirmLabel ?? t('common.confirm')
  const resolvedCancelLabel = cancelLabel ?? t('common.cancel')

  // Generate unique IDs for accessibility
  const dialogId = id ?? 'confirm-dialog'
  const titleId = `${dialogId}-title`
  const descriptionId = `${dialogId}-description`

  /**
   * Handle keyboard events for focus trap and escape key
   */
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (!open) return

      // Handle Escape key
      if (event.key === 'Escape' && closeOnEscape) {
        event.preventDefault()
        onCancel()
        return
      }

      // Handle Tab key for focus trap
      if (event.key === 'Tab' && dialogRef.current) {
        const focusableElements = getFocusableElements(dialogRef.current)
        if (focusableElements.length === 0) return

        const firstElement = focusableElements[0]
        const lastElement = focusableElements[focusableElements.length - 1]

        if (event.shiftKey) {
          // Shift + Tab: if on first element, move to last
          if (document.activeElement === firstElement) {
            event.preventDefault()
            lastElement.focus()
          }
        } else {
          // Tab: if on last element, move to first
          if (document.activeElement === lastElement) {
            event.preventDefault()
            firstElement.focus()
          }
        }
      }
    },
    [open, closeOnEscape, onCancel]
  )

  /**
   * Handle overlay click
   */
  const handleOverlayClick = useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      // Only close if clicking directly on the overlay, not the dialog content
      if (event.target === event.currentTarget && closeOnOverlayClick) {
        onCancel()
      }
    },
    [closeOnOverlayClick, onCancel]
  )

  /**
   * Handle confirm button click
   */
  const handleConfirm = useCallback(() => {
    onConfirm()
  }, [onConfirm])

  /**
   * Handle cancel button click
   */
  const handleCancel = useCallback(() => {
    onCancel()
  }, [onCancel])

  /**
   * Set up focus management and keyboard listeners when dialog opens/closes
   */
  useEffect(() => {
    if (open) {
      // Store the currently focused element to restore later
      previousActiveElement.current = document.activeElement as HTMLElement

      // Focus the dialog or first focusable element
      if (dialogRef.current) {
        const focusableElements = getFocusableElements(dialogRef.current)
        if (focusableElements.length > 0) {
          // Focus the cancel button by default (safer option)
          const cancelButton = dialogRef.current.querySelector<HTMLElement>(
            '[data-testid="confirm-dialog-cancel"]'
          )
          if (cancelButton) {
            cancelButton.focus()
          } else {
            focusableElements[0].focus()
          }
        } else {
          dialogRef.current.focus()
        }
      }

      // Add keyboard event listener
      document.addEventListener('keydown', handleKeyDown)

      // Prevent body scroll when dialog is open
      document.body.style.overflow = 'hidden'
    } else {
      // Restore focus to previously focused element
      if (previousActiveElement.current) {
        previousActiveElement.current.focus()
      }

      // Restore body scroll
      document.body.style.overflow = ''
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
    }
  }, [open, handleKeyDown])

  // Don't render anything if not open
  if (!open) {
    return null
  }

  return (
    <div
      className={styles.overlay}
      onClick={handleOverlayClick}
      data-testid="confirm-dialog-overlay"
      aria-hidden="true"
    >
      <div
        ref={dialogRef}
        className={`${styles.dialog} ${styles[variant]}`}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        data-testid="confirm-dialog"
        tabIndex={-1}
      >
        <h2 id={titleId} className={styles.title} data-testid="confirm-dialog-title">
          {title}
        </h2>
        <p id={descriptionId} className={styles.message} data-testid="confirm-dialog-message">
          {message}
        </p>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={handleCancel}
            data-testid="confirm-dialog-cancel"
          >
            {resolvedCancelLabel}
          </button>
          <button
            type="button"
            className={`${styles.confirmButton} ${variant === 'danger' ? styles.dangerButton : ''}`}
            onClick={handleConfirm}
            data-testid="confirm-dialog-confirm"
          >
            {resolvedConfirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export default ConfirmDialog
