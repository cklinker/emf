import { useCallback, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../services/apiClient'
import { useRealtimeClient } from '../realtime/RealtimeProvider'

/**
 * Chat data hooks (telehealth slice 3) over the /api/chat/** plain-JSON API
 * (participant authz server-side). Live updates arrive two ways:
 * - joined conversations: chat.* socket events → RealtimeProvider invalidates
 *   ['chat-messages', id] / ['chat-conversations'] (invalidation-only rule);
 * - everything else (new conversations for agents who haven't joined them):
 *   a 30s poll on the conversation lists — conversation events only reach
 *   JOINED sockets by design, so the inbox/bell can't rely on push alone.
 */

export interface ChatConversation {
  id: string
  queueId?: string | null
  subject?: string | null
  status: 'OPEN' | 'ASSIGNED' | 'CLOSED' | 'ARCHIVED'
  origin: 'PORTAL' | 'INTERNAL'
  assignedTo?: string | null
  contextRecordId?: string | null
  lastMessageAt?: string | null
  closedAt?: string | null
  createdAt?: string | null
  myLastReadAt?: string | null
}

export interface ChatMessage {
  id: string
  senderId?: string | null
  senderType: 'INTERNAL' | 'PORTAL' | 'SYSTEM'
  kind: 'TEXT' | 'SYSTEM' | 'ATTACHMENT'
  body: string
  sentAt?: string | null
}

export type ChatView = 'mine' | 'queue' | 'all'

const CONVERSATIONS_POLL_MS = 30_000

export function isUnread(conversation: ChatConversation): boolean {
  if (!conversation.lastMessageAt) return false
  if (conversation.status === 'CLOSED' || conversation.status === 'ARCHIVED') return false
  if (!conversation.myLastReadAt) return true
  return new Date(conversation.lastMessageAt) > new Date(conversation.myLastReadAt)
}

export function useConversations(
  view: ChatView,
  options?: { queueId?: string; status?: string; enabled?: boolean }
) {
  const params = new URLSearchParams({ view })
  if (options?.queueId) params.set('queueId', options.queueId)
  if (options?.status) params.set('status', options.status)
  const search = params.toString()

  return useQuery({
    queryKey: ['chat-conversations', view, options?.queueId ?? null, options?.status ?? null],
    queryFn: async () => {
      const response = await apiClient.get<{ data: ChatConversation[] }>(
        `/api/chat/conversations?${search}`
      )
      return response.data
    },
    enabled: options?.enabled !== false,
    refetchInterval: CONVERSATIONS_POLL_MS,
  })
}

/** Unread badge feed: my conversations with lastMessageAt beyond my read marker. */
export function useUnreadChatCount(enabled = true): number {
  const { data } = useConversations('mine', { enabled })
  return (data ?? []).filter(isUnread).length
}

/**
 * Messages for one conversation. Joining the socket channel and sending the
 * read receipt ride along: mount → chat.join + read receipt, unmount →
 * chat.leave. New chat.message events invalidate this query (provider).
 */
export function useChatMessages(conversationId: string | null) {
  const client = useRealtimeClient()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: ['chat-messages', conversationId],
    queryFn: async () => {
      const response = await apiClient.get<{ data: ChatMessage[] }>(
        `/api/chat/conversations/${conversationId}/messages?size=100`
      )
      return response.data
    },
    enabled: conversationId != null,
  })

  useEffect(() => {
    if (!client || !conversationId) return
    client.joinConversation(conversationId)
    return () => client.leaveConversation(conversationId)
  }, [client, conversationId])

  // Read receipt whenever the newest visible message changes.
  const newestId = query.data?.length ? query.data[query.data.length - 1].id : null
  useEffect(() => {
    if (!conversationId || !newestId) return
    void apiClient
      .post(`/api/chat/conversations/${conversationId}/read-receipt`)
      .then(() => queryClient.invalidateQueries({ queryKey: ['chat-conversations'] }))
      .catch(() => {
        // Best-effort: an offline/failed receipt only delays the unread clear.
      })
  }, [conversationId, newestId, queryClient])

  return query
}

export function useSendChatMessage(conversationId: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: string) =>
      apiClient.post<ChatMessage>(`/api/chat/conversations/${conversationId}/messages`, { body }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['chat-messages', conversationId] })
      void queryClient.invalidateQueries({ queryKey: ['chat-conversations'] })
    },
  })
}

export function useStartConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { queueId?: string; subject?: string; contextRecordId?: string }) =>
      apiClient.post<ChatConversation>('/api/chat/conversations', input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['chat-conversations'] })
    },
  })
}

export function useConversationActions(conversationId: string | null) {
  const queryClient = useQueryClient()
  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['chat-conversations'] })
    void queryClient.invalidateQueries({ queryKey: ['chat-conversation', conversationId] })
  }, [queryClient, conversationId])

  const claim = useMutation({
    mutationFn: () =>
      apiClient.post<ChatConversation>(`/api/chat/conversations/${conversationId}/assign`, {}),
    onSuccess: invalidate,
  })
  const close = useMutation({
    mutationFn: () =>
      apiClient.post<ChatConversation>(`/api/chat/conversations/${conversationId}/close`),
    onSuccess: invalidate,
  })
  return { claim, close }
}
