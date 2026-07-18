/**
 * useRecordVersions pure-function tests
 *
 * Covers the defensive JSONB normalization in parseRecordVersion (snapshot /
 * changedFields may arrive as native objects or JSON strings) and the
 * snapshot → record shaping in snapshotToRecord.
 */

import { describe, it, expect } from 'vitest'
import { parseRecordVersion, snapshotToRecord } from './useRecordVersions'
import type { RecordVersion } from './useRecordVersions'

function makeRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'ver-1',
    collectionId: 'col-1',
    recordId: 'rec-1',
    versionNumber: 3,
    changeType: 'UPDATED',
    snapshot: { id: 'rec-1', name: 'Acme' },
    changedFields: ['name'],
    changedBy: 'user-1',
    changedAt: '2026-07-01T10:00:00Z',
    changeSource: 'UI',
    ...overrides,
  } as Parameters<typeof parseRecordVersion>[0]
}

describe('parseRecordVersion', () => {
  it('passes an object snapshot through unchanged', () => {
    const version = parseRecordVersion(makeRow({ snapshot: { id: 'rec-1', name: 'Acme' } }))

    expect(version.snapshot).toEqual({ id: 'rec-1', name: 'Acme' })
  })

  it('parses a JSON-string snapshot', () => {
    const version = parseRecordVersion(
      makeRow({ snapshot: '{"id":"rec-1","name":"Acme","amount":42}' })
    )

    expect(version.snapshot).toEqual({ id: 'rec-1', name: 'Acme', amount: 42 })
  })

  it('falls back to an empty snapshot for a malformed JSON string', () => {
    const version = parseRecordVersion(makeRow({ snapshot: 'not-valid-json{' }))

    expect(version.snapshot).toEqual({})
  })

  it('falls back to an empty snapshot when missing', () => {
    const version = parseRecordVersion(makeRow({ snapshot: undefined }))

    expect(version.snapshot).toEqual({})
  })

  it('parses a JSON-string changedFields array', () => {
    const version = parseRecordVersion(makeRow({ changedFields: '["name","status"]' }))

    expect(version.changedFields).toEqual(['name', 'status'])
  })

  it('falls back to an empty changedFields array when missing or malformed', () => {
    expect(parseRecordVersion(makeRow({ changedFields: undefined })).changedFields).toEqual([])
    expect(parseRecordVersion(makeRow({ changedFields: null })).changedFields).toEqual([])
    expect(parseRecordVersion(makeRow({ changedFields: 'not-json{' })).changedFields).toEqual([])
  })

  it('filters non-string changedFields entries', () => {
    const version = parseRecordVersion(
      makeRow({ changedFields: ['name', 42, null, { nested: true }, 'status'] })
    )

    expect(version.changedFields).toEqual(['name', 'status'])
  })

  it('applies defaults for missing scalar columns', () => {
    const version = parseRecordVersion({ id: 'ver-9' })

    expect(version).toEqual({
      id: 'ver-9',
      collectionId: '',
      recordId: '',
      versionNumber: 0,
      changeType: 'UPDATED',
      snapshot: {},
      changedFields: [],
      changedBy: '',
      changedAt: '',
      changeSource: '',
    })
  })
})

describe('snapshotToRecord', () => {
  function makeVersion(overrides: Partial<RecordVersion> = {}): RecordVersion {
    return {
      id: 'ver-1',
      collectionId: 'col-1',
      recordId: 'rec-1',
      versionNumber: 2,
      changeType: 'UPDATED',
      snapshot: { id: 'snap-id', name: 'Acme' },
      changedFields: ['name'],
      changedBy: 'user-1',
      changedAt: '2026-07-01T10:00:00Z',
      changeSource: 'UI',
      ...overrides,
    }
  }

  it('uses the snapshot id when present', () => {
    const record = snapshotToRecord(makeVersion())

    expect(record).toEqual({ id: 'snap-id', name: 'Acme' })
  })

  it('falls back to the version recordId when the snapshot has no id', () => {
    const record = snapshotToRecord(makeVersion({ snapshot: { name: 'Acme' } }))

    expect(record).toEqual({ id: 'rec-1', name: 'Acme' })
  })

  it('falls back to the version recordId when the snapshot id is not a string', () => {
    const record = snapshotToRecord(makeVersion({ snapshot: { id: 42, name: 'Acme' } }))

    expect(record.id).toBe('rec-1')
  })
})
