import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MessageList } from './MessageList';
import { MessageComposer } from './MessageComposer';
import { ConversationListItem } from './ConversationListItem';
import type { ChatMessageItem } from './types';

const messages: ChatMessageItem[] = [
  {
    id: 'm1',
    senderId: 'u-agent',
    senderType: 'INTERNAL',
    kind: 'TEXT',
    body: 'Hello Pat',
    sentAt: '2026-07-10T12:00:00Z',
  },
  {
    id: 'm2',
    senderId: 'u-portal',
    senderType: 'PORTAL',
    kind: 'TEXT',
    body: 'Hi doctor',
    sentAt: '2026-07-10T12:01:00Z',
  },
  { id: 'm3', senderType: 'SYSTEM', kind: 'SYSTEM', body: 'Conversation assigned' },
];

describe('MessageList', () => {
  it('renders own messages right-aligned, others left, system centered', () => {
    render(<MessageList messages={messages} currentUserId="u-portal" />);

    expect(screen.getByTestId('kelta-message-list-message-m2').className).toContain('self-end');
    expect(screen.getByTestId('kelta-message-list-message-m1').className).toContain('self-start');
    expect(screen.getByTestId('kelta-message-list-system-m3')).toHaveTextContent(
      'Conversation assigned'
    );
  });

  it('shows the empty state when there are no messages', () => {
    render(<MessageList messages={[]} emptyText="Say hello" />);
    expect(screen.getByTestId('kelta-message-list-empty')).toHaveTextContent('Say hello');
  });

  it('renders sender labels when provided', () => {
    render(
      <MessageList
        messages={messages.slice(0, 1)}
        currentUserId="u-portal"
        senderLabel={(m) => (m.senderId === 'u-agent' ? 'Dr. K' : undefined)}
      />
    );
    expect(screen.getByText('Dr. K')).toBeInTheDocument();
  });
});

describe('MessageComposer', () => {
  it('sends the trimmed draft on Enter and clears it', async () => {
    const onSend = vi.fn().mockResolvedValue(undefined);
    render(<MessageComposer onSend={onSend} />);
    const input = screen.getByTestId('kelta-message-composer-input');

    await userEvent.type(input, '  hello there  ');
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(onSend).toHaveBeenCalledWith('hello there'));
    await waitFor(() => expect(input).toHaveValue(''));
  });

  it('Shift+Enter inserts a newline instead of sending', async () => {
    const onSend = vi.fn();
    render(<MessageComposer onSend={onSend} />);
    const input = screen.getByTestId('kelta-message-composer-input');

    await userEvent.type(input, 'line one');
    fireEvent.keyDown(input, { key: 'Enter', shiftKey: true });

    expect(onSend).not.toHaveBeenCalled();
  });

  it('keeps the draft when onSend rejects', async () => {
    const onSend = vi.fn().mockRejectedValue(new Error('offline'));
    render(<MessageComposer onSend={onSend} />);
    const input = screen.getByTestId('kelta-message-composer-input');

    await userEvent.type(input, 'important note');
    fireEvent.click(screen.getByTestId('kelta-message-composer-send'));

    await waitFor(() => expect(onSend).toHaveBeenCalled());
    expect(input).toHaveValue('important note');
  });

  it('disables the send button for empty drafts and while disabled', () => {
    render(<MessageComposer onSend={vi.fn()} disabled />);
    expect(screen.getByTestId('kelta-message-composer-send')).toBeDisabled();
  });
});

describe('ConversationListItem', () => {
  const conversation = {
    id: 'conv-1',
    subject: 'Prescription question',
    status: 'OPEN' as const,
    origin: 'PORTAL' as const,
  };

  it('renders subject, status, unread dot and fires onClick with the id', async () => {
    const onClick = vi.fn();
    render(
      <ConversationListItem
        conversation={conversation}
        unread
        timeLabel="2m ago"
        onClick={onClick}
      />
    );

    expect(screen.getByText('Prescription question')).toBeInTheDocument();
    expect(screen.getByText('OPEN')).toBeInTheDocument();
    expect(screen.getByText('2m ago')).toBeInTheDocument();
    expect(screen.getByTestId('kelta-conversation-item-unread').className).toContain('bg-primary');

    await userEvent.click(screen.getByTestId('kelta-conversation-item'));
    expect(onClick).toHaveBeenCalledWith('conv-1');
  });

  it('uses custom status labels and marks the active row', () => {
    render(
      <ConversationListItem
        conversation={{ ...conversation, status: 'CLOSED' }}
        active
        statusLabels={{ CLOSED: 'Done' }}
      />
    );
    expect(screen.getByText('Done')).toBeInTheDocument();
    expect(screen.getByTestId('kelta-conversation-item')).toHaveAttribute('aria-current', 'true');
  });
});
