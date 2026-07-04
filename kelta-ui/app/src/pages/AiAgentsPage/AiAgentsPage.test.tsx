/**
 * AiAgentsPage Tests — list, create (+ validation), delete, and run flows for governed AI agents.
 * API calls flow through the SDK's mocked Axios instance (see testUtils.mockAxios).
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { AiAgentsPage } from './AiAgentsPage'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'

const mockAgents = [
  {
    id: 'a1',
    tenantId: 't1',
    name: 'Support Bot',
    description: 'Answers tickets',
    systemPrompt: 'You are helpful.',
    model: null,
    maxTokens: null,
    allowedTools: ['search'],
    monthlyTokenBudget: null,
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

describe('AiAgentsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  it('renders the agents list', async () => {
    mockAxios.get.mockResolvedValue({ data: mockAgents })
    render(<AiAgentsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => expect(screen.getByText('Support Bot')).toBeInTheDocument())
  })

  it('renders the empty state when there are no agents', async () => {
    mockAxios.get.mockResolvedValue({ data: [] })
    render(<AiAgentsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => expect(screen.getByText(/no agents yet/i)).toBeInTheDocument())
  })

  it('creates an agent via the form', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValue({ data: [] })
    mockAxios.post.mockResolvedValueOnce({ data: { ...mockAgents[0], id: 'a2', name: 'New Bot' } })

    render(<AiAgentsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => expect(screen.getByText(/no agents yet/i)).toBeInTheDocument())

    await user.click(screen.getByTestId('new-agent-button'))
    await user.type(screen.getByTestId('agent-name-input'), 'New Bot')
    await user.type(screen.getByTestId('agent-system-prompt-input'), 'You are new.')
    await user.click(screen.getByTestId('agent-form-submit'))

    await waitFor(() =>
      expect(mockAxios.post).toHaveBeenCalledWith(
        '/api/ai/agents',
        expect.objectContaining({ name: 'New Bot', systemPrompt: 'You are new.' })
      )
    )
  })

  it('blocks submit and shows an error when required fields are missing', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValue({ data: [] })

    render(<AiAgentsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => expect(screen.getByText(/no agents yet/i)).toBeInTheDocument())

    await user.click(screen.getByTestId('new-agent-button'))
    await user.click(screen.getByTestId('agent-form-submit'))

    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(mockAxios.post).not.toHaveBeenCalled()
  })

  it('deletes an agent after confirmation', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValue({ data: mockAgents })
    mockAxios.delete.mockResolvedValueOnce({ data: {} })

    render(<AiAgentsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => expect(screen.getByText('Support Bot')).toBeInTheDocument())

    await user.click(screen.getByTestId('delete-button-a1'))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    await waitFor(() =>
      expect(mockAxios.delete).toHaveBeenCalledWith('/api/ai/agents/a1', undefined)
    )
  })

  it('runs an agent and shows the result', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValue({ data: mockAgents })
    mockAxios.post.mockResolvedValueOnce({
      data: {
        finalText: 'hi there',
        toolCalls: [],
        inputTokens: 5,
        outputTokens: 7,
        iterations: 1,
        stopReason: 'end_turn',
        budgetExceeded: false,
        maxIterationsReached: false,
      },
    })

    render(<AiAgentsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => expect(screen.getByText('Support Bot')).toBeInTheDocument())

    await user.click(screen.getByTestId('run-agent-button-a1'))
    await user.type(screen.getByTestId('run-input'), 'help me')
    await user.click(screen.getByTestId('run-submit'))

    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith('/api/ai/agents/a1/run', { input: 'help me' })
      expect(screen.getByTestId('run-result')).toHaveTextContent('hi there')
    })
  })
})
