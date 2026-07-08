import { describe, it, expect } from 'vitest'
import { queryKeysForEvent } from './invalidation'
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
