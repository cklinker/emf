/**
 * EnvironmentsPage Tests
 *
 * Tests for the EnvironmentsPage component including:
 * - Environment (sandbox) list rendering with type/status badges
 * - Create-sandbox flow with the one-time admin credentials dialog
 * - Sandbox refresh destructive confirm
 * - Promotion wizard: source/target selection, diff, create, and the
 *   approve button being disabled for the promotion creator (four-eyes)
 * - Promotion history rollback button visibility
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EnvironmentsPage } from './EnvironmentsPage'

// The test wrapper's mock ID token carries sub "test-user" — that is the
// current user id resolved by AuthContext in tests.
const CURRENT_USER_ID = 'test-user'

// Mock environments (snake_case attrs as served by the worker)
const mockEnvironments = [
  {
    id: 'env-prod',
    name: 'Production',
    description: 'Primary environment',
    type: 'PRODUCTION',
    status: 'ACTIVE',
    source_env_id: null,
    remote_base_url: null,
    created_by: CURRENT_USER_ID,
    created_at: '2026-06-01T10:00:00Z',
  },
  {
    id: 'env-sb1',
    name: 'Dev Sandbox',
    description: 'Feature work',
    type: 'SANDBOX',
    status: 'ACTIVE',
    source_env_id: 'env-prod',
    sandbox_tenant_id: 'tenant-sb1',
    created_by: CURRENT_USER_ID,
    created_at: '2026-06-02T09:00:00Z',
  },
  {
    id: 'env-remote',
    name: 'Remote Prod',
    description: null,
    type: 'PRODUCTION',
    status: 'ACTIVE',
    remote_base_url: 'https://prod.example.com',
    remote_tenant_slug: 'acme',
    credential_ref: 'remote-pat',
    created_at: '2026-06-03T08:00:00Z',
  },
]

// Created local sandbox — carries the one-time admin credentials
const mockCreatedSandbox = {
  id: 'env-new',
  name: 'qa-sandbox',
  type: 'SANDBOX',
  status: 'CREATING',
  sandboxSlug: 'default-qa-sandbox',
  adminUsername: 'qa-sandbox-admin',
  adminEmail: 'qa-sandbox-admin@sandbox.local',
  adminInitialPassword: 'one-time-secret-42',
}

// Diff between sandbox and its parent
const mockDiff = {
  status: 'COMPARED',
  environmentId: 'env-sb1',
  changes: [
    { action: 'ADD', type: 'collection', name: 'invoices' },
    { action: 'MODIFY', type: 'field', name: 'accounts.status' },
    { action: 'REMOVE', type: 'flow', name: 'old-notify-flow' },
  ],
}

// Promotion created by the current user (PENDING — awaiting another approver)
const mockCreatedPromotion = {
  id: 'promo-new',
  status: 'PENDING',
  promotion_type: 'FULL',
  conflict_mode: 'SKIP',
  promoted_by: CURRENT_USER_ID,
  source_env_name: 'Dev Sandbox',
  target_env_name: 'Production',
  created_at: '2026-07-04T10:00:00Z',
}

const mockPromotions = [
  {
    id: 'promo-1',
    status: 'COMPLETED',
    promotion_type: 'FULL',
    conflict_mode: 'SKIP',
    items_promoted: 3,
    items_skipped: 1,
    items_failed: 0,
    promoted_by: CURRENT_USER_ID,
    approved_by: 'other-user',
    source_env_name: 'Dev Sandbox',
    target_env_name: 'Production',
    created_at: '2026-07-01T10:00:00Z',
    completed_at: '2026-07-01T10:05:00Z',
    target_snapshot_id: 'snap-1',
  },
  {
    id: 'promo-2',
    status: 'FAILED',
    promotion_type: 'SELECTIVE',
    conflict_mode: 'OVERWRITE',
    items_promoted: 0,
    items_skipped: 0,
    items_failed: 2,
    promoted_by: 'other-user',
    approved_by: CURRENT_USER_ID,
    source_env_name: 'Dev Sandbox',
    target_env_name: 'Remote Prod',
    created_at: '2026-07-02T10:00:00Z',
    completed_at: '2026-07-02T10:01:00Z',
    error_message: 'Target unreachable',
    target_snapshot_id: null,
  },
]

const mockPromotionItems = [
  {
    id: 'item-1',
    item_type: 'collection',
    item_name: 'invoices',
    status: 'PROMOTED',
    error_message: null,
  },
]

// Helper to setup Axios mocks for all endpoints
function setupAxiosMocks(overrides: Record<string, unknown> = {}) {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/api/environments')) {
      return Promise.resolve({ data: overrides.environments ?? mockEnvironments })
    }
    if (url.includes('/api/promotions/preview')) {
      return Promise.resolve({ data: overrides.diff ?? mockDiff })
    }
    if (url.match(/\/api\/promotions\/[^/]+\/items/)) {
      return Promise.resolve({ data: overrides.items ?? mockPromotionItems })
    }
    if (url.match(/\/api\/promotions\/[^/?]+$/)) {
      const id = url.split('/').pop()
      if (id === 'promo-new') {
        return Promise.resolve({ data: overrides.createdPromotion ?? mockCreatedPromotion })
      }
      const promotion = mockPromotions.find((p) => p.id === id)
      return Promise.resolve({ data: promotion ?? mockCreatedPromotion })
    }
    if (url.includes('/api/promotions')) {
      return Promise.resolve({ data: overrides.promotions ?? mockPromotions })
    }
    return Promise.resolve({ data: {} })
  })

  mockAxios.post.mockImplementation((url: string) => {
    if (url.match(/\/api\/environments\/[^/]+\/refresh/)) {
      return Promise.resolve({ data: { status: 'refreshing' } })
    }
    if (url.match(/\/api\/environments\/[^/]+\/test/)) {
      return Promise.resolve({ data: { ok: true, status: 200 } })
    }
    if (url.includes('/api/environments')) {
      return Promise.resolve({ data: overrides.createdEnvironment ?? mockCreatedSandbox })
    }
    if (url.match(/\/api\/promotions\/[^/]+\/approve/)) {
      return Promise.resolve({ data: { status: 'approved' } })
    }
    if (url.match(/\/api\/promotions\/[^/]+\/execute/)) {
      return Promise.resolve({ data: { status: 'executing' } })
    }
    if (url.match(/\/api\/promotions\/[^/]+\/rollback/)) {
      return Promise.resolve({ data: { status: 'rolled_back' } })
    }
    if (url.includes('/api/promotions')) {
      return Promise.resolve({ data: overrides.createdPromotion ?? mockCreatedPromotion })
    }
    return Promise.resolve({ data: {} })
  })

  mockAxios.delete.mockImplementation(() => {
    return Promise.resolve({ data: { status: 'archived' } })
  })
}

describe('EnvironmentsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    setupAxiosMocks()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  describe('Rendering', () => {
    it('renders the page with title and tabs', async () => {
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      expect(screen.getByText('Environments')).toBeInTheDocument()
      expect(screen.getByTestId('tab-sandboxes')).toBeInTheDocument()
      expect(screen.getByTestId('tab-promotions')).toBeInTheDocument()
    })

    it('renders with custom testId', () => {
      render(<EnvironmentsPage testId="custom-environments" />, {
        wrapper: createTestWrapper(),
      })

      expect(screen.getByTestId('custom-environments')).toBeInTheDocument()
    })

    it('displays the environment list after loading', async () => {
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('environments-table')).toBeInTheDocument()
      })

      expect(screen.getByTestId('env-row-env-prod')).toBeInTheDocument()
      expect(screen.getByTestId('env-row-env-sb1')).toBeInTheDocument()
      expect(screen.getByTestId('env-row-env-remote')).toBeInTheDocument()
      // Names are scoped to their rows — the type badge also says "Production"
      expect(
        within(screen.getByTestId('env-row-env-prod')).getAllByText('Production').length
      ).toBeGreaterThan(0)
      expect(
        within(screen.getByTestId('env-row-env-sb1')).getByText('Dev Sandbox')
      ).toBeInTheDocument()
      expect(
        within(screen.getByTestId('env-row-env-remote')).getByText('Remote Prod')
      ).toBeInTheDocument()
    })

    it('shows sandbox actions only where they apply', async () => {
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('environments-table')).toBeInTheDocument()
      })

      // Refresh only on sandboxes; test connection only on remote environments
      expect(screen.getByTestId('refresh-env-env-sb1')).toBeInTheDocument()
      expect(screen.queryByTestId('refresh-env-env-prod')).not.toBeInTheDocument()
      expect(screen.getByTestId('test-env-env-remote')).toBeInTheDocument()
      expect(screen.queryByTestId('test-env-env-sb1')).not.toBeInTheDocument()
    })

    it('displays empty state when there are no environments', async () => {
      setupAxiosMocks({ environments: [] })

      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('environments-empty')).toBeInTheDocument()
      })
    })
  })

  describe('Create sandbox flow', () => {
    it('shows the one-time admin credentials dialog after creating a local sandbox', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('create-environment-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-environment-button'))

      await waitFor(() => {
        expect(screen.getByTestId('create-environment-modal')).toBeInTheDocument()
      })

      await user.type(screen.getByTestId('environment-name-input'), 'qa-sandbox')
      await user.click(screen.getByTestId('submit-create-environment-button'))

      // One-time credentials dialog — the password is shown once
      await waitFor(() => {
        expect(screen.getByTestId('sandbox-credentials-dialog')).toBeInTheDocument()
      })

      expect(screen.getByTestId('credential-password')).toHaveTextContent('one-time-secret-42')
      expect(screen.getByTestId('credential-username')).toHaveTextContent('qa-sandbox-admin')
      expect(screen.getByTestId('credential-slug')).toHaveTextContent('default-qa-sandbox')

      // Request was a JSON:API-wrapped SANDBOX create
      const createCall = mockAxios.post.mock.calls.find((call) => call[0] === '/api/environments')
      expect(createCall).toBeDefined()
      const body = createCall?.[1] as {
        data?: { attributes?: { name?: string; type?: string } }
      }
      expect(body?.data?.attributes?.name).toBe('qa-sandbox')
      expect(body?.data?.attributes?.type).toBe('SANDBOX')

      // Done closes the dialog
      await user.click(screen.getByTestId('credentials-done-button'))

      await waitFor(() => {
        expect(screen.queryByTestId('create-environment-modal')).not.toBeInTheDocument()
      })
    })

    it('requires remote fields for the remote variant', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('create-environment-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('create-environment-button'))
      await user.click(screen.getByTestId('variant-remote'))
      await user.type(screen.getByTestId('environment-name-input'), 'Remote Target')

      // Submit stays disabled until all remote fields are filled
      expect(screen.getByTestId('submit-create-environment-button')).toBeDisabled()

      await user.type(
        screen.getByTestId('environment-remote-url-input'),
        'https://prod.example.com'
      )
      await user.type(screen.getByTestId('environment-remote-slug-input'), 'acme')
      await user.type(screen.getByTestId('environment-credential-ref-input'), 'remote-pat')

      expect(screen.getByTestId('submit-create-environment-button')).not.toBeDisabled()
    })
  })

  describe('Sandbox refresh', () => {
    it('asks for confirmation before refreshing a sandbox', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('refresh-env-env-sb1')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('refresh-env-env-sb1'))

      // Destructive confirm dialog
      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      })
      expect(screen.getByTestId('confirm-dialog-title')).toHaveTextContent('Refresh sandbox')

      // No refresh call before confirming
      expect(
        mockAxios.post.mock.calls.some((call) =>
          String(call[0]).includes('/api/environments/env-sb1/refresh')
        )
      ).toBe(false)

      await user.click(screen.getByTestId('confirm-dialog-confirm'))

      await waitFor(() => {
        expect(
          mockAxios.post.mock.calls.some((call) =>
            String(call[0]).includes('/api/environments/env-sb1/refresh')
          )
        ).toBe(true)
      })
    })

    it('does not refresh when the confirm dialog is cancelled', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('refresh-env-env-sb1')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('refresh-env-env-sb1'))

      await waitFor(() => {
        expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('confirm-dialog-cancel'))

      await waitFor(() => {
        expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
      })

      expect(
        mockAxios.post.mock.calls.some((call) =>
          String(call[0]).includes('/api/environments/env-sb1/refresh')
        )
      ).toBe(false)
    })
  })

  describe('Promotion wizard', () => {
    it('creates a promotion and disables approve for the creator', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      // Go to the promotions tab and open the wizard
      await user.click(screen.getByTestId('tab-promotions'))

      await waitFor(() => {
        expect(screen.getByTestId('start-promotion-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('start-promotion-button'))

      await waitFor(() => {
        expect(screen.getByTestId('promotion-wizard-modal')).toBeInTheDocument()
      })

      // Pick the sandbox — the target defaults to its parent environment
      await waitFor(() => {
        expect(screen.getByTestId('wizard-source-select')).toBeInTheDocument()
      })
      await user.selectOptions(screen.getByTestId('wizard-source-select'), 'env-sb1')

      const targetSelect = screen.getByTestId('wizard-target-select') as HTMLSelectElement
      expect(targetSelect.value).toBe('env-prod')

      await user.click(screen.getByTestId('wizard-next-button'))

      // Diff loads with the detected changes
      await waitFor(() => {
        expect(screen.getByTestId('wizard-diff-list')).toBeInTheDocument()
      })

      const diffList = screen.getByTestId('wizard-diff-list')
      expect(within(diffList).getByText('invoices')).toBeInTheDocument()
      expect(within(diffList).getByText('accounts.status')).toBeInTheDocument()
      expect(within(diffList).getByText('old-notify-flow')).toBeInTheDocument()

      // Create the promotion (FULL / SKIP defaults)
      await user.click(screen.getByTestId('wizard-create-button'))

      // Run step — the creator cannot approve their own promotion
      await waitFor(() => {
        expect(screen.getByTestId('approve-promotion-button')).toBeInTheDocument()
      })

      expect(screen.getByTestId('approve-promotion-button')).toBeDisabled()
      expect(screen.getByTestId('creator-approve-hint')).toBeInTheDocument()

      // Create request carried the source/target and FULL type
      const createCall = mockAxios.post.mock.calls.find((call) => call[0] === '/api/promotions')
      expect(createCall).toBeDefined()
      const body = createCall?.[1] as {
        data?: {
          attributes?: { sourceEnvId?: string; targetEnvId?: string; promotionType?: string }
        }
      }
      expect(body?.data?.attributes?.sourceEnvId).toBe('env-sb1')
      expect(body?.data?.attributes?.targetEnvId).toBe('env-prod')
      expect(body?.data?.attributes?.promotionType).toBe('FULL')
    })

    it('supports selective promotion with per-change checkboxes', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await user.click(screen.getByTestId('tab-promotions'))

      await waitFor(() => {
        expect(screen.getByTestId('start-promotion-button')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('start-promotion-button'))

      await waitFor(() => {
        expect(screen.getByTestId('wizard-source-select')).toBeInTheDocument()
      })
      await user.selectOptions(screen.getByTestId('wizard-source-select'), 'env-sb1')
      await user.click(screen.getByTestId('wizard-next-button'))

      await waitFor(() => {
        expect(screen.getByTestId('wizard-diff-list')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('wizard-type-selective'))

      // Nothing selected — create is disabled
      expect(screen.getByTestId('wizard-create-button')).toBeDisabled()

      await user.click(screen.getByTestId('change-checkbox-collection::invoices'))
      expect(screen.getByTestId('wizard-create-button')).not.toBeDisabled()

      await user.click(screen.getByTestId('wizard-create-button'))

      await waitFor(() => {
        expect(screen.getByTestId('wizard-step-run')).toBeInTheDocument()
      })

      const createCall = mockAxios.post.mock.calls.find((call) => call[0] === '/api/promotions')
      const body = createCall?.[1] as {
        data?: {
          attributes?: {
            promotionType?: string
            items?: Array<{ itemType: string; itemName: string }>
          }
        }
      }
      expect(body?.data?.attributes?.promotionType).toBe('SELECTIVE')
      expect(body?.data?.attributes?.items).toEqual([
        { itemType: 'collection', itemName: 'invoices' },
      ])
    })
  })

  describe('Promotion history', () => {
    it('displays the promotion history list', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await user.click(screen.getByTestId('tab-promotions'))

      await waitFor(() => {
        expect(screen.getByTestId('promotions-table')).toBeInTheDocument()
      })

      expect(screen.getByTestId('promotion-row-promo-1')).toBeInTheDocument()
      expect(screen.getByTestId('promotion-row-promo-2')).toBeInTheDocument()
    })

    it('shows the rollback button for completed promotions with a snapshot', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await user.click(screen.getByTestId('tab-promotions'))

      await waitFor(() => {
        expect(screen.getByTestId('view-promotion-promo-1')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-promotion-promo-1'))

      await waitFor(() => {
        expect(screen.getByTestId('promotion-details-modal')).toBeInTheDocument()
      })

      // promo-1 is COMPLETED with a target snapshot — rollback is offered
      expect(screen.getByTestId('rollback-promotion-button')).toBeInTheDocument()

      // Items are listed
      await waitFor(() => {
        expect(screen.getByTestId('promotion-items-table')).toBeInTheDocument()
      })
      expect(screen.getByText('invoices')).toBeInTheDocument()
    })

    it('hides the rollback button for promotions without a snapshot', async () => {
      const user = userEvent.setup()
      render(<EnvironmentsPage />, { wrapper: createTestWrapper() })

      await user.click(screen.getByTestId('tab-promotions'))

      await waitFor(() => {
        expect(screen.getByTestId('view-promotion-promo-2')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('view-promotion-promo-2'))

      await waitFor(() => {
        expect(screen.getByTestId('promotion-details-modal')).toBeInTheDocument()
      })

      // promo-2 FAILED without a snapshot — no rollback offered
      expect(screen.queryByTestId('rollback-promotion-button')).not.toBeInTheDocument()
      // Its failure reason is surfaced
      expect(screen.getByTestId('promotion-error')).toHaveTextContent('Target unreachable')
    })
  })
})
