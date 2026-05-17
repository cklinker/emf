/**
 * usePageLayout Hook
 *
 * Resolves the page layout for a given collection and user.
 * Queries JSON:API layout-assignments to find the matching layout,
 * then fetches the page-layout details.
 *
 * Returns null when no layout is configured or on error,
 * so the caller can fall back to default rendering.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { useCollectionStore } from '../context/CollectionStoreContext'

// ---- TypeScript interfaces matching backend PageLayoutDto ----

export interface LayoutFieldPlacementDto {
  id: string
  fieldId: string
  fieldName: string
  fieldType: string
  fieldDisplayName: string
  columnNumber: number
  columnSpan: number
  sortOrder: number
  requiredOnLayout: boolean
  readOnlyOnLayout: boolean
  labelOverride: string | null
  helpTextOverride: string | null
  visibilityRule: string | null
}

export interface LayoutSectionDto {
  id: string
  heading: string
  columns: number
  sortOrder: number
  collapsed: boolean
  style: string
  sectionType: string
  tabGroup: string | null
  tabLabel: string | null
  visibilityRule: string | null
  fields: LayoutFieldPlacementDto[]
}

export interface LayoutRelatedListDto {
  id: string
  relatedCollectionId: string
  relatedCollectionName: string
  relationshipFieldId: string
  relationshipFieldName: string
  /**
   * JSONB-backed; can arrive as a real `string[]` or as a JSON-stringified
   * array. Use `parseDisplayColumns()` to normalize before reading.
   */
  displayColumns: string | string[]
  sortField: string | null
  sortDirection: string
  rowLimit: number
  sortOrder: number
}

export interface LayoutRuleDto {
  id: string
  layoutId: string
  name: string
  description: string | null
  kind: 'COMPUTE' | 'VALIDATE' | 'DEFAULT' | 'TRANSFORM'
  active: boolean
  whenEvents: string[] | string
  targetField: string | null
  dependsOn: string[] | string | null
  body: Record<string, unknown> | string
  sortOrder: number
}

/**
 * Record-detail header configuration (handoff). When NULL on the layout, the
 * renderer auto-derives sensible defaults from the record's contact-ish
 * fields. Shape matches `RecordHeaderConfig` in `@/components/detail`.
 */
export interface RecordHeaderConfigDto {
  titleFields?: string[]
  avatarFrom?: string[]
  metaFields?: Array<{ key: string; icon?: string; prefix?: string }>
}

/**
 * Side-rail block configuration. Each block carries a discriminating `kind`
 * + a `config` payload matching the corresponding component's props. The
 * renderer dispatches on `kind` and silently ignores unknown variants so
 * adding a new block kind on the server doesn't crash older clients.
 */
export type RailBlockDto =
  | { kind: 'metadataCard'; config: { title: string; rows: Array<{ label: string; value: string; mono?: boolean }> } }
  | { kind: 'statStrip'; config: { tiles: Array<Record<string, unknown>> } }
  | { kind: 'scoreCard'; config: Record<string, unknown> }
  | { kind: 'tagsCard'; config: { title: string; tags: Array<{ label: string; tone?: string }> } }
  | { kind: 'aiCard'; config: { title: string; summary: string; actions?: Array<{ label: string }> } }
  | { kind: 'timeline'; config: { title: string; events: Array<Record<string, unknown>> } }

export interface PageLayoutDto {
  id: string
  collectionId: string
  name: string
  description: string
  layoutType: string
  isDefault: boolean
  sections: LayoutSectionDto[]
  relatedLists: LayoutRelatedListDto[]
  rules: LayoutRuleDto[]
  /** When set, overrides the auto-derived RecordHeader meta row + actions */
  headerConfig: RecordHeaderConfigDto | null
  /** When non-empty, replaces the auto-derived rail (system-info MetadataCard) */
  railBlocks: RailBlockDto[] | null
  createdAt: string
  updatedAt: string
}

export interface UsePageLayoutResult {
  layout: PageLayoutDto | null
  isLoading: boolean
  error: Error | null
}

