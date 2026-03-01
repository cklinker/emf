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
  displayColumns: string
  sortField: string | null
  sortDirection: string
  rowLimit: number
  sortOrder: number
}

export interface PageLayoutDto {
  id: string
  collectionId: string
  name: string
  description: string
  layoutType: string
  isDefault: boolean
  sections: LayoutSectionDto[]
  relatedLists: LayoutRelatedListDto[]
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
 * @returns The resolved layout or null if none configured
 */
export function usePageLayout(
  collectionId: string | undefined,
  profileId: string | undefined
): UsePageLayoutResult {
  const { apiClient } = useApi()

  const {
    data: layout = null,
    isLoading,
    error,
  } = useQuery<PageLayoutDto | null, Error>({
    queryKey: ['page-layout-resolve', collectionId, profileId],
    queryFn: async () => {
      try {
        // Step 1: Find layout assignment for this collection (single query)
        const allAssignments = await apiClient.getList<{
          id: string
          collectionId: string
          profileId?: string
          layoutId: string
          isDefault?: boolean
        }>(`/api/layout-assignments?filter[collectionId][eq]=${collectionId}&page[size]=10`)

        // Prefer profile-specific assignment, then fall back to default
        let layoutId: string | undefined
        const profileMatch = allAssignments.find((a) => a.profileId === profileId)
        if (profileMatch) {
          layoutId = profileMatch.layoutId
        } else {
          const defaultAssignment = allAssignments.find((a) => a.isDefault)
          if (defaultAssignment) {
            layoutId = defaultAssignment.layoutId
          }
        }

        // Final fallback: query page-layouts directly for a default layout
        // matching this collection. This handles the common case where
        // layouts exist but no layout-assignment records have been created.
        if (!layoutId) {
          const defaultLayouts = await apiClient.getList<{
            id: string
            collectionId: string
            isDefault: boolean
          }>(
            `/api/page-layouts?filter[collectionId][eq]=${collectionId}&filter[isDefault][eq]=true&page[size]=1`
          )
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

        // Extract and sort sections
        const sections: LayoutSectionDto[] = included
          .filter((r) => r.type === 'layout-sections')
          .map((r) => {
            const flat = flatten(r)
            return { ...flat, fields: [] } as unknown as LayoutSectionDto
          })
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

        // Extract field placements and group by sectionId
        const fieldPlacements = included
          .filter((r) => r.type === 'layout-fields')
          .map((r) => flatten(r) as unknown as LayoutFieldPlacementDto & { sectionId: string })
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
        if (relatedLists.length > 0) {
          const uniqueCollIds = [
            ...new Set(relatedLists.map((rl) => rl.relatedCollectionId).filter(Boolean)),
          ]
          const uniqueFieldIds = [
            ...new Set(relatedLists.map((rl) => rl.relationshipFieldId).filter(Boolean)),
          ]

          // Fetch names in parallel
          const [collResults, fieldResults] = await Promise.all([
            Promise.all(
              uniqueCollIds.map(async (cid) => {
                try {
                  const c = await apiClient.getOne<{ id: string; name: string }>(
                    `/api/collections/${cid}`
                  )
                  return { id: cid, name: c?.name }
                } catch {
                  return { id: cid, name: undefined }
                }
              })
            ),
            Promise.all(
              uniqueFieldIds.map(async (fid) => {
                try {
                  const f = await apiClient.getOne<{ id: string; name: string }>(
                    `/api/fields/${fid}`
                  )
                  return { id: fid, name: f?.name }
                } catch {
                  return { id: fid, name: undefined }
                }
              })
            ),
          ])

          const collNameMap = new Map(collResults.map((r) => [r.id, r.name]))
          const fieldNameMap = new Map(fieldResults.map((r) => [r.id, r.name]))

          for (const rl of relatedLists) {
            rl.relatedCollectionName =
              rl.relatedCollectionName || collNameMap.get(rl.relatedCollectionId) || ''
            rl.relationshipFieldName =
              rl.relationshipFieldName || fieldNameMap.get(rl.relationshipFieldId) || ''
          }
        }

        const result: PageLayoutDto = {
          ...(flatLayout as unknown as Omit<PageLayoutDto, 'sections' | 'relatedLists'>),
          sections,
          relatedLists,
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
    staleTime: 5 * 60 * 1000, // Layouts rarely change; cache for 5 min
  })

  return {
    layout,
    isLoading,
    error: error ?? null,
  }
}
