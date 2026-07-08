import type { RecordChangedEvent } from './RealtimeClient'

/** Collections whose changes affect the approvals surfaces (inbox, bell, record badge). */
const APPROVAL_COLLECTIONS = new Set(['approval-instances', 'approval-step-instances'])

/**
 * Maps a record.changed event to the React Query key PREFIXES to invalidate.
 *
 * Pure function (unit-tested) — key strings are deliberately literal so this module does
 * not import from feature hooks; prefix invalidation catches every param variant:
 * - ['collection-records', <collection>]  — list pages
 * - ['related-records', <collection>]     — related lists
 * - ['record', <collection>]              — record detail
 * - ['activity-approvals']/['my-approvals']/['record-approval-state'] — approval surfaces
 */
export function queryKeysForEvent(event: RecordChangedEvent): unknown[][] {
  const keys: unknown[][] = [
    ['collection-records', event.collection],
    ['related-records', event.collection],
    ['record', event.collection],
  ]
  if (APPROVAL_COLLECTIONS.has(event.collection)) {
    keys.push(['activity-approvals'], ['my-approvals'], ['record-approval-state'])
  }
  return keys
}
