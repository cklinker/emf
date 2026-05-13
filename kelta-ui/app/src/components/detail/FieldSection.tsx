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

import React, { useState } from 'react'
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
}

export function FieldSection({
  title,
  fields,
  record,
  tenantSlug,
  lookupDisplayMap,
  defaultCollapsed = false,
  columns = 2,
}: FieldSectionProps): React.ReactElement | null {
  const [isOpen, setIsOpen] = useState(!defaultCollapsed)

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
