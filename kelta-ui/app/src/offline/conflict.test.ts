import { describe, expect, it } from 'vitest'
import { resolveConflict } from './conflict'
import type { ReplicaRecord } from './types'

const local: ReplicaRecord = { id: 'r1', updatedAt: '2026-06-21T10:00:00Z' }
const newerRemote: ReplicaRecord = { id: 'r1', updatedAt: '2026-06-21T12:00:00Z' }
const olderRemote: ReplicaRecord = { id: 'r1', updatedAt: '2026-06-21T08:00:00Z' }

describe('resolveConflict', () => {
  it('server-wins always accepts the server version', () => {
    expect(resolveConflict(local, olderRemote, 'server-wins')).toBe('remote')
  })

  it('client-wins always keeps the local edit', () => {
    expect(resolveConflict(local, newerRemote, 'client-wins')).toBe('local')
  })

  it('last-write-wins keeps local when the local edit is newer', () => {
    expect(resolveConflict(local, olderRemote, 'last-write-wins')).toBe('local')
  })

  it('last-write-wins accepts remote when the server version is newer', () => {
    expect(resolveConflict(local, newerRemote, 'last-write-wins')).toBe('remote')
  })

  it('last-write-wins lets the server win on a timestamp tie', () => {
    expect(
      resolveConflict(local, { id: 'r1', updatedAt: local.updatedAt }, 'last-write-wins')
    ).toBe('remote')
  })

  it('last-write-wins falls back to remote when a timestamp is missing or invalid', () => {
    expect(resolveConflict({ id: 'r1' }, newerRemote, 'last-write-wins')).toBe('remote')
    expect(resolveConflict(local, { id: 'r1', updatedAt: 'nonsense' }, 'last-write-wins')).toBe(
      'remote'
    )
  })
})
