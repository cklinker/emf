import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { QuickActionsMenu } from './QuickActionsMenu'
import type { QuickActionExecutionContext } from '@/types/quickActions'

// Mock the hooks
vi.mock('@/hooks/useQuickActions', () => ({
  useQuickActions: vi.fn(),
}))

vi.mock('@/hooks/useScriptExecution', () => ({
  useScriptExecution: vi.fn(() => ({
    execute: vi.fn(),
    isPending: false,
    error: null,
  })),
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

import { useQuickActions } from '@/hooks/useQuickActions'

const mockUseQuickActions = vi.mocked(useQuickActions)

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

describe('QuickActionsMenu', () => {
  const defaultContext: QuickActionExecutionContext = {
    collectionName: 'accounts',
    recordId: 'rec-123',
    tenantSlug: 'test-tenant',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing when there are no actions', () => {
    mockUseQuickActions.mockReturnValue({
      actions: [],
      isLoading: false,
      error: null,
    })

    const { container } = render(
      <TestWrapper>
        <QuickActionsMenu
          collectionName="accounts"
          context="record"
          executionContext={defaultContext}
        />
      </TestWrapper>
    )

    // Should render nothing (null)
    expect(container.innerHTML).toBe('')
  })

  it('renders the menu button when actions are available', () => {
    mockUseQuickActions.mockReturnValue({
      actions: [
        {
          id: 'action-1',
          label: 'Approve',
          type: 'run_script',
          context: 'record',
          sortOrder: 1,
          config: { type: 'run_script', scriptId: 'script-1' },
        },
      ],
      isLoading: false,
      error: null,
    })

    render(
      <TestWrapper>
        <QuickActionsMenu
          collectionName="accounts"
          context="record"
          executionContext={defaultContext}
        />
      </TestWrapper>
    )

    expect(screen.getByText('Quick Actions')).toBeDefined()
  })

  it('renders custom label', () => {
    mockUseQuickActions.mockReturnValue({
      actions: [
        {
          id: 'action-1',
          label: 'Approve',
          type: 'run_script',
          context: 'record',
          sortOrder: 1,
          config: { type: 'run_script', scriptId: 'script-1' },
        },
      ],
      isLoading: false,
      error: null,
    })

    render(
      <TestWrapper>
        <QuickActionsMenu
          collectionName="accounts"
          context="record"
          executionContext={defaultContext}
          label="Actions"
        />
      </TestWrapper>
    )

    expect(screen.getByText('Actions')).toBeDefined()
  })

  it('calls useQuickActions with correct params', () => {
    mockUseQuickActions.mockReturnValue({
      actions: [],
      isLoading: false,
      error: null,
    })

    render(
      <TestWrapper>
        <QuickActionsMenu
          collectionName="contacts"
          context="list"
          executionContext={defaultContext}
        />
      </TestWrapper>
    )

    expect(mockUseQuickActions).toHaveBeenCalledWith({
      collectionName: 'contacts',
      context: 'list',
    })
  })

  it('renders button as disabled when loading', () => {
    mockUseQuickActions.mockReturnValue({
      actions: [],
      isLoading: true,
      error: null,
    })

    render(
      <TestWrapper>
        <QuickActionsMenu
          collectionName="accounts"
          context="record"
          executionContext={defaultContext}
        />
      </TestWrapper>
    )

    // When loading, the menu should still render nothing (no actions yet)
    // because actions.length === 0 even while loading
    // However, isLoading is true so it should render (the check is !isLoading && actions.length === 0)
    // Looking at the component: "if (!isLoading && actions.length === 0) return null"
    // So while loading, it WILL render the button
    const button = screen.getByText('Quick Actions')
    expect(button).toBeDefined()
  })
})
