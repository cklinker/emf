import { useEffect, useRef, useCallback, useState } from 'react'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { Bot, MessageSquarePlus, History } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useAiChat } from './AiChatContext'
import { useAiStream } from './useAiStream'
import { ChatMessage, StreamingMessage } from './ChatMessage'
import { ChatInput } from './ChatInput'
import { CollectionProposalCard } from './CollectionProposalCard'
import { LayoutProposalCard } from './LayoutProposalCard'
import { TokenUsageBadge } from './TokenUsageBadge'
import type { AiProposal } from './types'

interface AiChatPanelProps {
  baseUrl?: string
  contextType?: string
  contextId?: string
  contextLabel?: string
}

export function AiChatPanel({
  baseUrl = '',
  contextType,
  contextId,
  contextLabel,
}: AiChatPanelProps) {
  const { getAccessToken } = useAuth()
  const { state, dispatch, closePanel } = useAiChat()
  const { sendStreamMessage, cancelStream } = useAiStream()
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const [applyingId, setApplyingId] = useState<string | null>(null)

  // Auto-scroll to bottom on new messages or streaming text
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [state.messages, state.streamingText])

  const handleSend = useCallback(
    (message: string) => {
      sendStreamMessage(
        baseUrl,
        message,
        state.activeConversationId,
        contextType,
        contextId,
        {
          onDone: (conversationId) => {
            dispatch({ type: 'SET_ACTIVE_CONVERSATION', id: conversationId })
          },
        }
      )
    },
    [baseUrl, state.activeConversationId, contextType, contextId, sendStreamMessage, dispatch]
  )

  const handleNewChat = useCallback(() => {
    dispatch({ type: 'SET_ACTIVE_CONVERSATION', id: null })
    dispatch({ type: 'SET_MESSAGES', messages: [] })
  }, [dispatch])

  const handleApply = useCallback(
    async (proposalId: string) => {
      setApplyingId(proposalId)
      try {
        const token = await getAccessToken()
        const headers: Record<string, string> = { 'Content-Type': 'application/json' }
        if (token) headers['Authorization'] = `Bearer ${token}`

        const response = await fetch(`${baseUrl}/api/ai/proposals/${proposalId}/apply`, {
          method: 'POST',
          headers,
        })
        if (response.ok) {
          dispatch({ type: 'UPDATE_PROPOSAL_STATUS', id: proposalId, status: 'applied' })
        }
      } catch (err) {
        console.error('Failed to apply proposal:', err)
      } finally {
        setApplyingId(null)
      }
    },
    [baseUrl, dispatch]
  )

  const handleDismiss = useCallback(
    (proposalId: string) => {
      dispatch({ type: 'UPDATE_PROPOSAL_STATUS', id: proposalId, status: 'dismissed' })
    },
    [dispatch]
  )

  const renderProposal = (proposal: AiProposal) => {
    if (proposal.type === 'collection') {
      return (
        <CollectionProposalCard
          key={proposal.id}
          proposal={proposal}
          onApply={handleApply}
          onDismiss={handleDismiss}
          isApplying={applyingId === proposal.id}
        />
      )
    }
    return (
      <LayoutProposalCard
        key={proposal.id}
        proposal={proposal}
        onApply={handleApply}
        onDismiss={handleDismiss}
        isApplying={applyingId === proposal.id}
      />
    )
  }

  return (
    <Sheet open={state.isOpen} onOpenChange={(open) => !open && closePanel()}>
      <SheetContent side="right" className="flex w-full flex-col p-0 sm:max-w-lg">
        <SheetHeader className="border-b px-4 py-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Bot className="h-5 w-5 text-primary" />
              <SheetTitle className="text-base">AI Assistant</SheetTitle>
            </div>
            <div className="flex items-center gap-1">
              {state.tokenUsage && (
                <TokenUsageBadge
                  used={state.tokenUsage.used}
                  limit={state.tokenUsage.limit}
                />
              )}
              <Button variant="ghost" size="icon" onClick={handleNewChat} title="New chat">
                <MessageSquarePlus className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" title="Chat history">
                <History className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </SheetHeader>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto">
          {state.messages.length === 0 && !state.isStreaming && (
            <div className="flex h-full items-center justify-center p-8">
              <div className="text-center">
                <Bot className="mx-auto mb-3 h-12 w-12 text-muted-foreground/30" />
                <h3 className="text-sm font-medium text-muted-foreground">
                  How can I help?
                </h3>
                <p className="mt-1 text-xs text-muted-foreground/60">
                  I can create collections and layouts for you.
                  <br />
                  Try: &quot;Create a customer collection&quot;
                </p>
              </div>
            </div>
          )}

          {state.messages.map((msg) => (
            <ChatMessage key={msg.id} message={msg} />
          ))}

          {state.isStreaming && <StreamingMessage text={state.streamingText} />}

          {/* Render proposals */}
          {state.proposals.map(renderProposal)}

          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        <ChatInput
          onSend={handleSend}
          onCancel={cancelStream}
          isStreaming={state.isStreaming}
          contextLabel={contextLabel}
        />
      </SheetContent>
    </Sheet>
  )
}
