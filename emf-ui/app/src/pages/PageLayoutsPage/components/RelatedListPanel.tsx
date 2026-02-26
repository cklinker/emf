/**
 * RelatedListPanel Component
 *
 * Panel for managing related lists in the layout editor.
 * Shows configured related lists with edit/delete, plus a form
 * to add or edit related lists with dropdown selectors for
 * collections, relationship fields, display columns, and sort fields.
 */

import React, { useState, useCallback, useMemo } from 'react'
import { Pencil, X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../../context/ApiContext'
import { useCollectionSummaries } from '../../../hooks/useCollectionSummaries'
import { useLayoutEditor, type EditorRelatedList } from './LayoutEditorContext'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface CollectionFieldDetail {
  id: string
  name: string
  displayName: string
  type: string
  required: boolean
  referenceTarget?: string
  referenceCollectionId?: string
}

interface CollectionDetailResponse {
  id: string
  name: string
  displayName: string
  fields: CollectionFieldDetail[]
}

interface RelatedListFormData {
  relatedCollectionId: string
  relationshipFieldId: string
  selectedDisplayColumns: string[]
  sortField: string
  sortDirection: string
  rowLimit: number
}

const INITIAL_FORM_DATA: RelatedListFormData = {
  relatedCollectionId: '',
  relationshipFieldId: '',
  selectedDisplayColumns: [],
  sortField: '',
  sortDirection: 'ASC',
  rowLimit: 10,
}

const REFERENCE_FIELD_TYPES = new Set(['reference', 'master_detail', 'lookup'])

function isReferenceField(type: string): boolean {
  return REFERENCE_FIELD_TYPES.has(type.toLowerCase())
}

// ---------------------------------------------------------------------------
// Shared input class
// ---------------------------------------------------------------------------

const inputClass =
  'rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15'

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function RelatedListPanel(): React.ReactElement {
  const { state, addRelatedList, updateRelatedList, removeRelatedList } = useLayoutEditor()
  const { relatedLists, collectionId } = state
  const { apiClient } = useApi()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [formData, setFormData] = useState<RelatedListFormData>(INITIAL_FORM_DATA)

  // Fetch all collections for the dropdown
  const { summaries: collections } = useCollectionSummaries()

  // Build a map of collection id -> display name for display
  const collectionNameMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const col of collections) {
      map.set(col.id, col.displayName || col.name)
    }
    return map
  }, [collections])

  // Look up collection names by ID for reference-target matching
  const collectionNameById = useMemo(() => {
    const map = new Map<string, string>()
    for (const col of collections) {
      map.set(col.id, col.name)
    }
    return map
  }, [collections])

  const currentCollectionName = collectionId ? (collectionNameById.get(collectionId) ?? null) : null

  // Fetch the current layout collection's fields (to find master-side relationships).
  // Fields are separate records fetched via ?include=fields.
  const { data: currentCollectionDetail } = useQuery({
    queryKey: ['collection-detail-for-rl', collectionId],
    queryFn: async () => {
      const raw = await apiClient.get<{
        data: { type: string; id: string; attributes: Record<string, unknown> }
        included?: { type: string; id: string; attributes: Record<string, unknown> }[]
      }>(`/api/collections/${collectionId}?include=fields`)

      const result: CollectionDetailResponse = {
        id: raw.data.id,
        name: raw.data.attributes.name as string,
        displayName: raw.data.attributes.displayName as string,
        fields: [],
      }

      if (raw.included && raw.included.length > 0) {
        result.fields = raw.included
          .filter((r) => r.type === 'fields')
          .map((r) => ({ id: r.id, ...r.attributes }) as unknown as CollectionFieldDetail)
      }

      return result
    },
    enabled: !!collectionId,
  })

  const currentCollectionFields = useMemo<CollectionFieldDetail[]>(
    () => currentCollectionDetail?.fields ?? [],
    [currentCollectionDetail]
  )

  // Fetch fields for the selected related collection
  const selectedCollectionId = formData.relatedCollectionId
  const { data: relatedCollectionDetail } = useQuery({
    queryKey: ['collection-detail-for-rl', selectedCollectionId],
    queryFn: async () => {
      const raw = await apiClient.get<{
        data: { type: string; id: string; attributes: Record<string, unknown> }
        included?: { type: string; id: string; attributes: Record<string, unknown> }[]
      }>(`/api/collections/${selectedCollectionId}?include=fields`)

      const result: CollectionDetailResponse = {
        id: raw.data.id,
        name: raw.data.attributes.name as string,
        displayName: raw.data.attributes.displayName as string,
        fields: [],
      }

      if (raw.included && raw.included.length > 0) {
        result.fields = raw.included
          .filter((r) => r.type === 'fields')
          .map((r) => ({ id: r.id, ...r.attributes }) as unknown as CollectionFieldDetail)
      }

      return result
    },
    enabled: !!selectedCollectionId,
  })

  const relatedCollectionFields = useMemo<CollectionFieldDetail[]>(
    () => relatedCollectionDetail?.fields ?? [],
    [relatedCollectionDetail]
  )

  // Look up selected related collection name for reference-target matching
  const selectedCollectionName = selectedCollectionId
    ? (collectionNameById.get(selectedCollectionId) ?? null)
    : null

  // Relationship fields from BOTH sides:
  // 1. Fields on the related collection that point to the current collection (detail → master)
  // 2. Fields on the current collection that point to the related collection (master → detail)
  interface RelationshipFieldOption {
    id: string
    label: string
    source: 'related' | 'current'
  }

  const relationshipFieldOptions = useMemo<RelationshipFieldOption[]>(() => {
    if (!selectedCollectionId) return []
    const options: RelationshipFieldOption[] = []

    // Fields on the related collection pointing to the current collection
    for (const f of relatedCollectionFields) {
      if (
        isReferenceField(f.type) &&
        (f.referenceTarget === currentCollectionName || f.referenceCollectionId === collectionId)
      ) {
        const relatedDisplayName =
          collectionNameMap.get(selectedCollectionId) || selectedCollectionName || 'Related'
        options.push({
          id: f.id,
          label: `${f.displayName || f.name} (on ${relatedDisplayName})`,
          source: 'related',
        })
      }
    }

    // Fields on the current collection pointing to the related collection
    for (const f of currentCollectionFields) {
      if (
        isReferenceField(f.type) &&
        (f.referenceTarget === selectedCollectionName ||
          f.referenceCollectionId === selectedCollectionId)
      ) {
        const currentDisplayName =
          collectionNameMap.get(collectionId!) || currentCollectionName || 'Current'
        options.push({
          id: f.id,
          label: `${f.displayName || f.name} (on ${currentDisplayName})`,
          source: 'current',
        })
      }
    }

    return options
  }, [
    selectedCollectionId,
    relatedCollectionFields,
    currentCollectionFields,
    currentCollectionName,
    selectedCollectionName,
    collectionId,
    collectionNameMap,
  ])

  // Non-reference fields for display columns and sort
  const displayableFields = useMemo(
    () => relatedCollectionFields.filter((f) => !isReferenceField(f.type)),
    [relatedCollectionFields]
  )

  // Build a map of field id -> display name (from both collections)
  const fieldNameMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const f of relatedCollectionFields) {
      map.set(f.id, f.displayName || f.name)
    }
    for (const f of currentCollectionFields) {
      if (!map.has(f.id)) {
        map.set(f.id, f.displayName || f.name)
      }
    }
    return map
  }, [relatedCollectionFields, currentCollectionFields])

  // ---------------------------------------------------------------------------
  // Handlers
  // ---------------------------------------------------------------------------

  const handleOpenForm = useCallback(() => {
    setEditingId(null)
    setFormData(INITIAL_FORM_DATA)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((rl: EditorRelatedList) => {
    setEditingId(rl.id)

    // Parse displayColumns: may be a JSON array string '["col1","col2"]' or CSV 'col1,col2'
    let parsedColumns: string[] = []
    if (rl.displayColumns) {
      const trimmed = rl.displayColumns.trim()
      if (trimmed.startsWith('[')) {
        try {
          const parsed = JSON.parse(trimmed) as unknown
          if (Array.isArray(parsed)) {
            parsedColumns = parsed.map(String).filter(Boolean)
          }
        } catch {
          // Fall back to CSV parsing if JSON parse fails
          parsedColumns = trimmed
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
        }
      } else {
        parsedColumns = trimmed
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      }
    }

    setFormData({
      relatedCollectionId: rl.relatedCollectionId,
      relationshipFieldId: rl.relationshipFieldId,
      selectedDisplayColumns: parsedColumns,
      sortField: rl.sortField ?? '',
      sortDirection: rl.sortDirection,
      rowLimit: rl.rowLimit,
    })
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingId(null)
    setFormData(INITIAL_FORM_DATA)
  }, [])

  const handleCollectionChange = useCallback((value: string) => {
    setFormData((prev) => ({
      ...prev,
      relatedCollectionId: value,
      relationshipFieldId: '',
      selectedDisplayColumns: [],
      sortField: '',
    }))
  }, [])

  const handleToggleDisplayColumn = useCallback((fieldName: string) => {
    setFormData((prev) => {
      const cols = prev.selectedDisplayColumns
      const next = cols.includes(fieldName)
        ? cols.filter((c) => c !== fieldName)
        : [...cols, fieldName]
      return { ...prev, selectedDisplayColumns: next }
    })
  }, [])

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (!formData.relatedCollectionId || !formData.relationshipFieldId) return

      // Store as JSON array string since the backend column is JSONB
      const displayColumns = JSON.stringify(formData.selectedDisplayColumns)

      if (editingId) {
        updateRelatedList(editingId, {
          relatedCollectionId: formData.relatedCollectionId,
          relationshipFieldId: formData.relationshipFieldId,
          displayColumns,
          sortField: formData.sortField || undefined,
          sortDirection: formData.sortDirection,
          rowLimit: formData.rowLimit,
        })
      } else {
        const maxSortOrder = relatedLists.reduce((max, r) => Math.max(max, r.sortOrder), -1)
        addRelatedList({
          relatedCollectionId: formData.relatedCollectionId,
          relationshipFieldId: formData.relationshipFieldId,
          displayColumns,
          sortField: formData.sortField || undefined,
          sortDirection: formData.sortDirection,
          rowLimit: formData.rowLimit,
          sortOrder: maxSortOrder + 1,
        })
      }
      handleCloseForm()
    },
    [formData, editingId, relatedLists, addRelatedList, updateRelatedList, handleCloseForm]
  )

  const handleDelete = useCallback(
    (relatedListId: string) => {
      removeRelatedList(relatedListId)
    },
    [removeRelatedList]
  )

  const sortedRelatedLists = useMemo(
    () => [...relatedLists].sort((a, b) => a.sortOrder - b.sortOrder),
    [relatedLists]
  )

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <div className="flex flex-col gap-3 px-4 py-3" data-testid="related-list-panel">
      <h3 className="m-0 text-sm font-semibold text-foreground">Related Lists</h3>

      {sortedRelatedLists.length === 0 ? (
        <div className="p-4 text-center text-[13px] text-muted-foreground">
          No related lists configured
        </div>
      ) : (
        sortedRelatedLists.map((rl) => (
          <div
            key={rl.id}
            className="flex items-center gap-2 rounded-md border border-border bg-background px-3 py-2 text-[13px]"
            data-testid={`related-list-${rl.id}`}
          >
            <div className="min-w-0 flex-1 overflow-hidden">
              <div className="overflow-hidden text-ellipsis whitespace-nowrap font-medium text-foreground">
                {collectionNameMap.get(rl.relatedCollectionId) || rl.relatedCollectionId}
              </div>
              <div className="text-[11px] text-muted-foreground">
                {fieldNameMap.get(rl.relationshipFieldId) || rl.relationshipFieldId} | Limit:{' '}
                {rl.rowLimit} | {rl.sortDirection}
              </div>
            </div>
            <button
              type="button"
              className="flex h-6 w-6 items-center justify-center rounded border-none bg-transparent p-0 text-muted-foreground cursor-pointer transition-colors duration-150 hover:bg-primary/10 hover:text-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 motion-reduce:transition-none"
              onClick={() => handleEdit(rl)}
              aria-label="Edit related list"
              title="Edit related list"
              data-testid={`related-list-edit-${rl.id}`}
            >
              <Pencil size={14} />
            </button>
            <button
              type="button"
              className="flex h-6 w-6 items-center justify-center rounded border-none bg-transparent p-0 text-muted-foreground cursor-pointer transition-colors duration-150 hover:bg-destructive/10 hover:text-destructive focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 motion-reduce:transition-none"
              onClick={() => handleDelete(rl.id)}
              aria-label="Delete related list"
              title="Delete related list"
              data-testid={`related-list-delete-${rl.id}`}
            >
              <X size={14} />
            </button>
          </div>
        ))
      )}

      <button
        type="button"
        className="w-full rounded-md border border-dashed border-input bg-background p-2 text-[13px] text-muted-foreground cursor-pointer transition-colors duration-150 hover:border-primary hover:text-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 motion-reduce:transition-none"
        onClick={handleOpenForm}
        data-testid="add-related-list-button"
      >
        + Add Related List
      </button>

      {isFormOpen && (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4"
          onClick={(e) => e.target === e.currentTarget && handleCloseForm()}
          role="presentation"
        >
          <div
            className="max-h-[90vh] w-full max-w-[520px] overflow-y-auto rounded-lg bg-background p-6 shadow-xl"
            role="dialog"
            aria-modal="true"
            aria-label={editingId ? 'Edit Related List' : 'Add Related List'}
          >
            <h4 className="mb-4 text-base font-semibold text-foreground">
              {editingId ? 'Edit Related List' : 'Add Related List'}
            </h4>
            <form onSubmit={handleSubmit} noValidate>
              {/* Related Collection */}
              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-collection"
                >
                  Related Collection
                </label>
                <select
                  id="rl-collection"
                  className={inputClass}
                  value={formData.relatedCollectionId}
                  onChange={(e) => handleCollectionChange(e.target.value)}
                  data-testid="rl-form-collection"
                >
                  <option value="">Select a collection...</option>
                  {collections
                    .filter((c) => c.id !== collectionId)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.displayName || c.name}
                      </option>
                    ))}
                </select>
              </div>

              {/* Relationship Field */}
              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-relationship"
                >
                  Relationship Field
                </label>
                <select
                  id="rl-relationship"
                  className={inputClass}
                  value={formData.relationshipFieldId}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, relationshipFieldId: e.target.value }))
                  }
                  disabled={!formData.relatedCollectionId}
                  data-testid="rl-form-relationship"
                >
                  <option value="">
                    {!formData.relatedCollectionId
                      ? 'Select a collection first'
                      : relationshipFieldOptions.length === 0
                        ? 'No relationship fields found'
                        : 'Select a relationship field...'}
                  </option>
                  {relationshipFieldOptions.map((opt) => (
                    <option key={opt.id} value={opt.id}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Display Columns */}
              <fieldset className="mb-3 flex flex-col gap-1 border-none p-0 m-0">
                <legend className="text-xs font-medium text-muted-foreground">
                  Display Columns
                </legend>
                {!formData.relatedCollectionId ? (
                  <div className="text-[12px] text-muted-foreground">Select a collection first</div>
                ) : displayableFields.length === 0 ? (
                  <div className="text-[12px] text-muted-foreground">No fields available</div>
                ) : (
                  <div
                    className="flex max-h-[160px] flex-col gap-1 overflow-y-auto rounded-md border border-input bg-background p-2"
                    data-testid="rl-form-display-columns"
                  >
                    {displayableFields.map((f) => (
                      <label
                        key={f.id}
                        className="flex items-center gap-2 rounded px-1.5 py-1 text-[13px] text-foreground cursor-pointer hover:bg-muted"
                      >
                        <input
                          type="checkbox"
                          className="h-3.5 w-3.5 accent-primary"
                          checked={formData.selectedDisplayColumns.includes(f.name)}
                          onChange={() => handleToggleDisplayColumn(f.name)}
                        />
                        <span className="overflow-hidden text-ellipsis whitespace-nowrap">
                          {f.displayName || f.name}
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </fieldset>

              {/* Sort Field */}
              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-sort-field"
                >
                  Sort Field
                </label>
                <select
                  id="rl-sort-field"
                  className={inputClass}
                  value={formData.sortField}
                  onChange={(e) => setFormData((prev) => ({ ...prev, sortField: e.target.value }))}
                  disabled={!formData.relatedCollectionId}
                  data-testid="rl-form-sort-field"
                >
                  <option value="">None (default order)</option>
                  {displayableFields.map((f) => (
                    <option key={f.id} value={f.name}>
                      {f.displayName || f.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Sort Direction */}
              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-sort-direction"
                >
                  Sort Direction
                </label>
                <select
                  id="rl-sort-direction"
                  className={inputClass}
                  value={formData.sortDirection}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, sortDirection: e.target.value }))
                  }
                  data-testid="rl-form-sort-direction"
                >
                  <option value="ASC">Ascending</option>
                  <option value="DESC">Descending</option>
                </select>
              </div>

              {/* Row Limit */}
              <div className="mb-3 flex flex-col gap-1">
                <label className="text-xs font-medium text-muted-foreground" htmlFor="rl-row-limit">
                  Row Limit
                </label>
                <input
                  id="rl-row-limit"
                  type="number"
                  className={inputClass}
                  value={formData.rowLimit}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      rowLimit: parseInt(e.target.value, 10) || 10,
                    }))
                  }
                  min={1}
                  max={100}
                  data-testid="rl-form-row-limit"
                />
              </div>

              {/* Actions */}
              <div className="mt-4 flex justify-end gap-2">
                <button
                  type="button"
                  className="rounded-md border border-input bg-background px-3 py-1.5 text-[13px] text-foreground cursor-pointer hover:bg-muted"
                  onClick={handleCloseForm}
                  data-testid="rl-form-cancel"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="rounded-md border-none bg-primary px-3 py-1.5 text-[13px] font-medium text-primary-foreground cursor-pointer hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={!formData.relatedCollectionId || !formData.relationshipFieldId}
                  data-testid="rl-form-submit"
                >
                  {editingId ? 'Save' : 'Add'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
