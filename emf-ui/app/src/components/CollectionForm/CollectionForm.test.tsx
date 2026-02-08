/**
 * CollectionForm Component Tests
 *
 * Tests for the CollectionForm component covering rendering, validation,
 * form submission, edit mode, and accessibility.
 *
 * Requirements tested:
 * - 3.4: Display form for entering collection details
 * - 3.5: Create collection via API and display success message
 * - 3.6: Display validation errors inline with form fields
 * - 3.9: Pre-populate form with current values in edit mode
 */

import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CollectionForm, Collection, CollectionFormData } from './CollectionForm'
import { I18nProvider } from '../../context/I18nContext'
import { ToastProvider } from '../Toast'

// Wrapper component to provide required contexts
function TestWrapper({ children }: { children: React.ReactNode }) {
  return (
    <I18nProvider>
      <ToastProvider>{children}</ToastProvider>
    </I18nProvider>
  )
}

// Helper to render with contexts
function renderWithProviders(ui: React.ReactElement) {
  return render(ui, { wrapper: TestWrapper })
}

// Mock collection for edit mode tests
const mockCollection: Collection = {
  id: 'col-123',
  serviceId: 'test-service',
  name: 'test_collection',
  displayName: 'Test Collection',
  description: 'A test collection for testing',
  storageMode: 'JSONB',
  active: true,
  currentVersion: 1,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-15T00:00:00Z',
}

