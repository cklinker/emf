import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'

import styles from './ListViewsPage.module.css'

interface CollectionSummary {
  id: string
  name: string
  displayName: string
}

interface ListView {
  id: string
  name: string
  collectionId: string
  visibility: string
  sortField: string
  sortDirection: string
  createdBy: string
  createdAt: string
  updatedAt: string
  columns: string[]
  filters: Record<string, unknown>[]
}

interface ListViewFormData {
  name: string
  collectionId: string
  visibility: string
  columns: string
  filters: string
  sortField: string
  sortDirection: string
}

interface FormErrors {
  name?: string
  collectionId?: string
  columns?: string
  filters?: string
}

export interface ListViewsPageProps {
  testId?: string
}

function validateForm(data: ListViewFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or less'
  }
  if (!data.collectionId) {
    errors.collectionId = 'Collection is required'
  }
  if (data.columns.trim()) {
    try {
      JSON.parse(data.columns)
    } catch {
      errors.columns = 'Columns must be valid JSON'
    }
  }
  if (data.filters.trim()) {
    try {
      JSON.parse(data.filters)
    } catch {
      errors.filters = 'Filters must be valid JSON'
    }
  }
  return errors
}

interface ListViewFormProps {
  listView?: ListView
  collections: CollectionSummary[]
  onSubmit: (data: ListViewFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ListViewForm({
  listView,
  collections,
  onSubmit,
  onCancel,
  isSubmitting,
}: ListViewFormProps): React.ReactElement {
  const isEditing = !!listView
  const [formData, setFormData] = useState<ListViewFormData>({
    name: listView?.name ?? '',
    collectionId: listView?.collectionId ?? '',
    visibility: listView?.visibility ?? 'PRIVATE',
    columns: listView?.columns ? JSON.stringify(listView.columns, null, 2) : '',
    filters: listView?.filters ? JSON.stringify(listView.filters, null, 2) : '',
    sortField: listView?.sortField ?? '',
    sortDirection: listView?.sortDirection ?? 'ASC',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof ListViewFormData, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof FormErrors) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData)
      if (validationErrors[field]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }))
      }
    },
    [formData]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData)
      setErrors(validationErrors)
      setTouched({ name: true, collectionId: true, columns: true, filters: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? 'Edit List View' : 'Create List View'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="listview-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="listview-form-title"
        data-testid="listview-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="listview-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="listview-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="listview-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="listview-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter list view name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="listview-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="listview-collectionId" className={styles.formLabel}>
                Collection
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="listview-collectionId"
                className={`${styles.formInput} ${touched.collectionId && errors.collectionId ? styles.hasError : ''}`}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                aria-required="true"
                aria-invalid={touched.collectionId && !!errors.collectionId}
                disabled={isSubmitting || isEditing}
                data-testid="listview-collectionId-input"
              >
                <option value="">Select a collection</option>
                {collections.map((col) => (
                  <option key={col.id} value={col.id}>
                    {col.displayName || col.name}
                  </option>
                ))}
              </select>
              {touched.collectionId && errors.collectionId && (
                <span className={styles.formError} role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="listview-visibility" className={styles.formLabel}>
                Visibility
              </label>
              <select
                id="listview-visibility"
                className={`${styles.formInput} ${styles.formSelect}`}
                value={formData.visibility}
                onChange={(e) => handleChange('visibility', e.target.value)}
                disabled={isSubmitting}
                data-testid="listview-visibility-input"
              >
                <option value="PRIVATE">PRIVATE</option>
                <option value="PUBLIC">PUBLIC</option>
                <option value="GROUP">GROUP</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="listview-columns" className={styles.formLabel}>
                Columns
              </label>
              <textarea
                id="listview-columns"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.columns && errors.columns ? styles.hasError : ''}`}
                value={formData.columns}
                onChange={(e) => handleChange('columns', e.target.value)}
                onBlur={() => handleBlur('columns')}
                placeholder='["field1", "field2"]'
                disabled={isSubmitting}
                rows={3}
                data-testid="listview-columns-input"
              />
              <span className={styles.formHint}>JSON array of column field names</span>
              {touched.columns && errors.columns && (
                <span className={styles.formError} role="alert">
                  {errors.columns}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="listview-filters" className={styles.formLabel}>
                Filters
              </label>
              <textarea
                id="listview-filters"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.filters && errors.filters ? styles.hasError : ''}`}
                value={formData.filters}
                onChange={(e) => handleChange('filters', e.target.value)}
                onBlur={() => handleBlur('filters')}
                placeholder='[{"field": "status", "op": "eq", "value": "active"}]'
                disabled={isSubmitting}
                rows={3}
                data-testid="listview-filters-input"
              />
              <span className={styles.formHint}>JSON array of filter objects</span>
              {touched.filters && errors.filters && (
                <span className={styles.formError} role="alert">
                  {errors.filters}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="listview-sortField" className={styles.formLabel}>
                Sort Field
              </label>
              <input
                id="listview-sortField"
                type="text"
                className={styles.formInput}
                value={formData.sortField}
                onChange={(e) => handleChange('sortField', e.target.value)}
                placeholder="Enter sort field name"
                disabled={isSubmitting}
                data-testid="listview-sortField-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="listview-sortDirection" className={styles.formLabel}>
                Sort Direction
              </label>
              <select
                id="listview-sortDirection"
                className={`${styles.formInput} ${styles.formSelect}`}
                value={formData.sortDirection}
                onChange={(e) => handleChange('sortDirection', e.target.value)}
                disabled={isSubmitting}
                data-testid="listview-sortDirection-input"
              >
                <option value="ASC">ASC</option>
                <option value="DESC">DESC</option>
              </select>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="listview-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="listview-form-submit"
              >
                {isSubmitting ? 'Saving...' : 'Save'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function ListViewsPage({
  testId = 'listviews-page',
}: ListViewsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingListView, setEditingListView] = useState<ListView | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [listViewToDelete, setListViewToDelete] = useState<ListView | null>(null)

  const {
    data: listViews,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['listviews'],
    queryFn: () => apiClient.get<ListView[]>(`/control/listviews`),
  })

  const { data: collectionsData } = useQuery({
    queryKey: ['collections-for-listviews'],
    queryFn: () =>
      apiClient.get<{ content: CollectionSummary[] }>('/control/collections?size=1000'),
  })

  const collections = useMemo<CollectionSummary[]>(
    () => collectionsData?.content ?? [],
    [collectionsData]
  )

  const collectionMap = useMemo(() => {
    const map = new Map<string, CollectionSummary>()
    for (const col of collections) {
      map.set(col.id, col)
    }
    return map
  }, [collections])

  const listViewList: ListView[] = listViews ?? []

  const createMutation = useMutation({
    mutationFn: (data: ListViewFormData) => {
      const payload = {
        name: data.name,
        visibility: data.visibility,
        columns: data.columns.trim() || '[]',
        filters: data.filters.trim() || '[]',
        sortField: data.sortField,
        sortDirection: data.sortDirection,
      }
      return apiClient.post<ListView>(
        `/control/listviews?collectionId=${encodeURIComponent(data.collectionId)}&userId=system`,
        payload
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['listviews'] })
      showToast('List view created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ListViewFormData }) => {
      const payload = {
        name: data.name,
        visibility: data.visibility,
        columns: data.columns.trim() || '[]',
        filters: data.filters.trim() || '[]',
        sortField: data.sortField,
        sortDirection: data.sortDirection,
      }
      return apiClient.put<ListView>(`/control/listviews/${id}`, payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['listviews'] })
      showToast('List view updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/listviews/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['listviews'] })
      showToast('List view deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setListViewToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingListView(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((lv: ListView) => {
    setEditingListView(lv)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingListView(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ListViewFormData) => {
      if (editingListView) {
        updateMutation.mutate({ id: editingListView.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingListView, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((lv: ListView) => {
    setListViewToDelete(lv)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (listViewToDelete) {
      deleteMutation.mutate(listViewToDelete.id)
    }
  }, [listViewToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setListViewToDelete(null)
  }, [])

  const getCollectionName = useCallback(
    (collectionId: string): string => {
      const col = collectionMap.get(collectionId)
      return col ? col.displayName || col.name : collectionId
    },
    [collectionMap]
  )

  const getVisibilityBadgeClass = (visibility: string): string => {
    switch (visibility) {
      case 'PUBLIC':
        return styles.badgePublic
      case 'GROUP':
        return styles.badgeGroup
      default:
        return styles.badgePrivate
    }
  }

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>List Views</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create List View"
          data-testid="add-listview-button"
        >
          Create List View
        </button>
      </header>

      {listViewList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No list views found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="List Views"
            data-testid="listviews-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Collection
                </th>
                <th role="columnheader" scope="col">
                  Visibility
                </th>
                <th role="columnheader" scope="col">
                  Created By
                </th>
                <th role="columnheader" scope="col">
                  Created
                </th>
                <th role="columnheader" scope="col">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {listViewList.map((lv, index) => (
                <tr
                  key={lv.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`listview-row-${index}`}
                >
                  <td role="gridcell">{lv.name}</td>
                  <td role="gridcell">{getCollectionName(lv.collectionId)}</td>
                  <td role="gridcell">
                    <span className={`${styles.badge} ${getVisibilityBadgeClass(lv.visibility)}`}>
                      {lv.visibility}
                    </span>
                  </td>
                  <td role="gridcell">{lv.createdBy}</td>
                  <td role="gridcell">
                    {formatDate(new Date(lv.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(lv)}
                        aria-label={`Edit ${lv.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(lv)}
                        aria-label={`Delete ${lv.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <ListViewForm
          listView={editingListView}
          collections={collections}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete List View"
        message="Are you sure you want to delete this list view? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ListViewsPage
