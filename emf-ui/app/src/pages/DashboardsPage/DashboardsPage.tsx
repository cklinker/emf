import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
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

import styles from './DashboardsPage.module.css'

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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="dashboard-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dashboard-form-title"
        data-testid="dashboard-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="dashboard-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="dashboard-form-close"
          >
            <X size={20} />
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="dashboard-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="dashboard-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="dashboard-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="dashboard-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter dashboard description"
                disabled={isSubmitting}
                rows={3}
                data-testid="dashboard-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="dashboard-access-level" className={styles.formLabel}>
                Access Level
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="dashboard-access-level"
                className={styles.formInput}
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

            <div className={styles.formGroup}>
              <label htmlFor="dashboard-column-count" className={styles.formLabel}>
                Column Count
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="dashboard-column-count"
                type="number"
                className={`${styles.formInput} ${touched.columnCount && errors.columnCount ? styles.hasError : ''}`}
                value={formData.columnCount}
                onChange={(e) => handleChange('columnCount', parseInt(e.target.value, 10) || 1)}
                onBlur={() => handleBlur('columnCount')}
                min={1}
                max={12}
                disabled={isSubmitting}
                data-testid="dashboard-column-count-input"
              />
              {touched.columnCount && errors.columnCount && (
                <span className={styles.formError} role="alert">
                  {errors.columnCount}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="dashboard-dynamic"
                type="checkbox"
                checked={formData.dynamic}
                onChange={(e) => handleChange('dynamic', e.target.checked)}
                disabled={isSubmitting}
                data-testid="dashboard-dynamic-input"
              />
              <label htmlFor="dashboard-dynamic" className={styles.formLabel}>
                Dynamic
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="dashboard-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="widget-config-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="widget-config-title"
        data-testid="widget-config-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="widget-config-title" className={styles.modalTitle}>
            Configure {getWidgetLabel(type)}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="widget-config-close"
          >
            <X size={20} />
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="widget-title" className={styles.formLabel}>
                Title
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="widget-title"
                type="text"
                className={styles.formInput}
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                placeholder="Enter widget title"
                data-testid="widget-title-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="widget-collection" className={styles.formLabel}>
                Collection
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="widget-collection"
                className={styles.formInput}
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
                <div className={styles.formGroup}>
                  <label htmlFor="widget-agg-field" className={styles.formLabel}>
                    Aggregation Field
                  </label>
                  <input
                    id="widget-agg-field"
                    type="text"
                    className={styles.formInput}
                    value={formData.aggregationField}
                    onChange={(e) => handleChange('aggregationField', e.target.value)}
                    placeholder="Leave empty for COUNT of all records"
                    data-testid="widget-agg-field-input"
                  />
                </div>
                <div className={styles.formGroup}>
                  <label htmlFor="widget-agg-function" className={styles.formLabel}>
                    Aggregation Function
                  </label>
                  <select
                    id="widget-agg-function"
                    className={styles.formInput}
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
                <div className={styles.formGroup}>
                  <label htmlFor="widget-category-field" className={styles.formLabel}>
                    Category Field
                  </label>
                  <input
                    id="widget-category-field"
                    type="text"
                    className={styles.formInput}
                    value={formData.categoryField}
                    onChange={(e) => handleChange('categoryField', e.target.value)}
                    placeholder="Field to group by"
                    data-testid="widget-category-field-input"
                  />
                </div>
                <div className={styles.formGroup}>
                  <label htmlFor="widget-value-field" className={styles.formLabel}>
                    Value Field
                  </label>
                  <input
                    id="widget-value-field"
                    type="text"
                    className={styles.formInput}
                    value={formData.valueField}
                    onChange={(e) => handleChange('valueField', e.target.value)}
                    placeholder="Field to aggregate (optional for COUNT)"
                    data-testid="widget-value-field-input"
                  />
                </div>
                <div className={styles.formGroup}>
                  <label htmlFor="widget-bar-agg-function" className={styles.formLabel}>
                    Aggregation Function
                  </label>
                  <select
                    id="widget-bar-agg-function"
                    className={styles.formInput}
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
                <div className={styles.formGroup}>
                  <label htmlFor="widget-columns" className={styles.formLabel}>
                    Columns (comma-separated field names)
                  </label>
                  <input
                    id="widget-columns"
                    type="text"
                    className={styles.formInput}
                    value={formData.columns}
                    onChange={(e) => handleChange('columns', e.target.value)}
                    placeholder="e.g., name, email, status"
                    data-testid="widget-columns-input"
                  />
                </div>
                <div className={styles.formGroup}>
                  <label htmlFor="widget-row-limit" className={styles.formLabel}>
                    Row Limit
                  </label>
                  <input
                    id="widget-row-limit"
                    type="number"
                    className={styles.formInput}
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
                <div className={styles.formGroup}>
                  <label htmlFor="widget-display-fields" className={styles.formLabel}>
                    Display Fields (comma-separated)
                  </label>
                  <input
                    id="widget-display-fields"
                    type="text"
                    className={styles.formInput}
                    value={formData.displayFields}
                    onChange={(e) => handleChange('displayFields', e.target.value)}
                    placeholder="e.g., name, createdAt"
                    data-testid="widget-display-fields-input"
                  />
                </div>
                <div className={styles.formGroup}>
                  <label htmlFor="widget-recent-row-limit" className={styles.formLabel}>
                    Row Limit
                  </label>
                  <input
                    id="widget-recent-row-limit"
                    type="number"
                    className={styles.formInput}
                    value={formData.rowLimit}
                    onChange={(e) => handleChange('rowLimit', parseInt(e.target.value, 10) || 5)}
                    min={1}
                    max={100}
                    data-testid="widget-recent-row-limit-input"
                  />
                </div>
              </>
            )}

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                data-testid="widget-config-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
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
      <div className={styles.widgetLoading} data-testid={`widget-loading-${component.id}`}>
        <Loader2 size={24} className={styles.spinner} />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.widgetError} data-testid={`widget-error-${component.id}`}>
        <AlertCircle size={20} />
        <span>Failed to load data</span>
      </div>
    )
  }

  const records = data?.data || []
  const value = computeAggregation(records, aggField, aggFn)

  return (
    <div className={styles.metricCard} data-testid={`widget-metric-${component.id}`}>
      <div className={styles.metricValue}>{formatNumber(value)}</div>
      <div className={styles.metricLabel}>{component.title}</div>
      <div className={styles.metricMeta}>
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
      <div className={styles.widgetLoading} data-testid={`widget-loading-${component.id}`}>
        <Loader2 size={24} className={styles.spinner} />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.widgetError} data-testid={`widget-error-${component.id}`}>
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
    <div className={styles.barChart} data-testid={`widget-bar-${component.id}`}>
      <div className={styles.barChartTitle}>{component.title}</div>
      <div className={styles.barChartBody}>
        {bars.length === 0 ? (
          <div className={styles.widgetEmpty}>No data</div>
        ) : (
          bars.map((bar) => (
            <div key={bar.label} className={styles.barRow}>
              <div className={styles.barLabel} title={bar.label}>
                {truncate(bar.label, 20)}
              </div>
              <div className={styles.barTrack}>
                <div
                  className={styles.barFill}
                  style={{ width: `${maxValue > 0 ? (bar.value / maxValue) * 100 : 0}%` }}
                />
              </div>
              <div className={styles.barValue}>{formatNumber(bar.value)}</div>
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
      <div className={styles.widgetLoading} data-testid={`widget-loading-${component.id}`}>
        <Loader2 size={24} className={styles.spinner} />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.widgetError} data-testid={`widget-error-${component.id}`}>
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
    <div className={styles.dataTableWidget} data-testid={`widget-table-${component.id}`}>
      <div className={styles.dataTableTitle}>{component.title}</div>
      {records.length === 0 ? (
        <div className={styles.widgetEmpty}>No records</div>
      ) : (
        <div className={styles.dataTableContainer}>
          <table className={styles.dataTable}>
            <thead>
              <tr>
                {displayColumns.map((col) => (
                  <th key={col}>{col}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {records.map((record, idx) => (
                <tr key={(record.id as string) || idx}>
                  {displayColumns.map((col) => (
                    <td key={col}>{record[col] != null ? String(record[col]) : ''}</td>
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
      <div className={styles.widgetLoading} data-testid={`widget-loading-${component.id}`}>
        <Loader2 size={24} className={styles.spinner} />
        <span>Loading...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.widgetError} data-testid={`widget-error-${component.id}`}>
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
    <div className={styles.recentRecordsWidget} data-testid={`widget-recent-${component.id}`}>
      <div className={styles.recentRecordsTitle}>
        <Clock size={16} />
        {component.title}
      </div>
      {records.length === 0 ? (
        <div className={styles.widgetEmpty}>No recent records</div>
      ) : (
        <ul className={styles.recentRecordsList}>
          {records.map((record, idx) => (
            <li key={(record.id as string) || idx} className={styles.recentRecordItem}>
              {fields.map((field) => (
                <span key={field} className={styles.recentRecordField}>
                  <span className={styles.recentRecordFieldLabel}>{field}:</span>{' '}
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
      return <div className={styles.widgetError}>Unknown widget type</div>
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
    <div className={styles.card} data-testid={`dashboard-card-${index}`}>
      <div
        className={styles.cardClickable}
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
        <div className={styles.cardHeader}>
          <div className={styles.cardTitleRow}>
            <LayoutGrid size={18} className={styles.cardIcon} />
            <h3 className={styles.cardName}>{dashboard.name}</h3>
          </div>
          <span
            className={`${styles.accessBadge} ${styles[`access${dashboard.accessLevel}`] || ''}`}
          >
            {formatAccessLevel(dashboard.accessLevel)}
          </span>
        </div>
        {dashboard.description && (
          <p className={styles.cardDescription}>{truncate(dashboard.description, 120)}</p>
        )}
        <div className={styles.cardMeta}>
          <span className={styles.cardMetaItem}>
            <LayoutGrid size={14} />
            {dashboard.columnCount} columns
          </span>
          <span className={styles.cardMetaItem}>{dashboard.components?.length || 0} widgets</span>
        </div>
        <div className={styles.cardFooter}>
          <span className={styles.cardDate}>
            Updated{' '}
            {formatDate(new Date(dashboard.updatedAt), {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })}
          </span>
          <span className={styles.cardCreator}>by {dashboard.createdBy || 'system'}</span>
        </div>
      </div>
      <div className={styles.cardActions}>
        <button
          type="button"
          className={styles.cardActionButton}
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
          className={styles.cardActionButton}
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
          className={`${styles.cardActionButton} ${styles.cardActionDelete}`}
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
    queryFn: () => apiClient.get<Dashboard>(`/control/dashboards/${dashboardId}`),
    enabled: !!dashboardId,
  })

  if (isLoading) {
    return (
      <div className={styles.viewerContainer} data-testid="dashboard-viewer-loading">
        <LoadingSpinner size="large" label="Loading dashboard..." />
      </div>
    )
  }

  if (error || !dashboard) {
    return (
      <div className={styles.viewerContainer} data-testid="dashboard-viewer-error">
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load dashboard')}
          onRetry={() => {}}
        />
        <button
          type="button"
          className={styles.backButton}
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
    <div className={styles.viewerContainer} data-testid="dashboard-viewer">
      <div className={styles.viewerHeader}>
        <div className={styles.viewerHeaderLeft}>
          <button
            type="button"
            className={styles.backButton}
            onClick={onBack}
            data-testid="viewer-back-button"
          >
            <ArrowLeft size={16} />
            Back to List
          </button>
          <h2 className={styles.viewerTitle}>{dashboard.name}</h2>
          {dashboard.description && (
            <p className={styles.viewerDescription}>{dashboard.description}</p>
          )}
        </div>
        <button
          type="button"
          className={styles.editDashboardButton}
          onClick={onEdit}
          data-testid="viewer-edit-button"
        >
          <Pencil size={16} />
          Edit Dashboard
        </button>
      </div>

      {components.length === 0 ? (
        <div className={styles.viewerEmpty} data-testid="viewer-empty">
          <LayoutGrid size={48} />
          <p>This dashboard has no widgets yet.</p>
          <button
            type="button"
            className={styles.submitButton}
            onClick={onEdit}
            data-testid="viewer-add-widgets-button"
          >
            <Plus size={16} />
            Add Widgets
          </button>
        </div>
      ) : (
        <div
          className={styles.widgetGrid}
          style={{
            gridTemplateColumns: `repeat(${dashboard.columnCount}, 1fr)`,
          }}
          data-testid="widget-grid"
        >
          {components.map((comp) => (
            <div
              key={comp.id}
              className={styles.widgetCell}
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
      apiClient.put<Dashboard>(`/control/dashboards/${dashboard.id}`, {
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
    <div className={styles.editorContainer} data-testid="dashboard-editor">
      <div className={styles.editorHeader}>
        <div className={styles.editorHeaderLeft}>
          <button
            type="button"
            className={styles.backButton}
            onClick={onBack}
            data-testid="editor-back-button"
          >
            <ArrowLeft size={16} />
            Back to Viewer
          </button>
          <h2 className={styles.editorTitle}>Editing: {dashboard.name}</h2>
        </div>
        <button
          type="button"
          className={styles.submitButton}
          onClick={handleSave}
          disabled={!hasChanges || saveMutation.isPending}
          data-testid="editor-save-button"
        >
          {saveMutation.isPending ? 'Saving...' : 'Save'}
        </button>
      </div>

      <div className={styles.editorBody}>
        {/* Palette */}
        <div className={styles.editorPalette} data-testid="widget-palette">
          <h3 className={styles.paletteTitle}>Widget Palette</h3>
          <div className={styles.paletteList}>
            {WIDGET_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                className={styles.paletteItem}
                onClick={() => handleAddWidget(type)}
                data-testid={`palette-item-${type}`}
              >
                <span className={styles.paletteItemIcon}>{getWidgetIcon(type)}</span>
                <span className={styles.paletteItemLabel}>{getWidgetLabel(type)}</span>
                <Plus size={14} className={styles.paletteItemAdd} />
              </button>
            ))}
          </div>
        </div>

        {/* Canvas */}
        <div className={styles.editorCanvas} data-testid="editor-canvas">
          {localComponents.length === 0 ? (
            <div className={styles.canvasEmpty} data-testid="canvas-empty">
              <LayoutGrid size={48} />
              <p>No widgets added yet</p>
              <p className={styles.canvasEmptyHint}>
                Click a widget type from the palette to add it
              </p>
            </div>
          ) : (
            <div
              className={styles.canvasGrid}
              style={{
                gridTemplateColumns: `repeat(${dashboard.columnCount}, 1fr)`,
              }}
            >
              {localComponents.map((comp) => (
                <div
                  key={comp.id}
                  className={styles.canvasWidget}
                  style={{
                    gridColumn: `span ${Math.min(comp.width || 1, dashboard.columnCount)}`,
                  }}
                  data-testid={`canvas-widget-${comp.id}`}
                >
                  <div className={styles.canvasWidgetHeader}>
                    <span className={styles.canvasWidgetType}>
                      {getWidgetIcon(comp.type)}
                      {getWidgetLabel(comp.type)}
                    </span>
                    <button
                      type="button"
                      className={styles.canvasWidgetRemove}
                      onClick={() => handleRemoveWidget(comp.id)}
                      aria-label={`Remove ${comp.title}`}
                      data-testid={`remove-widget-${comp.id}`}
                    >
                      <X size={16} />
                    </button>
                  </div>
                  <div className={styles.canvasWidgetBody}>
                    <div className={styles.canvasWidgetTitle}>{comp.title}</div>
                    <div className={styles.canvasWidgetInfo}>
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
    queryFn: () => apiClient.get<Dashboard>(`/control/dashboards/${dashboardId}`),
    enabled: !!dashboardId,
  })

  const { data: collectionsData } = useQuery({
    queryKey: ['collections-list'],
    queryFn: () => apiClient.get<{ content: CollectionSummary[] }>('/control/collections'),
  })

  const collections: CollectionSummary[] = useMemo(() => {
    return collectionsData?.content || []
  }, [collectionsData])

  if (dashboardLoading) {
    return (
      <div className={styles.editorContainer} data-testid="dashboard-editor-loading">
        <LoadingSpinner size="large" label="Loading dashboard..." />
      </div>
    )
  }

  if (dashboardError || !dashboard) {
    return (
      <div className={styles.editorContainer} data-testid="dashboard-editor-error">
        <ErrorMessage
          error={
            dashboardError instanceof Error ? dashboardError : new Error('Failed to load dashboard')
          }
          onRetry={() => {}}
        />
        <button
          type="button"
          className={styles.backButton}
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
    queryFn: () => apiClient.get<Dashboard[]>(`/control/dashboards`),
  })

  const dashboardList: Dashboard[] = dashboards ?? []

  const createMutation = useMutation({
    mutationFn: (data: DashboardFormData) =>
      apiClient.post<Dashboard>(
        `/control/dashboards?userId=system`,
        data
      ),
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
      apiClient.put<Dashboard>(`/control/dashboards/${id}`, data),
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
    mutationFn: (id: string) => apiClient.delete(`/control/dashboards/${id}`),
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
      <div className={styles.container} data-testid={testId}>
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
      <div className={styles.container} data-testid={testId}>
        <DashboardEditor dashboardId={selectedDashboardId} onBack={handleBackToViewer} />
      </div>
    )
  }

  // ─── List Mode ────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading dashboards..." />
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
        <h1 className={styles.title}>Dashboards</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Dashboard"
          data-testid="add-dashboard-button"
        >
          <Plus size={18} />
          Create Dashboard
        </button>
      </header>

      {dashboardList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <LayoutGrid size={48} className={styles.emptyIcon} />
          <p className={styles.emptyTitle}>No dashboards found</p>
          <p className={styles.emptyHint}>
            Create your first dashboard to start visualizing your data.
          </p>
        </div>
      ) : (
        <div className={styles.cardGrid} data-testid="dashboards-grid">
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
