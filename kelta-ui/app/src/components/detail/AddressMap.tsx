/**
 * AddressMap
 *
 * Composite field section: address fields on the left, map on the right.
 *
 * The map auto-promotes from a stylized placeholder to a real static map
 * tile when the record exposes lat/lng:
 *   1. If a geolocation field is identified on the record (either via the
 *      `geoField` prop or auto-detected from `fields`) and contains valid
 *      coordinates, render a static-image map.
 *   2. Static-image provider is picked at build time:
 *        - When `VITE_MAPBOX_TOKEN` is set, use Mapbox Static Images
 *          (better quality, requires the tenant's Mapbox account)
 *        - Otherwise, fall back to OpenStreetMap's free staticmap.de
 *          service (no API key required, lower quality)
 *   3. When no coordinates are present, fall back to the original stylized
 *      placeholder.
 *
 * Real interactive maps (pan, zoom, custom markers) are a separate
 * follow-up — static images are a deliberate intermediate that covers most
 * record-detail use cases without adding ~600KB of map-engine JS to the
 * bundle.
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
  /**
   * Field name on the record holding geolocation coordinates. The value is
   * expected to be either `{ latitude, longitude }` or `{ lat, lng }`.
   * Falls back to auto-detecting any `geolocation`-typed field in `fields`.
   */
  geoField?: string
  record: CollectionRecord
  tenantSlug?: string
  lookupDisplayMap?: Record<string, Record<string, string>>
  defaultCollapsed?: boolean
}

interface GeoPoint {
  lat: number
  lng: number
}

function extractGeoPoint(
  record: CollectionRecord,
  fields: FieldDefinition[],
  geoField: string | undefined
): GeoPoint | null {
  const fieldName =
    geoField ?? fields.find((f) => f.type === 'geolocation')?.name ?? undefined
  if (!fieldName) return null
  const raw = record[fieldName]
  if (!raw || typeof raw !== 'object') return null
  const g = raw as Record<string, unknown>
  const lat = Number(g.latitude ?? g.lat)
  const lng = Number(g.longitude ?? g.lng ?? g.lon)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
  return { lat, lng }
}

/**
 * Static-map image URL. Mapbox when the build has a token, else
 * OpenStreetMap's free staticmap service.
 */
function staticMapUrl(point: GeoPoint, width: number, height: number): string {
  const mapboxToken =
    typeof import.meta !== 'undefined' &&
    typeof (import.meta as { env?: Record<string, string | undefined> }).env !== 'undefined'
      ? (import.meta as { env: Record<string, string | undefined> }).env.VITE_MAPBOX_TOKEN
      : undefined

  if (mapboxToken) {
    const style =
      (import.meta as { env: Record<string, string | undefined> }).env
        .VITE_MAPBOX_STYLE ?? 'mapbox/dark-v11'
    return (
      `https://api.mapbox.com/styles/v1/${style}/static/` +
      `pin-l+3b82f6(${point.lng},${point.lat})/` +
      `${point.lng},${point.lat},14,0/${width}x${height}@2x?access_token=${mapboxToken}`
    )
  }

  // OSM staticmap fallback — free, no key required.
  return (
    `https://staticmap.openstreetmap.de/staticmap.php?` +
    `center=${point.lat},${point.lng}&zoom=14&size=${width}x${height}` +
    `&markers=${point.lat},${point.lng},lightblue1&maptype=mapnik`
  )
}

export function AddressMap({
  title,
  fields,
  mapLabelFields = [],
  geoField,
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

  const point = extractGeoPoint(record, fields, geoField)

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

            {point ? (
              <StaticMap point={point} label={mapLabel || 'Address'} />
            ) : (
              <MapPlaceholder label={mapLabel || 'Address'} />
            )}
          </div>
        </CollapsibleContent>
      </Card>
    </Collapsible>
  )
}

function StaticMap({
  point,
  label,
}: {
  point: GeoPoint
  label: string
}): React.ReactElement {
  const [errored, setErrored] = useState(false)
  if (errored) return <MapPlaceholder label={label} />
  return (
    <div className="relative h-[220px] overflow-hidden rounded-[10px] bg-muted">
      <img
        src={staticMapUrl(point, 600, 220)}
        alt={`Map showing ${label}`}
        loading="lazy"
        onError={() => setErrored(true)}
        className="h-full w-full object-cover"
      />
      <div className="absolute bottom-2 left-2 inline-flex items-center gap-1 rounded-md bg-black/55 px-2 py-1 font-mono text-[11px] text-white/90 backdrop-blur-sm">
        {label}
      </div>
    </div>
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
