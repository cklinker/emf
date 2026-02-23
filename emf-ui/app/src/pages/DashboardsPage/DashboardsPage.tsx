import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useCollectionSummaries } from '../../hooks/useCollectionSummaries'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import {
  Plus,
  Pencil,
  Trash2,
  BarChart3,
  Hash,
  Table,
  Clock,
  ArrowLeft,
  LayoutGrid,
  Eye,
  X,
  AlertCircle,
  Loader2,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ─── Types ───────────────────────────────────────────────────────────────────

interface DashboardComponent {
  id: string
  type: 'METRIC_CARD' | 'BAR_CHART' | 'DATA_TABLE' | 'RECENT_RECORDS'
  title: string
  config: Record<string, unknown>
  position: number
  width: number
}

interface Dashboard {
  id: string
  name: string
  description: string | null
  accessLevel: string
  dynamic: boolean
  columnCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
  components: DashboardComponent[]
}

interface DashboardFormData {
  name: string
  description: string
  accessLevel: string
  dynamic: boolean
  columnCount: number
}

interface FormErrors {
  name?: string
  description?: string
  columnCount?: string
}

type ViewMode = 'list' | 'viewer' | 'editor'

type WidgetType = 'METRIC_CARD' | 'BAR_CHART' | 'DATA_TABLE' | 'RECENT_RECORDS'

interface WidgetConfigFormData {
  type: WidgetType
  title: string
  collection: string
  // METRIC_CARD
  aggregationField?: string
  aggregationFunction?: string
  // BAR_CHART
  categoryField?: string
  valueField?: string
  barAggregationFunction?: string
  // DATA_TABLE / RECENT_RECORDS
  columns?: string
  displayFields?: string
  rowLimit?: number
}

interface CollectionSummary {
  id: string
  name: string
  displayName: string
}

interface PaginatedResponse {
  content: Record<string, unknown>[]
  totalElements: number
}

export interface DashboardsPageProps {
  testId?: string
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function validateForm(data: DashboardFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.columnCount < 1 || data.columnCount > 12) {
    errors.columnCount = 'Column count must be between 1 and 12'
  }
  return errors
}

function generateComponentId(): string {
  return `widget-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function truncate(text: string, maxLen: number): string {
  if (text.length <= maxLen) return text
  return text.slice(0, maxLen) + '...'
}

function formatAccessLevel(level: string): string {
  return level.charAt(0) + level.slice(1).toLowerCase()
}

function getWidgetIcon(type: WidgetType): React.ReactElement {
  switch (type) {
    case 'METRIC_CARD':
      return <Hash size={18} />
    case 'BAR_CHART':
      return <BarChart3 size={18} />
    case 'DATA_TABLE':
      return <Table size={18} />
    case 'RECENT_RECORDS':
      return <Clock size={18} />
  }
}

function getWidgetLabel(type: WidgetType): string {
  switch (type) {
    case 'METRIC_CARD':
      return 'Metric Card'
    case 'BAR_CHART':
      return 'Bar Chart'
    case 'DATA_TABLE':
      return 'Data Table'
    case 'RECENT_RECORDS':
      return 'Recent Records'
  }
}

const WIDGET_TYPES: WidgetType[] = ['METRIC_CARD', 'BAR_CHART', 'DATA_TABLE', 'RECENT_RECORDS']

const accessBadgeVariants: Record<string, string> = {
  PRIVATE: 'text-warning-foreground bg-warning/20',
  PUBLIC: 'text-emerald-700 bg-emerald-100 dark:text-emerald-300 dark:bg-emerald-900/40',
  HIDDEN: 'text-muted-foreground bg-muted',
}

// ─── DashboardForm (Create/Edit metadata modal) ─────────────────────────────

interface DashboardFormProps {
  dashboard?: Dashboard
  onSubmit: (data: DashboardFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function DashboardForm({
  dashboard,
  onSubmit,
  onCancel,
  isSubmitting,
}: DashboardFormProps): React.ReactElement {
  const isEditing = !!dashboard
  const [formData, setFormData] = useState<DashboardFormData>({
    name: dashboard?.name ?? '',
    description: dashboard?.description ?? '',
    accessLevel: dashboard?.accessLevel ?? 'PRIVATE',
    dynamic: dashboard?.dynamic ?? false,
    columnCount: dashboard?.columnCount ?? 3,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof DashboardFormData, value: string | boolean | number) => {
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
      setTouched({ name: true, description: true, columnCount: true })
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

  const title = isEditing ? 'Edit Dashboard' : 'Create Dashboard'

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="dashboard-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dashboard-form-title"
        data-testid="dashboard-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="dashboard-form-title" className="m-0 text-xl font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="flex items-center justify-center rounded p-1.5 text-muted-foreground transition-all duration-200 hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={onCancel}
            aria-label="Close"
            data-testid="dashboard-form-close"
          >
            <X size={20} />
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="dashboard-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="dashboard-name"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.name && errors.name && 'border-destructive'
                )}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter dashboard name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="dashboard-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="dashboard-description"
                className="text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="dashboard-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3.5 py-2.5 font-inherit text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter dashboard description"
                disabled={isSubmitting}
                rows={3}
                data-testid="dashboard-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="dashboard-access-level"
                className="text-sm font-medium text-foreground"
              >
                Access Level
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="dashboard-access-level"
                className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.accessLevel}
                onChange={(e) => handleChange('accessLevel', e.target.value)}
                disabled={isSubmitting}
                data-testid="dashboard-access-level-input"
              >
                <option value="PRIVATE">Private</option>
                <option value="PUBLIC">Public</option>
                <option value="HIDDEN">Hidden</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="dashboard-column-count"
                className="text-sm font-medium text-foreground"
              >
                Column Count
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="dashboard-column-count"
                type="number"
                className={cn(
                  'rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.columnCount && errors.columnCount && 'border-destructive'
                )}
                value={formData.columnCount}
                onChange={(e) => handleChange('columnCount', parseInt(e.target.value, 10) || 1)}
                onBlur={() => handleBlur('columnCount')}
                min={1}
                max={12}
                disabled={isSubmitting}
                data-testid="dashboard-column-count-input"
              />
              {touched.columnCount && errors.columnCount && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.columnCount}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="dashboard-dynamic"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.dynamic}
                onChange={(e) => handleChange('dynamic', e.target.checked)}
                disabled={isSubmitting}
                data-testid="dashboard-dynamic-input"
              />
              <label htmlFor="dashboard-dynamic" className="text-sm font-medium text-foreground">
                Dynamic
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <button
                type="button"
                className="rounded-md border border-border bg-background px-5 py-2.5 text-sm font-medium text-foreground transition-all duration-200 hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="dashboard-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="inline-flex items-center gap-1.5 rounded-md border-none bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                disabled={isSubmitting}
                data-testid="dashboard-form-submit"
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

// ─── Widget Config Modal ─────────────────────────────────────────────────────

interface WidgetConfigModalProps {
  type: WidgetType
  collections: CollectionSummary[]
  onSave: (config: WidgetConfigFormData) => void
  onCancel: () => void
}

function WidgetConfigModal({
  type,
  collections,
  onSave,
  onCancel,
}: WidgetConfigModalProps): React.ReactElement {
  const [formData, setFormData] = useState<WidgetConfigFormData>({
    type,
    title: '',
    collection: '',
    aggregationField: '',
    aggregationFunction: 'COUNT',
    categoryField: '',
    valueField: '',
    barAggregationFunction: 'COUNT',
    columns: '',
    displayFields: '',
    rowLimit: 10,
  })

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (!formData.title.trim() || !formData.collection) return
      onSave(formData)
    },
    [formData, onSave]
  )

  const handleChange = useCallback((field: keyof WidgetConfigFormData, value: string | number) => {
    setFormData((prev) => ({ ...prev, [field]: value }))
  }, [])

  const isValid = formData.title.trim() !== '' && formData.collection !== ''

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="widget-config-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="widget-config-title"
        data-testid="widget-config-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="widget-config-title" className="m-0 text-xl font-semibold text-foreground">
            Configure {getWidgetLabel(type)}
          </h2>
          <button
            type="button"
            className="flex items-center justify-center rounded p-1.5 text-muted-foreground transition-all duration-200 hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={onCancel}
            aria-label="Close"
            data-testid="widget-config-close"
          >
            <X size={20} />
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="widget-title" className="text-sm font-medium text-foreground">
                Title
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="widget-title"
                type="text"
                className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                placeholder="Enter widget title"
                data-testid="widget-title-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="widget-collection" className="text-sm font-medium text-foreground">
                Collection
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="widget-collection"
                className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.collection}
                onChange={(e) => handleChange('collection', e.target.value)}
                data-testid="widget-collection-input"
              >
                <option value="">Select a collection...</option>
                {collections.map((c) => (
                  <option key={c.id} value={c.name}>
                    {c.displayName || c.name}
                  </option>
                ))}
              </select>
            </div>

            {type === 'METRIC_CARD' && (
              <>
                <div className="flex flex-col gap-2">
                  <label htmlFor="widget-agg-field" className="text-sm font-medium text-foreground">
                    Aggregation Field
                  </label>
                  <input
                    id="widget-agg-field"
                    type="text"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.aggregationField}
                    onChange={(e) => handleChange('aggregationField', e.target.value)}
                    placeholder="Leave empty for COUNT of all records"
                    data-testid="widget-agg-field-input"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="widget-agg-function"
                    className="text-sm font-medium text-foreground"
                  >
                    Aggregation Function
                  </label>
                  <select
                    id="widget-agg-function"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.aggregationFunction}
                    onChange={(e) => handleChange('aggregationFunction', e.target.value)}
                    data-testid="widget-agg-function-input"
                  >
                    <option value="COUNT">COUNT</option>
                    <option value="SUM">SUM</option>
                    <option value="AVG">AVG</option>
                    <option value="MIN">MIN</option>
                    <option value="MAX">MAX</option>
                  </select>
                </div>
              </>
            )}

            {type === 'BAR_CHART' && (
              <>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="widget-category-field"
                    className="text-sm font-medium text-foreground"
                  >
                    Category Field
                  </label>
                  <input
                    id="widget-category-field"
                    type="text"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.categoryField}
                    onChange={(e) => handleChange('categoryField', e.target.value)}
                    placeholder="Field to group by"
                    data-testid="widget-category-field-input"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="widget-value-field"
                    className="text-sm font-medium text-foreground"
                  >
                    Value Field
                  </label>
                  <input
                    id="widget-value-field"
                    type="text"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.valueField}
                    onChange={(e) => handleChange('valueField', e.target.value)}
                    placeholder="Field to aggregate (optional for COUNT)"
                    data-testid="widget-value-field-input"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="widget-bar-agg-function"
                    className="text-sm font-medium text-foreground"
                  >
                    Aggregation Function
                  </label>
                  <select
                    id="widget-bar-agg-function"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.barAggregationFunction}
                    onChange={(e) => handleChange('barAggregationFunction', e.target.value)}
                    data-testid="widget-bar-agg-function-input"
                  >
                    <option value="COUNT">COUNT</option>
                    <option value="SUM">SUM</option>
                    <option value="AVG">AVG</option>
                    <option value="MIN">MIN</option>
                    <option value="MAX">MAX</option>
                  </select>
                </div>
              </>
            )}

            {type === 'DATA_TABLE' && (
              <>
                <div className="flex flex-col gap-2">
                  <label htmlFor="widget-columns" className="text-sm font-medium text-foreground">
                    Columns (comma-separated field names)
                  </label>
                  <input
                    id="widget-columns"
                    type="text"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.columns}
                    onChange={(e) => handleChange('columns', e.target.value)}
                    placeholder="e.g., name, email, status"
                    data-testid="widget-columns-input"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <label htmlFor="widget-row-limit" className="text-sm font-medium text-foreground">
                    Row Limit
                  </label>
                  <input
                    id="widget-row-limit"
                    type="number"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.rowLimit}
                    onChange={(e) => handleChange('rowLimit', parseInt(e.target.value, 10) || 5)}
                    min={1}
                    max={100}
                    data-testid="widget-row-limit-input"
                  />
                </div>
              </>
            )}

            {type === 'RECENT_RECORDS' && (
              <>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="widget-display-fields"
                    className="text-sm font-medium text-foreground"
                  >
                    Display Fields (comma-separated)
                  </label>
                  <input
                    id="widget-display-fields"
                    type="text"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.displayFields}
                    onChange={(e) => handleChange('displayFields', e.target.value)}
                    placeholder="e.g., name, createdAt"
                    data-testid="widget-display-fields-input"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="widget-recent-row-limit"
                    className="text-sm font-medium text-foreground"
                  >
                    Row Limit
                  </label>
                  <input
                    id="widget-recent-row-limit"
                    type="number"
                    className="rounded-md border border-border bg-background px-3.5 py-2.5 text-sm text-foreground transition-all duration-200 focus:border-primary focus:outline-none focus:ring-[3px] focus:ring-primary/10 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                    value={formData.rowLimit}
                    onChange={(e) => handleChange('rowLimit', parseInt(e.target.value, 10) || 5)}
                    min={1}
                    max={100}
                    data-testid="widget-recent-row-limit-input"
                  />
                </div>
              </>
            )}

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <button
                type="button"
                className="rounded-md border border-border bg-background px-5 py-2.5 text-sm font-medium text-foreground transition-all duration-200 hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                onClick={onCancel}
                data-testid="widget-config-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="inline-flex items-center gap-1.5 rounded-md border-none bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                disabled={!isValid}
                data-testid="widget-config-save"
              >
                Add Widget
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

// ─── Widget Renderers ────────────────────────────────────────────────────────

function computeAggregation(
  records: Record<string, unknown>[],
  field: string | undefined,
  fn: string
): number {
  if (fn === 'COUNT' || !field) {
    return records.length
  }
  const values = records
    .map((r) => {
      const v = r[field]
      return typeof v === 'number' ? v : parseFloat(String(v))
    })
    .filter((v) => !isNaN(v))

  if (values.length === 0) return 0

  switch (fn) {
    case 'SUM':
      return values.reduce((a, b) => a + b, 0)
    case 'AVG':
      return values.reduce((a, b) => a + b, 0) / values.length
    case 'MIN':
      return Math.min(...values)
    case 'MAX':
      return Math.max(...values)
    default:
      return values.length
  }
}

function formatNumber(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString()
  return n.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

interface WidgetRendererProps {
  component: DashboardComponent
  apiClient: {
    get: <T>(url: string) => Promise<T>
  }
}

function MetricCardWidget({ component, apiClient }: WidgetRendererProps): React.ReactElement {
  const collection = component.config.collection as string
  const aggField = component.config.aggregationField as string | undefined
  const aggFn = (component.config.aggregationFunction as string) || 'COUNT'

  const { data, isLoading, error } = useQuery({
    queryKey: ['widget-data', component.id, collection],
    queryFn: () => apiClient.get<PaginatedResponse>(`/api/${collection}?page[size]=10000`),
    enabled: !!collection,
    staleTime: 30000,
  })

  if (isLoading) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-3 p-8 text-muted-foreground"
        data-testid={`widget-loading-${component.id}`}
      >
        <Loader2 size={24} className="animate-spin" />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-2 p-8 text-sm text-destructive"
        data-testid={`widget-error-${component.id}`}
      >
        <AlertCircle size={20} />
        <span>Failed to load data</span>
      </div>
    )
  }

  const records = data?.data || []
  const value = computeAggregation(records, aggField, aggFn)

  return (
    <div
      className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center"
      data-testid={`widget-metric-${component.id}`}
    >
      <div className="text-4xl font-bold leading-none text-primary">{formatNumber(value)}</div>
      <div className="text-[15px] font-semibold text-foreground">{component.title}</div>
      <div className="text-xs text-muted-foreground">
        {aggFn}
        {aggField ? ` of ${aggField}` : ''} from {collection}
      </div>
    </div>
  )
}

function BarChartWidget({ component, apiClient }: WidgetRendererProps): React.ReactElement {
  const collection = component.config.collection as string
  const categoryField = component.config.categoryField as string
  const valueField = component.config.valueField as string | undefined
  const aggFn = (component.config.barAggregationFunction as string) || 'COUNT'

  const { data, isLoading, error } = useQuery({
    queryKey: ['widget-data', component.id, collection],
    queryFn: () => apiClient.get<PaginatedResponse>(`/api/${collection}?page[size]=10000`),
    enabled: !!collection,
    staleTime: 30000,
  })

  if (isLoading) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-3 p-8 text-muted-foreground"
        data-testid={`widget-loading-${component.id}`}
      >
        <Loader2 size={24} className="animate-spin" />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-2 p-8 text-sm text-destructive"
        data-testid={`widget-error-${component.id}`}
      >
        <AlertCircle size={20} />
        <span>Failed to load data</span>
      </div>
    )
  }

  const records = data?.data || []

  // Group by category field
  const groups: Record<string, Record<string, unknown>[]> = {}
  for (const record of records) {
    const key = String(record[categoryField] ?? 'Unknown')
    if (!groups[key]) groups[key] = []
    groups[key].push(record)
  }

  // Compute aggregation per group
  const bars: { label: string; value: number }[] = Object.entries(groups).map(
    ([label, groupRecords]) => ({
      label,
      value: computeAggregation(groupRecords, valueField, aggFn),
    })
  )

  const maxValue = bars.length > 0 ? Math.max(...bars.map((b) => b.value)) : 1

  return (
    <div className="flex h-full flex-col p-5" data-testid={`widget-bar-${component.id}`}>
      <div className="mb-4 text-[15px] font-semibold text-foreground">{component.title}</div>
      <div className="flex flex-1 flex-col gap-2.5">
        {bars.length === 0 ? (
          <div className="flex items-center justify-center p-6 text-sm text-muted-foreground">
            No data
          </div>
        ) : (
          bars.map((bar) => (
            <div key={bar.label} className="flex items-center gap-3">
              <div
                className="min-w-[80px] max-w-[120px] overflow-hidden text-ellipsis whitespace-nowrap text-right text-[13px] text-muted-foreground max-md:min-w-[60px] max-md:max-w-[80px]"
                title={bar.label}
              >
                {truncate(bar.label, 20)}
              </div>
              <div className="h-5 flex-1 overflow-hidden rounded bg-muted">
                <div
                  className="h-full min-w-[2px] rounded bg-gradient-to-r from-primary to-blue-500 transition-[width] duration-400 ease-out"
                  style={{ width: `${maxValue > 0 ? (bar.value / maxValue) * 100 : 0}%` }}
                />
              </div>
              <div className="min-w-[50px] text-right text-[13px] font-semibold text-foreground">
                {formatNumber(bar.value)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

function DataTableWidget({ component, apiClient }: WidgetRendererProps): React.ReactElement {
  const collection = component.config.collection as string
  const columnsStr = (component.config.columns as string) || ''
  const rowLimit = (component.config.rowLimit as number) || 10
  const columns = columnsStr
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean)

  const { data, isLoading, error } = useQuery({
    queryKey: ['widget-data', component.id, collection, rowLimit],
    queryFn: () => apiClient.get<PaginatedResponse>(`/api/${collection}?page[size]=${rowLimit}`),
    enabled: !!collection,
    staleTime: 30000,
  })

  if (isLoading) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-3 p-8 text-muted-foreground"
        data-testid={`widget-loading-${component.id}`}
      >
        <Loader2 size={24} className="animate-spin" />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-2 p-8 text-sm text-destructive"
        data-testid={`widget-error-${component.id}`}
      >
        <AlertCircle size={20} />
        <span>Failed to load data</span>
      </div>
    )
  }

  const records = data?.data || []
  const displayColumns =
    columns.length > 0
      ? columns
      : records.length > 0
        ? Object.keys(records[0])
            .filter((k) => k !== 'id')
            .slice(0, 5)
        : []

  return (
    <div className="flex h-full flex-col" data-testid={`widget-table-${component.id}`}>
      <div className="px-5 pb-3 pt-4 text-[15px] font-semibold text-foreground">
        {component.title}
      </div>
      {records.length === 0 ? (
        <div className="flex items-center justify-center p-6 text-sm text-muted-foreground">
          No records
        </div>
      ) : (
        <div className="flex-1 overflow-x-auto">
          <table className="w-full border-collapse text-[13px]">
            <thead className="bg-muted">
              <tr>
                {displayColumns.map((col) => (
                  <th
                    key={col}
                    className="border-b border-border px-3 py-2 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground"
                  >
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {records.map((record, idx) => (
                <tr
                  key={(record.id as string) || idx}
                  className="hover:bg-accent/50 [&:last-child>td]:border-b-0"
                >
                  {displayColumns.map((col) => (
                    <td
                      key={col}
                      className="max-w-[200px] overflow-hidden text-ellipsis whitespace-nowrap border-b border-border/50 px-3 py-2 text-foreground"
                    >
                      {record[col] != null ? String(record[col]) : ''}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function RecentRecordsWidget({ component, apiClient }: WidgetRendererProps): React.ReactElement {
  const collection = component.config.collection as string
  const displayFieldsStr = (component.config.displayFields as string) || ''
  const rowLimit = (component.config.rowLimit as number) || 10
  const displayFields = displayFieldsStr
    .split(',')
    .map((f) => f.trim())
    .filter(Boolean)

  const { data, isLoading, error } = useQuery({
    queryKey: ['widget-data', component.id, collection, rowLimit, 'recent'],
    queryFn: () =>
      apiClient.get<PaginatedResponse>(`/api/${collection}?sort=-createdAt&page[size]=${rowLimit}`),
    enabled: !!collection,
    staleTime: 30000,
  })

  if (isLoading) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-3 p-8 text-muted-foreground"
        data-testid={`widget-loading-${component.id}`}
      >
        <Loader2 size={24} className="animate-spin" />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div
        className="flex h-full min-h-[120px] flex-col items-center justify-center gap-2 p-8 text-sm text-destructive"
        data-testid={`widget-error-${component.id}`}
      >
        <AlertCircle size={20} />
        <span>Failed to load data</span>
      </div>
    )
  }

  const records = data?.data || []
  const fields =
    displayFields.length > 0
      ? displayFields
      : records.length > 0
        ? Object.keys(records[0])
            .filter((k) => k !== 'id')
            .slice(0, 4)
        : []

  return (
    <div className="flex h-full flex-col" data-testid={`widget-recent-${component.id}`}>
      <div className="flex items-center gap-2 px-5 pb-3 pt-4 text-[15px] font-semibold text-foreground">
        <Clock size={16} />
        {component.title}
      </div>
      {records.length === 0 ? (
        <div className="flex items-center justify-center p-6 text-sm text-muted-foreground">
          No recent records
        </div>
      ) : (
        <ul className="m-0 flex-1 list-none p-0">
          {records.map((record, idx) => (
            <li
              key={(record.id as string) || idx}
              className="flex flex-wrap gap-3 border-b border-border/50 px-5 py-2.5 text-[13px] transition-colors duration-150 last:border-b-0 hover:bg-accent/50"
            >
              {fields.map((field) => (
                <span key={field} className="inline-flex gap-1 text-foreground">
                  <span className="font-medium text-muted-foreground">{field}:</span>{' '}
                  {record[field] != null ? String(record[field]) : '-'}
                </span>
              ))}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function WidgetRenderer(props: WidgetRendererProps): React.ReactElement {
  switch (props.component.type) {
    case 'METRIC_CARD':
      return <MetricCardWidget {...props} />
    case 'BAR_CHART':
      return <BarChartWidget {...props} />
    case 'DATA_TABLE':
      return <DataTableWidget {...props} />
    case 'RECENT_RECORDS':
      return <RecentRecordsWidget {...props} />
    default:
      return (
        <div className="flex h-full min-h-[120px] flex-col items-center justify-center gap-2 p-8 text-sm text-destructive">
          Unknown widget type
        </div>
      )
  }
}

// ─── Dashboard Card Grid (List View) ─────────────────────────────────────────

interface DashboardCardProps {
  dashboard: Dashboard
  onView: (dashboard: Dashboard) => void
  onEdit: (dashboard: Dashboard) => void
  onDelete: (dashboard: Dashboard) => void
  formatDate: (date: Date, options?: Intl.DateTimeFormatOptions) => string
  index: number
}

function DashboardCard({
  dashboard,
  onView,
  onEdit,
  onDelete,
  formatDate,
  index,
}: DashboardCardProps): React.ReactElement {
  return (
    <div
      className="flex flex-col overflow-hidden rounded-lg border border-border bg-card transition-all duration-200 hover:border-primary hover:shadow-md"
      data-testid={`dashboard-card-${index}`}
    >
      <div
        className="flex flex-1 cursor-pointer flex-col gap-3 p-5 focus-visible:rounded-t-lg focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary"
        onClick={() => onView(dashboard)}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            onView(dashboard)
          }
        }}
        data-testid={`dashboard-card-view-${index}`}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2">
            <LayoutGrid size={18} className="shrink-0 text-primary" />
            <h3 className="m-0 overflow-hidden text-ellipsis whitespace-nowrap text-base font-semibold text-foreground">
              {dashboard.name}
            </h3>
          </div>
          <span
            className={cn(
              'inline-block shrink-0 rounded-full px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider',
              accessBadgeVariants[dashboard.accessLevel] || 'text-primary bg-muted'
            )}
          >
            {formatAccessLevel(dashboard.accessLevel)}
          </span>
        </div>
        {dashboard.description && (
          <p className="m-0 text-[13px] leading-relaxed text-muted-foreground">
            {truncate(dashboard.description, 120)}
          </p>
        )}
        <div className="flex gap-4 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            <LayoutGrid size={14} />
            {dashboard.columnCount} columns
          </span>
          <span className="inline-flex items-center gap-1">
            {dashboard.components?.length || 0} widgets
          </span>
        </div>
        <div className="mt-auto flex items-center justify-between border-t border-border/50 pt-2 text-xs text-muted-foreground">
          <span>
            Updated{' '}
            {formatDate(new Date(dashboard.updatedAt), {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })}
          </span>
          <span>by {dashboard.createdBy || 'system'}</span>
        </div>
      </div>
      <div className="flex border-t border-border">
        <button
          type="button"
          className="flex flex-1 items-center justify-center gap-1.5 border-r border-border bg-transparent px-2 py-2.5 text-[13px] font-medium text-muted-foreground transition-all duration-150 hover:bg-muted hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary"
          onClick={(e) => {
            e.stopPropagation()
            onView(dashboard)
          }}
          aria-label={`View ${dashboard.name}`}
          data-testid={`view-button-${index}`}
        >
          <Eye size={16} />
          View
        </button>
        <button
          type="button"
          className="flex flex-1 items-center justify-center gap-1.5 border-r border-border bg-transparent px-2 py-2.5 text-[13px] font-medium text-muted-foreground transition-all duration-150 hover:bg-muted hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary"
          onClick={(e) => {
            e.stopPropagation()
            onEdit(dashboard)
          }}
          aria-label={`Edit ${dashboard.name}`}
          data-testid={`edit-button-${index}`}
        >
          <Pencil size={16} />
          Edit
        </button>
        <button
          type="button"
          className="flex flex-1 items-center justify-center gap-1.5 bg-transparent px-2 py-2.5 text-[13px] font-medium text-muted-foreground transition-all duration-150 hover:bg-destructive/10 hover:text-destructive focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary"
          onClick={(e) => {
            e.stopPropagation()
            onDelete(dashboard)
          }}
          aria-label={`Delete ${dashboard.name}`}
          data-testid={`delete-button-${index}`}
        >
          <Trash2 size={16} />
          Delete
        </button>
      </div>
    </div>
  )
}

// ─── Dashboard Viewer ────────────────────────────────────────────────────────

interface DashboardViewerProps {
  dashboardId: string
  onBack: () => void
  onEdit: () => void
}

function DashboardViewer({
  dashboardId,
  onBack,
  onEdit,
}: DashboardViewerProps): React.ReactElement {
  const { apiClient } = useApi()

  const {
    data: dashboard,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['dashboard', dashboardId],
    queryFn: () => apiClient.getOne<Dashboard>(`/api/dashboards/${dashboardId}`),
    enabled: !!dashboardId,
  })

  if (isLoading) {
    return (
      <div className="flex flex-col gap-6" data-testid="dashboard-viewer-loading">
        <LoadingSpinner size="large" label="Loading dashboard..." />
      </div>
    )
  }

  if (error || !dashboard) {
    return (
      <div className="flex flex-col gap-6" data-testid="dashboard-viewer-error">
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load dashboard')}
          onRetry={() => {}}
        />
        <button
          type="button"
          className="inline-flex w-fit items-center gap-1.5 rounded-md border border-border bg-transparent px-4 py-2 text-sm font-medium text-muted-foreground transition-all duration-150 hover:border-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          onClick={onBack}
          data-testid="viewer-back-button"
        >
          <ArrowLeft size={16} />
          Back to List
        </button>
      </div>
    )
  }

  const components = [...(dashboard.components || [])].sort(
    (a, b) => (a.position ?? 0) - (b.position ?? 0)
  )

  return (
    <div className="flex flex-col gap-6" data-testid="dashboard-viewer">
      <div className="flex items-start justify-between gap-4 max-md:flex-col">
        <div className="flex flex-col gap-2">
          <button
            type="button"
            className="inline-flex w-fit items-center gap-1.5 rounded-md border border-border bg-transparent px-4 py-2 text-sm font-medium text-muted-foreground transition-all duration-150 hover:border-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={onBack}
            data-testid="viewer-back-button"
          >
            <ArrowLeft size={16} />
            Back to List
          </button>
          <h2 className="m-0 text-2xl font-semibold text-foreground">{dashboard.name}</h2>
          {dashboard.description && (
            <p className="m-0 text-sm text-muted-foreground">{dashboard.description}</p>
          )}
        </div>
        <button
          type="button"
          className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-primary bg-transparent px-5 py-2.5 text-sm font-medium text-primary transition-all duration-150 hover:bg-primary/5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          onClick={onEdit}
          data-testid="viewer-edit-button"
        >
          <Pencil size={16} />
          Edit Dashboard
        </button>
      </div>

      {components.length === 0 ? (
        <div
          className="flex flex-col items-center justify-center gap-4 rounded-lg border-2 border-dashed border-border bg-card p-16 text-center text-muted-foreground"
          data-testid="viewer-empty"
        >
          <LayoutGrid size={48} />
          <p className="m-0 text-base text-muted-foreground">This dashboard has no widgets yet.</p>
          <button
            type="button"
            className="inline-flex items-center gap-1.5 rounded-md border-none bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={onEdit}
            data-testid="viewer-add-widgets-button"
          >
            <Plus size={16} />
            Add Widgets
          </button>
        </div>
      ) : (
        <div
          className="grid gap-5"
          style={{
            gridTemplateColumns: `repeat(${dashboard.columnCount}, 1fr)`,
          }}
          data-testid="widget-grid"
        >
          {components.map((comp) => (
            <div
              key={comp.id}
              className="min-h-[160px] overflow-hidden rounded-lg border border-border bg-card"
              style={{
                gridColumn: `span ${Math.min(comp.width || 1, dashboard.columnCount)}`,
              }}
              data-testid={`widget-cell-${comp.id}`}
            >
              <WidgetRenderer component={comp} apiClient={apiClient} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ─── Dashboard Editor ────────────────────────────────────────────────────────

interface DashboardEditorProps {
  dashboardId: string
  onBack: () => void
}

interface DashboardEditorInnerProps {
  dashboard: Dashboard
  collections: CollectionSummary[]
  onBack: () => void
}

function DashboardEditorInner({
  dashboard,
  collections,
  onBack,
}: DashboardEditorInnerProps): React.ReactElement {
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [widgetConfigType, setWidgetConfigType] = useState<WidgetType | null>(null)

  const initialComponents = useMemo(
    () => [...(dashboard.components || [])].sort((a, b) => (a.position ?? 0) - (b.position ?? 0)),
    [dashboard.components]
  )

  const [localComponents, setLocalComponents] = useState<DashboardComponent[]>(initialComponents)
  const [hasChanges, setHasChanges] = useState(false)

  const saveMutation = useMutation({
    mutationFn: (components: DashboardComponent[]) =>
      apiClient.putResource<Dashboard>(`/api/dashboards/${dashboard.id}`, {
        name: dashboard.name,
        description: dashboard.description,
        accessLevel: dashboard.accessLevel,
        dynamic: dashboard.dynamic,
        columnCount: dashboard.columnCount,
        components,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard', dashboard.id] })
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard saved successfully', 'success')
      setHasChanges(false)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to save dashboard', 'error')
    },
  })

  const handleAddWidget = useCallback((type: WidgetType) => {
    setWidgetConfigType(type)
  }, [])

  const handleWidgetConfigSave = useCallback(
    (configData: WidgetConfigFormData) => {
      const config: Record<string, unknown> = {
        collection: configData.collection,
      }

      switch (configData.type) {
        case 'METRIC_CARD':
          config.aggregationField = configData.aggregationField || ''
          config.aggregationFunction = configData.aggregationFunction || 'COUNT'
          break
        case 'BAR_CHART':
          config.categoryField = configData.categoryField || ''
          config.valueField = configData.valueField || ''
          config.barAggregationFunction = configData.barAggregationFunction || 'COUNT'
          break
        case 'DATA_TABLE':
          config.columns = configData.columns || ''
          config.rowLimit = configData.rowLimit || 10
          break
        case 'RECENT_RECORDS':
          config.displayFields = configData.displayFields || ''
          config.rowLimit = configData.rowLimit || 10
          break
      }

      const newComponent: DashboardComponent = {
        id: generateComponentId(),
        type: configData.type,
        title: configData.title,
        config,
        position: localComponents.length,
        width: configData.type === 'METRIC_CARD' ? 1 : Math.min(2, dashboard.columnCount),
      }

      setLocalComponents((prev) => [...prev, newComponent])
      setHasChanges(true)
      setWidgetConfigType(null)
    },
    [localComponents.length, dashboard.columnCount]
  )

  const handleRemoveWidget = useCallback((componentId: string) => {
    setLocalComponents((prev) => {
      const filtered = prev.filter((c) => c.id !== componentId)
      return filtered.map((c, i) => ({ ...c, position: i }))
    })
    setHasChanges(true)
  }, [])

  const handleSave = useCallback(() => {
    saveMutation.mutate(localComponents)
  }, [localComponents, saveMutation])

  return (
    <div className="flex min-h-[calc(100vh-200px)] flex-col gap-4" data-testid="dashboard-editor">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-4 max-md:flex-col max-md:items-start">
          <button
            type="button"
            className="inline-flex w-fit items-center gap-1.5 rounded-md border border-border bg-transparent px-4 py-2 text-sm font-medium text-muted-foreground transition-all duration-150 hover:border-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={onBack}
            data-testid="editor-back-button"
          >
            <ArrowLeft size={16} />
            Back to Viewer
          </button>
          <h2 className="m-0 text-xl font-semibold text-foreground">Editing: {dashboard.name}</h2>
        </div>
        <button
          type="button"
          className="inline-flex items-center gap-1.5 rounded-md border-none bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          onClick={handleSave}
          disabled={!hasChanges || saveMutation.isPending}
          data-testid="editor-save-button"
        >
          {saveMutation.isPending ? 'Saving...' : 'Save'}
        </button>
      </div>

      <div className="flex flex-1 gap-6 max-md:flex-col">
        {/* Palette */}
        <div
          className="sticky top-4 w-60 shrink-0 self-start rounded-lg border border-border bg-card p-4 max-md:static max-md:w-full"
          data-testid="widget-palette"
        >
          <h3 className="m-0 mb-3 text-sm font-semibold uppercase tracking-wider text-foreground">
            Widget Palette
          </h3>
          <div className="flex flex-col gap-2 max-md:flex-row max-md:flex-wrap">
            {WIDGET_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                className="flex w-full items-center gap-2.5 rounded-md border border-border bg-muted p-3 text-left text-sm font-medium text-foreground transition-all duration-150 hover:border-primary hover:bg-primary/5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary max-md:min-w-[140px] max-md:flex-1"
                onClick={() => handleAddWidget(type)}
                data-testid={`palette-item-${type}`}
              >
                <span className="flex items-center text-primary">{getWidgetIcon(type)}</span>
                <span className="flex-1">{getWidgetLabel(type)}</span>
                <Plus size={14} className="text-muted-foreground" />
              </button>
            ))}
          </div>
        </div>

        {/* Canvas */}
        <div
          className="min-h-[400px] flex-1 rounded-lg border border-border bg-card p-5"
          data-testid="editor-canvas"
        >
          {localComponents.length === 0 ? (
            <div
              className="flex h-full min-h-[300px] flex-col items-center justify-center gap-2 text-center text-muted-foreground"
              data-testid="canvas-empty"
            >
              <LayoutGrid size={48} />
              <p className="m-0 text-base text-muted-foreground">No widgets added yet</p>
              <p className="m-0 text-sm text-muted-foreground">
                Click a widget type from the palette to add it
              </p>
            </div>
          ) : (
            <div
              className="grid gap-4"
              style={{
                gridTemplateColumns: `repeat(${dashboard.columnCount}, 1fr)`,
              }}
            >
              {localComponents.map((comp) => (
                <div
                  key={comp.id}
                  className="overflow-hidden rounded-lg border border-border bg-muted transition-colors duration-150 hover:border-primary"
                  style={{
                    gridColumn: `span ${Math.min(comp.width || 1, dashboard.columnCount)}`,
                  }}
                  data-testid={`canvas-widget-${comp.id}`}
                >
                  <div className="flex items-center justify-between border-b border-border bg-card px-3 py-2">
                    <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-primary">
                      {getWidgetIcon(comp.type)}
                      {getWidgetLabel(comp.type)}
                    </span>
                    <button
                      type="button"
                      className="flex items-center justify-center rounded p-1 text-muted-foreground transition-all duration-150 hover:bg-destructive/10 hover:text-destructive focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                      onClick={() => handleRemoveWidget(comp.id)}
                      aria-label={`Remove ${comp.title}`}
                      data-testid={`remove-widget-${comp.id}`}
                    >
                      <X size={16} />
                    </button>
                  </div>
                  <div className="p-3.5">
                    <div className="mb-1 text-sm font-semibold text-foreground">{comp.title}</div>
                    <div className="text-xs text-muted-foreground">
                      Collection: {(comp.config.collection as string) || 'N/A'}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {widgetConfigType && (
        <WidgetConfigModal
          type={widgetConfigType}
          collections={collections}
          onSave={handleWidgetConfigSave}
          onCancel={() => setWidgetConfigType(null)}
        />
      )}
    </div>
  )
}

function DashboardEditor({ dashboardId, onBack }: DashboardEditorProps): React.ReactElement {
  const { apiClient } = useApi()

  const {
    data: dashboard,
    isLoading: dashboardLoading,
    error: dashboardError,
  } = useQuery({
    queryKey: ['dashboard', dashboardId],
    queryFn: () => apiClient.getOne<Dashboard>(`/api/dashboards/${dashboardId}`),
    enabled: !!dashboardId,
  })

  const { summaries: collections } = useCollectionSummaries()

  if (dashboardLoading) {
    return (
      <div
        className="flex min-h-[calc(100vh-200px)] flex-col gap-4"
        data-testid="dashboard-editor-loading"
      >
        <LoadingSpinner size="large" label="Loading dashboard..." />
      </div>
    )
  }

  if (dashboardError || !dashboard) {
    return (
      <div
        className="flex min-h-[calc(100vh-200px)] flex-col gap-4"
        data-testid="dashboard-editor-error"
      >
        <ErrorMessage
          error={
            dashboardError instanceof Error ? dashboardError : new Error('Failed to load dashboard')
          }
          onRetry={() => {}}
        />
        <button
          type="button"
          className="inline-flex w-fit items-center gap-1.5 rounded-md border border-border bg-transparent px-4 py-2 text-sm font-medium text-muted-foreground transition-all duration-150 hover:border-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          onClick={onBack}
          data-testid="editor-back-button"
        >
          <ArrowLeft size={16} />
          Back
        </button>
      </div>
    )
  }

  return (
    <DashboardEditorInner
      key={dashboard.id}
      dashboard={dashboard}
      collections={collections}
      onBack={onBack}
    />
  )
}

// ─── Main DashboardsPage ────────────────────────────────────────────────────

export function DashboardsPage({
  testId = 'dashboards-page',
}: DashboardsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const [selectedDashboardId, setSelectedDashboardId] = useState<string | null>(null)

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingDashboard, setEditingDashboard] = useState<Dashboard | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [dashboardToDelete, setDashboardToDelete] = useState<Dashboard | null>(null)

  const {
    data: dashboards,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['dashboards'],
    queryFn: () => apiClient.getList<Dashboard>(`/api/dashboards`),
  })

  const dashboardList: Dashboard[] = dashboards ?? []

  const createMutation = useMutation({
    mutationFn: (data: DashboardFormData) =>
      apiClient.postResource<Dashboard>(`/api/dashboards`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: DashboardFormData }) =>
      apiClient.putResource<Dashboard>(`/api/dashboards/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/dashboards/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setDashboardToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingDashboard(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEditMetadata = useCallback((dashboard: Dashboard) => {
    setEditingDashboard(dashboard)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingDashboard(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: DashboardFormData) => {
      if (editingDashboard) {
        updateMutation.mutate({ id: editingDashboard.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingDashboard, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((dashboard: Dashboard) => {
    setDashboardToDelete(dashboard)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (dashboardToDelete) {
      deleteMutation.mutate(dashboardToDelete.id)
    }
  }, [dashboardToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setDashboardToDelete(null)
  }, [])

  const handleViewDashboard = useCallback((dashboard: Dashboard) => {
    setSelectedDashboardId(dashboard.id)
    setViewMode('viewer')
  }, [])

  const handleEditDashboard = useCallback(() => {
    setViewMode('editor')
  }, [])

  const handleBackToList = useCallback(() => {
    setSelectedDashboardId(null)
    setViewMode('list')
  }, [])

  const handleBackToViewer = useCallback(() => {
    setViewMode('viewer')
  }, [])

  // ─── Viewer Mode ───────────────────────────────────────────────────────────

  if (viewMode === 'viewer' && selectedDashboardId) {
    return (
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <DashboardViewer
          dashboardId={selectedDashboardId}
          onBack={handleBackToList}
          onEdit={handleEditDashboard}
        />
      </div>
    )
  }

  // ─── Editor Mode ──────────────────────────────────────────────────────────

  if (viewMode === 'editor' && selectedDashboardId) {
    return (
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <DashboardEditor dashboardId={selectedDashboardId} onBack={handleBackToViewer} />
      </div>
    )
  }

  // ─── List Mode ────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading dashboards..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
      <header className="mb-8 flex items-center justify-between max-md:flex-col max-md:items-start max-md:gap-4">
        <h1 className="m-0 text-3xl font-semibold text-foreground">Dashboards</h1>
        <button
          type="button"
          className="inline-flex items-center gap-2 rounded-md border-none bg-primary px-6 py-3 text-base font-medium text-primary-foreground transition-colors duration-200 hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary max-md:w-full max-md:justify-center"
          onClick={handleCreate}
          aria-label="Create Dashboard"
          data-testid="add-dashboard-button"
        >
          <Plus size={18} />
          Create Dashboard
        </button>
      </header>

      {dashboardList.length === 0 ? (
        <div
          className="flex flex-col items-center gap-2 rounded-lg border border-border bg-card px-8 py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <LayoutGrid size={48} className="mb-2 text-muted-foreground" />
          <p className="m-0 text-lg font-semibold text-foreground">No dashboards found</p>
          <p className="m-0 text-sm text-muted-foreground">
            Create your first dashboard to start visualizing your data.
          </p>
        </div>
      ) : (
        <div
          className="grid grid-cols-[repeat(auto-fill,minmax(340px,1fr))] gap-6 max-md:grid-cols-1"
          data-testid="dashboards-grid"
        >
          {dashboardList.map((dashboard, index) => (
            <DashboardCard
              key={dashboard.id}
              dashboard={dashboard}
              onView={handleViewDashboard}
              onEdit={handleEditMetadata}
              onDelete={handleDeleteClick}
              formatDate={formatDate}
              index={index}
            />
          ))}
        </div>
      )}

      {isFormOpen && (
        <DashboardForm
          dashboard={editingDashboard}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Dashboard"
        message="Are you sure you want to delete this dashboard? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default DashboardsPage
