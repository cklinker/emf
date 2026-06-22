import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CustomPage } from './CustomPage'
import { componentRegistry } from '@/services/componentRegistry'

const mockGet = vi.fn()
const mockGetList = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      get: mockGet,
      getList: mockGetList,
      getOne: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    },
  })),
}))

function contract(components: unknown[], title = 'My Page') {
  return { version: '1.0', slug: 'my-page', title, path: '/my-page', tree: { components } }
}

function renderAt(slug = 'my-page') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/test-tenant/app/p/${slug}`]}>
        <Routes>
          <Route path=":tenantSlug/app/p/:pageSlug" element={<CustomPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('CustomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    componentRegistry.clear()
  })

  it('shows page not found when the render endpoint 404s', async () => {
    mockGet.mockRejectedValueOnce(new Error('not found'))
    renderAt('missing')
    await waitFor(() => expect(screen.getByText('Page not found')).toBeDefined())
  })

  it('renders the component tree from the render contract', async () => {
    mockGet.mockResolvedValueOnce(
      contract([
        { id: 'h', type: 'heading', props: { text: 'Welcome' } },
        { id: 't', type: 'text', props: { content: 'Hello there' } },
        { id: 'b', type: 'button', props: { label: 'Go' } },
      ])
    )
    renderAt()
    await waitFor(() => {
      expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Welcome')
      expect(screen.getByTestId('page-node-text')).toHaveTextContent('Hello there')
      expect(screen.getByTestId('page-node-button')).toHaveTextContent('Go')
    })
  })

  it('renders nested children inside a container', async () => {
    mockGet.mockResolvedValueOnce(
      contract([
        {
          id: 'c',
          type: 'container',
          children: [{ id: 'h2', type: 'heading', props: { text: 'Nested' } }],
        },
      ])
    )
    renderAt()
    await waitFor(() => expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Nested'))
  })

  it('shows an empty-state when the tree has no components', async () => {
    mockGet.mockResolvedValueOnce(contract([]))
    renderAt()
    await waitFor(() => expect(screen.getByTestId('page-empty')).toBeDefined())
  })

  it('falls back to a placeholder for an unknown node type', async () => {
    mockGet.mockResolvedValueOnce(contract([{ id: 'x', type: 'mystery_widget', props: {} }]))
    renderAt()
    await waitFor(() =>
      expect(screen.getByTestId('page-node-unknown')).toHaveTextContent('mystery_widget')
    )
  })

  it('renders a registered plugin component for a custom node type', async () => {
    const Widget = ({ config }: { config?: Record<string, unknown> }) => (
      <div data-testid="plugin-widget">Plugin: {config?.message as string}</div>
    )
    componentRegistry.registerPageComponent('custom_widget', Widget)
    mockGet.mockResolvedValueOnce(
      contract([{ id: 'w', type: 'custom_widget', props: { message: 'hi' } }])
    )
    renderAt()
    await waitFor(() => expect(screen.getByTestId('plugin-widget')).toHaveTextContent('Plugin: hi'))
  })

  it('shows the page title in the breadcrumb', async () => {
    mockGet.mockResolvedValueOnce(contract([], 'Sales Dashboard'))
    renderAt()
    await waitFor(() => {
      expect(screen.getByText('Sales Dashboard')).toBeDefined()
      expect(screen.getByText('Home')).toBeDefined()
    })
  })

  it('binds a table node to a collection and renders the declared columns', async () => {
    mockGet.mockResolvedValueOnce(
      contract([
        {
          id: 'tbl',
          type: 'table',
          props: { dataView: { collection: 'orders', fields: ['id', 'status'] } },
        },
      ])
    )
    mockGetList.mockResolvedValueOnce([
      { id: 'o1', status: 'open' },
      { id: 'o2', status: 'shipped' },
    ])

    renderAt()

    await waitFor(() => {
      expect(screen.getByText('shipped')).toBeInTheDocument()
    })
    expect(screen.getByText('open')).toBeInTheDocument()
    // Fetches the bound collection through the authorized JSON:API path.
    expect(mockGetList).toHaveBeenCalledWith(expect.stringContaining('/api/orders'))
  })

  it('shows a placeholder for a table node without a data source', async () => {
    mockGet.mockResolvedValueOnce(contract([{ id: 'tbl', type: 'table', props: {} }]))
    renderAt()
    await waitFor(() =>
      expect(screen.getByTestId('page-node-table')).toHaveTextContent(/no data source/i)
    )
    expect(mockGetList).not.toHaveBeenCalled()
  })
})
