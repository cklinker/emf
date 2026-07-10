import { describe, it, expect } from 'vitest'
import { chatQueryKeysForEvent, queryKeysForEvent } from './invalidation'
import type { RecordChangedEvent } from './RealtimeClient'

function event(collection: string): RecordChangedEvent {
  return { event: 'record.changed', collection, changeType: 'UPDATE', recordId: 'r1' }
}

describe('queryKeysForEvent', () => {
  it('invalidates list, related, and detail prefixes for a data collection', () => {
    expect(queryKeysForEvent(event('orders'))).toEqual([
      ['collection-records', 'orders'],
      ['related-records', 'orders'],
      ['record', 'orders'],
    ])
  })

  it('adds the approval surfaces for approval collections', () => {
    const keys = queryKeysForEvent(event('approval-step-instances'))
    expect(keys).toContainEqual(['my-approvals'])
    expect(keys).toContainEqual(['record-approval-state'])
    expect(keys).toContainEqual(['activity-approvals'])
  })
})

describe('chatQueryKeysForEvent', () => {
  it('chat.message invalidates the thread and the conversation lists', () => {
    expect(chatQueryKeysForEvent({ event: 'chat.message', conversationId: 'conv-1' })).toEqual([
      ['chat-conversations'],
      ['chat-messages', 'conv-1'],
    ])
  })

  it('chat.conversation invalidates the lists and the conversation detail', () => {
    expect(chatQueryKeysForEvent({ event: 'chat.conversation', conversationId: 'conv-1' })).toEqual(
      [['chat-conversations'], ['chat-conversation', 'conv-1']]
    )
  })
})
