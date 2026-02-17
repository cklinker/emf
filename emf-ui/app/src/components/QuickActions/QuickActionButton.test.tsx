import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { QuickActionButton } from './QuickActionButton'
import type { QuickActionDefinition, QuickActionExecutionContext } from '@/types/quickActions'

// Mock navigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Mock script execution
const mockExecute = vi.fn()
vi.mock('@/hooks/useScriptExecution', () => ({
  useScriptExecution: vi.fn(() => ({
    execute: mockExecute,
    isPending: false,
    error: null,
  })),
}))

// Mock sonner toast
vi.mock('sonner', () => ({
  toast: Object.assign(vi.fn(), {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
}))

vi.mock('@/context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    },
  })),
}))

function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{children}</BrowserRouter>
    </QueryClientProvider>
  )
}

describe('QuickActionButton', () => {
  const defaultContext: QuickActionExecutionContext = {
    collectionName: 'accounts',
    recordId: 'rec-123',
    tenantSlug: 'test-tenant',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockExecute.mockResolvedValue({ success: true, message: 'Done' })
  })

  it('renders the action label', () => {
    const action: QuickActionDefinition = {
      id: 'action-1',
      label: 'Approve',
      type: 'run_script',
      context: 'record',
      sortOrder: 1,
      config: { type: 'run_script', scriptId: 'script-1' },
    }

    render(
      <TestWrapper>
        <QuickActionButton action={action} executionContext={defaultContext} />
      </TestWrapper>
    )

    expect(screen.getByText('Approve')).toBeDefined()
  })

  it('has correct aria-label', () => {
    const action: QuickActionDefinition = {
      id: 'action-1',
      label: 'Run Report',
      type: 'run_script',
      context: 'record',
      sortOrder: 1,
      config: { type: 'run_script', scriptId: 'script-1' },
    }

    render(
      <TestWrapper>
        <QuickActionButton action={action} executionContext={defaultContext} />
      </TestWrapper>
    )

    expect(screen.getByLabelText('Run Report')).toBeDefined()
  })

  it('navigates to new record form for create_related actions', () => {
    const action: QuickActionDefinition = {
      id: 'action-1',
      label: 'New Contact',
      type: 'create_related',
      context: 'record',
      sortOrder: 1,
      config: {
        type: 'create_related',
        targetCollection: 'contacts',
        lookupField: 'accountId',
      },
    }

    render(
      <TestWrapper>
        <QuickActionButton action={action} executionContext={defaultContext} />
      </TestWrapper>
    )

    fireEvent.click(screen.getByText('New Contact'))
    expect(mockNavigate).toHaveBeenCalledWith('/test-tenant/app/o/contacts/new?accountId=rec-123')
  })

  it('executes script for run_script actions', async () => {
    const action: QuickActionDefinition = {
      id: 'action-1',
      label: 'Process',
      type: 'run_script',
      context: 'record',
      sortOrder: 1,
      config: { type: 'run_script', scriptId: 'script-42' },
    }

    render(
      <TestWrapper>
        <QuickActionButton action={action} executionContext={defaultContext} />
      </TestWrapper>
    )

    fireEvent.click(screen.getByText('Process'))

    await waitFor(() => {
      expect(mockExecute).toHaveBeenCalledWith({
        scriptId: 'script-42',
        context: defaultContext,
      })
    })
  })

  it('shows confirmation dialog when requiresConfirmation is true', async () => {
    const action: QuickActionDefinition = {
      id: 'action-1',
      label: 'Delete All',
      type: 'run_script',
      context: 'record',
      sortOrder: 1,
      requiresConfirmation: true,
      confirmationMessage: 'Are you sure you want to delete everything?',
      config: { type: 'run_script', scriptId: 'script-1' },
    }

    render(
      <TestWrapper>
        <QuickActionButton action={action} executionContext={defaultContext} />
      </TestWrapper>
    )

    // Click button — should show dialog, not execute
    fireEvent.click(screen.getByText('Delete All'))
    expect(mockExecute).not.toHaveBeenCalled()

    // Confirmation dialog should be visible
    await waitFor(() => {
      expect(screen.getByText('Are you sure you want to delete everything?')).toBeDefined()
    })

    // Click confirm
    fireEvent.click(screen.getByText('Confirm'))

    await waitFor(() => {
      expect(mockExecute).toHaveBeenCalled()
    })
  })

  it('navigates with defaults for create_related', () => {
    const action: QuickActionDefinition = {
      id: 'action-1',
      label: 'New Contact',
      type: 'create_related',
      context: 'record',
      sortOrder: 1,
      config: {
        type: 'create_related',
        targetCollection: 'contacts',
        lookupField: 'accountId',
        defaults: { status: 'Active' },
      },
    }

    render(
      <TestWrapper>
        <QuickActionButton action={action} executionContext={defaultContext} />
      </TestWrapper>
    )

    fireEvent.click(screen.getByText('New Contact'))
    expect(mockNavigate).toHaveBeenCalledWith(
      '/test-tenant/app/o/contacts/new?accountId=rec-123&status=Active'
    )
  })
})
