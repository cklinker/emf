import { useCallback, useState, KeyboardEvent } from 'react';
import type { MessageComposerProps } from './types';

/**
 * Message input (telehealth slice 3): Enter sends, Shift+Enter inserts a
 * newline. Clears only after a successful onSend so failed sends keep the
 * draft; a failed promise surfaces the error state to the caller (toast).
 */
export function MessageComposer({
  onSend,
  disabled = false,
  placeholder = 'Type a message…',
  sendLabel = 'Send',
  className = '',
  testId = 'kelta-message-composer',
}: MessageComposerProps) {
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);

  const submit = useCallback(async () => {
    const body = draft.trim();
    if (!body || sending || disabled) return;
    setSending(true);
    try {
      await onSend(body);
      setDraft('');
    } catch {
      // Keep the draft; error surfacing (toast etc.) is the caller's onSend job.
    } finally {
      setSending(false);
    }
  }, [draft, sending, disabled, onSend]);

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        void submit();
      }
    },
    [submit]
  );

  return (
    <div
      className={`flex items-end gap-2 border-t border-border bg-card p-3 ${className}`}
      data-testid={testId}
    >
      <textarea
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled || sending}
        rows={Math.min(4, Math.max(1, draft.split('\n').length))}
        className="min-h-9 flex-1 resize-none rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:border-primary disabled:opacity-50"
        data-testid={`${testId}-input`}
        aria-label={placeholder}
      />
      <button
        type="button"
        onClick={() => void submit()}
        disabled={disabled || sending || draft.trim().length === 0}
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
        data-testid={`${testId}-send`}
      >
        {sendLabel}
      </button>
    </div>
  );
}
