import { describe, it, expect } from 'vitest'
import { canJoin, formatCountdown, msUntilOpen, windowState } from './visitWindow'

const start = new Date('2026-07-10T10:00:00Z')
const end = new Date('2026-07-10T10:30:00Z')
const win = { start, end, graceMinutes: 5 }

describe('windowState', () => {
  it('is "before" earlier than start − grace', () => {
    // 09:54 is 6 min before start; grace is 5 → still before the padded open.
    expect(windowState(win, new Date('2026-07-10T09:54:00Z'))).toBe('before')
  })

  it('is "open" once inside the grace-padded window (early edge)', () => {
    // 09:56 is 4 min before start; within the 5-min grace → open.
    expect(windowState(win, new Date('2026-07-10T09:56:00Z'))).toBe('open')
  })

  it('is "open" during the scheduled window', () => {
    expect(windowState(win, new Date('2026-07-10T10:15:00Z'))).toBe('open')
  })

  it('is "open" within the grace after the end', () => {
    // 10:34 is 4 min after end; within 5-min grace → open.
    expect(windowState(win, new Date('2026-07-10T10:34:00Z'))).toBe('open')
  })

  it('is "after" past end + grace', () => {
    // 10:36 is 6 min after end; beyond the 5-min grace → after.
    expect(windowState(win, new Date('2026-07-10T10:36:00Z'))).toBe('after')
  })

  it('defaults grace to 5 minutes when unset', () => {
    const noGrace = { start, end }
    expect(windowState(noGrace, new Date('2026-07-10T09:56:00Z'))).toBe('open')
    expect(windowState(noGrace, new Date('2026-07-10T09:54:00Z'))).toBe('before')
  })
})

describe('canJoin', () => {
  it('permits join only when the window is open', () => {
    expect(canJoin(win, new Date('2026-07-10T09:54:00Z'))).toBe(false)
    expect(canJoin(win, new Date('2026-07-10T10:15:00Z'))).toBe(true)
    expect(canJoin(win, new Date('2026-07-10T10:36:00Z'))).toBe(false)
  })
})

describe('msUntilOpen', () => {
  it('counts down to the padded open time and is 0 once open', () => {
    // 09:50 → open at 09:55 → 5 minutes = 300_000 ms.
    expect(msUntilOpen(win, new Date('2026-07-10T09:50:00Z'))).toBe(5 * 60_000)
    expect(msUntilOpen(win, new Date('2026-07-10T10:15:00Z'))).toBe(0)
  })
})

describe('formatCountdown', () => {
  it('formats sub-hour spans as M:SS', () => {
    expect(formatCountdown(5 * 60_000 + 9 * 1000)).toBe('5:09')
  })

  it('formats hour+ spans as H:MM:SS', () => {
    expect(formatCountdown(3_600_000 + 2 * 60_000 + 3 * 1000)).toBe('1:02:03')
  })
})
