/**
 * ConfirmDialog Component Tests
 *
 * Tests for the ConfirmDialog component covering rendering, interactions,
 * accessibility, and keyboard navigation.
 *
 * Requirements tested:
 * - 3.10: Display confirmation dialog before collection deletion
 * - 4.8: Display confirmation dialog before field deletion
 * - 5.5: Display confirmation dialog before role deletion
 * - 6.7: Display confirmation dialog before OIDC provider deletion
 */

import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmDialog } from './ConfirmDialog'
import { I18nProvider } from '../../context/I18nContext'

// Wrapper component to provide I18n context
function TestWrapper({ children }: { children: React.ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>
}

// Helper to render with I18n context
function renderWithI18n(ui: React.ReactElement) {
  return render(ui, { wrapper: TestWrapper })
}

describe('ConfirmDialog Component', () => {
  const defaultProps = {
    open: true,
    title: 'Confirm Action',
    message: 'Are you sure you want to proceed?',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // Clean up body overflow style
    document.body.style.overflow = ''
  })

  describe('Rendering', () => {
    it('should render dialog when open is true', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      expect(screen.getByTestId('confirm-dialog-overlay')).toBeInTheDocument()
    })

    it('should not render dialog when open is false', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} open={false} />)

      expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
      expect(screen.queryByTestId('confirm-dialog-overlay')).not.toBeInTheDocument()
    })

    it('should render title correctly', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog-title')).toHaveTextContent('Confirm Action')
    })

    it('should render message correctly', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog-message')).toHaveTextContent(
        'Are you sure you want to proceed?'
      )
    })

    it('should render confirm button with custom label', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} confirmLabel="Delete" />)

      expect(screen.getByTestId('confirm-dialog-confirm')).toHaveTextContent('Delete')
    })

    it('should render cancel button with custom label', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} cancelLabel="Go Back" />)

      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveTextContent('Go Back')
    })

    it('should render with default variant styling', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog.className).toMatch(/default/)
    })

    it('should render with danger variant styling', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} variant="danger" />)

      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog.className).toMatch(/danger/)
    })

    it('should render danger button with danger variant', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} variant="danger" />)

      const confirmButton = screen.getByTestId('confirm-dialog-confirm')
      expect(confirmButton.className).toMatch(/dangerButton/)
    })
  })

  describe('Button Interactions', () => {
    it('should call onConfirm when confirm button is clicked', async () => {
      const onConfirm = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

      fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))

      expect(onConfirm).toHaveBeenCalledTimes(1)
    })

    it('should call onCancel when cancel button is clicked', async () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))

      expect(onCancel).toHaveBeenCalledTimes(1)
    })
  })

  describe('Overlay Click', () => {
    it('should call onCancel when clicking overlay by default', () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('confirm-dialog-overlay'))

      expect(onCancel).toHaveBeenCalledTimes(1)
    })

    it('should not call onCancel when clicking dialog content', () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('confirm-dialog'))

      expect(onCancel).not.toHaveBeenCalled()
    })

    it('should not call onCancel when closeOnOverlayClick is false', () => {
      const onCancel = vi.fn()
      renderWithI18n(
        <ConfirmDialog {...defaultProps} onCancel={onCancel} closeOnOverlayClick={false} />
      )

      fireEvent.click(screen.getByTestId('confirm-dialog-overlay'))

      expect(onCancel).not.toHaveBeenCalled()
    })
  })

  describe('Keyboard Interactions', () => {
    it('should call onCancel when Escape key is pressed', () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.keyDown(document, { key: 'Escape' })

      expect(onCancel).toHaveBeenCalledTimes(1)
    })

    it('should not call onCancel when Escape is pressed and closeOnEscape is false', () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onCancel={onCancel} closeOnEscape={false} />)

      fireEvent.keyDown(document, { key: 'Escape' })

      expect(onCancel).not.toHaveBeenCalled()
    })

    it('should not respond to Escape when dialog is closed', () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} open={false} onCancel={onCancel} />)

      fireEvent.keyDown(document, { key: 'Escape' })

      expect(onCancel).not.toHaveBeenCalled()
    })
  })

  describe('Focus Management', () => {
    it('should focus cancel button when dialog opens', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveFocus()
    })

    it('should trap focus within dialog', async () => {
      const user = userEvent.setup()
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      // Cancel button should be focused initially
      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveFocus()

      // Tab to confirm button
      await user.tab()
      expect(screen.getByTestId('confirm-dialog-confirm')).toHaveFocus()

      // Tab should wrap back to cancel button
      await user.tab()
      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveFocus()
    })

    it('should trap focus in reverse with Shift+Tab', async () => {
      const user = userEvent.setup()
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      // Cancel button should be focused initially
      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveFocus()

      // Shift+Tab should wrap to confirm button
      await user.tab({ shift: true })
      expect(screen.getByTestId('confirm-dialog-confirm')).toHaveFocus()
    })

    it('should restore focus to previously focused element when closed', async () => {
      const TestComponent = () => {
        const [open, setOpen] = React.useState(false)
        return (
          <I18nProvider>
            <button data-testid="trigger" onClick={() => setOpen(true)}>
              Open Dialog
            </button>
            <ConfirmDialog {...defaultProps} open={open} onCancel={() => setOpen(false)} />
          </I18nProvider>
        )
      }

      render(<TestComponent />)

      const trigger = screen.getByTestId('trigger')
      trigger.focus()
      expect(trigger).toHaveFocus()

      // Open dialog
      fireEvent.click(trigger)
      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveFocus()

      // Close dialog
      fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))

      // Focus should return to trigger
      expect(trigger).toHaveFocus()
    })
  })

  describe('Body Scroll Lock', () => {
    it('should prevent body scroll when dialog is open', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(document.body.style.overflow).toBe('hidden')
    })

    it('should restore body scroll when dialog is closed', () => {
      const { rerender } = renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(document.body.style.overflow).toBe('hidden')

      rerender(
        <TestWrapper>
          <ConfirmDialog {...defaultProps} open={false} />
        </TestWrapper>
      )

      expect(document.body.style.overflow).toBe('')
    })
  })

  describe('Accessibility', () => {
    it('should have role="alertdialog"', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog')).toHaveAttribute('role', 'alertdialog')
    })

    it('should have aria-modal="true"', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog')).toHaveAttribute('aria-modal', 'true')
    })

    it('should have aria-labelledby pointing to title', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} id="test-dialog" />)

      const dialog = screen.getByTestId('confirm-dialog')
      const title = screen.getByTestId('confirm-dialog-title')

      expect(dialog).toHaveAttribute('aria-labelledby', 'test-dialog-title')
      expect(title).toHaveAttribute('id', 'test-dialog-title')
    })

    it('should have aria-describedby pointing to message', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} id="test-dialog" />)

      const dialog = screen.getByTestId('confirm-dialog')
      const message = screen.getByTestId('confirm-dialog-message')

      expect(dialog).toHaveAttribute('aria-describedby', 'test-dialog-description')
      expect(message).toHaveAttribute('id', 'test-dialog-description')
    })

    it('should have aria-hidden on overlay', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog-overlay')).toHaveAttribute('aria-hidden', 'true')
    })

    it('should have tabIndex=-1 on dialog for programmatic focus', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog')).toHaveAttribute('tabIndex', '-1')
    })
  })

  describe('Custom ID', () => {
    it('should use custom id for accessibility attributes', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} id="custom-dialog" />)

      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog).toHaveAttribute('aria-labelledby', 'custom-dialog-title')
      expect(dialog).toHaveAttribute('aria-describedby', 'custom-dialog-description')
    })

    it('should use default id when not provided', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog).toHaveAttribute('aria-labelledby', 'confirm-dialog-title')
      expect(dialog).toHaveAttribute('aria-describedby', 'confirm-dialog-description')
    })
  })
})

