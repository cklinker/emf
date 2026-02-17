/**
 * DetailSection Component
 *
 * Renders a collapsible section of field name-value pairs in a 2-column grid.
 * Used on the record detail page to organize fields into logical groups.
 */

import React, { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FieldRenderer } from '@/components/FieldRenderer'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'
import { cn } from '@/lib/utils'

export interface DetailSectionProps {
  /** Section heading */
  title: string
  /** Fields to display in this section */
  fields: FieldDefinition[]
  /** Record data */
  record: CollectionRecord
  /** Tenant slug for building lookup links */
  tenantSlug?: string
  /** Lookup display map: { fieldName: { recordId: displayLabel } } */
  lookupDisplayMap?: Record<string, Record<string, string>>
  /** Whether the section starts collapsed */
  defaultCollapsed?: boolean
}

export function DetailSection({
  title,
  fields,
  record,
  tenantSlug,
  lookupDisplayMap,
  defaultCollapsed = false,
}: DetailSectionProps): React.ReactElement {
  const [isOpen, setIsOpen] = useState(!defaultCollapsed)

  if (fields.length === 0) return <></>

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <Card>
        <CollapsibleTrigger asChild>
          <CardHeader className="cursor-pointer select-none py-3">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <ChevronDown
                className={cn(
                  'h-4 w-4 text-muted-foreground transition-transform',
                  isOpen && 'rotate-0',
                  !isOpen && '-rotate-90'
                )}
              />
              {title}
              <span className="text-xs font-normal text-muted-foreground">
                ({fields.length} field{fields.length !== 1 ? 's' : ''})
              </span>
            </CardTitle>
          </CardHeader>
        </CollapsibleTrigger>

        <CollapsibleContent>
          <CardContent className="pt-0">
            <div className="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-2">
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
                  <div key={field.name} className="space-y-1">
                    <dt className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                      {field.displayName || field.name}
                    </dt>
                    <dd className="text-sm">
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
          </CardContent>
        </CollapsibleContent>
      </Card>
    </Collapsible>
  )
}
