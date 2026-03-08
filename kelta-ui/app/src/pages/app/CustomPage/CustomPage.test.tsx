import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CustomPage } from './CustomPage'
import { componentRegistry } from '@/services/componentRegistry'

// Mock API context
const mockGetList = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: vi.fn(),
      getList: mockGetList,
      getOne: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    },
  })),
}))

function TestWrapper({
  children,
  initialEntries,
}: {
  children: React.ReactNode
  initialEntries: string[]
}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('CustomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    componentRegistry.clear()
  })

  it('shows page not found when API returns null', async () => {
    mockGetList.mockResolvedValueOnce([])

    render(
      <TestWrapper initialEntries={['/test-tenant/app/p/my-page']}>
        <Routes>
          <Route path=":tenantSlug/app/p/:pageSlug" element={<CustomPage />} />
        </Routes>
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('Page Not Found')).toBeDefined()
    })
  })

  it('shows component not available when not registered', async () => {
    mockGetList.mockResolvedValueOnce([
      {
        id: 'page-1',
        path: 'my-page',
        title: 'My Custom Page',
        component: 'unregistered_widget',
      },
    ])

    render(
      <TestWrapper initialEntries={['/test-tenant/app/p/my-page']}>
        <Routes>
          <Route path=":tenantSlug/app/p/:pageSlug" element={<CustomPage />} />
        </Routes>
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('Component Not Available')).toBeDefined()
    })
  })

  it('renders the registered component when found', async () => {
    // Register a test component
    const TestPageComponent = ({ config }: { config?: Record<string, unknown> }) => (
      <div data-testid="custom-component">Custom Content: {config?.message as string}</div>
    )
    componentRegistry.registerPageComponent('test_widget', TestPageComponent)

    mockGetList.mockResolvedValueOnce([
      {
        id: 'page-1',
        path: 'my-page',
        title: 'Test Page',
        component: 'test_widget',
        props: { message: 'Hello World' },
      },
    ])

    render(
      <TestWrapper initialEntries={['/test-tenant/app/p/my-page']}>
        <Routes>
          <Route path=":tenantSlug/app/p/:pageSlug" element={<CustomPage />} />
        </Routes>
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('custom-component')).toBeDefined()
      expect(screen.getByText('Custom Content: Hello World')).toBeDefined()
    })
  })

  it('shows breadcrumb with page title', async () => {
    const TestComponent = () => <div>Test</div>
    componentRegistry.registerPageComponent('my_comp', TestComponent)

    mockGetList.mockResolvedValueOnce([
      {
        id: 'page-1',
        path: 'dashboard',
        title: 'Sales Dashboard',
        component: 'my_comp',
      },
    ])

    render(
      <TestWrapper initialEntries={['/test-tenant/app/p/dashboard']}>
        <Routes>
          <Route path=":tenantSlug/app/p/:pageSlug" element={<CustomPage />} />
        </Routes>
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('Sales Dashboard')).toBeDefined()
      expect(screen.getByText('Home')).toBeDefined()
    })
  })

  it('handles API error gracefully', async () => {
    mockGetList.mockRejectedValueOnce(new Error('Network error'))

    render(
      <TestWrapper initialEntries={['/test-tenant/app/p/broken-page']}>
        <Routes>
          <Route path=":tenantSlug/app/p/:pageSlug" element={<CustomPage />} />
        </Routes>
      </TestWrapper>
    )

    // When the API fails, the query catch returns null, so we get "Page Not Found"
    await waitFor(() => {
      expect(screen.getByText('Page Not Found')).toBeDefined()
    })
  })
})
