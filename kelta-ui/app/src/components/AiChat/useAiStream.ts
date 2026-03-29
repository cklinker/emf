import { useCallback, useRef } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useAiChat } from './AiChatContext'
import type { AiProposal, ChatMessage } from './types'

interface StreamCallbacks {
  onDone?: (conversationId: string) => void
  onError?: (error: string) => void
}

export function useAiStream() {
  const { dispatch } = useAiChat()
  const { getAccessToken } = useAuth()
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
        // Get auth token
        const token = await getAccessToken()

        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
        }
        if (token) {
          headers['Authorization'] = `Bearer ${token}`
        }

        const response = await fetch(`${baseUrl}/api/ai/chat/stream`, {
          method: 'POST',
          headers,
          body: JSON.stringify({
            message,
            conversationId,
            contextType,
            contextId,
          }),
          signal: abortController.signal,
        })

        if (!response.ok) {
          let errorMsg = `Request failed with status ${response.status}`
          try {
            const errorBody = await response.json()
            if (errorBody.errors?.[0]?.title) {
              errorMsg = errorBody.errors[0].title
            }
          } catch {
            // ignore parse error
          }

          // Add error as assistant message so it's visible in the chat
          const errorChatMsg: ChatMessage = {
            id: crypto.randomUUID(),
            role: 'assistant',
            content: `Sorry, I encountered an error: ${errorMsg}`,
            createdAt: new Date().toISOString(),
          }
          dispatch({ type: 'ADD_MESSAGE', message: errorChatMsg })
          callbacks?.onError?.(errorMsg)
          return
        }

        if (!response.body) {
          throw new Error('No response body')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let fullText = ''
        let currentEvent = ''

        let done = false
        while (!done) {
          const result = await reader.read()
          done = result.done
          if (done) break

          buffer += decoder.decode(result.value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() ?? ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              try {
                const parsed = JSON.parse(data)

                switch (currentEvent) {
                  case 'delta':
                    fullText += parsed.text
                    dispatch({ type: 'APPEND_STREAMING_TEXT', text: parsed.text as string })
                    break
                  case 'tool_use':
                    // When tool use starts, finalize any accumulated text as a message
                    if (fullText.trim()) {
                      const textMsg: ChatMessage = {
                        id: crypto.randomUUID(),
                        role: 'assistant',
                        content: fullText,
                        createdAt: new Date().toISOString(),
                      }
                      dispatch({ type: 'ADD_MESSAGE', message: textMsg })
                      dispatch({ type: 'CLEAR_STREAMING_TEXT' })
                      fullText = ''
                    }
                    break
                  case 'proposal': {
                    const proposal = parsed as AiProposal
                    dispatch({ type: 'ADD_PROPOSAL', proposal })
                    // Add as a message so it appears inline in the chat flow
                    const proposalMsg: ChatMessage = {
                      id: `proposal-${proposal.id}`,
                      role: 'assistant',
                      content: '',
                      proposals: [proposal],
                      createdAt: new Date().toISOString(),
                    }
                    dispatch({ type: 'ADD_MESSAGE', message: proposalMsg })
                    break
                  }
                  case 'done':
                    if (parsed.tokensUsed) {
                      const tokens = parsed.tokensUsed as {
                        input: number
                        output: number
                      }
                      dispatch({
                        type: 'SET_TOKEN_USAGE',
                        usage: {
                          used: tokens.input + tokens.output,
                          limit: 0,
                        },
                      })
                    }
                    if (callbacks?.onDone) {
                      callbacks.onDone(parsed.conversationId)
                    }
                    break
                  case 'error':
                    callbacks?.onError?.(parsed.message)
                    break
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
          // Show error in the chat UI
          const errorChatMsg: ChatMessage = {
            id: crypto.randomUUID(),
            role: 'assistant',
            content: `Sorry, I encountered an error: ${errorMsg}`,
            createdAt: new Date().toISOString(),
          }
          dispatch({ type: 'ADD_MESSAGE', message: errorChatMsg })
          callbacks?.onError?.(errorMsg)
        }
      } finally {
        dispatch({ type: 'SET_STREAMING', isStreaming: false })
        dispatch({ type: 'CLEAR_STREAMING_TEXT' })
      }
    },
    [dispatch, getAccessToken]
  )

  const cancelStream = useCallback(() => {
    abortControllerRef.current?.abort()
    dispatch({ type: 'SET_STREAMING', isStreaming: false })
    dispatch({ type: 'CLEAR_STREAMING_TEXT' })
  }, [dispatch])

  return { sendStreamMessage, cancelStream }
}
