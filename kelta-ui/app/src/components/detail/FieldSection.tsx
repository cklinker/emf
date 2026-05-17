/**
 * FieldSection
 *
 * Collapsible card holding a CSS-grid of field name/value pairs.
 *
 * Layout:
 *   ┌─ header (cursor-pointer): chevron + title + "N fields" pill ─┐
 *   │                                                               │
 *   │ 1px top border                                                │
 *   │ grid (configurable columns, 22px row × 40px col gap)          │
 *   └───────────────────────────────────────────────────────────────┘
 *
 * Replaces the visual treatment of `<DetailSection>` while keeping
 * the same field-rendering responsibilities so it slots into
 * `LayoutFieldSections` cleanly.
 */

import React, { useCallback, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FieldRenderer } from '@/components/FieldRenderer'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'
import { cn } from '@/lib/utils'

export interface FieldSectionProps {
  /** Section heading */
  title: string
  /** Fields to render in this section */
  fields: FieldDefinition[]
  /** Record data */
  record: CollectionRecord
  /** Tenant slug for building lookup links */
  tenantSlug?: string
  /** Lookup display map: { fieldName: { recordId: displayLabel } } */
  lookupDisplayMap?: Record<string, Record<string, string>>
  /** Whether the section starts collapsed */
  defaultCollapsed?: boolean
  /** Number of grid columns (1-4). Default: 2 */
  columns?: 1 | 2 | 3 | 4
  /**
   * When set, the open/collapsed state is persisted to localStorage under
   * `kelta_detail_section_${persistKey}`. Survives navigation and reloads.
   * Defer to a real user-prefs API when one exists.
   */
  persistKey?: string
}

const STORAGE_PREFIX = 'kelta_detail_section_'

function readPersistedOpen(persistKey: string | undefined, fallback: boolean): boolean {
  if (!persistKey || typeof window === 'undefined') return fallback
  try {
    const raw = window.localStorage.getItem(STORAGE_PREFIX + persistKey)
    if (raw === '1') return true
    if (raw === '0') return false
  } catch {
    // localStorage may be disabled (private mode, quota) — fall back silently
  }
  return fallback
}

function writePersistedOpen(persistKey: string | undefined, open: boolean): void {
  if (!persistKey || typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_PREFIX + persistKey, open ? '1' : '0')
  } catch {
    // ignore — see readPersistedOpen
  }
}

export function FieldSection({
  title,
  fields,
  record,
  tenantSlug,
  lookupDisplayMap,
  defaultCollapsed = false,
  columns = 2,
  persistKey,
}: FieldSectionProps): React.ReactElement | null {
  const [isOpen, setIsOpenState] = useState(() =>
    readPersistedOpen(persistKey, !defaultCollapsed)
  )
  const setIsOpen = useCallback(
    (open: boolean) => {
      setIsOpenState(open)
      writePersistedOpen(persistKey, open)
    },
    [persistKey]
  )

  if (fields.length === 0) return null

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <Card
        data-component="FieldSection"
        className="overflow-hidden rounded-xl border border-border bg-card"
      >
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="flex w-full items-center gap-2 px-5 py-3.5 text-left transition-colors hover:bg-accent/30"
            aria-expanded={isOpen}
          >
            <ChevronRight
              className={cn('kelta-chev h-4 w-4 text-muted-foreground', isOpen && 'rotate-90')}
              aria-hidden="true"
            />
            <span className="text-sm font-semibold text-foreground">{title}</span>
            <span className="ml-2 inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground tabular-nums">
              {fields.length} field{fields.length === 1 ? '' : 's'}
            </span>
          </button>
        </CollapsibleTrigger>

        <CollapsibleContent>
          <div className="border-t border-border px-5 py-5">
            <div
              className="kelta-field-grid"
              style={{ ['--kelta-grid-cols' as string]: String(columns) } as React.CSSProperties}
            >
              {fields.map((field) => {
                const value = record[field.name]
                const isLookup =
                  field.type === 'master_detail' ||
                  field.type === 'lookup' ||
                  field.type === 'reference'
                const displayLabel =
                  isLookup && lookupDisplayMap?.[field.name]
                    ? lookupDisplayMap[field.name][String(value)] || undefined
                    : undefined

                return (
                  <div key={field.name} className="min-w-0 space-y-1">
                    <dt className="kelta-field-label">
                      {field.displayName || field.name}
                    </dt>
                    <dd className="text-sm text-foreground">
                      <FieldRenderer
                        type={field.type}
                        value={value}
                        fieldName={field.name}
                        displayName={field.displayName || field.name}
                        tenantSlug={tenantSlug}
                        targetCollection={field.referenceTarget}
                        displayLabel={displayLabel}
                        truncate={false}
                      />
                    </dd>
                  </div>
                )
              })}
            </div>
          </div>
        </CollapsibleContent>
      </Card>
    </Collapsible>
  )
}
