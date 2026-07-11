/**
 * Window-gating logic for a telehealth visit (slice 6). Join is only enabled
 * inside `[scheduledStart - grace, scheduledEnd + grace]`. Pure functions so the
 * gate is unit-tested without rendering.
 */

export type WindowState = 'before' | 'open' | 'after'

export interface VisitWindow {
  start: Date
  end: Date
  /** Grace minutes applied to both edges (default 5). */
  graceMinutes?: number
}

const MINUTE_MS = 60_000

/**
 * Where `now` sits relative to the grace-padded window.
 * - `before`: earlier than start − grace (join disabled, countdown shown)
 * - `open`: within the padded window (join enabled)
 * - `after`: later than end + grace (window closed)
 */
export function windowState(win: VisitWindow, now: Date = new Date()): WindowState {
  const grace = (win.graceMinutes ?? 5) * MINUTE_MS
  const openAt = win.start.getTime() - grace
  const closeAt = win.end.getTime() + grace
  const t = now.getTime()
  if (t < openAt) return 'before'
  if (t > closeAt) return 'after'
  return 'open'
}

/** Whether the "Join" action is currently allowed. */
export function canJoin(win: VisitWindow, now: Date = new Date()): boolean {
  return windowState(win, now) === 'open'
}

/**
 * Milliseconds until the join window opens (start − grace). 0 once open or past.
 * Drives the pre-window countdown.
 */
export function msUntilOpen(win: VisitWindow, now: Date = new Date()): number {
  const grace = (win.graceMinutes ?? 5) * MINUTE_MS
  const openAt = win.start.getTime() - grace
  return Math.max(0, openAt - now.getTime())
}

/** Human-friendly `H:MM:SS` / `M:SS` countdown from a millisecond span. */
export function formatCountdown(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`
}
