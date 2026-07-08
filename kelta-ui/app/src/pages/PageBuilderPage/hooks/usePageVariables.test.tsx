/** usePageVariables: seeds vars from typed defaults, setVar updates one, reset restores. */
import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { usePageVariables } from './usePageVariables'
import type { PageVariable } from '../pageConfig'

vi.mock('sonner', () => ({ toast: { error: vi.fn() } }))

const variables: PageVariable[] = [
  { name: 'count', type: 'number', default: 5 },
  { name: 'label', type: 'string', default: 'hi' },
  { name: 'flag', type: 'boolean', default: true },
  { name: 'blob', type: 'json', default: { a: 1 } },
  { name: 'numFromString', type: 'number', default: '12' },
]

describe('usePageVariables', () => {
  it('seeds vars from typed defaults', () => {
    const { result } = renderHook(() => usePageVariables(variables))
    expect(result.current.vars).toEqual({
      count: 5,
      label: 'hi',
      flag: true,
      blob: { a: 1 },
      numFromString: 12,
    })
  })

  it('falls back to type-appropriate empties when default is absent', () => {
    const { result } = renderHook(() =>
      usePageVariables([
        { name: 'n', type: 'number' },
        { name: 's', type: 'string' },
        { name: 'b', type: 'boolean' },
        { name: 'j', type: 'json' },
      ])
    )
    expect(result.current.vars).toEqual({ n: 0, s: '', b: false, j: null })
  })

  it('setVar updates a single variable', () => {
    const { result } = renderHook(() => usePageVariables(variables))
    act(() => result.current.setVar('count', 99))
    expect(result.current.vars.count).toBe(99)
    expect(result.current.vars.label).toBe('hi')
  })

  it('reset restores defaults', () => {
    const { result } = renderHook(() => usePageVariables(variables))
    act(() => result.current.setVar('count', 99))
    act(() => result.current.reset())
    expect(result.current.vars.count).toBe(5)
  })

  it('excludes computed variables from seeding (they derive per render)', () => {
    const { result } = renderHook(() =>
      usePageVariables([
        { name: 'a', type: 'number', default: 1 },
        { name: 'b', type: 'number', kind: 'computed', expression: 'vars.a + 1' },
      ])
    )
    expect(result.current.vars).toEqual({ a: 1 })
  })

  it('rejects setVar on a computed variable (no write, error toast)', async () => {
    const { toast } = await import('sonner')
    const { result } = renderHook(() =>
      usePageVariables([
        { name: 'a', type: 'number', default: 1 },
        { name: 'b', type: 'number', kind: 'computed', expression: 'vars.a + 1' },
      ])
    )
    act(() => result.current.setVar('b', 42))
    expect(result.current.vars).toEqual({ a: 1 })
    expect(toast.error).toHaveBeenCalled()
  })
})
