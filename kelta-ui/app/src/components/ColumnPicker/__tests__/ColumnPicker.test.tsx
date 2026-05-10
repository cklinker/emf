import React from 'react'
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ColumnPicker } from '../ColumnPicker'
import { loadHiddenColumns, persistHiddenColumns } from '../storage'

const columns = [
  { id: 'name', label: 'Name' },
  { id: 'type', label: 'Type' },
  { id: 'actions', label: 'Actions', required: true },
]

function Harness({ onChange = vi.fn() }: { onChange?: (set: Set<string>) => void }) {
  const [hidden, setHidden] = React.useState<Set<string>>(new Set())
  return (
    <ColumnPicker
      tableId="test-table"
      columns={columns}
      hidden={hidden}
      onChange={(next) => {
        setHidden(next)
        onChange(next)
      }}
    />
  )
}

// vitest.setup.ts mocks localStorage with vi.fn()s that don't actually store
// anything; install a Map-backed implementation locally so persistence is
// observable inside this file.
function installRealLocalStorage(): () => void {
  const map = new Map<string, string>()
  const storage = {
    getItem: vi.fn((key: string) => map.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      map.set(key, value)
    }),
    removeItem: vi.fn((key: string) => {
      map.delete(key)
    }),
    clear: vi.fn(() => map.clear()),
    key: vi.fn(),
    length: 0,
  }
  const previous = window.localStorage
  Object.defineProperty(window, 'localStorage', {
    value: storage,
    configurable: true,
  })
  return () => {
    Object.defineProperty(window, 'localStorage', {
      value: previous,
      configurable: true,
    })
  }
}

describe('ColumnPicker', () => {
  let cleanupStorage: (() => void) | null = null

  beforeEach(() => {
    cleanupStorage?.()
    cleanupStorage = installRealLocalStorage()
  })

  it('renders trigger with visible-count label', () => {
    render(<Harness />)
    expect(screen.getByTestId('column-picker-trigger')).toHaveTextContent('(3/3)')
  })

  it('persists hidden columns to localStorage', () => {
    persistHiddenColumns('persist-test', new Set(['type']))
    expect(loadHiddenColumns('persist-test')).toEqual(new Set(['type']))
  })

  it('returns empty set for missing key', () => {
    expect(loadHiddenColumns('no-such-key').size).toBe(0)
  })

  it('returns empty set for corrupt value', () => {
    window.localStorage.setItem('kelta.admin-table.bad.hidden-columns', '{notjson')
    expect(loadHiddenColumns('bad').size).toBe(0)
  })

  it('handles non-array stored value gracefully', () => {
    window.localStorage.setItem(
      'kelta.admin-table.notarray.hidden-columns',
      JSON.stringify({ foo: 'bar' })
    )
    expect(loadHiddenColumns('notarray').size).toBe(0)
  })

  it('round-trips an empty set', () => {
    persistHiddenColumns('empty', new Set())
    expect(loadHiddenColumns('empty').size).toBe(0)
  })
})
