/**
 * TypeScript type definitions for Page Layouts.
 *
 * These types correspond to the backend entities:
 *   - PageLayout → PageLayoutDetail
 *   - LayoutSection → LayoutSection
 *   - LayoutField → LayoutFieldPlacement
 *   - LayoutRelatedList → LayoutRelatedList
 *
 * The save request types match CreatePageLayoutRequest.java and its nested DTOs.
 */

// ─── Response types (from GET /control/layouts/{id}) ─────────────────────────

export interface LayoutFieldPlacement {
  id?: string
  fieldId: string
  fieldName?: string
  columnNumber: number
  sortOrder: number
  requiredOnLayout: boolean
  readOnlyOnLayout: boolean
}

export interface LayoutSection {
  id?: string
  heading: string
  columns: number
  sortOrder: number
  collapsed: boolean
  style: string
  fields: LayoutFieldPlacement[]
}

export interface LayoutRelatedList {
  id?: string
  relatedCollectionId: string
  relationshipFieldId: string
  displayColumns: string
  sortField: string
  sortDirection: string
  rowLimit: number
  sortOrder: number
}

export interface PageLayoutSummary {
  id: string
  name: string
  description: string | null
  layoutType: string
  collectionId: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export interface PageLayoutDetail extends PageLayoutSummary {
  sections: LayoutSection[]
  relatedLists: LayoutRelatedList[]
}

// ─── Request types (for POST/PUT /control/layouts) ───────────────────────────

export interface FieldPlacementRequest {
  fieldId: string
  columnNumber: number
  sortOrder: number
  requiredOnLayout: boolean
  readOnlyOnLayout: boolean
}

export interface SectionRequest {
  heading: string
  columns: number
  sortOrder: number
  collapsed: boolean
  style: string
  fields: FieldPlacementRequest[]
}

export interface RelatedListRequest {
  relatedCollectionId: string
  relationshipFieldId: string
  displayColumns: string
  sortField: string
  sortDirection: string
  rowLimit: number
  sortOrder: number
}

export interface PageLayoutSaveRequest {
  name: string
  description?: string
  layoutType: string
  isDefault: boolean
  sections: SectionRequest[]
  relatedLists: RelatedListRequest[]
}

// ─── Editor state types ──────────────────────────────────────────────────────

export type LayoutViewMode = 'list' | 'viewer' | 'editor'

export type SelectedItemType = 'section' | 'field' | 'relatedList'

export interface SelectedItem {
  type: SelectedItemType
  sectionIndex?: number
  fieldIndex?: number
  relatedListIndex?: number
}

export interface DragData {
  type: 'palette-field' | 'layout-field' | 'section' | 'related-list'
  fieldId?: string
  fieldName?: string
  sectionIndex?: number
  fieldIndex?: number
  relatedListIndex?: number
}

export const LAYOUT_TYPES = ['DETAIL', 'EDIT', 'MINI', 'LIST'] as const
export type LayoutType = (typeof LAYOUT_TYPES)[number]

export const SECTION_STYLES = ['DEFAULT', 'CARD', 'COMPACT'] as const
export type SectionStyle = (typeof SECTION_STYLES)[number]

export const DRAG_TYPES = {
  PALETTE_FIELD: 'application/emf-palette-field',
  LAYOUT_FIELD: 'application/emf-layout-field',
  SECTION: 'application/emf-section',
  RELATED_LIST: 'application/emf-related-list',
} as const
