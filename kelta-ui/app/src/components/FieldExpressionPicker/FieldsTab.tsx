import React, { useMemo, useState } from 'react'
import { ChevronRight, Search } from 'lucide-react'
import {
  useCollectionSchema,
  type FieldDefinition,
  type FieldType,
} from '../../hooks/useCollectionSchema'
import { useCollectionStore } from '../../context/CollectionStoreContext'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { StaticNamespace } from './types'

const RELATIONSHIP_TYPES: ReadonlySet<FieldType> = new Set(['reference', 'lookup', 'master_detail'])

/**
 * Encoding marker for inbound (child) relationships. The picker emits paths
 * like `order_lines__order_id.product_id` for child traversals; this marker
 * keeps the segment shape unambiguous from a normal field name.
 */
const CHILD_REL_SEPARATOR = '__'

function isRelationshipField(field: FieldDefinition): boolean {
  return RELATIONSHIP_TYPES.has(field.type) && !!field.referenceCollectionId
}

interface FieldRow {
  segment: string
  displayName: string
  type: string
  /** Set on relationship rows — collection id of the related target. */
  nextCollectionId?: string
  /** Set on a static-namespace root row to push that namespace's fields next. */
  namespaceName?: string
  /** True when this row represents a child (one-to-many / inbound) relationship. */
  isChildRelationship?: boolean
}

interface ColumnProps {
  /** Title shown above the column. */
  title: string
  /** Rows to display. When `collectionId` is set and `rows` is undefined, the column fetches them. */
  rows?: FieldRow[]
  /** Collection id to fetch fields from when `rows` is undefined. */
  collectionId?: string
  /** Selected segment in this column (for highlight). */
  selectedSegment?: string
  /** Click handler. */
  onPick: (row: FieldRow) => void
  testId?: string
}

function Column({
  title,
  rows,
  collectionId,
  selectedSegment,
  onPick,
  testId,
}: ColumnProps): React.ReactElement {
  const [filter, setFilter] = useState('')
  const { schema, fields, isLoading } = useCollectionSchema(
    !rows && collectionId ? collectionId : undefined
  )
  // The collection store gives us every collection's schema in memory, so we
  // can discover *inbound* (child) relationships — other collections whose
  // fields point at this collection — without an extra API call. The picker
  // is always rendered inside the app shell, where the provider is mounted.
  const collectionStore = useCollectionStore()

  const sourceRows: FieldRow[] = useMemo(() => {
    if (rows) return rows
    const fieldRows: FieldRow[] = fields.map((f) => ({
      segment: f.name,
      displayName: f.displayName ?? f.name,
      type: f.type,
      nextCollectionId: isRelationshipField(f) ? f.referenceCollectionId : undefined,
    }))
    if (!collectionId || !collectionStore) {
      return fieldRows
    }
    // Find every collection that has a field whose referenceCollectionId
    // points at *this* collection. Each such field becomes a "child" row.
    const childRows: FieldRow[] = []
    for (const otherCollection of collectionStore.collections) {
      if (otherCollection.id === collectionId) continue
      for (const otherField of otherCollection.fields) {
        if (otherField.referenceCollectionId !== collectionId) continue
        childRows.push({
          segment: `${otherCollection.name}${CHILD_REL_SEPARATOR}${otherField.name}`,
          displayName: `${otherCollection.displayName} (via ${otherField.displayName ?? otherField.name})`,
          type: 'list',
          nextCollectionId: otherCollection.id,
          isChildRelationship: true,
        })
      }
    }
    childRows.sort((a, b) => a.displayName.localeCompare(b.displayName))
    return [...fieldRows, ...childRows]
  }, [rows, fields, collectionId, collectionStore])

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return sourceRows
    return sourceRows.filter(
      (r) => r.segment.toLowerCase().includes(q) || r.displayName.toLowerCase().includes(q)
    )
  }, [sourceRows, filter])

  const headerLabel = !rows && schema ? schema.displayName : title
  const fetching = !rows && collectionId && isLoading

  return (
    <div
      className="flex h-full w-64 shrink-0 flex-col border-r border-border last:border-r-0"
      data-testid={testId}
    >
      <div className="flex flex-col gap-1 border-b border-border bg-muted/40 px-3 py-2">
        <div className="truncate text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          {headerLabel}
        </div>
        <div className="relative">
          <Search className="pointer-events-none absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter…"
            className="h-7 pl-7 text-xs"
          />
        </div>
      </div>
      <div className="flex-1 overflow-y-auto py-1">
        {fetching ? (
          <div className="px-3 py-4 text-xs text-muted-foreground">Loading…</div>
        ) : filtered.length === 0 ? (
          <div className="px-3 py-4 text-xs text-muted-foreground">No matches.</div>
        ) : (
          filtered.map((row) => {
            const cascades = !!row.nextCollectionId || !!row.namespaceName
            const isSelected = selectedSegment === row.segment
            return (
              <button
                key={row.segment}
                type="button"
                onClick={() => onPick(row)}
                className={cn(
                  'flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs transition-colors',
                  'hover:bg-muted/60',
                  isSelected && 'bg-primary/10 text-primary'
                )}
                data-testid={`${testId}-row-${row.segment}`}
              >
                <span className="flex-1 truncate">{row.displayName}</span>
                <span
                  className={cn(
                    'rounded px-1 py-0.5 text-[10px] font-medium',
                    row.isChildRelationship
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300'
                      : 'bg-muted text-muted-foreground'
                  )}
                >
                  {row.type}
                </span>
                {cascades && <ChevronRight className="h-3 w-3 text-muted-foreground" />}
              </button>
            )
          })
        )}
      </div>
    </div>
  )
}

