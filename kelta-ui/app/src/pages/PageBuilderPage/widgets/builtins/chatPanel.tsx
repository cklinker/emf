/**
 * Chat panel built-in (telehealth slice 3) — the portal-facing chat surface.
 * A tenant drops it on a custom page; the signed-in (portal) user sees their
 * most recent open conversation and can start one against the configured
 * queue. Reuses the @kelta/components chat primitives shared with the staff
 * console; data flows through /api/chat/** (participant authz server-side).
 *
 * Editor mode renders a static sample — no fetches, no socket joins.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React, { useMemo } from 'react'
import { Loader2, MessageCircle } from 'lucide-react'
import { MessageComposer, MessageList, type ChatMessageItem } from '@kelta/components'
import { useI18n } from '@/context/I18nContext'
import { useMyIdentity } from '@/hooks/useMyIdentity'
import {
  useChatMessages,
  useConversations,
  useSendChatMessage,
  useStartConversation,
} from '@/hooks/useChat'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'

function ChatPanelLive({
  queueId,
  welcomeText,
  subject,
}: {
  queueId?: string
  welcomeText: string
  subject: string
}): React.ReactElement {
  const { t } = useI18n()
  const { identity } = useMyIdentity()
  const conversations = useConversations('mine')

  // Single-thread portal UX: the newest conversation that is still writable.
  const active = useMemo(() => {
    const list = conversations.data ?? []
    return list.find((c) => c.status === 'OPEN' || c.status === 'ASSIGNED') ?? list[0] ?? null
  }, [conversations.data])

  const messages = useChatMessages(active?.id ?? null)
  const send = useSendChatMessage(active?.id ?? null)
  const start = useStartConversation()

  if (conversations.isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!active) {
    return (
      <div className="flex flex-col items-center gap-3 p-8 text-center">
        <MessageCircle size={22} className="text-muted-foreground" />
        <p className="text-sm text-muted-foreground">{welcomeText}</p>
        <button
          type="button"
          className="cursor-pointer rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          onClick={() =>
            start.mutate({ queueId: queueId || undefined, subject: subject || undefined })
          }
          disabled={start.isPending}
          data-testid="page-node-chat-panel-start"
        >
          {t('chat.startConversation', 'Start a conversation')}
        </button>
      </div>
    )
  }

  const closed = active.status === 'CLOSED' || active.status === 'ARCHIVED'
  return (
    <div className="flex h-full min-h-[320px] flex-col">
      <MessageList
        messages={(messages.data ?? []) as ChatMessageItem[]}
        currentUserId={identity?.userId}
        emptyText={welcomeText}
        testId="page-node-chat-panel-messages"
      />
      {closed ? (
        <div className="flex flex-col items-center gap-2 border-t border-border p-3">
          <span className="text-xs text-muted-foreground">
            {t('chat.readOnly', 'This conversation is closed')}
          </span>
          <button
            type="button"
            className="cursor-pointer rounded-md border border-border bg-card px-3 py-1.5 text-xs hover:bg-muted disabled:opacity-50"
            onClick={() =>
              start.mutate({ queueId: queueId || undefined, subject: subject || undefined })
            }
            disabled={start.isPending}
          >
            {t('chat.startConversation', 'Start a conversation')}
          </button>
        </div>
      ) : (
        <MessageComposer
          onSend={async (body) => {
            await send.mutateAsync(body)
          }}
          placeholder={t('chat.composerPlaceholder', 'Type a message…')}
          sendLabel={t('chat.send', 'Send')}
          testId="page-node-chat-panel-composer"
        />
      )}
    </div>
  )
}

function ChatPanelRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  const props = node.props ?? {}
  const welcomeText =
    asString(props.welcomeText) || 'Questions? Start a conversation and we will get back to you.'
  const subject = asString(props.subject) || ''
  const queueId = asString(props.queueId) || undefined

  if (mode === 'editor') {
    return (
      <div
        className="flex min-h-[220px] flex-col rounded-md border border-dashed border-border"
        data-testid="page-node-chat-panel"
      >
        <div className="flex flex-1 flex-col gap-2 p-4">
          <div className="max-w-[70%] self-start rounded-2xl rounded-bl-sm border border-border bg-card px-3 py-2 text-sm">
            {welcomeText}
          </div>
          <div className="max-w-[70%] self-end rounded-2xl rounded-br-sm bg-primary px-3 py-2 text-sm text-primary-foreground">
            Sample reply
          </div>
        </div>
        <div className="border-t border-border p-3 text-xs text-muted-foreground">
          Chat composer (live at runtime)
        </div>
      </div>
    )
  }

  return (
    <div
      className="flex flex-col overflow-hidden rounded-md border border-border bg-card"
      data-testid="page-node-chat-panel"
    >
      <ChatPanelLive queueId={queueId} welcomeText={welcomeText} subject={subject} />
    </div>
  )
}

export const chatPanelWidget: WidgetDescriptor = {
  type: 'chat-panel',
  label: 'Chat Panel',
  icon: MessageCircle,
  category: 'data',
  acceptsChildren: false,
  defaultProps: { welcomeText: '', subject: '', queueId: '' },
  propSchema: [
    { key: 'welcomeText', label: 'Welcome text', kind: 'text', bindable: true, group: 'content' },
    {
      key: 'subject',
      label: 'Conversation subject',
      kind: 'text',
      bindable: true,
      group: 'content',
    },
    { key: 'queueId', label: 'Queue id', kind: 'text', group: 'data' },
  ],
  Render: ChatPanelRender,
}
