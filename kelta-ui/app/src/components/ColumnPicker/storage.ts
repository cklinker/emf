/**
 * localStorage helpers for ColumnPicker — separated so the component file
 * only exports React components (satisfies react-refresh/only-export-components).
 */

const PREFIX = 'kelta.admin-table.'
const SUFFIX = '.hidden-columns'

function storageKey(tableId: string): string {
  return `${PREFIX}${tableId}${SUFFIX}`
}

export function loadHiddenColumns(tableId: string): Set<string> {
  if (typeof window === 'undefined') return new Set()
  try {
    const raw = window.localStorage.getItem(storageKey(tableId))
    if (!raw) return new Set()
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? new Set(parsed as string[]) : new Set()
  } catch {
    return new Set()
  }
}

export function persistHiddenColumns(tableId: string, hidden: Set<string>): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(storageKey(tableId), JSON.stringify(Array.from(hidden)))
  } catch {
    // Quota exceeded or storage disabled — silently ignore.
  }
}
