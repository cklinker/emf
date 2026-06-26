/**
 * Legacy → v2 migration. Converts a page tree whose nodes carry the now-ignored
 * `position {row, column, width, height}` into the v2 model: a single root `grid` whose children are
 * `column`s carrying a responsive `span` (`width` → `span.base`), grouped by `position.row`. `position`
 * is stripped from every node. Runs once in the builder on load (never server-side) when a page lacks
 * `schemaVersion: 2`, and is idempotent — a v2 tree (no `position`) is returned unchanged.
 */
import type { PageComponent, ResponsiveSpan } from './pageModel'

/** The deprecated per-node layout coords (slice 2c migrates these away — see pageModel `position`). */
interface LegacyPosition {
  row: number
  column: number
  width: number
  height: number
}

/** A node that may still carry a legacy `position`. */
type LegacyNode = PageComponent & { position?: LegacyPosition }

let migrateSeq = 0

/** A stable-enough generated id for synthesized grid/column wrappers. */
function genId(prefix: string): string {
  migrateSeq += 1
  return `${prefix}_${Date.now().toString(36)}_${migrateSeq.toString(36)}`
}

/** Clamp a legacy `width` into the valid 1..12 span range. */
function clampWidth(width: number): number {
  if (!Number.isFinite(width)) return 12
  return Math.min(12, Math.max(1, Math.round(width)))
}

/** Strip `position` from a node and recurse into its children. */
function stripPosition(node: LegacyNode): PageComponent {
  const { position: _drop, ...rest } = node
  void _drop
  const children = rest.children
    ? rest.children.map((c) => stripPosition(c as LegacyNode))
    : undefined
  return children ? { ...rest, children } : { ...rest }
}

/** True if any node (at any depth) carries a legacy `position` ⇒ the tree needs migration. */
export function needsMigration(components: PageComponent[]): boolean {
  const has = (nodes: LegacyNode[]): boolean =>
    nodes.some(
      (n) => n.position !== undefined || (n.children ? has(n.children as LegacyNode[]) : false)
    )
  return has(components as LegacyNode[])
}

/**
 * Convert a legacy component tree into a v2 grid/column tree with `span`.
 * - Top-level nodes are grouped by `position.row` (document order within a row preserved); each becomes a
 *   `column` (`span.base = clamp(width)`) inside one root `grid`.
 * - A node WITHOUT `position` is wrapped in a full-width column (`span.base = 12`).
 * - `position` is stripped from every node (children recurse).
 * - Idempotent: a tree with no `position` anywhere is returned with positions stripped only (a no-position
 *   tree is already v2-shaped, so it round-trips unchanged).
 */
export function migrateTree(components: LegacyNode[]): PageComponent[] {
  if (!needsMigration(components)) {
    // Already v2-shaped — return a structurally-equal copy with any stray position stripped (none here).
    return components.map((n) => stripPosition(n))
  }

  // Group top-level nodes by row, preserving the order rows first appear and order within a row.
  const rowOrder: number[] = []
  const byRow = new Map<number, LegacyNode[]>()
  for (const node of components) {
    const row = node.position?.row ?? 0
    if (!byRow.has(row)) {
      byRow.set(row, [])
      rowOrder.push(row)
    }
    byRow.get(row)!.push(node)
  }

  const columns: PageComponent[] = []
  for (const row of rowOrder) {
    for (const node of byRow.get(row)!) {
      const base = node.position ? clampWidth(node.position.width) : 12
      const span: ResponsiveSpan = { base }
      columns.push({
        id: genId('col'),
        type: 'column',
        props: {},
        span,
        children: [stripPosition(node)],
      })
    }
  }

  return [
    {
      id: genId('grid'),
      type: 'grid',
      props: {},
      children: columns,
    },
  ]
}
