import { useCallback, useRef } from 'react'
import { useAiChat } from './AiChatContext'
import type { AiProposal, ChatMessage } from './types'

interface StreamCallbacks {
  onDone?: (conversationId: string) => void
  onError?: (error: string) => void
}

export function useAiStream() {
  const { dispatch } = useAiChat()
  const abortControllerRef = useRef<AbortController | null>(null)

  const sendStreamMessage = useCallback(
    async (
      baseUrl: string,
      message: string,
      conversationId: string | null,
      contextType?: string,
      contextId?: string,
      callbacks?: StreamCallbacks
    ) => {
      // Abort any existing stream
      abortControllerRef.current?.abort()
      const abortController = new AbortController()
      abortControllerRef.current = abortController

      dispatch({ type: 'SET_STREAMING', isStreaming: true })
      dispatch({ type: 'CLEAR_STREAMING_TEXT' })

      // Add user message immediately
      const userMsg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: message,
        createdAt: new Date().toISOString(),
      }
      dispatch({ type: 'ADD_MESSAGE', message: userMsg })

      try {
        const response = await fetch(`${baseUrl}/api/ai/chat/stream`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            message,
            conversationId,
            contextType,
            contextId,
          }),
          signal: abortController.signal,
        })

        if (!response.ok || !response.body) {
          throw new Error(`Stream request failed: ${response.status}`)
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let fullText = ''
        let currentEvent = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() ?? ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              try {
                const parsed = JSON.parse(data)
                handleEvent(currentEvent, parsed, dispatch, fullText, (text) => {
                  fullText = text
                })
                if (currentEvent === 'delta') {
                  fullText += parsed.text
                }
                if (currentEvent === 'done' && callbacks?.onDone) {
                  callbacks.onDone(parsed.conversationId)
                }
              } catch {
                // Non-JSON data, ignore
              }
            }
          }
        }

        // Finalize: add the complete assistant message
        if (fullText) {
          const assistantMsg: ChatMessage = {
            id: crypto.randomUUID(),
            role: 'assistant',
            content: fullText,
            createdAt: new Date().toISOString(),
          }
          dispatch({ type: 'ADD_MESSAGE', message: assistantMsg })
        }
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          const errorMsg = (err as Error).message
          callbacks?.onError?.(errorMsg)
        }
      } finally {
        dispatch({ type: 'SET_STREAMING', isStreaming: false })
        dispatch({ type: 'CLEAR_STREAMING_TEXT' })
      }
    },
    [dispatch]
  )

  const cancelStream = useCallback(() => {
    abortControllerRef.current?.abort()
    dispatch({ type: 'SET_STREAMING', isStreaming: false })
    dispatch({ type: 'CLEAR_STREAMING_TEXT' })
  }, [dispatch])

  return { sendStreamMessage, cancelStream }
}

function handleEvent(
  event: string,
  data: Record<string, unknown>,
  dispatch: React.Dispatch<unknown>,
  _fullText: string,
  _setFullText: (text: string) => void
) {
  const d = dispatch as React.Dispatch<{
    type: string
    text?: string
    proposal?: AiProposal
    usage?: { used: number; limit: number }
    isStreaming?: boolean
  }>

  switch (event) {
    case 'delta':
      d({ type: 'APPEND_STREAMING_TEXT', text: data.text as string })
      break
    case 'proposal':
      d({ type: 'ADD_PROPOSAL', proposal: data as unknown as AiProposal })
      break
    case 'done':
      if (data.tokensUsed) {
        const tokens = data.tokensUsed as { input: number; output: number }
        d({
          type: 'SET_TOKEN_USAGE',
          usage: { used: tokens.input + tokens.output, limit: 0 },
        })
      }
      break
    case 'error':
      d({ type: 'SET_STREAMING', isStreaming: false })
      break
  }
}
