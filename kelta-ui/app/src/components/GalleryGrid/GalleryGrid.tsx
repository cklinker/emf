/**
 * GalleryGrid Component (app-data-entry slice 7)
 *
 * `viewType='gallery'` renderer for ObjectListPage: responsive card grid with
 * an optional image header (a `url` field; non-URL values fall back to an
 * initial-letter placeholder), bold title, and up to four FieldRenderer
 * label/value pairs. Same query/pagination as the table.
 */

import React, { useState } from 'react'
import { cn } from '@/lib/utils'
import { FieldRenderer } from '@/components/FieldRenderer'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

/** Max label/value pairs on a card body. */
const MAX_CARD_FIELDS = 4

/** Only http(s) URLs render as images; anything else gets the placeholder. */
function imageUrlOf(record: CollectionRecord, imageField?: FieldDefinition): string | null {
  if (!imageField) return null
  const raw = record[imageField.name]
  if (typeof raw !== 'string') return null
  return /^https?:\/\//i.test(raw) ? raw : null
}

function titleOf(record: CollectionRecord, titleField: string): string {
  const raw = record[titleField]
  return raw === null || raw === undefined || raw === '' ? record.id : String(raw)
}

function CardImage({ url, title }: { url: string | null; title: string }) {
  const [failed, setFailed] = useState(false)
  if (!url || failed) {
    return (
      <div
        className="flex aspect-video items-center justify-center rounded-t-[10px] bg-muted text-2xl font-semibold text-muted-foreground"
        data-testid="gallery-placeholder"
        aria-hidden
      >
        {title.charAt(0).toUpperCase()}
      </div>
    )
  }
  return (
    <img
      src={url}
      alt=""
      loading="lazy"
      className="aspect-video w-full rounded-t-[10px] object-cover"
      onError={() => setFailed(true)}
      data-testid="gallery-image"
    />
  )
}

export interface GalleryGridProps {
  records: CollectionRecord[]
  /** Optional `url` field rendered as the card image. */
  imageField?: FieldDefinition
  /** Bold card title field name. */
  titleField: string
  /** Card body fields (capped at four here). */
  cardFields: FieldDefinition[]
  onCardClick: (record: CollectionRecord) => void
  tenantSlug?: string
  lookupDisplayMap?: Record<string, Record<string, string>>
}

export function GalleryGrid({
  records,
  imageField,
  titleField,
  cardFields,
  onCardClick,
  tenantSlug,
  lookupDisplayMap,
}: GalleryGridProps): React.ReactElement {
  const bodyFields = cardFields.filter((f) => f.name !== titleField).slice(0, MAX_CARD_FIELDS)

  if (records.length === 0) {
    return (
      <div
        className="rounded-[10px] border border-border bg-card p-8 text-center text-sm text-muted-foreground"
        data-testid="gallery-empty"
      >
        No records found.
      </div>
    )
  }

  return (
    <div
      className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3"
      data-testid="gallery-grid"
    >
      {records.map((record) => {
        const title = titleOf(record, titleField)
        return (
          <div
            key={record.id}
            className={cn(
              'cursor-pointer rounded-[10px] border border-border bg-card shadow-sm',
              'hover:ring-1 hover:ring-primary/40'
            )}
            onClick={() => onCardClick(record)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onCardClick(record)
              }
            }}
            data-testid={`gallery-card-${record.id}`}
          >
            <CardImage url={imageUrlOf(record, imageField)} title={title} />
            <div className="space-y-1.5 p-3">
              <div className="truncate text-sm font-semibold">{title}</div>
              {bodyFields.map((field) => {
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
                  <div key={field.name} className="min-w-0">
                    <div className="text-[11px] font-medium uppercase tracking-[0.09em] text-muted-foreground">
                      {field.displayName || field.name}
                    </div>
                    <div className="truncate text-xs">
                      <FieldRenderer
                        type={field.type}
                        value={value}
                        fieldName={field.name}
                        displayName={field.displayName || field.name}
                        tenantSlug={tenantSlug}
                        targetCollection={field.referenceTarget}
                        displayLabel={displayLabel}
                        truncate
                      />
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}
