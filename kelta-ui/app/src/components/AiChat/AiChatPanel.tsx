import { useEffect, useRef, useCallback, useState } from 'react'
import { uuid } from '@/utils/uuid'
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
import { AddFieldsProposalCard } from './AddFieldsProposalCard'
import { UpdateFieldProposalCard } from './UpdateFieldProposalCard'
import { RemoveFieldProposalCard } from './RemoveFieldProposalCard'
import { PicklistProposalCard } from './PicklistProposalCard'
import { UiPageProposalCard } from './UiPageProposalCard'
import { ToolCallIndicator } from './ToolCallIndicator'
import { TokenUsageBadge } from './TokenUsageBadge'
import type { AiProposal, ChatMessage as ChatMessageType, ToolCallState } from './types'

interface AiChatPanelProps {
  baseUrl?: string
  contextType?: string
  contextId?: string
  contextLabel?: string
}

interface RawHistoryMessage {
  id: string
  role: 'user' | 'assistant'
  contentBlocks?: Array<Record<string, unknown>>
  content?: string
  tokensInput?: number
  tokensOutput?: number
  createdAt: string
}

function rehydrateMessages(raw: RawHistoryMessage[]): {
  messages: ChatMessageType[]
  proposals: AiProposal[]
} {
  const messages: ChatMessageType[] = []
  const proposals: AiProposal[] = []

  for (const m of raw) {
    const blocks = m.contentBlocks ?? []
    let textBuffer = ''

    for (const block of blocks) {
      const type = block.type as string | undefined

      if (type === 'text') {
        textBuffer += (block.text as string) ?? ''
        continue
      }

      if (type === 'tool_use') {
        if (textBuffer.trim()) {
          messages.push({
            id: uuid(),
            role: m.role,
            content: textBuffer,
            createdAt: m.createdAt,
          })
          textBuffer = ''
        }
        const name = block.name as string
        const toolUseId = block.id as string
        const proposalId = block.proposalId as string | undefined
        const input = block.input as Record<string, unknown> | undefined

        if (proposalId && name?.startsWith('propose_')) {
          const proposal: AiProposal = {
            id: proposalId,
            type: name.replace(/^propose_/, '') as AiProposal['type'],
            status: 'applied',
            data: input ?? {},
            createdAt: m.createdAt,
          }
          proposals.push(proposal)
          messages.push({
            id: `proposal-${proposalId}`,
            role: 'assistant',
            content: '',
            proposals: [proposal],
            createdAt: m.createdAt,
          })
        } else {
          const toolCall: ToolCallState = {
            id: toolUseId,
            name,
            status: 'done',
          }
          messages.push({
            id: `toolcall-${toolUseId}`,
            role: 'assistant',
            content: '',
            toolCall,
            createdAt: m.createdAt,
          })
        }
      }
    }

    if (textBuffer.trim()) {
      messages.push({
        id: uuid(),
        role: m.role,
        content: textBuffer,
        createdAt: m.createdAt,
      })
    }

    // Fallback for legacy rows where contentBlocks is empty
    if (blocks.length === 0 && m.content) {
      messages.push({
        id: uuid(),
        role: m.role,
        content: m.content,
        createdAt: m.createdAt,
      })
    }
  }

  return { messages, proposals }
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
  const hydratedConvIdRef = useRef<string | null>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [state.messages, state.streamingText, state.proposals])

  // Rehydrate conversation history on mount when an activeConversationId is set
  // (e.g. user reopens a saved conversation or refreshes the page).
  useEffect(() => {
    const convId = state.activeConversationId
    if (!convId || hydratedConvIdRef.current === convId) return
    if (state.messages.length > 0) {
      hydratedConvIdRef.current = convId
      return
    }

    let cancelled = false
    const load = async () => {
      try {
        const token = await getAccessToken()
        const headers: Record<string, string> = {}
        if (token) headers['Authorization'] = `Bearer ${token}`
        const response = await fetch(`${baseUrl}/api/ai/conversations/${convId}/messages`, {
          headers,
        })
        if (!response.ok) return
        const body = await response.json()
        const raw = (body.messages ?? []) as RawHistoryMessage[]
        if (cancelled) return
        const { messages, proposals } = rehydrateMessages(raw)
        dispatch({ type: 'LOAD_CONVERSATION_HISTORY', messages, proposals })
        hydratedConvIdRef.current = convId
      } catch (err) {
        console.error('Failed to rehydrate conversation:', err)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [state.activeConversationId, state.messages.length, baseUrl, dispatch, getAccessToken])

  const handleSend = useCallback(
    (message: string) => {
      sendStreamMessage(baseUrl, message, state.activeConversationId, contextType, contextId, {
        onDone: (conversationId) => {
          dispatch({ type: 'SET_CONVERSATION_ID', id: conversationId })
          hydratedConvIdRef.current = conversationId
        },
      })
    },
    [baseUrl, state.activeConversationId, contextType, contextId, sendStreamMessage, dispatch]
  )

  const handleNewChat = useCallback(() => {
    dispatch({ type: 'SET_ACTIVE_CONVERSATION', id: null })
    dispatch({ type: 'SET_MESSAGES', messages: [] })
    hydratedConvIdRef.current = null
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
          try {
            const body = await response.json()
            const warnings = body.warnings as string[] | undefined
            if (warnings && warnings.length > 0) {
              const warningMsg = {
                id: uuid(),
                role: 'assistant' as const,
                content: `Applied with ${warnings.length} warning(s):\n${warnings.map((w: string) => `- ${w}`).join('\n')}`,
                createdAt: new Date().toISOString(),
              }
              dispatch({ type: 'ADD_MESSAGE', message: warningMsg })
            }
          } catch {
            /* ignore */
          }
        } else {
          let errorMsg = `Apply failed (${response.status})`
          try {
            const errorBody = await response.json()
            if (errorBody.errors?.[0]?.detail) {
              errorMsg = errorBody.errors[0].detail
            } else if (errorBody.errors?.[0]?.title) {
              errorMsg = errorBody.errors[0].title
            }
          } catch {
            /* ignore */
          }
          const errorChatMsg = {
            id: uuid(),
            role: 'assistant' as const,
            content: `Failed to apply: ${errorMsg}`,
            createdAt: new Date().toISOString(),
          }
          dispatch({ type: 'ADD_MESSAGE', message: errorChatMsg })
        }
      } catch (err) {
        console.error('Failed to apply proposal:', err)
      } finally {
        setApplyingId(null)
      }
    },
    [baseUrl, dispatch, getAccessToken]
  )

  const handleDismiss = useCallback(
    (proposalId: string) => {
      dispatch({ type: 'UPDATE_PROPOSAL_STATUS', id: proposalId, status: 'dismissed' })
    },
    [dispatch]
  )

  const renderProposal = (proposal: AiProposal) => {
    const props = {
      proposal,
      onApply: handleApply,
      onDismiss: handleDismiss,
      isApplying: applyingId === proposal.id,
    }
    switch (proposal.type) {
      case 'collection':
        return <CollectionProposalCard key={proposal.id} {...props} />
      case 'layout':
        return <LayoutProposalCard key={proposal.id} {...props} />
      case 'add_fields':
        return <AddFieldsProposalCard key={proposal.id} {...props} />
      case 'update_field':
        return <UpdateFieldProposalCard key={proposal.id} {...props} />
      case 'remove_field':
        return <RemoveFieldProposalCard key={proposal.id} {...props} />
      case 'picklist':
        return <PicklistProposalCard key={proposal.id} {...props} />
      case 'ui_page':
        return <UiPageProposalCard key={proposal.id} {...props} />
      default:
        return null
    }
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
                <TokenUsageBadge used={state.tokenUsage.used} limit={state.tokenUsage.limit} />
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

        <div className="flex-1 overflow-y-auto">
          {state.messages.length === 0 && !state.isStreaming && state.proposals.length === 0 && (
            <div className="flex h-full items-center justify-center p-8">
              <div className="text-center">
                <Bot className="mx-auto mb-3 h-12 w-12 text-muted-foreground/30" />
                <h3 className="text-sm font-medium text-muted-foreground">How can I help?</h3>
                <p className="mt-1 text-xs text-muted-foreground/60">
                  Ask me to review or extend your collections.
                  <br />
                  Try: &quot;Review the fields in products&quot;
                </p>
              </div>
            </div>
          )}

          {state.messages.map((msg) => {
            if (msg.toolCall) {
              return <ToolCallIndicator key={msg.id} call={msg.toolCall} />
            }
            if (msg.proposals && msg.proposals.length > 0) {
              return msg.proposals.map(renderProposal)
            }
            return <ChatMessage key={msg.id} message={msg} />
          })}

          {state.isStreaming && <StreamingMessage text={state.streamingText} isGenerating={true} />}

          <div ref={messagesEndRef} />
        </div>

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
