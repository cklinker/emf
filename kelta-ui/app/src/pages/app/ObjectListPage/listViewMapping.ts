/**
 * Saved-view helpers for the end-user list (app-surfacing slice 5).
 *
 * Two view sources feed one ViewSelector:
 * - personal views — the existing localStorage `useSavedViews` mechanism;
 * - shared views — admin-authored `list-views` system-collection rows
 *   (ListViewsPage, MANAGE_LISTVIEWS), surfaced read-only with a `shared:` id prefix.
 */
import type { SavedView, FilterCondition } from '@/hooks/useSavedViews'

export const SHARED_VIEW_PREFIX = 'shared:'

export function isSharedViewId(viewId: string): boolean {
  return viewId.startsWith(SHARED_VIEW_PREFIX)
}

/** A raw `list-views` JSON:API row (attributes flattened by apiClient.getList). */
export interface ListViewRow {
  id: string
  name?: string
  columns?: unknown
  filters?: unknown
  sortField?: string | null
  sortDirection?: string | null
  rowLimit?: number | null
  isDefault?: boolean
}

function asStringArray(value: unknown): string[] {
  const parsed = typeof value === 'string' ? safeParse(value) : value
  if (!Array.isArray(parsed)) return []
  return parsed.filter((v): v is string => typeof v === 'string')
}

/** Accepts only filter entries already in the FE FilterCondition shape; drops the rest. */
function asFilterConditions(value: unknown): FilterCondition[] {
  const parsed = typeof value === 'string' ? safeParse(value) : value
  if (!Array.isArray(parsed)) return []
  return parsed.filter(
    (f): f is FilterCondition =>
      !!f &&
      typeof f === 'object' &&
      typeof (f as FilterCondition).field === 'string' &&
      typeof (f as FilterCondition).operator === 'string' &&
      typeof (f as FilterCondition).value === 'string'
  )
}

function safeParse(value: string): unknown {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

/**
 * Maps an admin-authored `list-views` row into the SavedView shape the ViewSelector
 * renders. Filters not matching the FE condition shape are dropped (the admin builder's
 * server-side operator grammar is a superset); columns/sort/rowLimit always apply.
 */
export function mapSharedListView(row: ListViewRow, collectionName: string): SavedView {
  const pageSize = [10, 25, 50, 100].includes(row.rowLimit ?? -1) ? row.rowLimit! : 25
  return {
    id: `${SHARED_VIEW_PREFIX}${row.id}`,
    name: row.name || 'Shared view',
    collectionName,
    filters: asFilterConditions(row.filters).map((f, i) => ({ ...f, id: f.id ?? `s${i + 1}` })),
    sortField: row.sortField ?? null,
    sortDirection: row.sortDirection?.toUpperCase() === 'DESC' ? 'desc' : 'asc',
    sorts: row.sortField
      ? [
          {
            field: row.sortField,
            direction:
              row.sortDirection?.toUpperCase() === 'DESC' ? ('desc' as const) : ('asc' as const),
          },
        ]
      : undefined,
    visibleColumns: asStringArray(row.columns),
    pageSize,
    isDefault: row.isDefault === true,
    createdAt: '',
  }
}

/**
 * Orders the schema fields by a view's visibleColumns (view order wins); returns null when
 * the view declares no columns so the caller falls back to the default first-6 rule.
 */
export function orderFieldsByView<T extends { name: string }>(
  fields: T[],
  visibleColumns: string[]
): T[] | null {
  if (visibleColumns.length === 0) return null
  const byName = new Map(fields.map((f) => [f.name, f]))
  const ordered = visibleColumns.map((name) => byName.get(name)).filter((f): f is T => !!f)
  return ordered.length > 0 ? ordered : null
}
