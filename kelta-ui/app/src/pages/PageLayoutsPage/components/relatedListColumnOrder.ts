/**
 * Pure helpers for ordering display columns in the related-list editor.
 * Extracted from RelatedListPanel so the reorder logic is independently testable.
 */

/**
 * Compute the order in which display-column rows should render.
 *
 * - User-arranged names (from previous reorder or saved selection) come first
 *   in their stored order.
 * - Any remaining available field names follow in their natural order.
 * - Names in `orderedFieldNames` that are no longer available are dropped.
 */
export function computeDisplayOrder(
  availableFieldNames: readonly string[],
  orderedFieldNames: readonly string[]
): string[] {
  const available = new Set(availableFieldNames)
  const kept = orderedFieldNames.filter((n) => available.has(n))
  const keptSet = new Set(kept)
  const remaining = availableFieldNames.filter((n) => !keptSet.has(n))
  return [...kept, ...remaining]
}

/**
 * Move `draggedName` to the position currently held by `targetName` and
 * return the new full ordering. Returns `currentOrder` unchanged if either
 * name is missing or the move is a no-op.
 */
export function reorderColumns(
  currentOrder: readonly string[],
  draggedName: string,
  targetName: string
): string[] {
  if (draggedName === targetName) return [...currentOrder]
  const fromIdx = currentOrder.indexOf(draggedName)
  const toIdx = currentOrder.indexOf(targetName)
  if (fromIdx === -1 || toIdx === -1) return [...currentOrder]
  const next = [...currentOrder]
  const [moved] = next.splice(fromIdx, 1)
  next.splice(toIdx, 0, moved)
  return next
}

/**
 * Swap two adjacent items (used by Alt+Arrow keyboard reorder).
 * `direction` is -1 to move the item at `index` up, +1 to move it down.
 * Returns `currentOrder` unchanged if the move is out of bounds.
 */
export function swapAdjacent(
  currentOrder: readonly string[],
  index: number,
  direction: -1 | 1
): string[] {
  const j = index + direction
  if (index < 0 || index >= currentOrder.length) return [...currentOrder]
  if (j < 0 || j >= currentOrder.length) return [...currentOrder]
  const next = [...currentOrder]
  ;[next[index], next[j]] = [next[j], next[index]]
  return next
}

/**
 * Filter `displayOrder` to the names present in `selected`, preserving
 * the order from `displayOrder`. Used at save time to produce the
 * persisted column list.
 */
export function selectedInDisplayOrder(
  displayOrder: readonly string[],
  selected: readonly string[]
): string[] {
  const selectedSet = new Set(selected)
  return displayOrder.filter((n) => selectedSet.has(n))
}
