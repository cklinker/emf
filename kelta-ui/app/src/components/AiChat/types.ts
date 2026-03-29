export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  proposals?: AiProposal[]
  tokensInput?: number
  tokensOutput?: number
  createdAt: string
}

export interface AiProposal {
  id: string
  type: 'collection' | 'layout'
  status: 'pending' | 'applied' | 'dismissed'
  data: Record<string, unknown>
  createdAt: string
}

export interface ConversationSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface AiChatState {
  isOpen: boolean
  conversations: ConversationSummary[]
  activeConversationId: string | null
  messages: ChatMessage[]
  isStreaming: boolean
  streamingText: string
  proposals: AiProposal[]
  tokenUsage: { used: number; limit: number } | null
}

export interface SseEvent {
  event: string
  data: string
}

export interface ProposedField {
  name: string
  type: string
  nullable?: boolean
  unique?: boolean
  defaultValue?: string
  validationRules?: Array<{ type: string; value: string }>
  enumValues?: string[]
  referenceConfig?: {
    targetCollection: string
    relationshipName?: string
    cascadeDelete?: boolean
  }
}

export interface ProposedSection {
  heading: string
  columns: number
  style: string
  fieldPlacements: Array<{
    fieldName: string
    columnNumber: number
    sortOrder: number
  }>
}
