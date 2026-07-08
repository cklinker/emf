/**
 * Month-key ('YYYY-MM') arithmetic for CalendarMonthView (app-data-entry
 * slice 6). Separate module so the component file only exports components
 * (react-refresh rule); the page imports these to build the range filter.
 */

/** Month key for today, browser-local ('YYYY-MM'). */
export function currentMonthKey(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** Shift a 'YYYY-MM' month key by n months (n may be negative). */
export function addMonths(month: string, n: number): string {
  const [y, m] = month.split('-').map(Number)
  const total = y * 12 + (m - 1) + n
  const year = Math.floor(total / 12)
  const mon = (total % 12) + 1
  return `${year}-${String(mon).padStart(2, '0')}`
}

/** Inclusive 'YYYY-MM-DD' bounds of a 'YYYY-MM' month (for gte/lte filters). */
export function monthRange(month: string): { gte: string; lte: string } {
  const [y, m] = month.split('-').map(Number)
  const lastDay = new Date(y, m, 0).getDate()
  return {
    gte: `${month}-01`,
    lte: `${month}-${String(lastDay).padStart(2, '0')}`,
  }
}
