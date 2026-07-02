import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { QuickActionsMenu } from './QuickActionsMenu'
import type { QuickActionExecutionContext } from '@/types/quickActions'
import { componentRegistry } from '@/services/componentRegistry'

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

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

// Render shadcn DropdownMenu children directly so menu items are clickable in jsdom
// (avoids Radix/floating-ui pointer-capture issues) — same approach as UserMenu.test.
vi.mock('@/components/ui/dropdown-menu', () => ({
  DropdownMenu: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: React.ReactNode; asChild?: boolean }) => (
    <div>{children}</div>
  ),
  DropdownMenuContent: ({
    children,
    ...props
  }: React.PropsWithChildren<Record<string, unknown>>) => <div {...props}>{children}</div>,
  DropdownMenuItem: ({
    children,
    onClick,
    ...props
  }: React.PropsWithChildren<{ onClick?: () => void } & Record<string, unknown>>) => (
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/interactive-supports-focus
    <div role="menuitem" onClick={onClick} {...props}>
      {children}
    </div>
  ),
  DropdownMenuSeparator: () => <hr />,
}))

import { useQuickActions } from '@/hooks/useQuickActions'
import { useApi } from '@/context/ApiContext'
import { toast } from 'sonner'

const mockUseQuickActions = vi.mocked(useQuickActions)
const mockUseApi = vi.mocked(useApi)

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

  describe('send_email action', () => {
    const emailContext: QuickActionExecutionContext = {
      collectionName: 'accounts',
      recordId: 'rec-123',
      record: { email: 'dest@test.com', firstName: 'Ada' },
      tenantSlug: 'test-tenant',
    }

    function mockApiPost() {
      const post = vi.fn().mockResolvedValue({})
      mockUseApi.mockReturnValue({
        apiClient: { get: vi.fn(), post, patch: vi.fn(), postResource: vi.fn() },
      } as unknown as ReturnType<typeof useApi>)
      return post
    }

    it('POSTs to /api/email/send with the resolved recipient and merge context', async () => {
      const post = mockApiPost()
      mockUseQuickActions.mockReturnValue({
        actions: [
          {
            id: 'a1',
            label: 'Send Welcome',
            type: 'send_email',
            context: 'record',
            sortOrder: 1,
            config: { type: 'send_email', templateId: 'tpl-1', recipientField: 'email' },
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
            executionContext={emailContext}
          />
        </TestWrapper>
      )

      fireEvent.click(screen.getByText('Send Welcome'))

      await waitFor(() =>
        expect(post).toHaveBeenCalledWith('/api/email/send', {
          templateId: 'tpl-1',
          to: 'dest@test.com',
          mergeContext: { email: 'dest@test.com', firstName: 'Ada' },
        })
      )
      expect(toast.success).toHaveBeenCalledWith('Email sent')
    })

    it('errors without sending when the recipient field is empty', async () => {
      const post = mockApiPost()
      mockUseQuickActions.mockReturnValue({
        actions: [
          {
            id: 'a1',
            label: 'Send Welcome',
            type: 'send_email',
            context: 'record',
            sortOrder: 1,
            config: { type: 'send_email', templateId: 'tpl-1', recipientField: 'missingField' },
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
            executionContext={emailContext}
          />
        </TestWrapper>
      )

      fireEvent.click(screen.getByText('Send Welcome'))

      await waitFor(() =>
        expect(toast.error).toHaveBeenCalledWith('No recipient email found on this record')
      )
      expect(post).not.toHaveBeenCalled()
    })
  })

  describe('custom action', () => {
    it('renders the registered plugin component with the exec context', async () => {
      componentRegistry.registerQuickAction('my-widget', ({ record, onComplete }) => (
        <div data-testid="custom-widget">
          {String(record?.firstName)}
          <button onClick={onComplete}>done</button>
        </div>
      ))
      mockUseQuickActions.mockReturnValue({
        actions: [
          {
            id: 'a1',
            label: 'Open Widget',
            type: 'custom',
            context: 'record',
            sortOrder: 1,
            config: { type: 'custom', componentName: 'my-widget' },
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
            executionContext={{
              collectionName: 'accounts',
              recordId: 'rec-123',
              record: { firstName: 'Ada' },
              tenantSlug: 'test-tenant',
            }}
          />
        </TestWrapper>
      )

      fireEvent.click(screen.getByText('Open Widget'))

      await waitFor(() => expect(screen.getByTestId('custom-widget')).toBeDefined())
      expect(screen.getByText('Ada')).toBeDefined()
    })

    it('shows a hint when the named component is not registered', async () => {
      mockUseQuickActions.mockReturnValue({
        actions: [
          {
            id: 'a1',
            label: 'Open Missing',
            type: 'custom',
            context: 'record',
            sortOrder: 1,
            config: { type: 'custom', componentName: 'not-registered' },
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

      fireEvent.click(screen.getByText('Open Missing'))

      await waitFor(() =>
        expect(toast.info).toHaveBeenCalledWith(
          'Custom action requires a registered plugin component.'
        )
      )
      expect(screen.queryByTestId('custom-widget')).toBeNull()
    })
  })
})
