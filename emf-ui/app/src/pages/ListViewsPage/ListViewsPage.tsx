import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useCollectionSummaries, type CollectionSummary } from '../../hooks/useCollectionSummaries'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'

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
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="listview-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[700px] max-h-[90vh] overflow-y-auto rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="listview-form-title"
        data-testid="listview-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="listview-form-title" className="m-0 text-xl font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="listview-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            <div>
              <label
                htmlFor="listview-name"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Name
                <span className="ml-0.5 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="listview-name"
                type="text"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.name && errors.name ? 'border-destructive' : 'border-border'
                )}
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
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div>
              <label
                htmlFor="listview-collectionId"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Collection
                <span className="ml-0.5 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="listview-collectionId"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.collectionId && errors.collectionId
                    ? 'border-destructive'
                    : 'border-border'
                )}
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
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div>
              <label
                htmlFor="listview-visibility"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Visibility
              </label>
              <select
                id="listview-visibility"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
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

            <div>
              <label
                htmlFor="listview-columns"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Columns
              </label>
              <textarea
                id="listview-columns"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background font-mono focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.columns && errors.columns ? 'border-destructive' : 'border-border'
                )}
                value={formData.columns}
                onChange={(e) => handleChange('columns', e.target.value)}
                onBlur={() => handleBlur('columns')}
                placeholder='["field1", "field2"]'
                disabled={isSubmitting}
                rows={3}
                data-testid="listview-columns-input"
              />
              <span className="mt-1 block text-xs text-muted-foreground">
                JSON array of column field names
              </span>
              {touched.columns && errors.columns && (
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.columns}
                </span>
              )}
            </div>

            <div>
              <label
                htmlFor="listview-filters"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Filters
              </label>
              <textarea
                id="listview-filters"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background font-mono focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.filters && errors.filters ? 'border-destructive' : 'border-border'
                )}
                value={formData.filters}
                onChange={(e) => handleChange('filters', e.target.value)}
                onBlur={() => handleBlur('filters')}
                placeholder='[{"field": "status", "op": "eq", "value": "active"}]'
                disabled={isSubmitting}
                rows={3}
                data-testid="listview-filters-input"
              />
              <span className="mt-1 block text-xs text-muted-foreground">
                JSON array of filter objects
              </span>
              {touched.filters && errors.filters && (
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.filters}
                </span>
              )}
            </div>

            <div>
              <label
                htmlFor="listview-sortField"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Sort Field
              </label>
              <input
                id="listview-sortField"
                type="text"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                value={formData.sortField}
                onChange={(e) => handleChange('sortField', e.target.value)}
                placeholder="Enter sort field name"
                disabled={isSubmitting}
                data-testid="listview-sortField-input"
              />
            </div>

            <div>
              <label
                htmlFor="listview-sortDirection"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Sort Direction
              </label>
              <select
                id="listview-sortDirection"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                value={formData.sortDirection}
                onChange={(e) => handleChange('sortDirection', e.target.value)}
                disabled={isSubmitting}
                data-testid="listview-sortDirection-input"
              >
                <option value="ASC">ASC</option>
                <option value="DESC">DESC</option>
              </select>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded-md border border-border bg-secondary px-4 py-2 text-sm text-foreground hover:bg-muted disabled:opacity-50"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="listview-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
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

  const { summaries: collections } = useCollectionSummaries()

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
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
      case 'GROUP':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300'
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="m-0 text-2xl font-semibold text-foreground">List Views</h1>
        <button
          type="button"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          onClick={handleCreate}
          aria-label="Create List View"
          data-testid="add-listview-button"
        >
          Create List View
        </button>
      </header>

      {listViewList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card p-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No list views found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse text-sm"
            role="grid"
            aria-label="List Views"
            data-testid="listviews-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Name
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Collection
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Visibility
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Created By
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Created
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {listViewList.map((lv, index) => (
                <tr
                  key={lv.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`listview-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {lv.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {getCollectionName(lv.collectionId)}
                  </td>
                  <td role="gridcell" className="px-4 py-3">
                    <span
                      className={cn(
                        'inline-block rounded px-2 py-0.5 text-xs font-medium',
                        getVisibilityBadgeClass(lv.visibility)
                      )}
                    >
                      {lv.visibility}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {lv.createdBy}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {formatDate(new Date(lv.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-primary hover:border-primary hover:bg-muted"
                        onClick={() => handleEdit(lv)}
                        aria-label={`Edit ${lv.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-destructive hover:border-destructive hover:bg-destructive/10"
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
