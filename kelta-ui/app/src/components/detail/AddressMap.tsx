/**
 * AddressMap
 *
 * Composite field section: address fields on the left, stylized map
 * placeholder on the right (radial gradient on slate + 28px dotted grid +
 * teardrop pin). Real map provider integration (Mapbox / Google Maps) is
 * a deliberate follow-up — see the handoff README.
 */

import React, { useState } from 'react'
import { ChevronRight, MapPin } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FieldRenderer } from '@/components/FieldRenderer'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'
import { cn } from '@/lib/utils'

export interface AddressMapProps {
  title: string
  /**
   * Address fields to display on the left side. The first field is rendered
   * full-width (typically "Street"); the remainder flow into a 2-column grid.
   */
  fields: FieldDefinition[]
  /** Record-name fields used to compose the map-pin chip label */
  mapLabelFields?: string[]
  record: CollectionRecord
  tenantSlug?: string
  lookupDisplayMap?: Record<string, Record<string, string>>
  defaultCollapsed?: boolean
}

export function AddressMap({
  title,
  fields,
  mapLabelFields = [],
  record,
  tenantSlug,
  lookupDisplayMap,
  defaultCollapsed = false,
}: AddressMapProps): React.ReactElement | null {
  const [isOpen, setIsOpen] = useState(!defaultCollapsed)

  if (fields.length === 0) return null

  const [streetField, ...restFields] = fields

  const mapLabel = mapLabelFields
    .map((f) => record[f])
    .filter((v) => v !== null && v !== undefined && v !== '')
    .map(String)
    .join(', ')

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <Card
        data-component="AddressMap"
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
          </button>
        </CollapsibleTrigger>

        <CollapsibleContent>
          <div className="grid grid-cols-1 gap-6 border-t border-border px-5 py-5 lg:grid-cols-2">
            <div className="kelta-field-grid">
              {streetField && (
                <div className="min-w-0 space-y-1" style={{ gridColumn: '1 / -1' }}>
                  <dt className="kelta-field-label">
                    {streetField.displayName || streetField.name}
                  </dt>
                  <dd className="text-sm text-foreground">
                    <FieldRenderer
                      type={streetField.type}
                      value={record[streetField.name]}
                      fieldName={streetField.name}
                      displayName={streetField.displayName || streetField.name}
                      tenantSlug={tenantSlug}
                      targetCollection={streetField.referenceTarget}
                      truncate={false}
                    />
                  </dd>
                </div>
              )}
              {restFields.map((field) => (
                <div key={field.name} className="min-w-0 space-y-1">
                  <dt className="kelta-field-label">{field.displayName || field.name}</dt>
                  <dd className="text-sm text-foreground">
                    <FieldRenderer
                      type={field.type}
                      value={record[field.name]}
                      fieldName={field.name}
                      displayName={field.displayName || field.name}
                      tenantSlug={tenantSlug}
                      targetCollection={field.referenceTarget}
                      displayLabel={
                        lookupDisplayMap?.[field.name]?.[String(record[field.name])] || undefined
                      }
                      truncate={false}
                    />
                  </dd>
                </div>
              ))}
            </div>

            <MapPlaceholder label={mapLabel || 'Address'} />
          </div>
        </CollapsibleContent>
      </Card>
    </Collapsible>
  )
}

function MapPlaceholder({ label }: { label: string }): React.ReactElement {
  return (
    <div
      className="relative h-[220px] overflow-hidden rounded-[10px]"
      aria-hidden="true"
      style={{
        background:
          'radial-gradient(circle at 30% 40%, rgba(59,130,246,0.18), transparent 60%), linear-gradient(180deg, #0f1c33, #0a1322)',
      }}
    >
      <div
        className="absolute inset-0 opacity-50"
        style={{
          backgroundImage:
            'radial-gradient(circle, rgba(148,163,184,0.18) 1px, transparent 1px)',
          backgroundSize: '28px 28px',
        }}
      />
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
        <span className="inline-flex h-9 w-9 items-center justify-center rounded-full kelta-brand-gradient shadow-[0_6px_14px_-4px_rgba(6,182,212,0.5)]">
          <MapPin className="h-4 w-4 text-white" aria-hidden="true" />
        </span>
      </div>
      <div className="absolute bottom-2 left-2 inline-flex items-center gap-1 rounded-md bg-black/40 px-2 py-1 font-mono text-[11px] text-white/90 backdrop-blur-sm">
        {label}
      </div>
    </div>
  )
}
