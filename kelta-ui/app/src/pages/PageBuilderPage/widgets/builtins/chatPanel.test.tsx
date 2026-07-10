/**
 * Chat-panel widget tests (telehealth slice 3) — editor sample without any
 * network/socket activity, runtime empty-state start button, and the live
 * thread path over mocked chat hooks.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { chatPanelWidget } from './chatPanel'
import type { RenderNode } from '../types'

const useConversationsMock = vi.fn()
const useChatMessagesMock = vi.fn()
const sendMutateAsync = vi.fn()
const startMutate = vi.fn()

vi.mock('@/hooks/useChat', () => ({
  useConversations: (...args: unknown[]) => useConversationsMock(...args),
  useChatMessages: (...args: unknown[]) => useChatMessagesMock(...args),
  useSendChatMessage: () => ({ mutateAsync: sendMutateAsync }),
  useStartConversation: () => ({ mutate: startMutate, isPending: false }),
}))
vi.mock('@/hooks/useMyIdentity', () => ({
  useMyIdentity: () => ({ identity: { userId: 'u-portal' } }),
}))
vi.mock('@/context/I18nContext', () => ({
  useI18n: () => ({ t: (_key: string, fallback: string) => fallback }),
}))

const node = (props: Record<string, unknown> = {}): RenderNode => ({
  id: 'c1',
  type: 'chat-panel',
  props,
})

function renderPanel(nodeArg: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  const Render = chatPanelWidget.Render
  return render(
    <Render node={nodeArg} scope={{}} mode={mode} tenantSlug="acme" renderChild={() => null} />
  )
}

describe('chat-panel widget', () => {
  beforeEach(() => {
    useConversationsMock.mockReset()
    useChatMessagesMock.mockReset()
    sendMutateAsync.mockReset()
    startMutate.mockReset()
  })

  it('renders a static sample in editor mode with no data hooks firing', () => {
    renderPanel(node({ welcomeText: 'Ask us anything' }), 'editor')
    expect(screen.getByTestId('page-node-chat-panel')).toHaveTextContent('Ask us anything')
    expect(useConversationsMock).not.toHaveBeenCalled()
  })

  it('offers to start a conversation when the portal user has none', async () => {
    useConversationsMock.mockReturnValue({ data: [], isLoading: false })
    useChatMessagesMock.mockReturnValue({ data: [] })

    renderPanel(node({ queueId: 'q1', subject: 'Portal question' }))
    await userEvent.click(screen.getByTestId('page-node-chat-panel-start'))

    expect(startMutate).toHaveBeenCalledWith({ queueId: 'q1', subject: 'Portal question' })
  })

  it('renders the newest writable conversation thread with the composer', () => {
    useConversationsMock.mockReturnValue({
      data: [
        { id: 'conv-old', status: 'CLOSED', origin: 'PORTAL' },
        { id: 'conv-live', status: 'ASSIGNED', origin: 'PORTAL' },
      ],
      isLoading: false,
    })
    useChatMessagesMock.mockReturnValue({
      data: [
        { id: 'm1', senderId: 'u-agent', senderType: 'INTERNAL', kind: 'TEXT', body: 'Hello' },
      ],
    })

    renderPanel(node())

    expect(useChatMessagesMock).toHaveBeenCalledWith('conv-live')
    expect(screen.getByText('Hello')).toBeInTheDocument()
    expect(screen.getByTestId('page-node-chat-panel-composer')).toBeInTheDocument()
  })

  it('shows the closed state with a restart button when only closed conversations exist', () => {
    useConversationsMock.mockReturnValue({
      data: [{ id: 'conv-old', status: 'CLOSED', origin: 'PORTAL' }],
      isLoading: false,
    })
    useChatMessagesMock.mockReturnValue({ data: [] })

    renderPanel(node())

    expect(screen.getByText('This conversation is closed')).toBeInTheDocument()
    expect(screen.queryByTestId('page-node-chat-panel-composer')).not.toBeInTheDocument()
  })
})
