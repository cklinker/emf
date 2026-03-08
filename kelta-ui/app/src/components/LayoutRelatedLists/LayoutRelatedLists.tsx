/**
 * LayoutRelatedLists Component
 *
 * Renders related record lists as configured in a page layout.
 * Uses the enriched DTO fields (relatedCollectionName, relationshipFieldName)
 * to avoid extra API calls. Delegates rendering to the existing RelatedList component.
 */

import React, { useMemo } from 'react'
import { RelatedList } from '@/components/RelatedList'
import type { LayoutRelatedListDto } from '@/hooks/usePageLayout'

export interface LayoutRelatedListsProps {
  /** Related list configurations from the resolved page layout */
  relatedLists: LayoutRelatedListDto[]
  /** Parent record ID */
  parentRecordId: string
  /** Tenant slug for building URLs */
  tenantSlug: string
  /**
   * Optional: raw JSON:API response from the parent record request.
   * When provided, related records are extracted from the `included` array
   * instead of making separate API calls per related list.
   */
  includedData?: unknown
}

export function LayoutRelatedLists({
  relatedLists,
  parentRecordId,
  tenantSlug,
  includedData,
}: LayoutRelatedListsProps): React.ReactElement {
  const sortedLists = useMemo(
    () => [...relatedLists].sort((a, b) => a.sortOrder - b.sortOrder),
    [relatedLists]
  )

  if (sortedLists.length === 0) return <></>

  return (
    <>
      {sortedLists.map((rl) => (
        <RelatedList
          key={rl.id}
          collectionName={rl.relatedCollectionName}
          foreignKeyField={rl.relationshipFieldName}
          parentRecordId={parentRecordId}
          tenantSlug={tenantSlug}
          limit={rl.rowLimit}
          includedData={includedData}
        />
      ))}
    </>
  )
}
