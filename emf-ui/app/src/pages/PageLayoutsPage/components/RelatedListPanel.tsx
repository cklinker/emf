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
import styles from './RelatedListPanel.module.css'

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
    <div className={styles.relatedListPanel} data-testid="related-list-panel">
      <h3 className={styles.relatedListTitle}>Related Lists</h3>

      {sortedRelatedLists.length === 0 ? (
        <div className={styles.emptyState}>No related lists configured</div>
      ) : (
        sortedRelatedLists.map((rl) => (
          <div key={rl.id} className={styles.relatedListItem} data-testid={`related-list-${rl.id}`}>
            <div className={styles.relatedListInfo}>
              <div className={styles.relatedListName}>{rl.relatedCollectionId}</div>
              <div className={styles.relatedListMeta}>
                Field: {rl.relationshipFieldId} | Limit: {rl.rowLimit} | {rl.sortDirection}
              </div>
            </div>
            <button
              type="button"
              className={styles.relatedListDeleteButton}
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
        className={styles.addRelatedListButton}
        onClick={handleOpenForm}
        data-testid="add-related-list-button"
      >
        + Add Related List
      </button>

      {isFormOpen && (
        <div
          className={styles.formOverlay}
          onClick={(e) => e.target === e.currentTarget && handleCloseForm()}
          role="presentation"
        >
          <div
            className={styles.formModal}
            role="dialog"
            aria-modal="true"
            aria-label="Add Related List"
          >
            <h4 className={styles.formTitle}>Add Related List</h4>
            <form onSubmit={handleSubmit} noValidate>
              <div className={styles.formGroup}>
                <label className={styles.formLabel} htmlFor="rl-collection">
                  Related Collection ID
                </label>
                <input
                  id="rl-collection"
                  type="text"
                  className={styles.formInput}
                  value={formData.relatedCollectionId}
                  onChange={(e) => handleFormChange('relatedCollectionId', e.target.value)}
                  placeholder="Collection ID"
                  required
                  data-testid="rl-form-collection"
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.formLabel} htmlFor="rl-relationship">
                  Relationship Field ID
                </label>
                <input
                  id="rl-relationship"
                  type="text"
                  className={styles.formInput}
                  value={formData.relationshipFieldId}
                  onChange={(e) => handleFormChange('relationshipFieldId', e.target.value)}
                  placeholder="Relationship field ID"
                  required
                  data-testid="rl-form-relationship"
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.formLabel} htmlFor="rl-display-columns">
                  Display Columns
                </label>
                <input
                  id="rl-display-columns"
                  type="text"
                  className={styles.formInput}
                  value={formData.displayColumns}
                  onChange={(e) => handleFormChange('displayColumns', e.target.value)}
                  placeholder="Comma-separated column names"
                  data-testid="rl-form-display-columns"
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.formLabel} htmlFor="rl-sort-field">
                  Sort Field
                </label>
                <input
                  id="rl-sort-field"
                  type="text"
                  className={styles.formInput}
                  value={formData.sortField}
                  onChange={(e) => handleFormChange('sortField', e.target.value)}
                  placeholder="Sort field name"
                  data-testid="rl-form-sort-field"
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.formLabel} htmlFor="rl-sort-direction">
                  Sort Direction
                </label>
                <select
                  id="rl-sort-direction"
                  className={styles.formSelect}
                  value={formData.sortDirection}
                  onChange={(e) => handleFormChange('sortDirection', e.target.value)}
                  data-testid="rl-form-sort-direction"
                >
                  <option value="ASC">Ascending</option>
                  <option value="DESC">Descending</option>
                </select>
              </div>

              <div className={styles.formGroup}>
                <label className={styles.formLabel} htmlFor="rl-row-limit">
                  Row Limit
                </label>
                <input
                  id="rl-row-limit"
                  type="number"
                  className={styles.formInput}
                  value={formData.rowLimit}
                  onChange={(e) => handleFormChange('rowLimit', parseInt(e.target.value, 10) || 10)}
                  min={1}
                  max={100}
                  data-testid="rl-form-row-limit"
                />
              </div>

              <div className={styles.formActions}>
                <button
                  type="button"
                  className={styles.cancelButton}
                  onClick={handleCloseForm}
                  data-testid="rl-form-cancel"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className={styles.submitButton}
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