describe('ConfirmDialog Integration', () => {
  it('should work with state management for open/close', () => {
    const TestComponent = () => {
      const [open, setOpen] = React.useState(false)
      const [confirmed, setConfirmed] = React.useState(false)

      return (
        <I18nProvider>
          <button data-testid="open-btn" onClick={() => setOpen(true)}>
            Open
          </button>
          <div data-testid="status">{confirmed ? 'Confirmed' : 'Not confirmed'}</div>
          <ConfirmDialog
            open={open}
            title="Confirm"
            message="Are you sure?"
            onConfirm={() => {
              setConfirmed(true)
              setOpen(false)
            }}
            onCancel={() => setOpen(false)}
          />
        </I18nProvider>
      )
    }

    render(<TestComponent />)

    // Initially closed
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('status')).toHaveTextContent('Not confirmed')

    // Open dialog
    fireEvent.click(screen.getByTestId('open-btn'))
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()

    // Confirm
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('status')).toHaveTextContent('Confirmed')
  })

  it('should work with danger variant for destructive actions', () => {
    const onConfirm = vi.fn()
    const TestComponent = () => {
      const [open, setOpen] = React.useState(true)

      return (
        <I18nProvider>
          <ConfirmDialog
            open={open}
            title="Delete Item"
            message="This action cannot be undone."
            confirmLabel="Delete"
            cancelLabel="Keep"
            variant="danger"
            onConfirm={() => {
              onConfirm()
              setOpen(false)
            }}
            onCancel={() => setOpen(false)}
          />
        </I18nProvider>
      )
    }

    render(<TestComponent />)

    // Verify danger styling
    expect(screen.getByTestId('confirm-dialog').className).toMatch(/danger/)
    expect(screen.getByTestId('confirm-dialog-confirm').className).toMatch(/dangerButton/)

    // Verify custom labels
    expect(screen.getByTestId('confirm-dialog-confirm')).toHaveTextContent('Delete')
    expect(screen.getByTestId('confirm-dialog-cancel')).toHaveTextContent('Keep')

    // Confirm deletion
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })
})
