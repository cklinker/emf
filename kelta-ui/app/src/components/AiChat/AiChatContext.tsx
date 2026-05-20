import React, { createContext, useContext, useReducer, useCallback } from 'react'
import type {
  ChatMessage,
  AiProposal,
  ConversationSummary,
  AiChatState,
  ToolCallStatus,
} from './types'

type AiChatAction =
  | { type: 'TOGGLE_PANEL' }
  | { type: 'OPEN_PANEL' }
  | { type: 'CLOSE_PANEL' }
  | { type: 'SET_CONVERSATIONS'; conversations: ConversationSummary[] }
  | { type: 'SET_ACTIVE_CONVERSATION'; id: string | null }
  | { type: 'SET_CONVERSATION_ID'; id: string }
  | { type: 'SET_MESSAGES'; messages: ChatMessage[] }
  | { type: 'LOAD_CONVERSATION_HISTORY'; messages: ChatMessage[]; proposals: AiProposal[] }
  | { type: 'ADD_MESSAGE'; message: ChatMessage }
  | { type: 'SET_STREAMING'; isStreaming: boolean }
  | { type: 'APPEND_STREAMING_TEXT'; text: string }
  | { type: 'CLEAR_STREAMING_TEXT' }
  | { type: 'ADD_PROPOSAL'; proposal: AiProposal }
  | { type: 'UPDATE_PROPOSAL_STATUS'; id: string; status: AiProposal['status'] }
  | { type: 'UPDATE_TOOL_CALL_STATUS'; toolUseId: string; status: ToolCallStatus; summary?: string }
  | { type: 'SET_TOKEN_USAGE'; usage: { used: number; limit: number } | null }

const initialState: AiChatState = {
  isOpen: false,
  conversations: [],
  activeConversationId: null,
  messages: [],
  isStreaming: false,
  streamingText: '',
  proposals: [],
  tokenUsage: null,
}

function reducer(state: AiChatState, action: AiChatAction): AiChatState {
  switch (action.type) {
    case 'TOGGLE_PANEL':
      return { ...state, isOpen: !state.isOpen }
    case 'OPEN_PANEL':
      return { ...state, isOpen: true }
    case 'CLOSE_PANEL':
      return { ...state, isOpen: false }
    case 'SET_CONVERSATIONS':
      return { ...state, conversations: action.conversations }
    case 'SET_ACTIVE_CONVERSATION':
      return {
        ...state,
        activeConversationId: action.id,
        messages: [],
        streamingText: '',
        proposals: [],
      }
    case 'SET_CONVERSATION_ID':
      return { ...state, activeConversationId: action.id }
    case 'SET_MESSAGES':
      return { ...state, messages: action.messages }
    case 'LOAD_CONVERSATION_HISTORY':
      return {
        ...state,
        messages: action.messages,
        proposals: action.proposals,
        streamingText: '',
      }
    case 'ADD_MESSAGE':
      return { ...state, messages: [...state.messages, action.message] }
    case 'SET_STREAMING':
      return { ...state, isStreaming: action.isStreaming }
    case 'APPEND_STREAMING_TEXT':
      return { ...state, streamingText: state.streamingText + action.text }
    case 'CLEAR_STREAMING_TEXT':
      return { ...state, streamingText: '' }
    case 'ADD_PROPOSAL':
      return { ...state, proposals: [...state.proposals, action.proposal] }
    case 'UPDATE_PROPOSAL_STATUS':
      return {
        ...state,
        proposals: state.proposals.map((p) =>
          p.id === action.id ? { ...p, status: action.status } : p
        ),
        messages: state.messages.map((msg) =>
          msg.proposals
            ? {
                ...msg,
                proposals: msg.proposals.map((p) =>
                  p.id === action.id ? { ...p, status: action.status } : p
                ),
              }
            : msg
        ),
      }
    case 'UPDATE_TOOL_CALL_STATUS':
      return {
        ...state,
        messages: state.messages.map((msg) =>
          msg.toolCall && msg.toolCall.id === action.toolUseId
            ? {
                ...msg,
                toolCall: { ...msg.toolCall, status: action.status, summary: action.summary },
              }
            : msg
        ),
      }
    case 'SET_TOKEN_USAGE':
      return { ...state, tokenUsage: action.usage }
    default:
      return state
  }
}

interface AiChatContextValue {
  state: AiChatState
  dispatch: React.Dispatch<AiChatAction>
  togglePanel: () => void
  openPanel: () => void
  closePanel: () => void
}

const AiChatContext = createContext<AiChatContextValue | undefined>(undefined)

export function AiChatProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState)

  const togglePanel = useCallback(() => dispatch({ type: 'TOGGLE_PANEL' }), [])
  const openPanel = useCallback(() => dispatch({ type: 'OPEN_PANEL' }), [])
  const closePanel = useCallback(() => dispatch({ type: 'CLOSE_PANEL' }), [])

  return (
    <AiChatContext.Provider value={{ state, dispatch, togglePanel, openPanel, closePanel }}>
      {children}
    </AiChatContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAiChat() {
  const context = useContext(AiChatContext)
  if (!context) {
    throw new Error('useAiChat must be used within an AiChatProvider')
  }
  return context
}
