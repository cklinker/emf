/**
 * Chat primitives (telehealth slice 3) — purely presentational, fed by the
 * consuming app's hooks. No KeltaClient coupling so the same components serve
 * the staff console and the portal page-builder widget.
 */

export type ChatSenderType = 'INTERNAL' | 'PORTAL' | 'SYSTEM';

export interface ChatMessageItem {
  id: string;
  senderId?: string | null;
  senderType: ChatSenderType;
  kind: 'TEXT' | 'SYSTEM' | 'ATTACHMENT';
  body: string;
  sentAt?: string | null;
}

export interface ChatConversationItem {
  id: string;
  subject?: string | null;
  status: 'OPEN' | 'ASSIGNED' | 'CLOSED' | 'ARCHIVED';
  origin: 'PORTAL' | 'INTERNAL';
  assignedTo?: string | null;
  lastMessageAt?: string | null;
  createdAt?: string | null;
}

export interface MessageListProps {
  messages: ChatMessageItem[];
  /** Messages from this user render right-aligned as "own". */
  currentUserId?: string;
  /** Shown when there are no messages. */
  emptyText?: string;
  /** Resolves a display name for a sender id (falls back to sender type). */
  senderLabel?: (message: ChatMessageItem) => string | undefined;
  className?: string;
  testId?: string;
}

export interface MessageComposerProps {
  /** Called with the trimmed body. May be async; the composer clears on resolve. */
  onSend: (body: string) => void | Promise<void>;
  disabled?: boolean;
  placeholder?: string;
  sendLabel?: string;
  className?: string;
  testId?: string;
}

export interface ConversationListItemProps {
  conversation: ChatConversationItem;
  active?: boolean;
  /** Unread marker (dot) — the consumer computes it (lastMessageAt > lastReadAt). */
  unread?: boolean;
  /** Preformatted timestamp label (the consumer owns locale formatting). */
  timeLabel?: string;
  statusLabels?: Partial<Record<ChatConversationItem['status'], string>>;
  onClick?: (conversationId: string) => void;
  testId?: string;
}
