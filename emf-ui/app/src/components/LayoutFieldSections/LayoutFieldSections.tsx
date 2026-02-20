/**
 * LayoutFieldSections Component
 *
 * Renders record fields organized by a page layout's sections.
 * Each section becomes a collapsible DetailSection with the configured
 * column count, field placements, and label overrides.
 *
 * Falls through gracefully when layout fields reference schema fields
 * that no longer exist (silently skipped).
 */

import React, { useMemo } from 'react'
import { DetailSection } from '@/components/DetailSection'
import type { LayoutSectionDto, LayoutFieldPlacementDto } from '@/hooks/usePageLayout'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

export interface LayoutFieldSectionsProps {
  /** Sections from the resolved page layout */
  sections: LayoutSectionDto[]
  /** Full collection schema fields for type/reference info */
  schemaFields: FieldDefinition[]
  /** Record data */
  record: CollectionRecord
  /** Tenant slug for building lookup links */
  tenantSlug?: string
  /** Lookup display map: { fieldName: { recordId: displayLabel } } */
  lookupDisplayMap?: Record<string, Record<string, string>>
}

/**
 * Maps layout field placements to FieldDefinition objects that DetailSection expects.
 * Applies label overrides from the layout and preserves schema metadata (type, reference info).
 */
function mapPlacementsToFields(
  placements: LayoutFieldPlacementDto[],
  schemaFieldsByName: Map<string, FieldDefinition>,
  schemaFieldsById: Map<string, FieldDefinition>
): FieldDefinition[] {
  const sorted = [...placements].sort((a, b) => a.sortOrder - b.sortOrder)

  return sorted
    .map((placement) => {
      // Try to find the schema field by ID first, then by name
      const schemaField =
        schemaFieldsById.get(placement.fieldId) || schemaFieldsByName.get(placement.fieldName)
      if (!schemaField) return null // Field no longer exists in schema; skip silently

      return {
        ...schemaField,
        // Apply label override from layout if set
        displayName: placement.labelOverride || schemaField.displayName || schemaField.name,
      }
    })
    .filter((f): f is FieldDefinition => f !== null)
}

export function LayoutFieldSections({
  sections,
  schemaFields,
  record,
  tenantSlug,
  lookupDisplayMap,
}: LayoutFieldSectionsProps): React.ReactElement {
  // Build lookup maps once for efficient field resolution
  const { schemaFieldsByName, schemaFieldsById } = useMemo(() => {
    const byName = new Map<string, FieldDefinition>()
    const byId = new Map<string, FieldDefinition>()
    for (const field of schemaFields) {
      byName.set(field.name, field)
      byId.set(field.id, field)
    }
    return { schemaFieldsByName: byName, schemaFieldsById: byId }
  }, [schemaFields])

  // Sort sections by sortOrder
  const sortedSections = useMemo(
    () => [...sections].sort((a, b) => a.sortOrder - b.sortOrder),
    [sections]
  )

  return (
    <>
      {sortedSections.map((section) => {
        const fields = mapPlacementsToFields(section.fields, schemaFieldsByName, schemaFieldsById)

        if (fields.length === 0) return null

        return (
          <DetailSection
            key={section.id}
            title={section.heading || 'Details'}
            fields={fields}
            record={record}
            tenantSlug={tenantSlug}
            lookupDisplayMap={lookupDisplayMap}
            defaultCollapsed={section.collapsed}
            columns={(section.columns as 1 | 2 | 3) || 2}
          />
        )
      })}
    </>
  )
}
