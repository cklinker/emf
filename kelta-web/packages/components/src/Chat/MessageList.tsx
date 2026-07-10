import { useEffect, useRef } from 'react';
import type { MessageListProps } from './types';

/**
 * Reverse-chronology-safe message stream (telehealth slice 3): renders the
 * given messages oldest→newest, right-aligns the current user's, centers
 * SYSTEM notices, and keeps the viewport pinned to the newest message when it
 * was already at (or near) the bottom.
 */
export function MessageList({
  messages,
  currentUserId,
  emptyText = 'No messages yet',
  senderLabel,
  className = '',
  testId = 'kelta-message-list',
}: MessageListProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastCountRef = useRef(0);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const appended = messages.length > lastCountRef.current;
    lastCountRef.current = messages.length;
    if (!appended) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (nearBottom || el.scrollTop === 0) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div
        className={`flex flex-1 items-center justify-center p-8 text-sm text-muted-foreground ${className}`}
        data-testid={`${testId}-empty`}
      >
        {emptyText}
      </div>
    );
  }

  return (
    <div
      ref={scrollRef}
      className={`flex flex-1 flex-col gap-2 overflow-y-auto p-4 ${className}`}
      data-testid={testId}
      role="log"
      aria-live="polite"
    >
      {messages.map((message) => {
        if (message.senderType === 'SYSTEM' || message.kind === 'SYSTEM') {
          return (
            <div
              key={message.id}
              className="self-center rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground"
              data-testid={`${testId}-system-${message.id}`}
            >
              {message.body}
            </div>
          );
        }
        const own = currentUserId != null && message.senderId === currentUserId;
        const label = senderLabel?.(message);
        return (
          <div
            key={message.id}
            className={`flex max-w-[80%] flex-col gap-0.5 ${own ? 'self-end items-end' : 'self-start items-start'}`}
            data-testid={`${testId}-message-${message.id}`}
          >
            {label && <span className="px-1 text-[11px] text-muted-foreground">{label}</span>}
            <div
              className={`whitespace-pre-wrap break-words rounded-2xl px-3 py-2 text-sm ${
                own
                  ? 'rounded-br-sm bg-primary text-primary-foreground'
                  : 'rounded-bl-sm border border-border bg-card text-foreground'
              }`}
            >
              {message.body}
            </div>
            {message.sentAt && (
              <span className="px-1 text-[10px] text-muted-foreground">
                {new Date(message.sentAt).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}