describe('CollectionForm Component', () => {
  const defaultProps = {
    onSubmit: vi.fn().mockResolvedValue(undefined),
    onCancel: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering - Create Mode', () => {
    it('should render all form fields', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-form')).toBeInTheDocument()
      expect(screen.getByTestId('collection-name-input')).toBeInTheDocument()
      expect(screen.getByTestId('collection-display-name-input')).toBeInTheDocument()
      expect(screen.getByTestId('collection-description-input')).toBeInTheDocument()
      expect(screen.getByTestId('collection-storage-mode-select')).toBeInTheDocument()
      expect(screen.getByTestId('collection-active-checkbox')).toBeInTheDocument()
    })

    it('should render form with empty values in create mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-name-input')).toHaveValue('')
      expect(screen.getByTestId('collection-display-name-input')).toHaveValue('')
      expect(screen.getByTestId('collection-description-input')).toHaveValue('')
      expect(screen.getByTestId('collection-storage-mode-select')).toHaveValue('JSONB')
      expect(screen.getByTestId('collection-active-checkbox')).toBeChecked()
    })

    it('should render submit button with "Create" text in create mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      const submitButton = screen.getByTestId('collection-form-submit')
      expect(submitButton).toHaveTextContent('Create')
    })

    it('should render cancel button', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-form-cancel')).toBeInTheDocument()
    })

    it('should render name field as enabled in create mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-name-input')).not.toBeDisabled()
    })

    it('should render storage mode field as enabled in create mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-storage-mode-select')).not.toBeDisabled()
    })

    it('should render hint text for name field', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('name-hint')).toBeInTheDocument()
    })

    it('should render hint text for storage mode field', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('storage-mode-hint')).toBeInTheDocument()
    })
  })

  describe('Rendering - Edit Mode', () => {
    it('should pre-populate form with collection data', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      expect(screen.getByTestId('collection-name-input')).toHaveValue('test_collection')
      expect(screen.getByTestId('collection-display-name-input')).toHaveValue('Test Collection')
      expect(screen.getByTestId('collection-description-input')).toHaveValue(
        'A test collection for testing'
      )
      expect(screen.getByTestId('collection-storage-mode-select')).toHaveValue('JSONB')
      expect(screen.getByTestId('collection-active-checkbox')).toBeChecked()
    })

    it('should render submit button with "Save" text in edit mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      const submitButton = screen.getByTestId('collection-form-submit')
      expect(submitButton).toHaveTextContent('Save')
    })

    it('should disable name field in edit mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      expect(screen.getByTestId('collection-name-input')).toBeDisabled()
    })

    it('should disable storage mode field in edit mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      expect(screen.getByTestId('collection-storage-mode-select')).toBeDisabled()
    })

    it('should not render name hint in edit mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      expect(screen.queryByTestId('name-hint')).not.toBeInTheDocument()
    })

    it('should not render storage mode hint in edit mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      expect(screen.queryByTestId('storage-mode-hint')).not.toBeInTheDocument()
    })

    it('should disable submit button when form is not dirty in edit mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      expect(screen.getByTestId('collection-form-submit')).toBeDisabled()
    })

    it('should enable submit button when form is dirty in edit mode', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} collection={mockCollection} />)

      await user.clear(screen.getByTestId('collection-display-name-input'))
      await user.type(screen.getByTestId('collection-display-name-input'), 'Updated Name')

      expect(screen.getByTestId('collection-form-submit')).not.toBeDisabled()
    })
  })

  describe('Validation - Name Field', () => {
    it('should show error when name is empty', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      const nameInput = screen.getByTestId('collection-name-input')
      await user.click(nameInput)
      await user.tab() // Blur the field

      await waitFor(() => {
        expect(screen.getByTestId('name-error')).toBeInTheDocument()
      })
    })

    it('should show error when name contains uppercase letters', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.type(screen.getByTestId('collection-name-input'), 'TestCollection')
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('name-error')).toBeInTheDocument()
      })
    })

    it('should show error when name starts with a number', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.type(screen.getByTestId('collection-name-input'), '123collection')
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('name-error')).toBeInTheDocument()
      })
    })

    it('should show error when name contains special characters', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.type(screen.getByTestId('collection-name-input'), 'test-collection')
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('name-error')).toBeInTheDocument()
      })
    })

    it('should accept valid name with lowercase and underscores', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.type(screen.getByTestId('collection-name-input'), 'valid_collection_name')
      await user.tab()

      await waitFor(() => {
        expect(screen.queryByTestId('name-error')).not.toBeInTheDocument()
      })
    })

    it('should mark name input as invalid when error exists', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.click(screen.getByTestId('collection-name-input'))
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('collection-name-input')).toHaveAttribute('aria-invalid', 'true')
      })
    })
  })

  describe('Validation - Display Name Field', () => {
    it('should show error when display name is empty', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      const displayNameInput = screen.getByTestId('collection-display-name-input')
      await user.click(displayNameInput)
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('display-name-error')).toBeInTheDocument()
      })
    })

    it('should accept valid display name', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.type(screen.getByTestId('collection-display-name-input'), 'My Collection')
      await user.tab()

      await waitFor(() => {
        expect(screen.queryByTestId('display-name-error')).not.toBeInTheDocument()
      })
    })
  })

  describe('Form Submission - Create Mode', () => {
    it('should call onSubmit with form data when valid', async () => {
      const onSubmit = vi.fn().mockResolvedValue(undefined)
      const user = userEvent.setup()
      const services = [{ id: 'test-service', name: 'Test Service' }]
      renderWithProviders(
        <CollectionForm {...defaultProps} onSubmit={onSubmit} services={services} />
      )

      await user.selectOptions(screen.getByTestId('collection-service-select'), 'test-service')
      await user.type(screen.getByTestId('collection-name-input'), 'my_collection')
      await user.type(screen.getByTestId('collection-display-name-input'), 'My Collection')
      await user.type(screen.getByTestId('collection-description-input'), 'A description')
      await user.selectOptions(
        screen.getByTestId('collection-storage-mode-select'),
        'PHYSICAL_TABLE'
      )

      await user.click(screen.getByTestId('collection-form-submit'))

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledTimes(1)
        expect(onSubmit).toHaveBeenCalledWith({
          serviceId: 'test-service',
          name: 'my_collection',
          displayName: 'My Collection',
          description: 'A description',
          storageMode: 'PHYSICAL_TABLE',
          active: true,
        })
      })
    })

    it('should not call onSubmit when form is invalid', async () => {
      const onSubmit = vi.fn().mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} onSubmit={onSubmit} />)

      // Submit without filling required fields
      await user.click(screen.getByTestId('collection-form-submit'))

      await waitFor(() => {
        expect(onSubmit).not.toHaveBeenCalled()
      })
    })

    it('should submit with empty description as undefined', async () => {
      const onSubmit = vi.fn().mockResolvedValue(undefined)
      const user = userEvent.setup()
      const services = [{ id: 'test-service', name: 'Test Service' }]
      renderWithProviders(
        <CollectionForm {...defaultProps} onSubmit={onSubmit} services={services} />
      )

      await user.selectOptions(screen.getByTestId('collection-service-select'), 'test-service')
      await user.type(screen.getByTestId('collection-name-input'), 'my_collection')
      await user.type(screen.getByTestId('collection-display-name-input'), 'My Collection')
      // Leave description empty

      await user.click(screen.getByTestId('collection-form-submit'))

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            serviceId: 'test-service',
            description: undefined,
          })
        )
      })
    })

    it('should submit with active unchecked', async () => {
      const onSubmit = vi.fn().mockResolvedValue(undefined)
      const user = userEvent.setup()
      const services = [{ id: 'test-service', name: 'Test Service' }]
      renderWithProviders(
        <CollectionForm {...defaultProps} onSubmit={onSubmit} services={services} />
      )

      await user.selectOptions(screen.getByTestId('collection-service-select'), 'test-service')
      await user.type(screen.getByTestId('collection-name-input'), 'my_collection')
      await user.type(screen.getByTestId('collection-display-name-input'), 'My Collection')
      await user.click(screen.getByTestId('collection-active-checkbox')) // Uncheck

      await user.click(screen.getByTestId('collection-form-submit'))

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            active: false,
          })
        )
      })
    })
  })

  describe('Form Submission - Edit Mode', () => {
    it('should call onSubmit with updated data', async () => {
      const onSubmit = vi.fn().mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderWithProviders(
        <CollectionForm {...defaultProps} collection={mockCollection} onSubmit={onSubmit} />
      )

      await user.clear(screen.getByTestId('collection-display-name-input'))
      await user.type(screen.getByTestId('collection-display-name-input'), 'Updated Collection')

      await user.click(screen.getByTestId('collection-form-submit'))

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledWith({
          serviceId: 'test-service',
          name: 'test_collection',
          displayName: 'Updated Collection',
          description: 'A test collection for testing',
          storageMode: 'JSONB',
          active: true,
        })
      })
    })
  })

  describe('Loading State', () => {
    it('should disable all inputs when submitting', () => {
      renderWithProviders(<CollectionForm {...defaultProps} isSubmitting={true} />)

      expect(screen.getByTestId('collection-name-input')).toBeDisabled()
      expect(screen.getByTestId('collection-display-name-input')).toBeDisabled()
      expect(screen.getByTestId('collection-description-input')).toBeDisabled()
      expect(screen.getByTestId('collection-storage-mode-select')).toBeDisabled()
      expect(screen.getByTestId('collection-active-checkbox')).toBeDisabled()
    })

    it('should disable buttons when submitting', () => {
      renderWithProviders(<CollectionForm {...defaultProps} isSubmitting={true} />)

      expect(screen.getByTestId('collection-form-submit')).toBeDisabled()
      expect(screen.getByTestId('collection-form-cancel')).toBeDisabled()
    })

    it('should show loading spinner in submit button when submitting', () => {
      renderWithProviders(<CollectionForm {...defaultProps} isSubmitting={true} />)

      const submitButton = screen.getByTestId('collection-form-submit')
      expect(submitButton.querySelector('[data-testid="loading-spinner"]')).toBeInTheDocument()
    })
  })

  describe('Cancel Action', () => {
    it('should call onCancel when cancel button is clicked', async () => {
      const onCancel = vi.fn()
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} onCancel={onCancel} />)

      await user.click(screen.getByTestId('collection-form-cancel'))

      expect(onCancel).toHaveBeenCalledTimes(1)
    })
  })

  describe('Accessibility', () => {
    it('should have proper labels for all inputs', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByLabelText(/Collection Name/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/Display Name/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/Description/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/Storage Mode/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/Active/i)).toBeInTheDocument()
    })

    it('should have aria-required on required fields', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-name-input')).toHaveAttribute('aria-required', 'true')
      expect(screen.getByTestId('collection-display-name-input')).toHaveAttribute(
        'aria-required',
        'true'
      )
      expect(screen.getByTestId('collection-storage-mode-select')).toHaveAttribute(
        'aria-required',
        'true'
      )
    })

    it('should have aria-describedby pointing to hint in create mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-name-input')).toHaveAttribute(
        'aria-describedby',
        'name-hint'
      )
      expect(screen.getByTestId('collection-storage-mode-select')).toHaveAttribute(
        'aria-describedby',
        'storage-mode-hint'
      )
      expect(screen.getByTestId('collection-active-checkbox')).toHaveAttribute(
        'aria-describedby',
        'active-hint'
      )
    })

    it('should have aria-describedby pointing to error when error exists', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.click(screen.getByTestId('collection-name-input'))
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('collection-name-input')).toHaveAttribute(
          'aria-describedby',
          'name-error'
        )
      })
    })

    it('should have role="alert" on error messages', async () => {
      const user = userEvent.setup()
      renderWithProviders(<CollectionForm {...defaultProps} />)

      await user.click(screen.getByTestId('collection-name-input'))
      await user.tab()

      await waitFor(() => {
        expect(screen.getByTestId('name-error')).toHaveAttribute('role', 'alert')
      })
    })

    it('should have noValidate on form to use custom validation', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-form')).toHaveAttribute('noValidate')
    })

    it('should have hint IDs matching aria-describedby', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('name-hint')).toHaveAttribute('id', 'name-hint')
      expect(screen.getByTestId('storage-mode-hint')).toHaveAttribute('id', 'storage-mode-hint')
      expect(screen.getByTestId('active-hint')).toHaveAttribute('id', 'active-hint')
    })
  })

  describe('Storage Mode Options', () => {
    it('should have JSONB and PHYSICAL_TABLE options', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      const select = screen.getByTestId('collection-storage-mode-select')
      const options = select.querySelectorAll('option')

      expect(options).toHaveLength(2)
      expect(options[0]).toHaveValue('JSONB')
      expect(options[1]).toHaveValue('PHYSICAL_TABLE')
    })

    it('should default to JSONB in create mode', () => {
      renderWithProviders(<CollectionForm {...defaultProps} />)

      expect(screen.getByTestId('collection-storage-mode-select')).toHaveValue('JSONB')
    })
  })

  describe('Form Reset on Collection Change', () => {
    it('should reset form when collection prop changes', async () => {
      const { rerender } = renderWithProviders(
        <CollectionForm {...defaultProps} collection={mockCollection} />
      )

      expect(screen.getByTestId('collection-display-name-input')).toHaveValue('Test Collection')

      const updatedCollection: Collection = {
        ...mockCollection,
        displayName: 'Updated Collection Name',
      }

      rerender(
        <TestWrapper>
          <CollectionForm {...defaultProps} collection={updatedCollection} />
        </TestWrapper>
      )

      expect(screen.getByTestId('collection-display-name-input')).toHaveValue(
        'Updated Collection Name'
      )
    })
  })
})

