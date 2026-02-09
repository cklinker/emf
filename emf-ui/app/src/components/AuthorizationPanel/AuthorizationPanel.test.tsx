/**
 * AuthorizationPanel Component Tests
 *
 * Tests for the AuthorizationPanel component including:
 * - Route-level authorization configuration display
 * - Field-level authorization configuration display
 * - Policy selection for operations
 * - Loading and saving states
 * - Accessibility
 *
 * Requirements tested:
 * - 5.9: Route-level authorization configuration per operation
 * - 5.10: Policy selection for each operation
 * - 5.11: Field-level authorization configuration per field
 * - 5.12: Policy selection for each field and operation
 */

import React from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AuthorizationPanel, ROUTE_OPERATIONS, FIELD_OPERATIONS } from './AuthorizationPanel'
import type { PolicySummary, FieldDefinition, CollectionAuthz } from './AuthorizationPanel'
import { I18nProvider } from '../../context/I18nContext'

// Mock policies
const mockPolicies: PolicySummary[] = [
  { id: 'policy-1', name: 'admin_only', description: 'Admin access only' },
  { id: 'policy-2', name: 'authenticated', description: 'Any authenticated user' },
  { id: 'policy-3', name: 'owner_only', description: 'Resource owner only' },
]

// Mock fields
const mockFields: FieldDefinition[] = [
  { id: 'field-1', name: 'email', displayName: 'Email Address' },
  { id: 'field-2', name: 'password', displayName: 'Password' },
  { id: 'field-3', name: 'created_at' },
]

// Mock authorization configuration
const mockAuthz: CollectionAuthz = {
  routePolicies: [
    { operation: 'create', policyId: 'policy-1' },
    { operation: 'read', policyId: 'policy-2' },
  ],
  fieldPolicies: [
    { fieldName: 'password', operation: 'read', policyId: 'policy-1' },
    { fieldName: 'password', operation: 'write', policyId: 'policy-1' },
  ],
}

