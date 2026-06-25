/**
 * Unit tests for the span → Tailwind class mapping (slice 2c). The load-bearing guarantee: every emitted
 * class is one of the 48 FULL LITERAL class names, so an accidental `` `col-span-${n}` `` template-string
 * regression (which Tailwind's JIT cannot see) fails CI instead of silently dropping the class.
 */
import { describe, it, expect } from 'vitest'
import { spanToClasses, clampSpan, GRID_CONTAINER_CLASS } from './spanClasses'

/** The full set of 48 literals the mapping may legitimately emit. */
const LITERALS = new Set<string>()
for (let n = 1; n <= 12; n++) {
  LITERALS.add(`col-span-${n}`)
  LITERALS.add(`sm:col-span-${n}`)
  LITERALS.add(`md:col-span-${n}`)
  LITERALS.add(`lg:col-span-${n}`)
}

describe('spanToClasses', () => {
  it('defaults to col-span-12 when no span is set', () => {
    expect(spanToClasses(undefined)).toBe('col-span-12')
  })

  it('maps base only', () => {
    expect(spanToClasses({ base: 6 })).toBe('col-span-6')
  })

  it('adds breakpoint prefixes additively', () => {
    expect(spanToClasses({ base: 12, md: 6 })).toBe('col-span-12 md:col-span-6')
  })

  it('emits all four breakpoints in base sm md lg order', () => {
    expect(spanToClasses({ base: 12, sm: 6, md: 4, lg: 3 })).toBe(
      'col-span-12 sm:col-span-6 md:col-span-4 lg:col-span-3'
    )
  })

  it('clamps out-of-range spans', () => {
    expect(spanToClasses({ base: 99 })).toBe('col-span-12')
    expect(spanToClasses({ base: 0 })).toBe('col-span-1')
  })

  it('every produced class is one of the 48 literals (no template interpolation)', () => {
    const inputs = [
      undefined,
      { base: 1 },
      { base: 12, sm: 7, md: 5, lg: 11 },
      { base: 0 },
      { base: 13 },
    ]
    for (const span of inputs) {
      for (const cls of spanToClasses(span).split(' ')) {
        expect(LITERALS.has(cls)).toBe(true)
      }
    }
  })
})

describe('clampSpan', () => {
  it('clamps and rounds', () => {
    expect(clampSpan(0)).toBe(1)
    expect(clampSpan(13)).toBe(12)
    expect(clampSpan(5.6)).toBe(6)
  })
})

describe('GRID_CONTAINER_CLASS', () => {
  it('is the 12-col grid track', () => {
    expect(GRID_CONTAINER_CLASS).toContain('grid-cols-12')
  })
})
