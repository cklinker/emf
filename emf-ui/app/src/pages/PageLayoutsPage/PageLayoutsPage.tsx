import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import {
  Plus,
  Pencil,
  Trash2,
  ArrowLeft,
  LayoutGrid,
  Eye,
  GripVertical,
  Search,
  ChevronRight,
  X,
  Columns,
  Settings,
  List,
} from 'lucide-react'
import { getTenantId } from '../../hooks'
import type { FieldDefinition } from '../../types/collections'
import type {
  PageLayoutSummary,
  PageLayoutDetail,
  PageLayoutSaveRequest,
  LayoutSection,
  LayoutFieldPlacement,
  LayoutRelatedList,
  LayoutViewMode,
  SelectedItem,
} from '../../types/layouts'
import { LAYOUT_TYPES, SECTION_STYLES, DRAG_TYPES } from '../../types/layouts'
import styles from './PageLayoutsPage.module.css'

// ─── Types ───────────────────────────────────────────────────────────────────

interface CollectionSummary {
  id: string
  name: string
  displayName: string
}

interface PageLayoutFormData {
  name: string
  description: string
  layoutType: string
  collectionId: string
  isDefault: boolean
}

interface FormErrors {
  name?: string
  description?: string
  collectionId?: string
}

export interface PageLayoutsPageProps {
  testId?: string
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function generateId(): string {
  return `temp-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function validateForm(data: PageLayoutFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (!data.collectionId) {
    errors.collectionId = 'Collection is required'
  }
  return errors
}

function createEmptySection(sortOrder: number): LayoutSection {
  return {
    id: generateId(),
    heading: 'New Section',
    columns: 2,
    sortOrder,
    collapsed: false,
    style: 'DEFAULT',
    fields: [],
  }
}

function buildSaveRequest(
  layout: PageLayoutDetail,
  sections: LayoutSection[],
  relatedLists: LayoutRelatedList[]
): PageLayoutSaveRequest {
  return {
    name: layout.name,
    description: layout.description ?? undefined,
    layoutType: layout.layoutType,
    isDefault: layout.isDefault,
    sections: sections.map((s, si) => ({
      heading: s.heading,
      columns: s.columns,
      sortOrder: si,
      collapsed: s.collapsed,
      style: s.style,
      fields: s.fields.map((f, fi) => ({
        fieldId: f.fieldId,
        columnNumber: f.columnNumber,
        sortOrder: fi,
        requiredOnLayout: f.requiredOnLayout,
        readOnlyOnLayout: f.readOnlyOnLayout,
      })),
    })),
    relatedLists: relatedLists.map((rl, rli) => ({
      relatedCollectionId: rl.relatedCollectionId,
      relationshipFieldId: rl.relationshipFieldId,
      displayColumns: rl.displayColumns,
      sortField: rl.sortField || '',
      sortDirection: rl.sortDirection || 'ASC',
      rowLimit: rl.rowLimit || 10,
      sortOrder: rli,
    })),
  }
}

// ─── PageLayoutForm (Create/Edit metadata modal) ─────────────────────────────

interface PageLayoutFormProps {
  layout?: PageLayoutSummary
  collections: CollectionSummary[]
  onSubmit: (data: PageLayoutFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function PageLayoutForm({
  layout,
  collections,
  onSubmit,
  onCancel,
  isSubmitting,
}: PageLayoutFormProps): React.ReactElement {
  const isEditing = !!layout
  const [formData, setFormData] = useState<PageLayoutFormData>({
    name: layout?.name ?? '',
    description: layout?.description ?? '',
    layoutType: layout?.layoutType ?? 'DETAIL',
    collectionId: layout?.collectionId ?? '',
    isDefault: layout?.isDefault ?? false,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof PageLayoutFormData, value: string | boolean) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (typeof value === 'string' && errors[field as keyof FormErrors]) {
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
      setTouched({ name: true, description: true, collectionId: true })
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

  const title = isEditing ? 'Edit Layout' : 'Create Layout'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="layout-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="layout-form-title"
        data-testid="layout-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="layout-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="layout-form-close"
          >
            <X size={18} />
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="layout-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="layout-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter layout name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="layout-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="layout-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="layout-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter layout description"
                disabled={isSubmitting}
                rows={3}
                data-testid="layout-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="layout-type" className={styles.formLabel}>
                Layout Type
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="layout-type"
                className={styles.formInput}
                value={formData.layoutType}
                onChange={(e) => handleChange('layoutType', e.target.value)}
                disabled={isSubmitting}
                data-testid="layout-type-select"
              >
                {LAYOUT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="layout-collection-id" className={styles.formLabel}>
                Collection
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="layout-collection-id"
                className={`${styles.formInput} ${touched.collectionId && errors.collectionId ? styles.hasError : ''}`}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                aria-required="true"
                aria-invalid={touched.collectionId && !!errors.collectionId}
                disabled={isSubmitting || isEditing}
                data-testid="layout-collection-id-input"
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

            <div className={styles.checkboxGroup}>
              <input
                id="layout-is-default"
                type="checkbox"
                checked={formData.isDefault}
                onChange={(e) => handleChange('isDefault', e.target.checked)}
                disabled={isSubmitting}
                data-testid="layout-is-default-input"
              />
              <label htmlFor="layout-is-default" className={styles.formLabel}>
                Default Layout
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="layout-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="layout-form-submit"
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

// ─── Layout Viewer ───────────────────────────────────────────────────────────

interface LayoutViewerProps {
  layoutId: string
  collectionMap: Map<string, CollectionSummary>
  onBack: () => void
  onEdit: () => void
  onDelete: (layout: PageLayoutSummary) => void
}

function LayoutViewer({
  layoutId,
  collectionMap,
  onBack,
  onEdit,
  onDelete,
}: LayoutViewerProps): React.ReactElement {
  const { apiClient } = useApi()

  const {
    data: layout,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['layout', layoutId],
    queryFn: () => apiClient.get<PageLayoutDetail>(`/control/layouts/${layoutId}`),
    enabled: !!layoutId,
  })

  if (isLoading) {
    return (
      <div className={styles.viewerContainer} data-testid="layout-viewer-loading">
        <LoadingSpinner size="large" label="Loading layout..." />
      </div>
    )
  }

  if (error || !layout) {
    return (
      <div className={styles.viewerContainer} data-testid="layout-viewer-error">
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load layout')}
          onRetry={() => {}}
        />
        <button
          type="button"
          className={styles.backButton}
          onClick={onBack}
          data-testid="viewer-back-button"
        >
          <ArrowLeft size={16} />
          Back to Layouts
        </button>
      </div>
    )
  }

  const col = collectionMap.get(layout.collectionId)
  const collectionName = col ? col.displayName || col.name : layout.collectionId
  const sections = [...(layout.sections || [])].sort((a, b) => a.sortOrder - b.sortOrder)
  const relatedLists = [...(layout.relatedLists || [])].sort((a, b) => a.sortOrder - b.sortOrder)

  return (
    <div className={styles.viewerContainer} data-testid="layout-viewer">
      <div className={styles.viewerHeader}>
        <div className={styles.viewerHeaderLeft}>
          <button
            type="button"
            className={styles.backButton}
            onClick={onBack}
            data-testid="viewer-back-button"
          >
            <ArrowLeft size={16} />
            Back to Layouts
          </button>
          <h2 className={styles.viewerTitle}>{layout.name}</h2>
          {layout.description && <p className={styles.viewerDescription}>{layout.description}</p>}
          <div className={styles.viewerMeta}>
            <span className={styles.badge}>{layout.layoutType}</span>
            <span className={styles.metaItem}>Collection: {collectionName}</span>
            {layout.isDefault && (
              <span className={`${styles.boolBadge} ${styles.boolTrue}`}>Default</span>
            )}
          </div>
        </div>
        <div className={styles.viewerActions}>
          <button
            type="button"
            className={styles.editDashboardButton}
            onClick={onEdit}
            data-testid="viewer-edit-button"
          >
            <Pencil size={16} />
            Edit Layout
          </button>
          <button
            type="button"
            className={`${styles.actionButton} ${styles.deleteButton}`}
            onClick={() => onDelete(layout)}
            data-testid="viewer-delete-button"
          >
            <Trash2 size={16} />
            Delete
          </button>
        </div>
      </div>

      {sections.length === 0 && relatedLists.length === 0 ? (
        <div className={styles.viewerEmpty} data-testid="viewer-empty">
          <LayoutGrid size={48} />
          <p>This layout has no sections yet.</p>
          <button
            type="button"
            className={styles.submitButton}
            onClick={onEdit}
            data-testid="viewer-add-sections-button"
          >
            <Plus size={16} />
            Design Layout
          </button>
        </div>
      ) : (
        <div className={styles.viewerContent}>
          {sections.map((section, si) => {
            const sectionFields = [...(section.fields || [])].sort(
              (a, b) => a.sortOrder - b.sortOrder
            )
            return (
              <div
                key={section.id || si}
                className={styles.viewerSection}
                data-testid={`viewer-section-${si}`}
              >
                <div className={styles.viewerSectionHeader}>
                  <h3 className={styles.viewerSectionHeading}>
                    {section.heading || 'Untitled Section'}
                  </h3>
                  <span className={styles.viewerSectionMeta}>
                    {section.columns} col{section.columns > 1 ? 's' : ''} &middot;{' '}
                    {sectionFields.length} field{sectionFields.length !== 1 ? 's' : ''}
                    {section.collapsed && ' (collapsed)'}
                  </span>
                </div>
                {sectionFields.length === 0 ? (
                  <div className={styles.viewerSectionEmpty}>No fields in this section</div>
                ) : (
                  <div
                    className={styles.viewerFieldGrid}
                    style={{ gridTemplateColumns: `repeat(${section.columns}, 1fr)` }}
                  >
                    {sectionFields.map((field, fi) => (
                      <div
                        key={field.id || fi}
                        className={styles.viewerFieldCard}
                        style={{ gridColumn: field.columnNumber }}
                      >
                        <span className={styles.viewerFieldName}>
                          {field.fieldName || field.fieldId}
                        </span>
                        <div className={styles.viewerFieldFlags}>
                          {field.requiredOnLayout && (
                            <span className={styles.viewerFieldFlag}>Required</span>
                          )}
                          {field.readOnlyOnLayout && (
                            <span className={styles.viewerFieldFlag}>Read-only</span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}

          {relatedLists.length > 0 && (
            <div className={styles.viewerRelatedSection}>
              <h3 className={styles.viewerSectionHeading}>Related Lists</h3>
              <div className={styles.viewerRelatedGrid}>
                {relatedLists.map((rl, rli) => (
                  <div key={rl.id || rli} className={styles.viewerRelatedCard}>
                    <span className={styles.viewerRelatedName}>{rl.relatedCollectionId}</span>
                    <span className={styles.viewerRelatedMeta}>
                      {rl.rowLimit} rows &middot; Sort: {rl.sortDirection || 'ASC'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Layout Editor ───────────────────────────────────────────────────────────

interface LayoutEditorProps {
  layoutId: string
  collectionMap: Map<string, CollectionSummary>
  onBack: () => void
}

function LayoutEditor({ layoutId, collectionMap, onBack }: LayoutEditorProps): React.ReactElement {
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  // ── Fetch layout detail ──────────────────────────────────────────────────
  const {
    data: layout,
    isLoading: layoutLoading,
    error: layoutError,
  } = useQuery({
    queryKey: ['layout', layoutId],
    queryFn: () => apiClient.get<PageLayoutDetail>(`/control/layouts/${layoutId}`),
    enabled: !!layoutId,
  })

  const collectionId = layout?.collectionId ?? ''

  // ── Fetch collection fields ──────────────────────────────────────────────
  const { data: fieldsData } = useQuery({
    queryKey: ['fields', collectionId],
    queryFn: () => apiClient.get<FieldDefinition[]>(`/control/collections/${collectionId}/fields`),
    enabled: !!collectionId,
  })

  const allFields = useMemo<FieldDefinition[]>(() => fieldsData ?? [], [fieldsData])

  // ── Editor state ─────────────────────────────────────────────────────────
  const [editorSections, setEditorSections] = useState<LayoutSection[]>([])
  const [editorRelatedLists, setEditorRelatedLists] = useState<LayoutRelatedList[]>([])
  const [selectedItem, setSelectedItem] = useState<SelectedItem | null>(null)
  const [hasChanges, setHasChanges] = useState(false)
  const [fieldSearch, setFieldSearch] = useState('')
  const [dragOverTarget, setDragOverTarget] = useState<string | null>(null)

  // Initialize editor state from fetched layout — setState is intentional here
  // to hydrate local editor state from async query data.
  useEffect(() => {
    if (layout) {
      const sections = [...(layout.sections || [])].sort((a, b) => a.sortOrder - b.sortOrder)
      const sorted = sections.map((s) => ({
        ...s,
        fields: [...(s.fields || [])].sort((a, b) => a.sortOrder - b.sortOrder),
      }))
      const sortedRelated = [...(layout.relatedLists || [])].sort(
        (a, b) => a.sortOrder - b.sortOrder
      )
      // eslint-disable-next-line react-hooks/set-state-in-effect -- Intentional: hydrating local editor state from async query
      setEditorSections(sorted)
      setEditorRelatedLists(sortedRelated)
      setHasChanges(false)
      setSelectedItem(null)
    }
  }, [layout])

  // ── Computed values ──────────────────────────────────────────────────────

  const placedFieldIds = useMemo(() => {
    const ids = new Set<string>()
    for (const section of editorSections) {
      for (const field of section.fields) {
        ids.add(field.fieldId)
      }
    }
    return ids
  }, [editorSections])

  const filteredFields = useMemo(() => {
    const query = fieldSearch.toLowerCase().trim()
    if (!query) return allFields
    return allFields.filter(
      (f) =>
        f.name.toLowerCase().includes(query) ||
        (f.displayName && f.displayName.toLowerCase().includes(query)) ||
        f.type.toLowerCase().includes(query)
    )
  }, [allFields, fieldSearch])

  const fieldMap = useMemo(() => {
    const map = new Map<string, FieldDefinition>()
    for (const f of allFields) {
      map.set(f.id, f)
    }
    return map
  }, [allFields])

  // ── Save mutation ────────────────────────────────────────────────────────

  const saveMutation = useMutation({
    mutationFn: (request: PageLayoutSaveRequest) =>
      apiClient.put<PageLayoutDetail>(`/control/layouts/${layoutId}`, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['layout', layoutId] })
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout saved successfully', 'success')
      setHasChanges(false)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to save layout', 'error')
    },
  })

  // ── Handlers ─────────────────────────────────────────────────────────────

  const markChanged = useCallback(() => setHasChanges(true), [])

  const handleSave = useCallback(() => {
    if (!layout) return
    const request = buildSaveRequest(layout, editorSections, editorRelatedLists)
    saveMutation.mutate(request)
  }, [layout, editorSections, editorRelatedLists, saveMutation])

  const handleCancel = useCallback(() => {
    if (hasChanges) {
      if (!window.confirm('You have unsaved changes. Discard them?')) {
        return
      }
    }
    onBack()
  }, [hasChanges, onBack])

  // ── Section operations ───────────────────────────────────────────────────

  const handleAddSection = useCallback(() => {
    setEditorSections((prev) => [...prev, createEmptySection(prev.length)])
    markChanged()
  }, [markChanged])

  const handleRemoveSection = useCallback(
    (sectionIndex: number) => {
      setEditorSections((prev) => prev.filter((_, i) => i !== sectionIndex))
      if (selectedItem?.sectionIndex === sectionIndex) {
        setSelectedItem(null)
      }
      markChanged()
    },
    [selectedItem, markChanged]
  )

  const handleSelectSection = useCallback((sectionIndex: number) => {
    setSelectedItem({ type: 'section', sectionIndex })
  }, [])

  const handleUpdateSection = useCallback(
    (sectionIndex: number, updates: Partial<LayoutSection>) => {
      setEditorSections((prev) =>
        prev.map((s, i) => (i === sectionIndex ? { ...s, ...updates } : s))
      )
      markChanged()
    },
    [markChanged]
  )

  // ── Section drag-and-drop (reorder) ──────────────────────────────────────

  const handleSectionDragStart = useCallback((e: React.DragEvent, sectionIndex: number) => {
    e.dataTransfer.setData(DRAG_TYPES.SECTION, JSON.stringify({ sectionIndex }))
    e.dataTransfer.effectAllowed = 'move'
  }, [])

  const handleSectionDragOver = useCallback((e: React.DragEvent, targetIndex: number) => {
    if (e.dataTransfer.types.includes(DRAG_TYPES.SECTION)) {
      e.preventDefault()
      e.dataTransfer.dropEffect = 'move'
      setDragOverTarget(`section-${targetIndex}`)
    }
  }, [])

  const handleSectionDrop = useCallback(
    (e: React.DragEvent, targetIndex: number) => {
      e.preventDefault()
      setDragOverTarget(null)
      const raw = e.dataTransfer.getData(DRAG_TYPES.SECTION)
      if (!raw) return
      const { sectionIndex: sourceIndex } = JSON.parse(raw) as { sectionIndex: number }
      if (sourceIndex === targetIndex) return

      setEditorSections((prev) => {
        const result = [...prev]
        const [moved] = result.splice(sourceIndex, 1)
        result.splice(targetIndex > sourceIndex ? targetIndex - 1 : targetIndex, 0, moved)
        return result
      })
      markChanged()
    },
    [markChanged]
  )

  // ── Field operations ─────────────────────────────────────────────────────

  const handleSelectField = useCallback((sectionIndex: number, fieldIndex: number) => {
    setSelectedItem({ type: 'field', sectionIndex, fieldIndex })
  }, [])

  const handleUpdateField = useCallback(
    (sectionIndex: number, fieldIndex: number, updates: Partial<LayoutFieldPlacement>) => {
      setEditorSections((prev) =>
        prev.map((s, si) =>
          si === sectionIndex
            ? {
                ...s,
                fields: s.fields.map((f, fi) => (fi === fieldIndex ? { ...f, ...updates } : f)),
              }
            : s
        )
      )
      markChanged()
    },
    [markChanged]
  )

  const handleRemoveField = useCallback(
    (sectionIndex: number, fieldIndex: number) => {
      setEditorSections((prev) =>
        prev.map((s, si) =>
          si === sectionIndex ? { ...s, fields: s.fields.filter((_, fi) => fi !== fieldIndex) } : s
        )
      )
      if (
        selectedItem?.type === 'field' &&
        selectedItem.sectionIndex === sectionIndex &&
        selectedItem.fieldIndex === fieldIndex
      ) {
        setSelectedItem(null)
      }
      markChanged()
    },
    [selectedItem, markChanged]
  )

  // ── Field drag-and-drop ──────────────────────────────────────────────────

  const handlePaletteFieldDragStart = useCallback((e: React.DragEvent, field: FieldDefinition) => {
    e.dataTransfer.setData(
      DRAG_TYPES.PALETTE_FIELD,
      JSON.stringify({ fieldId: field.id, fieldName: field.displayName || field.name })
    )
    e.dataTransfer.effectAllowed = 'copy'
  }, [])

  const handleFieldDragStart = useCallback(
    (e: React.DragEvent, sectionIndex: number, fieldIndex: number) => {
      e.dataTransfer.setData(DRAG_TYPES.LAYOUT_FIELD, JSON.stringify({ sectionIndex, fieldIndex }))
      e.dataTransfer.effectAllowed = 'move'
      e.stopPropagation()
    },
    []
  )

  const handleFieldDragOver = useCallback(
    (e: React.DragEvent, sectionIndex: number, fieldIndex?: number) => {
      const types = e.dataTransfer.types
      if (types.includes(DRAG_TYPES.PALETTE_FIELD) || types.includes(DRAG_TYPES.LAYOUT_FIELD)) {
        e.preventDefault()
        e.dataTransfer.dropEffect = types.includes(DRAG_TYPES.PALETTE_FIELD) ? 'copy' : 'move'
        setDragOverTarget(
          fieldIndex !== undefined
            ? `field-${sectionIndex}-${fieldIndex}`
            : `section-body-${sectionIndex}`
        )
      }
    },
    []
  )

  const handleFieldDrop = useCallback(
    (e: React.DragEvent, targetSectionIndex: number, targetFieldIndex?: number) => {
      e.preventDefault()
      e.stopPropagation()
      setDragOverTarget(null)

      // Handle palette field drop (add new field)
      const paletteData = e.dataTransfer.getData(DRAG_TYPES.PALETTE_FIELD)
      if (paletteData) {
        const { fieldId, fieldName } = JSON.parse(paletteData) as {
          fieldId: string
          fieldName: string
        }
        if (placedFieldIds.has(fieldId)) return

        const newField: LayoutFieldPlacement = {
          id: generateId(),
          fieldId,
          fieldName,
          columnNumber: 1,
          sortOrder: targetFieldIndex ?? editorSections[targetSectionIndex]?.fields.length ?? 0,
          requiredOnLayout: false,
          readOnlyOnLayout: false,
        }

        setEditorSections((prev) =>
          prev.map((s, si) =>
            si === targetSectionIndex
              ? {
                  ...s,
                  fields:
                    targetFieldIndex !== undefined
                      ? [
                          ...s.fields.slice(0, targetFieldIndex),
                          newField,
                          ...s.fields.slice(targetFieldIndex),
                        ]
                      : [...s.fields, newField],
                }
              : s
          )
        )
        markChanged()
        return
      }

      // Handle layout field drop (move existing field)
      const layoutData = e.dataTransfer.getData(DRAG_TYPES.LAYOUT_FIELD)
      if (layoutData) {
        const { sectionIndex: sourceSectionIndex, fieldIndex: sourceFieldIndex } = JSON.parse(
          layoutData
        ) as { sectionIndex: number; fieldIndex: number }

        if (sourceSectionIndex === targetSectionIndex && sourceFieldIndex === targetFieldIndex) {
          return
        }

        setEditorSections((prev) => {
          const result = prev.map((s) => ({ ...s, fields: [...s.fields] }))
          const [movedField] = result[sourceSectionIndex].fields.splice(sourceFieldIndex, 1)

          const insertIndex =
            targetFieldIndex !== undefined
              ? sourceSectionIndex === targetSectionIndex && sourceFieldIndex < targetFieldIndex
                ? targetFieldIndex - 1
                : targetFieldIndex
              : result[targetSectionIndex].fields.length

          result[targetSectionIndex].fields.splice(insertIndex, 0, movedField)
          return result
        })
        markChanged()
      }
    },
    [editorSections, placedFieldIds, markChanged]
  )

  const handleDragLeave = useCallback(() => {
    setDragOverTarget(null)
  }, [])

  // ── Click-to-add field from palette ──────────────────────────────────────

  const handlePaletteFieldClick = useCallback(
    (field: FieldDefinition) => {
      if (placedFieldIds.has(field.id)) return
      const targetSectionIndex =
        selectedItem?.type === 'section' || selectedItem?.type === 'field'
          ? (selectedItem.sectionIndex ?? 0)
          : 0

      if (editorSections.length === 0) return

      const newField: LayoutFieldPlacement = {
        id: generateId(),
        fieldId: field.id,
        fieldName: field.displayName || field.name,
        columnNumber: 1,
        sortOrder: editorSections[targetSectionIndex]?.fields.length ?? 0,
        requiredOnLayout: false,
        readOnlyOnLayout: false,
      }

      setEditorSections((prev) =>
        prev.map((s, si) =>
          si === targetSectionIndex ? { ...s, fields: [...s.fields, newField] } : s
        )
      )
      markChanged()
    },
    [editorSections, placedFieldIds, selectedItem, markChanged]
  )

  // ── Related list operations ──────────────────────────────────────────────

  const handleSelectRelatedList = useCallback((relatedListIndex: number) => {
    setSelectedItem({ type: 'relatedList', relatedListIndex })
  }, [])

  const handleRemoveRelatedList = useCallback(
    (relatedListIndex: number) => {
      setEditorRelatedLists((prev) => prev.filter((_, i) => i !== relatedListIndex))
      if (
        selectedItem?.type === 'relatedList' &&
        selectedItem.relatedListIndex === relatedListIndex
      ) {
        setSelectedItem(null)
      }
      markChanged()
    },
    [selectedItem, markChanged]
  )

  const handleUpdateRelatedList = useCallback(
    (relatedListIndex: number, updates: Partial<LayoutRelatedList>) => {
      setEditorRelatedLists((prev) =>
        prev.map((rl, i) => (i === relatedListIndex ? { ...rl, ...updates } : rl))
      )
      markChanged()
    },
    [markChanged]
  )

  const handleAddRelatedList = useCallback(() => {
    const newRelatedList: LayoutRelatedList = {
      id: generateId(),
      relatedCollectionId: '',
      relationshipFieldId: '',
      displayColumns: '[]',
      sortField: '',
      sortDirection: 'ASC',
      rowLimit: 10,
      sortOrder: editorRelatedLists.length,
    }
    setEditorRelatedLists((prev) => [...prev, newRelatedList])
    markChanged()
  }, [editorRelatedLists.length, markChanged])

  // ── Loading / error states ───────────────────────────────────────────────

  if (layoutLoading) {
    return (
      <div className={styles.editorContainer} data-testid="layout-editor-loading">
        <LoadingSpinner size="large" label="Loading layout..." />
      </div>
    )
  }

  if (layoutError || !layout) {
    return (
      <div className={styles.editorContainer} data-testid="layout-editor-error">
        <ErrorMessage
          error={layoutError instanceof Error ? layoutError : new Error('Failed to load layout')}
          onRetry={() => {}}
        />
        <button type="button" className={styles.backButton} onClick={onBack}>
          <ArrowLeft size={16} />
          Back
        </button>
      </div>
    )
  }

  const col = collectionMap.get(layout.collectionId)
  const collectionName = col ? col.displayName || col.name : layout.collectionId

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div className={styles.editorContainer} data-testid="layout-editor">
      {/* Editor toolbar */}
      <div className={styles.editorToolbar}>
        <div className={styles.editorToolbarLeft}>
          <button
            type="button"
            className={styles.backButton}
            onClick={handleCancel}
            data-testid="editor-back-button"
          >
            <ArrowLeft size={16} />
            Back
          </button>
          <div className={styles.editorTitleGroup}>
            <h2 className={styles.editorTitle}>{layout.name}</h2>
            <span className={styles.editorSubtitle}>
              {collectionName} &middot; {layout.layoutType}
            </span>
          </div>
        </div>
        <div className={styles.editorToolbarRight}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={handleCancel}
            disabled={saveMutation.isPending}
            data-testid="editor-cancel-button"
          >
            Cancel
          </button>
          <button
            type="button"
            className={styles.submitButton}
            onClick={handleSave}
            disabled={!hasChanges || saveMutation.isPending}
            data-testid="editor-save-button"
          >
            {saveMutation.isPending ? 'Saving...' : 'Save Layout'}
          </button>
        </div>
      </div>

      {/* Three-panel editor */}
      <div className={styles.editorBody}>
        {/* Left: Field Palette */}
        <div className={styles.palette} data-testid="field-palette">
          <div className={styles.paletteHeader}>
            <h3 className={styles.paletteSectionTitle}>
              <List size={16} />
              Fields
            </h3>
            <div className={styles.paletteSearch}>
              <Search size={14} className={styles.searchIcon} />
              <input
                type="text"
                className={styles.searchInput}
                placeholder="Search fields..."
                value={fieldSearch}
                onChange={(e) => setFieldSearch(e.target.value)}
                data-testid="field-search-input"
              />
            </div>
          </div>

          <div className={styles.paletteFieldList}>
            {filteredFields.length === 0 ? (
              <div className={styles.paletteEmpty}>
                {fieldSearch ? 'No matching fields' : 'No fields available'}
              </div>
            ) : (
              filteredFields.map((field) => {
                const isPlaced = placedFieldIds.has(field.id)
                return (
                  <div
                    key={field.id}
                    className={`${styles.paletteField} ${isPlaced ? styles.paletteFieldPlaced : ''}`}
                    draggable={!isPlaced}
                    onDragStart={(e) => handlePaletteFieldDragStart(e, field)}
                    onClick={() => handlePaletteFieldClick(field)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        handlePaletteFieldClick(field)
                      }
                    }}
                    data-testid={`palette-field-${field.id}`}
                  >
                    <GripVertical size={14} className={styles.paletteFieldDragHandle} />
                    <span className={styles.paletteFieldName}>
                      {field.displayName || field.name}
                    </span>
                    <span className={styles.paletteFieldType}>{field.type}</span>
                    {isPlaced && <span className={styles.paletteFieldBadge}>Added</span>}
                  </div>
                )
              })
            )}
          </div>
        </div>

        {/* Center: Canvas */}
        <div className={styles.canvas} data-testid="layout-canvas">
          {editorSections.length === 0 ? (
            <div className={styles.canvasEmpty} data-testid="canvas-empty">
              <LayoutGrid size={48} />
              <p>No sections yet</p>
              <p className={styles.canvasEmptyHint}>
                Click &ldquo;Add Section&rdquo; to get started
              </p>
            </div>
          ) : (
            editorSections.map((section, si) => (
              <React.Fragment key={section.id || si}>
                {/* Section drop zone (before) */}
                <div
                  className={`${styles.sectionDropZone} ${dragOverTarget === `section-${si}` ? styles.dropZoneActive : ''}`}
                  onDragOver={(e) => handleSectionDragOver(e, si)}
                  onDragLeave={handleDragLeave}
                  onDrop={(e) => handleSectionDrop(e, si)}
                  data-testid={`section-drop-zone-${si}`}
                />

                {/* Section card */}
                <div
                  className={`${styles.section} ${selectedItem?.type === 'section' && selectedItem.sectionIndex === si ? styles.sectionSelected : ''}`}
                  role="button"
                  tabIndex={0}
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest(`.${styles.fieldChip}`)) return
                    handleSelectSection(si)
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      handleSelectSection(si)
                    }
                  }}
                  data-testid={`section-${si}`}
                >
                  <div
                    className={styles.sectionHeader}
                    draggable
                    onDragStart={(e) => handleSectionDragStart(e, si)}
                  >
                    <GripVertical size={16} className={styles.sectionDragHandle} />
                    <span className={styles.sectionHeading}>
                      {section.heading || 'Untitled Section'}
                    </span>
                    <span className={styles.sectionColumnBadge}>
                      <Columns size={14} />
                      {section.columns}
                    </span>
                    {section.collapsed && (
                      <ChevronRight size={14} className={styles.sectionCollapsedIcon} />
                    )}
                    <div className={styles.sectionActions}>
                      <button
                        type="button"
                        className={styles.sectionActionBtn}
                        onClick={(e) => {
                          e.stopPropagation()
                          handleSelectSection(si)
                        }}
                        aria-label="Configure section"
                        data-testid={`section-config-${si}`}
                      >
                        <Settings size={14} />
                      </button>
                      <button
                        type="button"
                        className={`${styles.sectionActionBtn} ${styles.sectionDeleteBtn}`}
                        onClick={(e) => {
                          e.stopPropagation()
                          handleRemoveSection(si)
                        }}
                        aria-label="Remove section"
                        data-testid={`section-remove-${si}`}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>

                  {/* Section body: field grid */}
                  <div
                    className={`${styles.sectionBody} ${dragOverTarget === `section-body-${si}` ? styles.dropZoneActive : ''}`}
                    style={{
                      gridTemplateColumns: `repeat(${section.columns}, 1fr)`,
                    }}
                    onDragOver={(e) => handleFieldDragOver(e, si)}
                    onDragLeave={handleDragLeave}
                    onDrop={(e) => handleFieldDrop(e, si)}
                    data-testid={`section-body-${si}`}
                  >
                    {section.fields.length === 0 ? (
                      <div className={styles.sectionEmptyDrop} style={{ gridColumn: `1 / -1` }}>
                        Drag fields here
                      </div>
                    ) : (
                      section.fields.map((field, fi) => {
                        const fieldDef = fieldMap.get(field.fieldId)
                        const isSelected =
                          selectedItem?.type === 'field' &&
                          selectedItem.sectionIndex === si &&
                          selectedItem.fieldIndex === fi
                        return (
                          <div
                            key={field.id || fi}
                            className={`${styles.fieldChip} ${isSelected ? styles.fieldChipSelected : ''} ${dragOverTarget === `field-${si}-${fi}` ? styles.dropZoneActive : ''}`}
                            style={{ gridColumn: field.columnNumber }}
                            role="button"
                            tabIndex={0}
                            draggable
                            onDragStart={(e) => handleFieldDragStart(e, si, fi)}
                            onDragOver={(e) => handleFieldDragOver(e, si, fi)}
                            onDragLeave={handleDragLeave}
                            onDrop={(e) => handleFieldDrop(e, si, fi)}
                            onClick={(e) => {
                              e.stopPropagation()
                              handleSelectField(si, fi)
                            }}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault()
                                e.stopPropagation()
                                handleSelectField(si, fi)
                              }
                            }}
                            data-testid={`field-chip-${si}-${fi}`}
                          >
                            <GripVertical size={12} className={styles.fieldDragHandle} />
                            <span className={styles.fieldChipName}>
                              {field.fieldName ||
                                fieldDef?.displayName ||
                                fieldDef?.name ||
                                field.fieldId}
                            </span>
                            <span className={styles.fieldChipType}>{fieldDef?.type || ''}</span>
                            {field.requiredOnLayout && (
                              <span className={styles.fieldChipRequired}>*</span>
                            )}
                            <button
                              type="button"
                              className={styles.fieldChipRemove}
                              onClick={(e) => {
                                e.stopPropagation()
                                handleRemoveField(si, fi)
                              }}
                              aria-label={`Remove ${field.fieldName || field.fieldId}`}
                            >
                              <X size={12} />
                            </button>
                          </div>
                        )
                      })
                    )}
                  </div>
                </div>
              </React.Fragment>
            ))
          )}

          {/* Final section drop zone */}
          {editorSections.length > 0 && (
            <div
              className={`${styles.sectionDropZone} ${dragOverTarget === `section-${editorSections.length}` ? styles.dropZoneActive : ''}`}
              onDragOver={(e) => handleSectionDragOver(e, editorSections.length)}
              onDragLeave={handleDragLeave}
              onDrop={(e) => handleSectionDrop(e, editorSections.length)}
            />
          )}

          {/* Add Section button */}
          <button
            type="button"
            className={styles.addSectionButton}
            onClick={handleAddSection}
            data-testid="add-section-button"
          >
            <Plus size={16} />
            Add Section
          </button>

          {/* Related lists area */}
          {editorRelatedLists.length > 0 && (
            <div className={styles.relatedListsArea}>
              <h3 className={styles.relatedListsTitle}>Related Lists</h3>
              {editorRelatedLists.map((rl, rli) => (
                <div
                  key={rl.id || rli}
                  className={`${styles.relatedListCard} ${selectedItem?.type === 'relatedList' && selectedItem.relatedListIndex === rli ? styles.relatedListCardSelected : ''}`}
                  role="button"
                  tabIndex={0}
                  onClick={() => handleSelectRelatedList(rli)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      handleSelectRelatedList(rli)
                    }
                  }}
                  data-testid={`related-list-${rli}`}
                >
                  <span className={styles.relatedListName}>
                    {rl.relatedCollectionId || 'Unconfigured'}
                  </span>
                  <span className={styles.relatedListMeta}>{rl.rowLimit} rows</span>
                  <button
                    type="button"
                    className={styles.relatedListRemove}
                    onClick={(e) => {
                      e.stopPropagation()
                      handleRemoveRelatedList(rli)
                    }}
                    aria-label="Remove related list"
                  >
                    <X size={14} />
                  </button>
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            className={styles.addRelatedListButton}
            onClick={handleAddRelatedList}
            data-testid="add-related-list-button"
          >
            <Plus size={16} />
            Add Related List
          </button>
        </div>

        {/* Right: Properties Panel */}
        <div className={styles.propertiesPanel} data-testid="properties-panel">
          <h3 className={styles.propertiesPanelTitle}>Properties</h3>

          {selectedItem === null ? (
            <div className={styles.propertiesEmpty}>
              <Settings size={32} />
              <p>Select a section, field, or related list to view its properties.</p>
            </div>
          ) : selectedItem.type === 'section' && selectedItem.sectionIndex !== undefined ? (
            <SectionPropertiesPanel
              section={editorSections[selectedItem.sectionIndex]}
              onUpdate={(updates) => handleUpdateSection(selectedItem.sectionIndex!, updates)}
            />
          ) : selectedItem.type === 'field' &&
            selectedItem.sectionIndex !== undefined &&
            selectedItem.fieldIndex !== undefined ? (
            <FieldPropertiesPanel
              field={editorSections[selectedItem.sectionIndex]?.fields[selectedItem.fieldIndex]}
              fieldDef={fieldMap.get(
                editorSections[selectedItem.sectionIndex]?.fields[selectedItem.fieldIndex]?.fieldId
              )}
              maxColumns={editorSections[selectedItem.sectionIndex]?.columns ?? 2}
              onUpdate={(updates) =>
                handleUpdateField(selectedItem.sectionIndex!, selectedItem.fieldIndex!, updates)
              }
            />
          ) : selectedItem.type === 'relatedList' && selectedItem.relatedListIndex !== undefined ? (
            <RelatedListPropertiesPanel
              relatedList={editorRelatedLists[selectedItem.relatedListIndex]}
              collections={Array.from(collectionMap.values())}
              onUpdate={(updates) =>
                handleUpdateRelatedList(selectedItem.relatedListIndex!, updates)
              }
            />
          ) : null}
        </div>
      </div>
    </div>
  )
}

// ─── Properties Panel Sub-components ─────────────────────────────────────────

interface SectionPropertiesPanelProps {
  section: LayoutSection
  onUpdate: (updates: Partial<LayoutSection>) => void
}

function SectionPropertiesPanel({
  section,
  onUpdate,
}: SectionPropertiesPanelProps): React.ReactElement {
  if (!section) return <div className={styles.propertiesEmpty}>Section not found</div>

  return (
    <div className={styles.propertiesForm} data-testid="section-properties">
      <h4 className={styles.propertiesSubtitle}>Section Properties</h4>

      <div className={styles.propertyGroup}>
        <label htmlFor="section-heading" className={styles.propertyLabel}>
          Heading
        </label>
        <input
          id="section-heading"
          type="text"
          className={styles.propertyInput}
          value={section.heading}
          onChange={(e) => onUpdate({ heading: e.target.value })}
          data-testid="section-heading-input"
        />
      </div>

      <div className={styles.propertyGroup}>
        <label htmlFor="section-columns" className={styles.propertyLabel}>
          Columns
        </label>
        <select
          id="section-columns"
          className={styles.propertyInput}
          value={section.columns}
          onChange={(e) => onUpdate({ columns: parseInt(e.target.value, 10) })}
          data-testid="section-columns-select"
        >
          <option value="1">1 Column</option>
          <option value="2">2 Columns</option>
          <option value="3">3 Columns</option>
          <option value="4">4 Columns</option>
        </select>
      </div>

      <div className={styles.propertyGroup}>
        <label htmlFor="section-style" className={styles.propertyLabel}>
          Style
        </label>
        <select
          id="section-style"
          className={styles.propertyInput}
          value={section.style}
          onChange={(e) => onUpdate({ style: e.target.value })}
          data-testid="section-style-select"
        >
          {SECTION_STYLES.map((s) => (
            <option key={s} value={s}>
              {s.charAt(0) + s.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.propertyCheckboxGroup}>
        <input
          id="section-collapsed"
          type="checkbox"
          checked={section.collapsed}
          onChange={(e) => onUpdate({ collapsed: e.target.checked })}
          data-testid="section-collapsed-checkbox"
        />
        <label htmlFor="section-collapsed" className={styles.propertyLabel}>
          Collapsed by Default
        </label>
      </div>
    </div>
  )
}

interface FieldPropertiesPanelProps {
  field: LayoutFieldPlacement | undefined
  fieldDef: FieldDefinition | undefined
  maxColumns: number
  onUpdate: (updates: Partial<LayoutFieldPlacement>) => void
}

function FieldPropertiesPanel({
  field,
  fieldDef,
  maxColumns,
  onUpdate,
}: FieldPropertiesPanelProps): React.ReactElement {
  if (!field) return <div className={styles.propertiesEmpty}>Field not found</div>

  return (
    <div className={styles.propertiesForm} data-testid="field-properties">
      <h4 className={styles.propertiesSubtitle}>Field Properties</h4>

      <div className={styles.propertyGroup}>
        <span className={styles.propertyLabel}>Field</span>
        <div className={styles.propertyReadonly} data-testid="field-name-display">
          {field.fieldName || fieldDef?.displayName || fieldDef?.name || field.fieldId}
        </div>
      </div>

      {fieldDef && (
        <div className={styles.propertyGroup}>
          <span className={styles.propertyLabel}>Type</span>
          <div className={styles.propertyReadonly} data-testid="field-type-display">
            {fieldDef.type}
          </div>
        </div>
      )}

      <div className={styles.propertyGroup}>
        <label htmlFor="field-column" className={styles.propertyLabel}>
          Column
        </label>
        <select
          id="field-column"
          className={styles.propertyInput}
          value={field.columnNumber}
          onChange={(e) => onUpdate({ columnNumber: parseInt(e.target.value, 10) })}
          data-testid="field-column-select"
        >
          {Array.from({ length: maxColumns }, (_, i) => i + 1).map((col) => (
            <option key={col} value={col}>
              Column {col}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.propertyCheckboxGroup}>
        <input
          id="field-required"
          type="checkbox"
          checked={field.requiredOnLayout}
          onChange={(e) => onUpdate({ requiredOnLayout: e.target.checked })}
          data-testid="field-required-checkbox"
        />
        <label htmlFor="field-required" className={styles.propertyLabel}>
          Required on Layout
        </label>
      </div>

      <div className={styles.propertyCheckboxGroup}>
        <input
          id="field-readonly"
          type="checkbox"
          checked={field.readOnlyOnLayout}
          onChange={(e) => onUpdate({ readOnlyOnLayout: e.target.checked })}
          data-testid="field-readonly-checkbox"
        />
        <label htmlFor="field-readonly" className={styles.propertyLabel}>
          Read-Only on Layout
        </label>
      </div>
    </div>
  )
}

interface RelatedListPropertiesPanelProps {
  relatedList: LayoutRelatedList | undefined
  collections: CollectionSummary[]
  onUpdate: (updates: Partial<LayoutRelatedList>) => void
}

function RelatedListPropertiesPanel({
  relatedList,
  collections,
  onUpdate,
}: RelatedListPropertiesPanelProps): React.ReactElement {
  if (!relatedList) return <div className={styles.propertiesEmpty}>Related list not found</div>

  return (
    <div className={styles.propertiesForm} data-testid="related-list-properties">
      <h4 className={styles.propertiesSubtitle}>Related List Properties</h4>

      <div className={styles.propertyGroup}>
        <label htmlFor="rl-collection" className={styles.propertyLabel}>
          Related Collection
        </label>
        <select
          id="rl-collection"
          className={styles.propertyInput}
          value={relatedList.relatedCollectionId}
          onChange={(e) => onUpdate({ relatedCollectionId: e.target.value })}
          data-testid="rl-collection-select"
        >
          <option value="">Select collection</option>
          {collections.map((col) => (
            <option key={col.id} value={col.id}>
              {col.displayName || col.name}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.propertyGroup}>
        <label htmlFor="rl-sort-direction" className={styles.propertyLabel}>
          Sort Direction
        </label>
        <select
          id="rl-sort-direction"
          className={styles.propertyInput}
          value={relatedList.sortDirection}
          onChange={(e) => onUpdate({ sortDirection: e.target.value })}
          data-testid="rl-sort-direction-select"
        >
          <option value="ASC">Ascending</option>
          <option value="DESC">Descending</option>
        </select>
      </div>

      <div className={styles.propertyGroup}>
        <label htmlFor="rl-row-limit" className={styles.propertyLabel}>
          Row Limit
        </label>
        <input
          id="rl-row-limit"
          type="number"
          className={styles.propertyInput}
          value={relatedList.rowLimit}
          onChange={(e) => onUpdate({ rowLimit: parseInt(e.target.value, 10) || 10 })}
          min={1}
          max={100}
          data-testid="rl-row-limit-input"
        />
      </div>
    </div>
  )
}

// ─── Main Page Component ─────────────────────────────────────────────────────

export function PageLayoutsPage({
  testId = 'page-layouts-page',
}: PageLayoutsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [viewMode, setViewMode] = useState<LayoutViewMode>('list')
  const [selectedLayoutId, setSelectedLayoutId] = useState<string | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingLayout, setEditingLayout] = useState<PageLayoutSummary | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [layoutToDelete, setLayoutToDelete] = useState<PageLayoutSummary | null>(null)

  // ── Data queries ─────────────────────────────────────────────────────────

  const {
    data: layouts,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['pageLayouts'],
    queryFn: () => apiClient.get<PageLayoutSummary[]>(`/control/layouts?tenantId=${getTenantId()}`),
  })

  const { data: collectionsData } = useQuery({
    queryKey: ['collections-for-layouts'],
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

  const layoutList = layouts ?? []

  // ── Mutations ────────────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (data: PageLayoutFormData) =>
      apiClient.post<PageLayoutSummary>(
        `/control/layouts?tenantId=${getTenantId()}&collectionId=${encodeURIComponent(data.collectionId)}`,
        data
      ),
    onSuccess: (newLayout) => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout created successfully', 'success')
      handleCloseForm()
      // Transition to editor mode for the new layout
      if (newLayout?.id) {
        setSelectedLayoutId(newLayout.id)
        setViewMode('editor')
      }
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMetaMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: PageLayoutFormData }) =>
      apiClient.put<PageLayoutSummary>(`/control/layouts/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/layouts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pageLayouts'] })
      showToast('Layout deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setLayoutToDelete(null)
      // If we were viewing/editing this layout, go back to list
      if (layoutToDelete && layoutToDelete.id === selectedLayoutId) {
        setSelectedLayoutId(null)
        setViewMode('list')
      }
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  // ── Handlers ─────────────────────────────────────────────────────────────

  const handleCreate = useCallback(() => {
    setEditingLayout(undefined)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingLayout(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: PageLayoutFormData) => {
      if (editingLayout) {
        updateMetaMutation.mutate({ id: editingLayout.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingLayout, createMutation, updateMetaMutation]
  )

  const handleDeleteClick = useCallback((layout: PageLayoutSummary) => {
    setLayoutToDelete(layout)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (layoutToDelete) {
      deleteMutation.mutate(layoutToDelete.id)
    }
  }, [layoutToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setLayoutToDelete(null)
  }, [])

  const handleViewLayout = useCallback((layout: PageLayoutSummary) => {
    setSelectedLayoutId(layout.id)
    setViewMode('viewer')
  }, [])

  const handleEditLayout = useCallback((layout: PageLayoutSummary) => {
    setSelectedLayoutId(layout.id)
    setViewMode('editor')
  }, [])

  const handleBackToList = useCallback(() => {
    setSelectedLayoutId(null)
    setViewMode('list')
  }, [])

  const handleBackToViewer = useCallback(() => {
    setViewMode('viewer')
  }, [])

  const handleEditFromViewer = useCallback(() => {
    setViewMode('editor')
  }, [])

  const getCollectionName = useCallback(
    (collectionId: string): string => {
      const col = collectionMap.get(collectionId)
      return col ? col.displayName || col.name : collectionId
    },
    [collectionMap]
  )

  // ─── Viewer Mode ─────────────────────────────────────────────────────────

  if (viewMode === 'viewer' && selectedLayoutId) {
    return (
      <div className={styles.container} data-testid={testId}>
        <LayoutViewer
          layoutId={selectedLayoutId}
          collectionMap={collectionMap}
          onBack={handleBackToList}
          onEdit={handleEditFromViewer}
          onDelete={handleDeleteClick}
        />

        <ConfirmDialog
          open={deleteDialogOpen}
          title="Delete Layout"
          message="Are you sure you want to delete this layout? This action cannot be undone."
          confirmLabel="Delete"
          cancelLabel="Cancel"
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteCancel}
          variant="danger"
        />
      </div>
    )
  }

  // ─── Editor Mode ─────────────────────────────────────────────────────────

  if (viewMode === 'editor' && selectedLayoutId) {
    return (
      <div className={styles.container} data-testid={testId}>
        <LayoutEditor
          layoutId={selectedLayoutId}
          collectionMap={collectionMap}
          onBack={handleBackToViewer}
        />
      </div>
    )
  }

  // ─── List Mode ───────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading layouts..." />
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

  const isSubmitting = createMutation.isPending || updateMetaMutation.isPending

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>Page Layouts</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Layout"
          data-testid="add-layout-button"
        >
          <Plus size={18} />
          Create Layout
        </button>
      </header>

      {layoutList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <LayoutGrid size={48} className={styles.emptyIcon} />
          <p className={styles.emptyTitle}>No page layouts found</p>
          <p className={styles.emptyHint}>
            Create your first page layout to start designing record views.
          </p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Page Layouts"
            data-testid="page-layouts-table"
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
                  Type
                </th>
                <th role="columnheader" scope="col">
                  Default
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
              {layoutList.map((layout, index) => (
                <tr
                  key={layout.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`layout-row-${index}`}
                >
                  <td role="gridcell">
                    <button
                      type="button"
                      className={styles.nameLink}
                      onClick={() => handleViewLayout(layout)}
                      data-testid={`layout-name-${index}`}
                    >
                      {layout.name}
                    </button>
                  </td>
                  <td role="gridcell">{getCollectionName(layout.collectionId)}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{layout.layoutType}</span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${layout.isDefault ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {layout.isDefault ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(layout.createdAt), {
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
                        onClick={() => handleViewLayout(layout)}
                        aria-label={`View ${layout.name}`}
                        data-testid={`view-button-${index}`}
                      >
                        <Eye size={14} />
                        View
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEditLayout(layout)}
                        aria-label={`Edit ${layout.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        <Pencil size={14} />
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(layout)}
                        aria-label={`Delete ${layout.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        <Trash2 size={14} />
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
        <PageLayoutForm
          layout={editingLayout}
          collections={collections}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Layout"
        message="Are you sure you want to delete this layout? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default PageLayoutsPage
