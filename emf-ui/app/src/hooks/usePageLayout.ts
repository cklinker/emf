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
        // Step 1: Find layout assignment for this collection + profile
        const assignments = await apiClient.getList<{
          id: string
          collectionId: string
          profileId: string
          layoutId: string
        }>(
          `/api/layout-assignments?filter[collectionId][eq]=${collectionId}&filter[profileId][eq]=${profileId}&page[size]=1`
        )

        // Try default assignment if no profile-specific one found
        let layoutId: string | undefined
        if (assignments.length > 0) {
          layoutId = assignments[0].layoutId
        } else {
          // Fall back to default layout for this collection
          const defaultAssignments = await apiClient.getList<{
            id: string
            collectionId: string
            layoutId: string
            isDefault: boolean
          }>(`/api/layout-assignments?filter[collectionId][eq]=${collectionId}&page[size]=10`)
          const defaultAssignment = defaultAssignments.find((a) => a.isDefault)
          if (defaultAssignment) {
            layoutId = defaultAssignment.layoutId
          }
        }

        if (!layoutId) return null

        // Step 2: Fetch the layout itself
        const result = await apiClient.getOne<PageLayoutDto>(`/api/page-layouts/${layoutId}`)
        if (!result || !result.sections) {
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
