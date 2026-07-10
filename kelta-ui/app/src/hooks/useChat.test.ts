import { describe, it, expect } from 'vitest'
import { isUnread, type ChatConversation } from './useChat'

function conversation(overrides: Partial<ChatConversation>): ChatConversation {
  return {
    id: 'conv-1',
    status: 'OPEN',
    origin: 'PORTAL',
    ...overrides,
  }
}

describe('isUnread', () => {
  it('is unread when there are messages and no read marker', () => {
    expect(isUnread(conversation({ lastMessageAt: '2026-07-10T12:00:00Z' }))).toBe(true)
  })

  it('is unread when the last message is newer than the read marker', () => {
    expect(
      isUnread(
        conversation({
          lastMessageAt: '2026-07-10T12:05:00Z',
          myLastReadAt: '2026-07-10T12:00:00Z',
        })
      )
    ).toBe(true)
  })

  it('is read when the marker is at or after the last message', () => {
    expect(
      isUnread(
        conversation({
          lastMessageAt: '2026-07-10T12:00:00Z',
          myLastReadAt: '2026-07-10T12:00:00Z',
        })
      )
    ).toBe(false)
  })

  it('never counts empty, closed, or archived conversations', () => {
    expect(isUnread(conversation({}))).toBe(false)
    expect(
      isUnread(conversation({ status: 'CLOSED', lastMessageAt: '2026-07-10T12:00:00Z' }))
    ).toBe(false)
    expect(
      isUnread(conversation({ status: 'ARCHIVED', lastMessageAt: '2026-07-10T12:00:00Z' }))
    ).toBe(false)
  })
})
