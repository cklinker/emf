import { useCallback, useRef } from 'react'
import { uuid } from '@/utils/uuid'
import { useAuth } from '../../context/AuthContext'
import { useAiChat } from './AiChatContext'
import type { AiProposal, ChatMessage, ToolCallStatus } from './types'

interface StreamCallbacks {
  onDone?: (conversationId: string) => void
  onError?: (error: string) => void
}

function toolCallMessageId(toolUseId: string): string {
  return `toolcall-${toolUseId}`
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
      abortControllerRef.current?.abort()
      const abortController = new AbortController()
      abortControllerRef.current = abortController

      dispatch({ type: 'SET_STREAMING', isStreaming: true })
      dispatch({ type: 'CLEAR_STREAMING_TEXT' })

      const userMsg: ChatMessage = {
        id: uuid(),
        role: 'user',
        content: message,
        createdAt: new Date().toISOString(),
      }
      dispatch({ type: 'ADD_MESSAGE', message: userMsg })

      try {
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
          const errorChatMsg: ChatMessage = {
            id: uuid(),
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

        const flushStreamingText = () => {
          if (fullText.trim()) {
            const textMsg: ChatMessage = {
              id: uuid(),
              role: 'assistant',
              content: fullText,
              createdAt: new Date().toISOString(),
            }
            dispatch({ type: 'ADD_MESSAGE', message: textMsg })
            dispatch({ type: 'CLEAR_STREAMING_TEXT' })
            fullText = ''
          }
        }

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
                    // Propose tools — finalize streaming text; the proposal event will follow.
                    flushStreamingText()
                    break
                  case 'tool_call': {
                    // Read tool — render a transient pill.
                    flushStreamingText()
                    const toolUseId = parsed.toolUseId as string
                    const callMsg: ChatMessage = {
                      id: toolCallMessageId(toolUseId),
                      role: 'assistant',
                      content: '',
                      toolCall: {
                        id: toolUseId,
                        name: parsed.name as string,
                        status: 'pending',
                      },
                      createdAt: new Date().toISOString(),
                    }
                    dispatch({ type: 'ADD_MESSAGE', message: callMsg })
                    break
                  }
                  case 'tool_result': {
                    const status = (parsed.status as ToolCallStatus | undefined) ??
                      (parsed.isError ? 'error' : 'done')
                    dispatch({
                      type: 'UPDATE_TOOL_CALL_STATUS',
                      toolUseId: parsed.toolUseId as string,
                      status,
                      summary: parsed.summary as string | undefined,
                    })
                    break
                  }
                  case 'proposal': {
                    flushStreamingText()
                    const proposal = parsed as AiProposal
                    dispatch({ type: 'ADD_PROPOSAL', proposal })
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

        if (fullText) {
          const assistantMsg: ChatMessage = {
            id: uuid(),
            role: 'assistant',
            content: fullText,
            createdAt: new Date().toISOString(),
          }
          dispatch({ type: 'ADD_MESSAGE', message: assistantMsg })
        }
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          const errorMsg = (err as Error).message
          const errorChatMsg: ChatMessage = {
            id: uuid(),
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