export interface FieldsTabProps {
  rootCollectionId: string | null
  staticNamespaces?: StaticNamespace[]
  /** Currently assembled path (dot notation). */
  value: string
  /** Called when the path changes. The leaf row is also passed for type inspection. */
  onChange: (path: string, leaf?: FieldRow) => void
  maxDepth?: number
  testId?: string
}

/**
 * Cascading column-based field picker.
 *
 * Column 0 lists root-collection fields (plus any static namespaces). When a
 * relationship row is picked, its target collection is loaded into column 1,
 * and so on. Static-namespace rows expand into a column populated from the
 * namespace's pre-declared field list.
 */
export function FieldsTab({
  rootCollectionId,
  staticNamespaces,
  value,
  onChange,
  maxDepth = 6,
  testId = 'field-expression-picker-fields',
}: FieldsTabProps): React.ReactElement {
  const segments = value ? value.split('.') : []
  // Mirror trail tracks which collection id each picked segment opens up.
  const [trail, setTrail] = useState<Array<{ collectionId?: string; namespaceName?: string }>>([])

  // Root column rows: namespaces (if any) + the (lazily loaded) collection's fields.
  // We compute namespace rows here so they appear above collection fields when both exist.
  const namespaceRows: FieldRow[] = useMemo(
    () =>
      (staticNamespaces ?? []).map((ns) => ({
        segment: ns.name,
        displayName: ns.label,
        type: 'namespace',
        namespaceName: ns.name,
      })),
    [staticNamespaces]
  )

  // When the root collection is null AND there are no namespaces, we render
  // an empty column with a hint.
  const rootHasContent = rootCollectionId !== null || namespaceRows.length > 0

  const handlePick = (columnIndex: number, row: FieldRow) => {
    const baseSegments = segments.slice(0, columnIndex)
    const baseTrail = trail.slice(0, columnIndex)
    const nextSegments = [...baseSegments, row.segment]
    const isLeaf = !row.nextCollectionId && !row.namespaceName

    if (isLeaf) {
      onChange(nextSegments.join('.'), row)
      setTrail(baseTrail)
    } else {
      onChange(nextSegments.join('.'), row)
      setTrail([
        ...baseTrail,
        row.namespaceName
          ? { namespaceName: row.namespaceName }
          : { collectionId: row.nextCollectionId },
      ])
    }
  }

  // Build column specs from current segments + trail.
  const columnsToRender: Array<{
    title: string
    rows?: FieldRow[]
    collectionId?: string
  }> = []

  // Column 0: root.
  if (rootCollectionId && namespaceRows.length > 0) {
    // Mixed: we cannot use the schema-fetching column directly because we
    // also need namespace rows. Render a custom rows column built from
    // namespaces + the fetched fields. To keep things simple, render two
    // columns side-by-side is not what we want — instead, expose a small
    // helper that fetches and merges.
    columnsToRender.push({
      title: 'Root',
      rows: undefined,
      collectionId: rootCollectionId,
    })
    // Note: namespaces are surfaced via a thin "Globals" column to the
    // right of the root, only when there is no field selection yet.
  } else if (rootCollectionId) {
    columnsToRender.push({ title: 'Root', collectionId: rootCollectionId })
  } else if (namespaceRows.length > 0) {
    columnsToRender.push({ title: 'Available variables', rows: namespaceRows })
  }

  // Subsequent columns from the trail.
  for (let i = 0; i < trail.length && columnsToRender.length < maxDepth; i++) {
    const step = trail[i]
    if (step.namespaceName) {
      const ns = staticNamespaces?.find((n) => n.name === step.namespaceName)
      if (ns) {
        columnsToRender.push({
          title: ns.label,
          rows: ns.fields.map((f) => ({
            segment: f.name,
            displayName: f.displayName,
            type: String(f.type),
          })),
        })
      }
    } else if (step.collectionId) {
      columnsToRender.push({
        title: segments[i] ?? '',
        collectionId: step.collectionId,
      })
    }
  }

  return (
    <div className="flex h-full w-full overflow-x-auto" data-testid={testId}>
      {!rootHasContent ? (
        <div className="flex h-full w-full items-center justify-center px-6 text-center text-xs text-muted-foreground">
          No collection or namespace is configured for this template, so there is nothing to insert.
        </div>
      ) : (
        <>
          {/* Globals column — shown only when both a root collection AND
              namespaces exist; lets the user start at a namespace instead. */}
          {rootCollectionId && namespaceRows.length > 0 && (
            <Column
              title="Variables"
              rows={namespaceRows}
              selectedSegment={
                segments[0] && namespaceRows.some((n) => n.segment === segments[0])
                  ? segments[0]
                  : undefined
              }
              onPick={(row) => handlePick(0, row)}
              testId={`${testId}-namespaces`}
            />
          )}
          {columnsToRender.map((col, i) => (
            <Column
              key={`col-${i}-${col.collectionId ?? 'static'}-${col.title}`}
              title={col.title}
              rows={col.rows}
              collectionId={col.collectionId}
              selectedSegment={segments[i]}
              onPick={(row) => handlePick(i, row)}
              testId={`${testId}-col-${i}`}
            />
          ))}
        </>
      )}
    </div>
  )
}
