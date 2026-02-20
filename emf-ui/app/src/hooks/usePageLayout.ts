/**
 * usePageLayout Hook
 *
 * Resolves the page layout for a given collection and user.
 * Calls GET /control/layouts/resolve to determine which layout
 * (if any) should be used for rendering the record detail view.
 *
 * Returns null when no layout is configured (204) or on error,
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
        const result = await apiClient.get<PageLayoutDto>(
          `/control/layouts/resolve?collectionId=${collectionId}&profileId=${profileId}`
        )
        // If the response has no sections, treat it as no layout
        if (!result || !result.sections) {
          return null
        }
        return result
      } catch {
        // 204 No Content or any error means no layout configured
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