describe('CollectionForm Integration', () => {
  it('should work with async submission', async () => {
    const onSubmit = vi
      .fn()
      .mockImplementation(() => new Promise((resolve) => setTimeout(resolve, 100)))
    const user = userEvent.setup()
    const services = [{ id: 'test-service', name: 'Test Service' }]

    const TestComponent = () => {
      const [isSubmitting, setIsSubmitting] = React.useState(false)

      const handleSubmit = async (data: CollectionFormData) => {
        setIsSubmitting(true)
        try {
          await onSubmit(data)
        } finally {
          setIsSubmitting(false)
        }
      }

      return (
        <CollectionForm
          onSubmit={handleSubmit}
          onCancel={() => {}}
          isSubmitting={isSubmitting}
          services={services}
        />
      )
    }

    renderWithProviders(<TestComponent />)

    await user.selectOptions(screen.getByTestId('collection-service-select'), 'test-service')
    await user.type(screen.getByTestId('collection-name-input'), 'my_collection')
    await user.type(screen.getByTestId('collection-display-name-input'), 'My Collection')
    await user.click(screen.getByTestId('collection-form-submit'))

    // Should show loading state
    await waitFor(() => {
      expect(screen.getByTestId('collection-form-submit')).toBeDisabled()
    })

    // Should complete submission
    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalled()
    })
  })
})