// Test wrapper with I18n provider
function TestWrapper({ children }: { children: React.ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>
}

// Helper to render with wrapper
function renderWithI18n(ui: React.ReactElement) {
  return render(ui, { wrapper: TestWrapper })
}

describe('AuthorizationPanel', () => {
  const defaultProps = {
    collectionId: 'collection-123',
    collectionName: 'users',
    fields: mockFields,
    policies: mockPolicies,
    onRouteAuthzChange: vi.fn(),
    onFieldAuthzChange: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('renders the authorization panel with title', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      expect(screen.getByTestId('authorization-panel')).toBeInTheDocument()
      // Check for the main title in the header
      const header = screen.getByTestId('authorization-panel').querySelector('h3')
      expect(header).toHaveTextContent(/authorization/i)
    })

    it('renders loading state when isLoading is true', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} isLoading={true} />)

      expect(screen.getByTestId('authorization-panel-loading')).toBeInTheDocument()
    })

    it('renders saving indicator when isSaving is true', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} isSaving={true} />)

      expect(screen.getByTestId('authorization-panel-saving')).toBeInTheDocument()
    })

    it('renders authorization hint', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      expect(screen.getByTestId('authorization-panel-hint')).toBeInTheDocument()
    })
  })

  describe('Route-Level Authorization (Requirements 5.9, 5.10)', () => {
    it('displays route authorization section', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      expect(screen.getByTestId('authorization-panel-route-section')).toBeInTheDocument()
    })

    it('displays all route operations', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      ROUTE_OPERATIONS.forEach((operation) => {
        expect(screen.getByTestId(`authorization-panel-route-${operation}`)).toBeInTheDocument()
      })
    })

    it('displays policy select for each route operation', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      ROUTE_OPERATIONS.forEach((operation) => {
        expect(
          screen.getByTestId(`authorization-panel-route-${operation}-select`)
        ).toBeInTheDocument()
      })
    })

    it('displays all available policies in route operation selects', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const createSelect = screen.getByTestId('authorization-panel-route-create-select')

      // Check that all policies are available as options
      mockPolicies.forEach((policy) => {
        expect(within(createSelect).getByText(policy.name)).toBeInTheDocument()
      })
    })

    it('shows current route policy selection from authz config', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={mockAuthz} />)

      const createSelect = screen.getByTestId(
        'authorization-panel-route-create-select'
      ) as HTMLSelectElement
      const readSelect = screen.getByTestId(
        'authorization-panel-route-read-select'
      ) as HTMLSelectElement

      expect(createSelect.value).toBe('policy-1')
      expect(readSelect.value).toBe('policy-2')
    })

    it('shows empty selection for operations without configured policy', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={mockAuthz} />)

      const updateSelect = screen.getByTestId(
        'authorization-panel-route-update-select'
      ) as HTMLSelectElement
      const deleteSelect = screen.getByTestId(
        'authorization-panel-route-delete-select'
      ) as HTMLSelectElement

      expect(updateSelect.value).toBe('')
      expect(deleteSelect.value).toBe('')
    })

    it('calls onRouteAuthzChange when policy is selected', async () => {
      const user = userEvent.setup()
      const onRouteAuthzChange = vi.fn()

      renderWithI18n(
        <AuthorizationPanel {...defaultProps} onRouteAuthzChange={onRouteAuthzChange} />
      )

      const updateSelect = screen.getByTestId('authorization-panel-route-update-select')
      await user.selectOptions(updateSelect, 'policy-2')

      expect(onRouteAuthzChange).toHaveBeenCalledWith([
        { operation: 'update', policyId: 'policy-2' },
      ])
    })

    it('calls onRouteAuthzChange with updated policies when changing existing policy', async () => {
      const user = userEvent.setup()
      const onRouteAuthzChange = vi.fn()

      renderWithI18n(
        <AuthorizationPanel
          {...defaultProps}
          authz={mockAuthz}
          onRouteAuthzChange={onRouteAuthzChange}
        />
      )

      const createSelect = screen.getByTestId('authorization-panel-route-create-select')
      await user.selectOptions(createSelect, 'policy-3')

      expect(onRouteAuthzChange).toHaveBeenCalledWith(
        expect.arrayContaining([
          expect.objectContaining({ operation: 'create', policyId: 'policy-3' }),
        ])
      )
    })

    it('removes policy when empty option is selected', async () => {
      const user = userEvent.setup()
      const onRouteAuthzChange = vi.fn()

      renderWithI18n(
        <AuthorizationPanel
          {...defaultProps}
          authz={mockAuthz}
          onRouteAuthzChange={onRouteAuthzChange}
        />
      )

      const createSelect = screen.getByTestId('authorization-panel-route-create-select')
      await user.selectOptions(createSelect, '')

      // Should remove the create policy from the list
      expect(onRouteAuthzChange).toHaveBeenCalledWith(
        expect.arrayContaining([
          expect.objectContaining({ operation: 'read', policyId: 'policy-2' }),
        ])
      )
    })

    it('displays configured route policies count', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={mockAuthz} />)

      const countBadge = screen.getByTestId('authorization-panel-route-count')
      expect(countBadge).toHaveTextContent('2')
    })

    it('disables route selects when saving', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} isSaving={true} />)

      ROUTE_OPERATIONS.forEach((operation) => {
        const select = screen.getByTestId(`authorization-panel-route-${operation}-select`)
        expect(select).toBeDisabled()
      })
    })
  })

  describe('Field-Level Authorization (Requirements 5.11, 5.12)', () => {
    it('displays field authorization section', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      expect(screen.getByTestId('authorization-panel-field-section')).toBeInTheDocument()
    })

    it('displays all fields in the field list', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      mockFields.forEach((field) => {
        expect(screen.getByTestId(`authorization-panel-field-${field.id}`)).toBeInTheDocument()
      })
    })

    it('displays field display name when available', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      expect(screen.getByText('Email Address')).toBeInTheDocument()
      expect(screen.getByText('Password')).toBeInTheDocument()
    })

    it('displays field technical name when different from display name', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      // Email field should show both display name and technical name
      const emailField = screen.getByTestId('authorization-panel-field-field-1')
      expect(within(emailField).getByText('Email Address')).toBeInTheDocument()
      expect(within(emailField).getByText('(email)')).toBeInTheDocument()
    })

    it('shows empty state when no fields are provided', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} fields={[]} />)

      expect(screen.getByTestId('authorization-panel-no-fields')).toBeInTheDocument()
    })

    it('expands field to show operations when clicked', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      await user.click(fieldToggle)

      expect(screen.getByTestId('authorization-panel-field-field-1-operations')).toBeInTheDocument()
    })

    it('displays read and write operations for expanded field', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      await user.click(fieldToggle)

      FIELD_OPERATIONS.forEach((operation) => {
        expect(
          screen.getByTestId(`authorization-panel-field-field-1-${operation}`)
        ).toBeInTheDocument()
      })
    })

    it('displays policy select for each field operation', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      await user.click(fieldToggle)

      FIELD_OPERATIONS.forEach((operation) => {
        expect(
          screen.getByTestId(`authorization-panel-field-field-1-${operation}-select`)
        ).toBeInTheDocument()
      })
    })

    it('shows current field policy selection from authz config', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={mockAuthz} />)

      // Expand password field (field-2)
      const fieldToggle = screen.getByTestId('authorization-panel-field-field-2-toggle')
      await user.click(fieldToggle)

      const readSelect = screen.getByTestId(
        'authorization-panel-field-field-2-read-select'
      ) as HTMLSelectElement
      const writeSelect = screen.getByTestId(
        'authorization-panel-field-field-2-write-select'
      ) as HTMLSelectElement

      expect(readSelect.value).toBe('policy-1')
      expect(writeSelect.value).toBe('policy-1')
    })

    it('calls onFieldAuthzChange when field policy is selected', async () => {
      const user = userEvent.setup()
      const onFieldAuthzChange = vi.fn()

      renderWithI18n(
        <AuthorizationPanel {...defaultProps} onFieldAuthzChange={onFieldAuthzChange} />
      )

      // Expand email field (field-1)
      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      await user.click(fieldToggle)

      const readSelect = screen.getByTestId('authorization-panel-field-field-1-read-select')
      await user.selectOptions(readSelect, 'policy-2')

      expect(onFieldAuthzChange).toHaveBeenCalledWith([
        { fieldName: 'email', operation: 'read', policyId: 'policy-2' },
      ])
    })

    it('shows configured badge for fields with policies', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={mockAuthz} />)

      // Password field (field-2) has policies configured
      const passwordField = screen.getByTestId('authorization-panel-field-field-2')
      expect(within(passwordField).getByText('🔒')).toBeInTheDocument()

      // Email field (field-1) has no policies configured
      const emailField = screen.getByTestId('authorization-panel-field-field-1')
      expect(within(emailField).queryByText('🔒')).not.toBeInTheDocument()
    })

    it('displays configured field policies count', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={mockAuthz} />)

      const countBadge = screen.getByTestId('authorization-panel-field-count')
      expect(countBadge).toHaveTextContent('2')
    })

    it('collapses field when clicked again', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')

      // Expand
      await user.click(fieldToggle)
      expect(screen.getByTestId('authorization-panel-field-field-1-operations')).toBeInTheDocument()

      // Collapse
      await user.click(fieldToggle)
      expect(
        screen.queryByTestId('authorization-panel-field-field-1-operations')
      ).not.toBeInTheDocument()
    })

    it('disables field selects when saving', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} isSaving={true} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      await user.click(fieldToggle)

      FIELD_OPERATIONS.forEach((operation) => {
        const select = screen.getByTestId(`authorization-panel-field-field-1-${operation}-select`)
        expect(select).toBeDisabled()
      })
    })
  })

  describe('No Policies Warning', () => {
    it('shows warning when no policies are available', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} policies={[]} />)

      expect(screen.getByTestId('authorization-panel-no-policies-warning')).toBeInTheDocument()
    })

    it('does not show warning when policies are available', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      expect(
        screen.queryByTestId('authorization-panel-no-policies-warning')
      ).not.toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('has accessible route operations list', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const routeSection = screen.getByTestId('authorization-panel-route-section')
      const list = within(routeSection).getByRole('list')
      expect(list).toHaveAttribute('aria-label')
    })

    it('has accessible field list', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldSection = screen.getByTestId('authorization-panel-field-section')
      const list = within(fieldSection).getByRole('list')
      expect(list).toHaveAttribute('aria-label')
    })

    it('has proper aria-expanded on field toggles', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')

      expect(fieldToggle).toHaveAttribute('aria-expanded', 'false')

      await user.click(fieldToggle)

      expect(fieldToggle).toHaveAttribute('aria-expanded', 'true')
    })

    it('has proper aria-controls on field toggles', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      expect(fieldToggle).toHaveAttribute('aria-controls', 'field-authz-field-1')
    })

    it('has labels for all select elements', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      ROUTE_OPERATIONS.forEach((operation) => {
        const select = screen.getByTestId(`authorization-panel-route-${operation}-select`)
        expect(select).toHaveAccessibleName()
      })
    })

    it('supports keyboard navigation for field expansion', async () => {
      const user = userEvent.setup()

      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')

      // Focus and press Enter
      fieldToggle.focus()
      await user.keyboard('{Enter}')

      expect(screen.getByTestId('authorization-panel-field-field-1-operations')).toBeInTheDocument()
    })
  })

  describe('Edge Cases', () => {
    it('handles undefined authz gracefully', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={undefined} />)

      // Should render without errors
      expect(screen.getByTestId('authorization-panel')).toBeInTheDocument()

      // All selects should be empty
      ROUTE_OPERATIONS.forEach((operation) => {
        const select = screen.getByTestId(
          `authorization-panel-route-${operation}-select`
        ) as HTMLSelectElement
        expect(select.value).toBe('')
      })
    })

    it('handles empty routePolicies array', () => {
      const emptyAuthz: CollectionAuthz = {
        routePolicies: [],
        fieldPolicies: [],
      }

      renderWithI18n(<AuthorizationPanel {...defaultProps} authz={emptyAuthz} />)

      expect(screen.getByTestId('authorization-panel-route-count')).toHaveTextContent('0')
    })

    it('handles fields without displayName', () => {
      renderWithI18n(<AuthorizationPanel {...defaultProps} />)

      // created_at field has no displayName
      const createdAtField = screen.getByTestId('authorization-panel-field-field-3')
      expect(within(createdAtField).getByText('created_at')).toBeInTheDocument()
      // Should not show parenthetical technical name
      expect(within(createdAtField).queryByText('(created_at)')).not.toBeInTheDocument()
    })

    it('handles policy selection for field with same name as another field', async () => {
      const user = userEvent.setup()
      const onFieldAuthzChange = vi.fn()

      renderWithI18n(
        <AuthorizationPanel {...defaultProps} onFieldAuthzChange={onFieldAuthzChange} />
      )

      // Expand first field
      const fieldToggle = screen.getByTestId('authorization-panel-field-field-1-toggle')
      await user.click(fieldToggle)

      const readSelect = screen.getByTestId('authorization-panel-field-field-1-read-select')
      await user.selectOptions(readSelect, 'policy-1')

      // Should use the correct field name
      expect(onFieldAuthzChange).toHaveBeenCalledWith([
        { fieldName: 'email', operation: 'read', policyId: 'policy-1' },
      ])
    })
  })
})
