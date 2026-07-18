/**
 * Section-nav helpers shared by `LayoutFieldSections` (which renders the
 * anchors) and the record detail pages (which build `RecordSectionNav` items).
 * Kept out of the component file so it only exports components (react-refresh).
 */

import type { RecordSectionNavItem } from '@/components/record/RecordSectionNav'
import type { LayoutSectionDto } from '@/hooks/usePageLayout'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'

/** DOM anchor id for a rendered layout section (RecordSectionNav scroll target). */
export function sectionAnchorId(sectionId: string): string {
  return `record-section-${sectionId}`
}

/**
 * Resolves the nav entries `RecordSectionNav` shows for a layout's sections.
 * Mirrors the `LayoutFieldSections` render path: sections sort by `sortOrder`,
 * placements that no longer resolve to a schema field are skipped, and
 * sections with no resolvable fields (which render nothing) are omitted.
 * Counts therefore match each section header's field badge.
 */
export function resolveSectionNavItems(
  sections: LayoutSectionDto[],
  schemaFields: FieldDefinition[]
): RecordSectionNavItem[] {
  const byName = new Map(schemaFields.map((f) => [f.name, f]))
  const byId = new Map(schemaFields.map((f) => [f.id, f]))
  return [...sections]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((section) => ({
      anchorId: sectionAnchorId(section.id),
      label: section.heading || 'Details',
      count: section.fields.filter((p) => byId.has(p.fieldId) || byName.has(p.fieldName)).length,
    }))
    .filter((item) => item.count > 0)
}