/**
 * Hook to resolve the page layout for a collection and user.
 *
 * @param collectionId - The collection UUID
 * @param profileId - The user ID (used as profileId for layout resolution)
 * @param recordTypeId - Optional record type ID for type-specific layout resolution
 * @returns The resolved layout or null if none configured
 */
export function usePageLayout(
  collectionId: string | undefined,
  profileId: string | undefined,
  recordTypeId?: string
): UsePageLayoutResult {
  const { apiClient } = useApi()
  const collectionStore = useCollectionStore()

  const {
    data: layout = null,
    isLoading,
    error,
  } = useQuery<PageLayoutDto | null, Error>({
    queryKey: ['page-layout-resolve', collectionId, profileId, recordTypeId],
    queryFn: async () => {
      try {
        // Step 1: Find layout assignment for this collection (single query)
        const allAssignments = await apiClient.getList<{
          id: string
          collectionId: string
          profileId?: string
          recordTypeId?: string
          layoutId: string
          isDefault?: boolean
        }>(`/api/layout-assignments?filter[collectionId][eq]=${collectionId}&page[size]=50`)

        // Resolution order:
        // 1. Profile + record type match
        // 2. Record type match (any profile)
        // 3. Profile match (no record type)
        // 4. Default assignment
        let layoutId: string | undefined
        if (recordTypeId) {
          const profileAndTypeMatch = allAssignments.find(
            (a) => a.profileId === profileId && a.recordTypeId === recordTypeId
          )
          if (profileAndTypeMatch) {
            layoutId = profileAndTypeMatch.layoutId
          } else {
            const typeMatch = allAssignments.find(
              (a) => a.recordTypeId === recordTypeId && !a.profileId
            )
            if (typeMatch) {
              layoutId = typeMatch.layoutId
            }
          }
        }
        if (!layoutId) {
          const profileMatch = allAssignments.find(
            (a) => a.profileId === profileId && !a.recordTypeId
          )
          if (profileMatch) {
            layoutId = profileMatch.layoutId
          } else {
            const defaultAssignment = allAssignments.find((a) => a.isDefault)
            if (defaultAssignment) {
              layoutId = defaultAssignment.layoutId
            }
          }
        }

        // Final fallback: query page-layouts directly for a default DETAIL
        // layout matching this collection. Filtering by layoutType prevents
        // a LIST layout sharing the same collection from being resolved here
        // (both can have isDefault=true). Sort + larger page size so the
        // result is deterministic even if multiple defaults exist for the
        // same type — the oldest by createdAt wins, and a console warning
        // surfaces the conflict.
        if (!layoutId) {
          const defaultLayouts = await apiClient.getList<{
            id: string
            collectionId: string
            isDefault: boolean
            layoutType?: string
            createdAt?: string
          }>(
            `/api/page-layouts?filter[collectionId][eq]=${collectionId}&filter[isDefault][eq]=true&filter[layoutType][eq]=DETAIL&sort=createdAt&page[size]=10`
          )
          if (defaultLayouts.length > 1) {
            console.warn(
              `[usePageLayout] Multiple default DETAIL layouts for collection ${collectionId}; using oldest (${defaultLayouts[0].id}). Clean up duplicate is_default flags.`
            )
          }
          if (defaultLayouts.length > 0) {
            layoutId = defaultLayouts[0].id
          }
        }

        if (!layoutId) return null

        // Step 2: Fetch the layout with all children in a single request.
        // The backend supports transitive includes: layout-fields are
        // resolved via layout-sections even though they reference sections
        // (not page-layouts) directly.
        type JsonApiResource = {
          type: string
          id: string
          attributes: Record<string, unknown>
          relationships?: Record<string, { data?: { type: string; id: string } | null }>
        }
        const raw = await apiClient.get<{
          data: JsonApiResource
          included?: JsonApiResource[]
        }>(
          `/api/page-layouts/${layoutId}?include=layout-sections,layout-fields,layout-related-lists`
        )

        // Flatten a JSON:API resource: merge attributes + relationship IDs
        const flatten = (r: JsonApiResource): Record<string, unknown> => {
          const obj: Record<string, unknown> = { id: r.id, ...r.attributes }
          if (r.relationships) {
            for (const [key, rel] of Object.entries(r.relationships)) {
              obj[key] = rel?.data?.id ?? null
            }
          }
          return obj
        }

        const flatLayout = flatten(raw.data)
        const included = raw.included ?? []

        // Guard: an assignment can point at a LIST layout for the same
        // collection. Detail rendering must ignore non-DETAIL layouts so we
        // don't show empty sections / fall back unexpectedly.
        const resolvedType =
          typeof flatLayout.layoutType === 'string' ? flatLayout.layoutType : 'DETAIL'
        if (resolvedType !== 'DETAIL') {
          return null
        }

        // Extract and sort sections
        const sections: LayoutSectionDto[] = included
          .filter((r) => r.type === 'layout-sections')
          .map((r) => {
            const flat = flatten(r)
            return { ...flat, fields: [] } as unknown as LayoutSectionDto
          })
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

        // Extract field placements and group by sectionId.
        // Backend serializes the boolean toggles with their `is*` Java field
        // names (`isRequiredOnLayout`, `isReadOnlyOnLayout`); normalize to the
        // unprefixed names the renderer + DTO consumers expect.
        const fieldPlacements = included
          .filter((r) => r.type === 'layout-fields')
          .map((r) => {
            const flat = flatten(r) as Record<string, unknown>
            if ('isRequiredOnLayout' in flat && !('requiredOnLayout' in flat)) {
              flat.requiredOnLayout = flat.isRequiredOnLayout
            }
            if ('isReadOnlyOnLayout' in flat && !('readOnlyOnLayout' in flat)) {
              flat.readOnlyOnLayout = flat.isReadOnlyOnLayout
            }
            return flat as unknown as LayoutFieldPlacementDto & { sectionId: string }
          })
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

        const fieldsBySection = new Map<string, LayoutFieldPlacementDto[]>()
        for (const fp of fieldPlacements) {
          const list = fieldsBySection.get(fp.sectionId) ?? []
          list.push(fp)
          fieldsBySection.set(fp.sectionId, list)
        }

        // Attach fields to their sections
        for (const section of sections) {
          section.fields = fieldsBySection.get(section.id) ?? []
        }

        // Extract related lists (only have IDs from relationships — names resolved below)
        const relatedLists: LayoutRelatedListDto[] = included
          .filter((r) => r.type === 'layout-related-lists')
          .map((r) => flatten(r) as unknown as LayoutRelatedListDto)
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

        // Resolve collection names and field names for related lists.
        // The DB only stores relatedCollectionId / relationshipFieldId (UUIDs),
        // but the UI needs the collection name (for API URLs) and field name.
        // Uses the centralized collection store for instant lookups (no API calls).
        if (relatedLists.length > 0) {
          for (const rl of relatedLists) {
            if (!rl.relatedCollectionName && rl.relatedCollectionId) {
              const coll = collectionStore.getCollectionById(rl.relatedCollectionId)
              rl.relatedCollectionName = coll?.name || ''
            }
            if (!rl.relationshipFieldName && rl.relationshipFieldId) {
              const field = collectionStore.getFieldById(rl.relationshipFieldId)
              rl.relationshipFieldName = field?.name || ''
            }
          }
        }

        // Step 3: Fetch layout rules separately. The page-layouts include
        // chain doesn't reach layout-rules, so we issue a parallel request.
        // Empty result is the common case — no rules configured for this layout.
        let rules: LayoutRuleDto[] = []
        try {
          rules = await apiClient.getList<LayoutRuleDto>(
            `/api/layout-rules?filter[layoutId][eq]=${layoutId}&filter[active][eq]=true&sort=sortOrder&page[size]=100`,
          )
        } catch {
          rules = []
        }

        const result: PageLayoutDto = {
          ...(flatLayout as unknown as Omit<PageLayoutDto, 'sections' | 'relatedLists' | 'rules'>),
          sections,
          relatedLists,
          rules,
        }

        if (!result.sections || result.sections.length === 0) {
          return null
        }
        return result
      } catch {
        // Any error means no layout configured
        return null
      }
    },
    enabled: !!collectionId && !!profileId,
    staleTime: 30 * 1000,
  })

  return {
    layout,
    isLoading,
    error: error ?? null,
  }
}
