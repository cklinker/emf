/**
 * RelatedListPanel Component
 *
 * Panel for managing related lists in the layout editor.
 * Shows configured related lists with sort order and delete,
 * plus a form to add new related lists.
 */

import React, { useState, useCallback } from 'react'
import { X } from 'lucide-react'
import { useLayoutEditor, type EditorRelatedList } from './LayoutEditorContext'

interface RelatedListFormData {
  relatedCollectionId: string
  relationshipFieldId: string
  displayColumns: string
  sortField: string
  sortDirection: string
  rowLimit: number
}

const INITIAL_FORM_DATA: RelatedListFormData = {
  relatedCollectionId: '',
  relationshipFieldId: '',
  displayColumns: '',
  sortField: '',
  sortDirection: 'ASC',
  rowLimit: 10,
}

export function RelatedListPanel(): React.ReactElement {
  const { state, addRelatedList, removeRelatedList } = useLayoutEditor()
  const { relatedLists } = state

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [formData, setFormData] = useState<RelatedListFormData>(INITIAL_FORM_DATA)

  const handleOpenForm = useCallback(() => {
    setFormData(INITIAL_FORM_DATA)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setFormData(INITIAL_FORM_DATA)
  }, [])

  const handleFormChange = useCallback(
    (field: keyof RelatedListFormData, value: string | number) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
    },
    []
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (!formData.relatedCollectionId || !formData.relationshipFieldId) return

      const maxSortOrder = relatedLists.reduce((max, r) => Math.max(max, r.sortOrder), -1)

      const newRelatedList: Omit<EditorRelatedList, 'id'> = {
        relatedCollectionId: formData.relatedCollectionId,
        relationshipFieldId: formData.relationshipFieldId,
        displayColumns: formData.displayColumns,
        sortField: formData.sortField || undefined,
        sortDirection: formData.sortDirection,
        rowLimit: formData.rowLimit,
        sortOrder: maxSortOrder + 1,
      }

      addRelatedList(newRelatedList)
      handleCloseForm()
    },
    [formData, relatedLists, addRelatedList, handleCloseForm]
  )

  const handleDelete = useCallback(
    (relatedListId: string) => {
      removeRelatedList(relatedListId)
    },
    [removeRelatedList]
  )

  const sortedRelatedLists = [...relatedLists].sort((a, b) => a.sortOrder - b.sortOrder)

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
                {rl.relatedCollectionId}
              </div>
              <div className="text-[11px] text-muted-foreground">
                Field: {rl.relationshipFieldId} | Limit: {rl.rowLimit} | {rl.sortDirection}
              </div>
            </div>
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
            className="max-h-[90vh] w-full max-w-[480px] overflow-y-auto rounded-lg bg-background p-6 shadow-xl"
            role="dialog"
            aria-modal="true"
            aria-label="Add Related List"
          >
            <h4 className="mb-4 text-base font-semibold text-foreground">Add Related List</h4>
            <form onSubmit={handleSubmit} noValidate>
              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-collection"
                >
                  Related Collection ID
                </label>
                <input
                  id="rl-collection"
                  type="text"
                  className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                  value={formData.relatedCollectionId}
                  onChange={(e) => handleFormChange('relatedCollectionId', e.target.value)}
                  placeholder="Collection ID"
                  required
                  data-testid="rl-form-collection"
                />
              </div>

              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-relationship"
                >
                  Relationship Field ID
                </label>
                <input
                  id="rl-relationship"
                  type="text"
                  className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                  value={formData.relationshipFieldId}
                  onChange={(e) => handleFormChange('relationshipFieldId', e.target.value)}
                  placeholder="Relationship field ID"
                  required
                  data-testid="rl-form-relationship"
                />
              </div>

              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-display-columns"
                >
                  Display Columns
                </label>
                <input
                  id="rl-display-columns"
                  type="text"
                  className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                  value={formData.displayColumns}
                  onChange={(e) => handleFormChange('displayColumns', e.target.value)}
                  placeholder="Comma-separated column names"
                  data-testid="rl-form-display-columns"
                />
              </div>

              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-sort-field"
                >
                  Sort Field
                </label>
                <input
                  id="rl-sort-field"
                  type="text"
                  className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                  value={formData.sortField}
                  onChange={(e) => handleFormChange('sortField', e.target.value)}
                  placeholder="Sort field name"
                  data-testid="rl-form-sort-field"
                />
              </div>

              <div className="mb-3 flex flex-col gap-1">
                <label
                  className="text-xs font-medium text-muted-foreground"
                  htmlFor="rl-sort-direction"
                >
                  Sort Direction
                </label>
                <select
                  id="rl-sort-direction"
                  className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                  value={formData.sortDirection}
                  onChange={(e) => handleFormChange('sortDirection', e.target.value)}
                  data-testid="rl-form-sort-direction"
                >
                  <option value="ASC">Ascending</option>
                  <option value="DESC">Descending</option>
                </select>
              </div>

              <div className="mb-3 flex flex-col gap-1">
                <label className="text-xs font-medium text-muted-foreground" htmlFor="rl-row-limit">
                  Row Limit
                </label>
                <input
                  id="rl-row-limit"
                  type="number"
                  className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
                  value={formData.rowLimit}
                  onChange={(e) => handleFormChange('rowLimit', parseInt(e.target.value, 10) || 10)}
                  min={1}
                  max={100}
                  data-testid="rl-form-row-limit"
                />
              </div>

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
                  className="rounded-md border-none bg-primary px-3 py-1.5 text-[13px] font-medium text-primary-foreground cursor-pointer hover:bg-primary/90"
                  disabled={!formData.relatedCollectionId || !formData.relationshipFieldId}
                  data-testid="rl-form-submit"
                >
                  Add
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
