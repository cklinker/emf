import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RulesEditor } from '../RulesEditor'

const apiClientMock = {
  getList: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}

vi.mock('../../../context/ApiContext', () => ({
  useApi: () => ({ apiClient: apiClientMock }),
}))

function renderWithProviders(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('RulesEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiClientMock.getList.mockResolvedValue([])
  })

  afterEach(() => {
    cleanup()
  })

  it('renders empty state when no rules exist', async () => {
    renderWithProviders(
      <RulesEditor layoutId="lay-1" layoutName="Edit" fieldNames={['quantity', 'unit_price']} onClose={() => {}} />,
    )
    await waitFor(() => expect(screen.getByText(/No rules yet/i)).toBeInTheDocument())
  })

  it('opens compute draft and validates formula syntax live', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <RulesEditor layoutId="lay-1" layoutName="Edit" fieldNames={['quantity', 'unit_price']} onClose={() => {}} />,
    )
    await waitFor(() => screen.getByTestId('rules-editor-add-compute'))
    await user.click(screen.getByTestId('rules-editor-add-compute'))

    const formula = screen.getByTestId('rule-formula-input')
    await user.type(formula, '1 + +')
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/Unexpected/i),
    )
  })

  it('save is disabled until required fields are filled', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <RulesEditor layoutId="lay-1" layoutName="Edit" fieldNames={[]} onClose={() => {}} />,
    )
    await waitFor(() => screen.getByTestId('rules-editor-add-compute'))
    await user.click(screen.getByTestId('rules-editor-add-compute'))
    const save = screen.getByTestId('rules-editor-save') as HTMLButtonElement
    expect(save.disabled).toBe(true)
  })

  it('detects cycles between compute rules', async () => {
    apiClientMock.getList.mockResolvedValue([
      {
        id: 'r1',
        layoutId: 'lay-1',
        name: 'r1',
        description: null,
        kind: 'COMPUTE',
        active: true,
        whenEvents: ['onChange'],
        targetField: 'fa',
        dependsOn: null,
        body: { formula: 'fb + 1' },
        sortOrder: 0,
      },
    ])

    const user = userEvent.setup()
    renderWithProviders(
      <RulesEditor layoutId="lay-1" layoutName="Edit" fieldNames={['fa', 'fb']} onClose={() => {}} />,
    )
    await waitFor(() => screen.getByTestId('rules-editor-rule-r1'))
    await user.click(screen.getByTestId('rules-editor-add-compute'))
    const target = screen.getByTestId('rule-target-input') as HTMLInputElement
    fireEvent.change(target, { target: { value: 'fb' } })
    const formula = screen.getByTestId('rule-formula-input') as HTMLTextAreaElement
    fireEvent.change(formula, { target: { value: 'fa + 1' } })

    await waitFor(() => expect(screen.getByText(/Cycle detected/i)).toBeInTheDocument())
  })

  it('saves a new compute rule via POST', async () => {
    apiClientMock.post.mockResolvedValue({})
    const user = userEvent.setup()
    renderWithProviders(
      <RulesEditor layoutId="lay-1" layoutName="Edit" fieldNames={[]} onClose={() => {}} />,
    )
    await waitFor(() => screen.getByTestId('rules-editor-add-compute'))
    await user.click(screen.getByTestId('rules-editor-add-compute'))

    fireEvent.change(screen.getByTestId('rule-name-input'), { target: { value: 'Line total' } })
    fireEvent.change(screen.getByTestId('rule-target-input'), { target: { value: 'line_total' } })
    fireEvent.change(screen.getByTestId('rule-formula-input'), {
      target: { value: '(quantity * unit_price) - discount' },
    })

    await waitFor(() => {
      const save = screen.getByTestId('rules-editor-save') as HTMLButtonElement
      expect(save.disabled).toBe(false)
    })
    await user.click(screen.getByTestId('rules-editor-save'))
    await waitFor(() => expect(apiClientMock.post).toHaveBeenCalledTimes(1))
    expect(apiClientMock.post.mock.calls[0][0]).toBe('/api/layout-rules')
    const envelope = apiClientMock.post.mock.calls[0][1]
    expect(envelope.data.attributes.name).toBe('Line total')
    expect(envelope.data.attributes.kind).toBe('COMPUTE')
    expect(envelope.data.attributes.targetField).toBe('line_total')
    expect(envelope.data.attributes.body.formula).toBe('(quantity * unit_price) - discount')
  })
})

import { afterEach } from 'vitest'
