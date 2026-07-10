import type { ConversationListItemProps } from './types';

const STATUS_STYLES: Record<string, string> = {
  OPEN: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
  ASSIGNED: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
  CLOSED: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300',
  ARCHIVED: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
};

/** One row in a conversation inbox (telehealth slice 3). */
export function ConversationListItem({
  conversation,
  active = false,
  unread = false,
  timeLabel,
  statusLabels,
  onClick,
  testId = 'kelta-conversation-item',
}: ConversationListItemProps) {
  const statusLabel = statusLabels?.[conversation.status] ?? conversation.status;
  return (
    <button
      type="button"
      onClick={() => onClick?.(conversation.id)}
      className={`flex w-full items-center gap-3 border-b border-border px-4 py-3 text-left transition-colors hover:bg-muted/60 ${
        active ? 'bg-muted' : 'bg-transparent'
      }`}
      data-testid={testId}
      aria-current={active ? 'true' : undefined}
    >
      <span
        className={`h-2 w-2 shrink-0 rounded-full ${unread ? 'bg-primary' : 'bg-transparent'}`}
        data-testid={`${testId}-unread`}
        aria-hidden="true"
      />
      <span className="min-w-0 flex-1">
        <span className={`block truncate text-sm ${unread ? 'font-semibold' : 'font-medium'}`}>
          {conversation.subject || conversation.id}
        </span>
        {timeLabel && (
          <span className="block truncate text-xs text-muted-foreground">{timeLabel}</span>
        )}
      </span>
      <span
        className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
          STATUS_STYLES[conversation.status] ?? STATUS_STYLES.CLOSED
        }`}
      >
        {statusLabel}
      </span>
    </button>
  );
}
