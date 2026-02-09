import React, { useState, useCallback, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import {
  Plus,
  Pencil,
  Trash2,
  Play,
  ArrowLeft,
  ArrowUp,
  ArrowDown,
  Check,
  X,
  Download,
  FileSpreadsheet,
  Filter,
  Columns,
  Group,
  SortAsc,
  ChevronDown,
  ChevronRight,
} from 'lucide-react'
import styles from './ReportsPage.module.css'

/* ─────────────────────────── Types ─────────────────────────── */

interface Report {
  id: string
  name: string
  description: string | null
  reportType: string
  primaryCollectionId: string
  columns: string | null
  filters: string | null
  groupBy?: string | null
  sortBy?: string | null
  sortDirection?: string | null
  scope: string
  accessLevel: string
  folderId: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ColumnConfig {
  fieldName: string
  label: string
  type: string
}

interface FilterConfig {
  field: string
  operator: 'equals' | 'not_equals' | 'contains' | 'greater_than' | 'less_than'
  value: string
}

interface CollectionSummary {
  id: string
  name: string
  displayName: string
  description: string | null
}

interface FieldDefinition {
  id: string
  name: string
  displayName: string
  type: string
  required: boolean
}

interface GatewayResponse {
  content: Record<string, unknown>[]
  totalElements: number
}

type ViewMode = 'list' | 'builder' | 'viewer'

/* ─────────── Builder State ─────────── */

interface BuilderState {
  collectionId: string
  collectionName: string
  selectedColumns: ColumnConfig[]
  filters: FilterConfig[]
  groupByField: string
  sortByField: string
  sortDirection: string
  reportName: string
  reportDescription: string
  reportType: string
  accessLevel: string
}

const INITIAL_BUILDER: BuilderState = {
  collectionId: '',
  collectionName: '',
  selectedColumns: [],
  filters: [],
  groupByField: '',
  sortByField: '',
  sortDirection: 'ASC',
  reportName: '',
  reportDescription: '',
  reportType: 'TABULAR',
  accessLevel: 'PRIVATE',
}

const WIZARD_STEPS = [
  { key: 'source', label: 'Source', icon: FileSpreadsheet },
  { key: 'columns', label: 'Columns', icon: Columns },
  { key: 'filters', label: 'Filters', icon: Filter },
  { key: 'groupby', label: 'Group By', icon: Group },
  { key: 'save', label: 'Sort & Save', icon: SortAsc },
]

const OPERATORS: { value: FilterConfig['operator']; label: string }[] = [
  { value: 'equals', label: 'Equals' },
  { value: 'not_equals', label: 'Not Equals' },
  { value: 'contains', label: 'Contains' },
  { value: 'greater_than', label: 'Greater Than' },
  { value: 'less_than', label: 'Less Than' },
]

const NUMERIC_TYPES = new Set([
  'number',
  'integer',
  'long',
  'double',
  'currency',
  'percent',
  'DOUBLE',
  'INTEGER',
  'LONG',
  'CURRENCY',
  'PERCENT',
  'NUMBER',
])

/* ──────────────────── Utility helpers ──────────────────── */

function isNumericType(type: string): boolean {
  return NUMERIC_TYPES.has(type) || NUMERIC_TYPES.has(type.toLowerCase())
}

function toNumber(val: unknown): number {
  if (val == null) return 0
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

function formatCellValue(val: unknown): string {
  if (val == null) return ''
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function applyFilters(
  records: Record<string, unknown>[],
  filters: FilterConfig[]
): Record<string, unknown>[] {
  if (filters.length === 0) return records
  return records.filter((record) =>
    filters.every((f) => {
      const raw = record[f.field]
      const cellStr = formatCellValue(raw).toLowerCase()
      const valStr = f.value.toLowerCase()
      switch (f.operator) {
        case 'equals':
          return cellStr === valStr
        case 'not_equals':
          return cellStr !== valStr
        case 'contains':
          return cellStr.includes(valStr)
        case 'greater_than':
          return toNumber(raw) > toNumber(f.value)
        case 'less_than':
          return toNumber(raw) < toNumber(f.value)
        default:
          return true
      }
    })
  )
}

function applySorting(
  records: Record<string, unknown>[],
  sortBy: string,
  direction: string
): Record<string, unknown>[] {
  if (!sortBy) return records
  const sorted = [...records]
  sorted.sort((a, b) => {
    const aVal = a[sortBy]
    const bVal = b[sortBy]
    if (aVal == null && bVal == null) return 0
    if (aVal == null) return 1
    if (bVal == null) return -1
    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return direction === 'DESC' ? bVal - aVal : aVal - bVal
    }
    const cmp = String(aVal).localeCompare(String(bVal))
    return direction === 'DESC' ? -cmp : cmp
  })
  return sorted
}

function generateCsv(records: Record<string, unknown>[], columns: ColumnConfig[]): string {
  const escape = (v: string) => {
    if (v.includes('"') || v.includes(',') || v.includes('\n')) {
      return `"${v.replace(/"/g, '""')}"`
    }
    return v
  }
  const header = columns.map((c) => escape(c.label)).join(',')
  const rows = records.map((r) =>
    columns.map((c) => escape(formatCellValue(r[c.fieldName]))).join(',')
  )
  return [header, ...rows].join('\n')
}

/* ──────────────────────── Component ──────────────────────── */

export interface ReportsPageProps {
  testId?: string
}

export function ReportsPage({ testId = 'reports-page' }: ReportsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  /* ────── Top-level state ────── */
  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null)
  const [builderStep, setBuilderStep] = useState(1)
  const [builder, setBuilder] = useState<BuilderState>(INITIAL_BUILDER)
  const [editingReportId, setEditingReportId] = useState<string | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [reportToDelete, setReportToDelete] = useState<Report | null>(null)
  const [collectionSearch, setCollectionSearch] = useState('')
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({})

  /* ────── Reports query ────── */
  const {
    data: reports,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['reports'],
    queryFn: () => apiClient.get<Report[]>('/control/reports?tenantId=default'),
  })

  const reportList = useMemo<Report[]>(() => reports ?? [], [reports])
  const selectedReport = useMemo(
    () => reportList.find((r) => r.id === selectedReportId) ?? null,
    [reportList, selectedReportId]
  )

  /* ────── Collections query (for builder) ────── */
  const { data: collectionsData } = useQuery({
    queryKey: ['collections-for-reports'],
    queryFn: () => apiClient.get<{ content: CollectionSummary[] }>('/control/collections?size=100'),
    enabled: viewMode === 'builder',
  })

  const collections = useMemo<CollectionSummary[]>(
    () => collectionsData?.content ?? [],
    [collectionsData]
  )

  const filteredCollections = useMemo(() => {
    if (!collectionSearch.trim()) return collections
    const q = collectionSearch.toLowerCase()
    return collections.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.displayName.toLowerCase().includes(q) ||
        (c.description ?? '').toLowerCase().includes(q)
    )
  }, [collections, collectionSearch])

  /* ────── Fields query (for builder steps 2-4) ────── */
  const { data: fieldsData } = useQuery({
    queryKey: ['fields-for-report', builder.collectionId],
    queryFn: () =>
      apiClient.get<FieldDefinition[]>(`/control/collections/${builder.collectionId}/fields`),
    enabled: !!builder.collectionId && viewMode === 'builder',
  })

  const fields = useMemo<FieldDefinition[]>(() => fieldsData ?? [], [fieldsData])

  /* ────── Gateway data query (for viewer) ────── */
  const collectionNameForViewer = useMemo(() => {
    if (!selectedReport) return ''
    const match = collections.find((c) => c.id === selectedReport.primaryCollectionId)
    return match?.name ?? ''
  }, [selectedReport, collections])

  const { data: gatewayData, isLoading: isLoadingGateway } = useQuery({
    queryKey: ['report-data', selectedReportId, collectionNameForViewer],
    queryFn: () =>
      apiClient.get<GatewayResponse>(
        `/gateway/${collectionNameForViewer}?tenantId=default&size=1000`
      ),
    enabled: viewMode === 'viewer' && !!selectedReportId && !!collectionNameForViewer,
  })

  /* Also fetch collections list in viewer mode so we can resolve names */
  useQuery({
    queryKey: ['collections-for-reports'],
    queryFn: () => apiClient.get<{ content: CollectionSummary[] }>('/control/collections?size=100'),
    enabled: viewMode === 'viewer',
  })

  /* ────── Mutations ────── */
  const createMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      apiClient.post<Report>('/control/reports?tenantId=default&userId=system', data),
    onSuccess: (created: Report) => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      showToast('Report created successfully', 'success')
      setSelectedReportId(created.id)
      setViewMode('viewer')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to create report', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      apiClient.put<Report>(`/control/reports/${id}`, data),
    onSuccess: (updated: Report) => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      showToast('Report updated successfully', 'success')
      setSelectedReportId(updated.id)
      setViewMode('viewer')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to update report', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/reports/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      showToast('Report deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setReportToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to delete report', 'error')
    },
  })

  /* ────── Processed viewer data ────── */
  const viewerColumns: ColumnConfig[] = useMemo(() => {
    if (!selectedReport?.columns) return []
    try {
      return JSON.parse(selectedReport.columns) as ColumnConfig[]
    } catch {
      return []
    }
  }, [selectedReport])

  const viewerFilters: FilterConfig[] = useMemo(() => {
    if (!selectedReport?.filters) return []
    try {
      return JSON.parse(selectedReport.filters) as FilterConfig[]
    } catch {
      return []
    }
  }, [selectedReport])

  const processedRecords = useMemo(() => {
    const raw = gatewayData?.content ?? []
    const filtered = applyFilters(raw, viewerFilters)
    const sortBy = selectedReport?.sortBy ?? ''
    const dir = selectedReport?.sortDirection ?? 'ASC'
    return applySorting(filtered, sortBy, dir)
  }, [gatewayData, viewerFilters, selectedReport])

  const viewerGroupBy = selectedReport?.groupBy ?? ''

  const groupedRecords = useMemo(() => {
    if (!viewerGroupBy) return null
    const groups: Record<string, Record<string, unknown>[]> = {}
    for (const record of processedRecords) {
      const key = formatCellValue(record[viewerGroupBy]) || '(empty)'
      if (!groups[key]) groups[key] = []
      groups[key].push(record)
    }
    return groups
  }, [processedRecords, viewerGroupBy])

  /* ────── Handlers ────── */

  const handleCreateNew = useCallback(() => {
    setBuilder(INITIAL_BUILDER)
    setEditingReportId(null)
    setBuilderStep(1)
    setCollectionSearch('')
    setViewMode('builder')
  }, [])

  const handleEditReport = useCallback(
    (report: Report) => {
      const match = collections.find((c) => c.id === report.primaryCollectionId)
      let cols: ColumnConfig[] = []
      try {
        cols = report.columns ? JSON.parse(report.columns) : []
      } catch {
        /* ignore */
      }
      let fils: FilterConfig[] = []
      try {
        fils = report.filters ? JSON.parse(report.filters) : []
      } catch {
        /* ignore */
      }
      setBuilder({
        collectionId: report.primaryCollectionId,
        collectionName: match?.name ?? '',
        selectedColumns: cols,
        filters: fils,
        groupByField: report.groupBy ?? '',
        sortByField: report.sortBy ?? '',
        sortDirection: report.sortDirection ?? 'ASC',
        reportName: report.name,
        reportDescription: report.description ?? '',
        reportType: report.reportType,
        accessLevel: report.accessLevel,
      })
      setEditingReportId(report.id)
      setBuilderStep(1)
      setCollectionSearch('')
      setViewMode('builder')
    },
    [collections]
  )

  const handleRunReport = useCallback((report: Report) => {
    setSelectedReportId(report.id)
    setExpandedGroups({})
    setViewMode('viewer')
  }, [])

  const handleDeleteClick = useCallback((report: Report) => {
    setReportToDelete(report)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (reportToDelete) {
      deleteMutation.mutate(reportToDelete.id)
    }
  }, [reportToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setReportToDelete(null)
  }, [])

  const handleBackToList = useCallback(() => {
    setViewMode('list')
    setSelectedReportId(null)
    setEditingReportId(null)
  }, [])

  /* ──── Builder step navigation ──── */

  const canProceedStep = useCallback(
    (step: number): boolean => {
      switch (step) {
        case 1:
          return !!builder.collectionId
        case 2:
          return builder.selectedColumns.length > 0
        case 3:
          return true // filters are optional
        case 4:
          return true // groupBy is optional
        case 5:
          return builder.reportName.trim().length > 0
        default:
          return false
      }
    },
    [builder]
  )

  const handleNextStep = useCallback(() => {
    if (canProceedStep(builderStep) && builderStep < 5) {
      setBuilderStep((s) => s + 1)
    }
  }, [builderStep, canProceedStep])

  const handlePrevStep = useCallback(() => {
    if (builderStep > 1) {
      setBuilderStep((s) => s - 1)
    }
  }, [builderStep])

  /* ──── Builder field handlers ──── */

  const handleSelectCollection = useCallback(
    (col: CollectionSummary) => {
      if (col.id !== builder.collectionId) {
        setBuilder((prev) => ({
          ...prev,
          collectionId: col.id,
          collectionName: col.name,
          selectedColumns: [],
          filters: [],
          groupByField: '',
          sortByField: '',
        }))
      }
    },
    [builder.collectionId]
  )

  const handleToggleColumn = useCallback((field: FieldDefinition) => {
    setBuilder((prev) => {
      const exists = prev.selectedColumns.find((c) => c.fieldName === field.name)
      if (exists) {
        return {
          ...prev,
          selectedColumns: prev.selectedColumns.filter((c) => c.fieldName !== field.name),
        }
      }
      return {
        ...prev,
        selectedColumns: [
          ...prev.selectedColumns,
          {
            fieldName: field.name,
            label: field.displayName || field.name,
            type: field.type,
          },
        ],
      }
    })
  }, [])

  const handleSelectAllColumns = useCallback(() => {
    setBuilder((prev) => {
      if (prev.selectedColumns.length === fields.length) {
        return { ...prev, selectedColumns: [] }
      }
      return {
        ...prev,
        selectedColumns: fields.map((f) => ({
          fieldName: f.name,
          label: f.displayName || f.name,
          type: f.type,
        })),
      }
    })
  }, [fields])

  const handleMoveColumn = useCallback((index: number, direction: 'up' | 'down') => {
    setBuilder((prev) => {
      const cols = [...prev.selectedColumns]
      const targetIndex = direction === 'up' ? index - 1 : index + 1
      if (targetIndex < 0 || targetIndex >= cols.length) return prev
      const temp = cols[index]
      cols[index] = cols[targetIndex]
      cols[targetIndex] = temp
      return { ...prev, selectedColumns: cols }
    })
  }, [])

  const handleAddFilter = useCallback(() => {
    const firstField = fields.length > 0 ? fields[0].name : ''
    setBuilder((prev) => ({
      ...prev,
      filters: [...prev.filters, { field: firstField, operator: 'equals' as const, value: '' }],
    }))
  }, [fields])

  const handleUpdateFilter = useCallback(
    (index: number, key: keyof FilterConfig, value: string) => {
      setBuilder((prev) => {
        const updated = [...prev.filters]
        updated[index] = { ...updated[index], [key]: value }
        return { ...prev, filters: updated }
      })
    },
    []
  )

  const handleRemoveFilter = useCallback((index: number) => {
    setBuilder((prev) => ({
      ...prev,
      filters: prev.filters.filter((_, i) => i !== index),
    }))
  }, [])

  const handleSetGroupBy = useCallback((fieldName: string) => {
    setBuilder((prev) => ({
      ...prev,
      groupByField: prev.groupByField === fieldName ? '' : fieldName,
    }))
  }, [])

  /* ──── Save / Submit ──── */

  const handleSave = useCallback(
    (andRun: boolean) => {
      if (!builder.reportName.trim()) return

      const payload: Record<string, unknown> = {
        name: builder.reportName.trim(),
        description: builder.reportDescription.trim() || null,
        reportType: builder.reportType,
        primaryCollectionId: builder.collectionId,
        columns: JSON.stringify(builder.selectedColumns),
        filters: builder.filters.length > 0 ? JSON.stringify(builder.filters) : null,
        groupBy: builder.groupByField || null,
        sortBy: builder.sortByField || null,
        sortDirection: builder.sortByField ? builder.sortDirection : null,
        scope: 'ALL_RECORDS',
        accessLevel: builder.accessLevel,
      }

      if (editingReportId) {
        updateMutation.mutate({ id: editingReportId, data: payload })
      } else {
        if (andRun) {
          createMutation.mutate(payload)
        } else {
          createMutation.mutate(payload)
        }
      }
    },
    [builder, editingReportId, createMutation, updateMutation]
  )

  /* ──── CSV Export ──── */

  const handleExportCsv = useCallback(() => {
    if (viewerColumns.length === 0 || processedRecords.length === 0) return
    const csv = generateCsv(processedRecords, viewerColumns)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${selectedReport?.name ?? 'report'}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    showToast('CSV exported successfully', 'success')
  }, [viewerColumns, processedRecords, selectedReport, showToast])

  /* ──── Group toggle ──── */

  const handleToggleGroup = useCallback((groupKey: string) => {
    setExpandedGroups((prev) => ({
      ...prev,
      [groupKey]: !prev[groupKey],
    }))
  }, [])

  /* ──── Computed summaries ──── */

  const numericColumnSums = useMemo(() => {
    const sums: Record<string, number> = {}
    for (const col of viewerColumns) {
      if (isNumericType(col.type)) {
        sums[col.fieldName] = processedRecords.reduce(
          (acc, r) => acc + toNumber(r[col.fieldName]),
          0
        )
      }
    }
    return sums
  }, [viewerColumns, processedRecords])

  /* ─────────────── Loading / Error ─────────────── */

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading reports..." />
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

  const isSaving = createMutation.isPending || updateMutation.isPending

  /* ═══════════════════════ REPORT LIST VIEW ═══════════════════════ */

  if (viewMode === 'list') {
    return (
      <div className={styles.container} data-testid={testId}>
        <header className={styles.header}>
          <h1 className={styles.title}>Reports</h1>
          <div className={styles.headerActions}>
            <button
              type="button"
              className={styles.createButton}
              onClick={handleCreateNew}
              aria-label="Create Report"
              data-testid="add-report-button"
            >
              <Plus size={18} />
              Create Report
            </button>
          </div>
        </header>

        {reportList.length === 0 ? (
          <div className={styles.emptyState} data-testid="empty-state">
            <div className={styles.emptyIcon}>
              <FileSpreadsheet size={48} />
            </div>
            <p>No reports found. Create your first report to get started.</p>
          </div>
        ) : (
          <div className={styles.cardsGrid} data-testid="reports-grid">
            {reportList.map((report, index) => {
              const collectionMatch = collections.find((c) => c.id === report.primaryCollectionId)
              const collectionLabel = collectionMatch?.displayName ?? report.primaryCollectionId
              return (
                <div
                  key={report.id}
                  className={styles.reportCard}
                  data-testid={`report-card-${index}`}
                >
                  <div className={styles.cardHeader}>
                    <h3 className={styles.cardTitle}>{report.name}</h3>
                  </div>
                  {report.description && (
                    <p className={styles.cardDescription}>{report.description}</p>
                  )}
                  <div className={styles.cardMeta}>
                    <span className={`${styles.badge} ${styles.badgeType}`}>
                      {report.reportType}
                    </span>
                    <span className={`${styles.badge} ${styles.badgeScope}`}>{report.scope}</span>
                    <span className={`${styles.badge} ${styles.badgeAccess}`}>
                      {report.accessLevel}
                    </span>
                    <span className={styles.cardMetaText}>{collectionLabel}</span>
                    <span className={styles.cardMetaText}>
                      Modified{' '}
                      {formatDate(new Date(report.updatedAt), {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </span>
                  </div>
                  <div className={styles.cardActions}>
                    <button
                      type="button"
                      className={`${styles.cardActionButton} ${styles.runButton}`}
                      onClick={() => handleRunReport(report)}
                      aria-label={`Run ${report.name}`}
                      data-testid={`run-button-${index}`}
                    >
                      <Play size={14} />
                      Run
                    </button>
                    <button
                      type="button"
                      className={styles.cardActionButton}
                      onClick={() => handleEditReport(report)}
                      aria-label={`Edit ${report.name}`}
                      data-testid={`edit-button-${index}`}
                    >
                      <Pencil size={14} />
                      Edit
                    </button>
                    <button
                      type="button"
                      className={`${styles.cardActionButton} ${styles.deleteCardButton}`}
                      onClick={() => handleDeleteClick(report)}
                      aria-label={`Delete ${report.name}`}
                      data-testid={`delete-button-${index}`}
                    >
                      <Trash2 size={14} />
                      Delete
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        <ConfirmDialog
          open={deleteDialogOpen}
          title="Delete Report"
          message="Are you sure you want to delete this report? This action cannot be undone."
          confirmLabel="Delete"
          cancelLabel="Cancel"
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteCancel}
          variant="danger"
        />
      </div>
    )
  }

  /* ═══════════════════════ REPORT BUILDER VIEW ═══════════════════════ */

  if (viewMode === 'builder') {
    const renderStepIndicator = () => (
      <div className={styles.stepIndicator} data-testid="step-indicator">
        {WIZARD_STEPS.map((step, i) => {
          const stepNum = i + 1
          const isActive = stepNum === builderStep
          const isComplete = stepNum < builderStep
          return (
            <React.Fragment key={step.key}>
              {i > 0 && (
                <div
                  className={`${styles.stepConnector} ${
                    isComplete ? styles.stepConnectorComplete : ''
                  }`}
                />
              )}
              <div className={styles.stepItem}>
                <div
                  className={`${styles.stepCircle} ${
                    isActive ? styles.stepCircleActive : isComplete ? styles.stepCircleComplete : ''
                  }`}
                  data-testid={`step-circle-${stepNum}`}
                >
                  {isComplete ? <Check size={14} /> : stepNum}
                </div>
                <span
                  className={`${styles.stepLabel} ${
                    isActive ? styles.stepLabelActive : isComplete ? styles.stepLabelComplete : ''
                  }`}
                >
                  {step.label}
                </span>
              </div>
            </React.Fragment>
          )
        })}
      </div>
    )

    const renderStep1Source = () => (
      <div>
        <h2 className={styles.stepTitle}>Select a Collection</h2>
        <p className={styles.stepDescription}>Choose the collection to build your report on.</p>
        <input
          type="text"
          className={styles.collectionSearchInput}
          placeholder="Search collections..."
          value={collectionSearch}
          onChange={(e) => setCollectionSearch(e.target.value)}
          data-testid="collection-search-input"
        />
        <div className={styles.collectionsList} data-testid="collections-list">
          {filteredCollections.length === 0 && (
            <p className={styles.cardMetaText}>No collections found.</p>
          )}
          {filteredCollections.map((col) => {
            const isSelected = col.id === builder.collectionId
            return (
              <div
                key={col.id}
                className={`${styles.collectionItem} ${
                  isSelected ? styles.collectionItemSelected : ''
                }`}
                onClick={() => handleSelectCollection(col)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault()
                    handleSelectCollection(col)
                  }
                }}
                data-testid={`collection-item-${col.name}`}
              >
                <div
                  className={`${styles.collectionItemRadio} ${
                    isSelected ? styles.collectionItemRadioSelected : ''
                  }`}
                >
                  {isSelected && <div className={styles.collectionItemRadioDot} />}
                </div>
                <div className={styles.collectionItemInfo}>
                  <div className={styles.collectionItemName}>{col.displayName || col.name}</div>
                  {col.description && (
                    <div className={styles.collectionItemDesc}>{col.description}</div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    )

    const renderStep2Columns = () => (
      <div>
        <h2 className={styles.stepTitle}>Select Columns</h2>
        <p className={styles.stepDescription}>
          Choose which fields to display in the report. At least one column is required.
        </p>
        {fields.length === 0 ? (
          <p className={styles.cardMetaText}>No fields found for this collection.</p>
        ) : (
          <>
            <div className={styles.selectAllRow}>
              <input
                type="checkbox"
                className={styles.columnCheckbox}
                checked={builder.selectedColumns.length === fields.length && fields.length > 0}
                onChange={handleSelectAllColumns}
                data-testid="select-all-columns"
              />
              <span className={styles.selectAllLabel}>
                Select All ({builder.selectedColumns.length} / {fields.length})
              </span>
            </div>
            <div className={styles.columnsContainer} data-testid="columns-list">
              {/* Show selected columns first (in order), then unselected */}
              {[
                ...(builder.selectedColumns
                  .map((sc) => {
                    const f = fields.find((ff) => ff.name === sc.fieldName)
                    return f
                      ? {
                          field: f,
                          selected: true,
                          orderIndex: builder.selectedColumns.indexOf(sc),
                        }
                      : null
                  })
                  .filter(Boolean) as {
                  field: FieldDefinition
                  selected: boolean
                  orderIndex: number
                }[]),
                ...fields
                  .filter((f) => !builder.selectedColumns.find((sc) => sc.fieldName === f.name))
                  .map((f) => ({
                    field: f,
                    selected: false,
                    orderIndex: -1,
                  })),
              ].map((item) => {
                const { field, selected } = item
                const selectedIndex = builder.selectedColumns.findIndex(
                  (c) => c.fieldName === field.name
                )
                return (
                  <div
                    key={field.id}
                    className={styles.columnItem}
                    data-testid={`column-item-${field.name}`}
                  >
                    <input
                      type="checkbox"
                      className={styles.columnCheckbox}
                      checked={selected}
                      onChange={() => handleToggleColumn(field)}
                      data-testid={`column-checkbox-${field.name}`}
                    />
                    <span className={styles.columnLabel}>{field.displayName || field.name}</span>
                    <span className={styles.columnType}>{field.type}</span>
                    {selected && (
                      <div className={styles.columnMoveButtons}>
                        <button
                          type="button"
                          className={styles.moveButton}
                          onClick={() => handleMoveColumn(selectedIndex, 'up')}
                          disabled={selectedIndex <= 0}
                          aria-label={`Move ${field.name} up`}
                          data-testid={`move-up-${field.name}`}
                        >
                          <ArrowUp size={12} />
                        </button>
                        <button
                          type="button"
                          className={styles.moveButton}
                          onClick={() => handleMoveColumn(selectedIndex, 'down')}
                          disabled={selectedIndex >= builder.selectedColumns.length - 1}
                          aria-label={`Move ${field.name} down`}
                          data-testid={`move-down-${field.name}`}
                        >
                          <ArrowDown size={12} />
                        </button>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </>
        )}
      </div>
    )

    const renderStep3Filters = () => (
      <div>
        <h2 className={styles.stepTitle}>Add Filters</h2>
        <p className={styles.stepDescription}>
          Optionally filter the data. Multiple filters use AND logic.
        </p>
        <div className={styles.filtersContainer} data-testid="filters-container">
          {builder.filters.length === 0 && (
            <p className={styles.noFiltersHint}>
              No filters added. Click the button below to add a filter condition.
            </p>
          )}
          {builder.filters.map((filter, index) => (
            <div key={index} className={styles.filterRow} data-testid={`filter-row-${index}`}>
              <select
                className={styles.filterSelect}
                value={filter.field}
                onChange={(e) => handleUpdateFilter(index, 'field', e.target.value)}
                data-testid={`filter-field-${index}`}
              >
                {fields.map((f) => (
                  <option key={f.id} value={f.name}>
                    {f.displayName || f.name}
                  </option>
                ))}
              </select>
              <select
                className={styles.filterSelect}
                value={filter.operator}
                onChange={(e) => handleUpdateFilter(index, 'operator', e.target.value)}
                data-testid={`filter-operator-${index}`}
              >
                {OPERATORS.map((op) => (
                  <option key={op.value} value={op.value}>
                    {op.label}
                  </option>
                ))}
              </select>
              <input
                type="text"
                className={styles.filterInput}
                value={filter.value}
                onChange={(e) => handleUpdateFilter(index, 'value', e.target.value)}
                placeholder="Value"
                data-testid={`filter-value-${index}`}
              />
              <button
                type="button"
                className={styles.filterRemoveButton}
                onClick={() => handleRemoveFilter(index)}
                aria-label={`Remove filter ${index + 1}`}
                data-testid={`filter-remove-${index}`}
              >
                <X size={14} />
              </button>
            </div>
          ))}
          <button
            type="button"
            className={styles.addFilterButton}
            onClick={handleAddFilter}
            data-testid="add-filter-button"
          >
            <Plus size={14} />
            Add Filter
          </button>
        </div>
      </div>
    )

    const renderStep4GroupBy = () => {
      const groupableFields = fields.filter(
        (f) => builder.selectedColumns.find((c) => c.fieldName === f.name) !== undefined
      )
      return (
        <div>
          <h2 className={styles.stepTitle}>Group By</h2>
          <p className={styles.stepDescription}>
            Optionally group records by a field. Numeric columns will show subtotals.
          </p>
          <div className={styles.groupByContainer} data-testid="groupby-container">
            <div
              className={`${styles.groupByItem} ${
                builder.groupByField === '' ? styles.groupByItemSelected : ''
              }`}
              onClick={() => handleSetGroupBy('')}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  handleSetGroupBy('')
                }
              }}
              data-testid="groupby-none"
            >
              <div
                className={`${styles.collectionItemRadio} ${
                  builder.groupByField === '' ? styles.collectionItemRadioSelected : ''
                }`}
              >
                {builder.groupByField === '' && <div className={styles.collectionItemRadioDot} />}
              </div>
              <span className={styles.columnLabel}>None (no grouping)</span>
            </div>
            {groupableFields.map((field) => {
              const isSelected = builder.groupByField === field.name
              return (
                <div
                  key={field.id}
                  className={`${styles.groupByItem} ${
                    isSelected ? styles.groupByItemSelected : ''
                  }`}
                  onClick={() => handleSetGroupBy(field.name)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      handleSetGroupBy(field.name)
                    }
                  }}
                  data-testid={`groupby-field-${field.name}`}
                >
                  <div
                    className={`${styles.collectionItemRadio} ${
                      isSelected ? styles.collectionItemRadioSelected : ''
                    }`}
                  >
                    {isSelected && <div className={styles.collectionItemRadioDot} />}
                  </div>
                  <span className={styles.columnLabel}>{field.displayName || field.name}</span>
                  <span className={styles.columnType}>{field.type}</span>
                </div>
              )
            })}
          </div>
        </div>
      )
    }

    const renderStep5Save = () => {
      const sortableFields = fields.filter(
        (f) => builder.selectedColumns.find((c) => c.fieldName === f.name) !== undefined
      )
      return (
        <div>
          <h2 className={styles.stepTitle}>Sort & Save</h2>
          <p className={styles.stepDescription}>Configure sorting and save your report.</p>
          <div className={styles.saveForm}>
            {/* Sort */}
            <div className={styles.formRow}>
              <div className={styles.formRowHalf}>
                <div className={styles.formGroup}>
                  <label htmlFor="sort-by-field" className={styles.formLabel}>
                    Sort By
                  </label>
                  <select
                    id="sort-by-field"
                    className={styles.formSelect}
                    value={builder.sortByField}
                    onChange={(e) =>
                      setBuilder((prev) => ({
                        ...prev,
                        sortByField: e.target.value,
                      }))
                    }
                    data-testid="sort-by-select"
                  >
                    <option value="">None</option>
                    {sortableFields.map((f) => (
                      <option key={f.id} value={f.name}>
                        {f.displayName || f.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className={styles.formRowHalf}>
                <div className={styles.formGroup}>
                  <label htmlFor="sort-direction" className={styles.formLabel}>
                    Direction
                  </label>
                  <select
                    id="sort-direction"
                    className={styles.formSelect}
                    value={builder.sortDirection}
                    onChange={(e) =>
                      setBuilder((prev) => ({
                        ...prev,
                        sortDirection: e.target.value,
                      }))
                    }
                    disabled={!builder.sortByField}
                    data-testid="sort-direction-select"
                  >
                    <option value="ASC">Ascending</option>
                    <option value="DESC">Descending</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Report Name */}
            <div className={styles.formGroup}>
              <label htmlFor="report-name" className={styles.formLabel}>
                Report Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="report-name"
                type="text"
                className={`${styles.formInput} ${
                  builder.reportName.trim() === '' ? styles.hasError : ''
                }`}
                value={builder.reportName}
                onChange={(e) =>
                  setBuilder((prev) => ({
                    ...prev,
                    reportName: e.target.value,
                  }))
                }
                placeholder="Enter report name"
                aria-required="true"
                data-testid="report-name-input"
              />
              {builder.reportName.trim() === '' && (
                <span className={styles.formError} role="alert">
                  Name is required
                </span>
              )}
            </div>

            {/* Description */}
            <div className={styles.formGroup}>
              <label htmlFor="report-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="report-description"
                className={`${styles.formInput} ${styles.formTextarea}`}
                value={builder.reportDescription}
                onChange={(e) =>
                  setBuilder((prev) => ({
                    ...prev,
                    reportDescription: e.target.value,
                  }))
                }
                placeholder="Enter report description"
                rows={3}
                data-testid="report-description-input"
              />
            </div>

            {/* Type & Access */}
            <div className={styles.formRow}>
              <div className={styles.formRowHalf}>
                <div className={styles.formGroup}>
                  <label htmlFor="report-type" className={styles.formLabel}>
                    Report Type
                  </label>
                  <select
                    id="report-type"
                    className={styles.formSelect}
                    value={builder.reportType}
                    onChange={(e) =>
                      setBuilder((prev) => ({
                        ...prev,
                        reportType: e.target.value,
                      }))
                    }
                    data-testid="report-type-select"
                  >
                    <option value="TABULAR">Tabular</option>
                    <option value="SUMMARY">Summary</option>
                    <option value="MATRIX">Matrix</option>
                  </select>
                </div>
              </div>
              <div className={styles.formRowHalf}>
                <div className={styles.formGroup}>
                  <label htmlFor="report-access" className={styles.formLabel}>
                    Access Level
                  </label>
                  <select
                    id="report-access"
                    className={styles.formSelect}
                    value={builder.accessLevel}
                    onChange={(e) =>
                      setBuilder((prev) => ({
                        ...prev,
                        accessLevel: e.target.value,
                      }))
                    }
                    data-testid="report-access-select"
                  >
                    <option value="PRIVATE">Private</option>
                    <option value="PUBLIC">Public</option>
                  </select>
                </div>
              </div>
            </div>
          </div>
        </div>
      )
    }

    return (
      <div className={styles.container} data-testid={testId}>
        <header className={styles.header}>
          <h1 className={styles.title}>{editingReportId ? 'Edit Report' : 'Create Report'}</h1>
          <button
            type="button"
            className={styles.backButton}
            onClick={handleBackToList}
            data-testid="back-to-list-button"
          >
            <ArrowLeft size={16} />
            Back to Reports
          </button>
        </header>

        <div className={styles.builderContainer} data-testid="report-builder">
          {renderStepIndicator()}

          <div className={styles.stepContent} data-testid="step-content">
            {builderStep === 1 && renderStep1Source()}
            {builderStep === 2 && renderStep2Columns()}
            {builderStep === 3 && renderStep3Filters()}
            {builderStep === 4 && renderStep4GroupBy()}
            {builderStep === 5 && renderStep5Save()}
          </div>

          <div className={styles.stepFooter}>
            <div className={styles.stepNavLeft}>
              {builderStep > 1 && (
                <button
                  type="button"
                  className={`${styles.navButton} ${styles.navButtonSecondary}`}
                  onClick={handlePrevStep}
                  data-testid="step-back-button"
                >
                  <ArrowLeft size={14} />
                  Back
                </button>
              )}
            </div>
            <div className={styles.stepNavRight}>
              {builderStep < 5 && (
                <button
                  type="button"
                  className={`${styles.navButton} ${styles.navButtonPrimary}`}
                  onClick={handleNextStep}
                  disabled={!canProceedStep(builderStep)}
                  data-testid="step-next-button"
                >
                  Next
                </button>
              )}
              {builderStep === 5 && (
                <>
                  <button
                    type="button"
                    className={`${styles.navButton} ${styles.navButtonSecondary}`}
                    onClick={() => handleSave(false)}
                    disabled={isSaving || !canProceedStep(5)}
                    data-testid="save-report-button"
                  >
                    {isSaving ? 'Saving...' : 'Save'}
                  </button>
                  <button
                    type="button"
                    className={`${styles.navButton} ${styles.navButtonSuccess}`}
                    onClick={() => handleSave(true)}
                    disabled={isSaving || !canProceedStep(5)}
                    data-testid="save-and-run-button"
                  >
                    <Play size={14} />
                    {isSaving ? 'Saving...' : 'Save & Run'}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    )
  }

  /* ═══════════════════════ REPORT VIEWER VIEW ═══════════════════════ */

  if (viewMode === 'viewer' && selectedReport) {
    const renderGroupedTable = () => {
      if (!groupedRecords) return null
      const groupKeys = Object.keys(groupedRecords)
      return (
        <tbody>
          {groupKeys.map((groupKey) => {
            const groupRows = groupedRecords[groupKey]
            const isExpanded = expandedGroups[groupKey] !== false // default expanded
            const groupSubtotals: Record<string, number> = {}
            for (const col of viewerColumns) {
              if (isNumericType(col.type)) {
                groupSubtotals[col.fieldName] = groupRows.reduce(
                  (acc, r) => acc + toNumber(r[col.fieldName]),
                  0
                )
              }
            }
            return (
              <React.Fragment key={groupKey}>
                <tr
                  className={styles.groupHeaderRow}
                  onClick={() => handleToggleGroup(groupKey)}
                  data-testid={`group-header-${groupKey}`}
                >
                  <td className={styles.groupHeaderCell} colSpan={viewerColumns.length}>
                    <span className={styles.groupToggle}>
                      {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                      {viewerGroupBy}: {groupKey} ({groupRows.length} records)
                    </span>
                  </td>
                </tr>
                {isExpanded &&
                  groupRows.map((record, rIdx) => (
                    <tr key={rIdx} data-testid={`group-row-${groupKey}-${rIdx}`}>
                      {viewerColumns.map((col) => (
                        <td key={col.fieldName}>{formatCellValue(record[col.fieldName])}</td>
                      ))}
                    </tr>
                  ))}
                {isExpanded && (
                  <tr className={styles.subtotalRow} data-testid={`group-subtotal-${groupKey}`}>
                    {viewerColumns.map((col, cIdx) => (
                      <td key={col.fieldName}>
                        {cIdx === 0 && !isNumericType(col.type)
                          ? `Subtotal (${groupRows.length})`
                          : isNumericType(col.type)
                            ? (groupSubtotals[col.fieldName]?.toLocaleString() ?? '')
                            : ''}
                      </td>
                    ))}
                  </tr>
                )}
              </React.Fragment>
            )
          })}
        </tbody>
      )
    }

    const renderFlatTable = () => (
      <tbody>
        {processedRecords.map((record, rIdx) => (
          <tr key={rIdx} data-testid={`data-row-${rIdx}`}>
            {viewerColumns.map((col) => (
              <td key={col.fieldName}>{formatCellValue(record[col.fieldName])}</td>
            ))}
          </tr>
        ))}
      </tbody>
    )

    const hasNumericColumns = viewerColumns.some((c) => isNumericType(c.type))

    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.viewerContainer}>
          <div className={styles.viewerHeader}>
            <div className={styles.viewerTitleSection}>
              <h1 className={styles.viewerTitle}>{selectedReport.name}</h1>
              {selectedReport.description && (
                <p className={styles.viewerDescription}>{selectedReport.description}</p>
              )}
            </div>
            <div className={styles.viewerActions}>
              <button
                type="button"
                className={styles.viewerActionButton}
                onClick={handleBackToList}
                data-testid="viewer-back-button"
              >
                <ArrowLeft size={14} />
                Back
              </button>
              <button
                type="button"
                className={styles.viewerActionButton}
                onClick={() => handleEditReport(selectedReport)}
                data-testid="viewer-edit-button"
              >
                <Pencil size={14} />
                Edit
              </button>
              <button
                type="button"
                className={styles.viewerActionButton}
                onClick={handleExportCsv}
                disabled={processedRecords.length === 0}
                data-testid="viewer-export-button"
              >
                <Download size={14} />
                Export CSV
              </button>
            </div>
          </div>

          <div className={styles.viewerMeta}>
            <span className={styles.rowCount} data-testid="row-count">
              {processedRecords.length} records
            </span>
            <span className={`${styles.badge} ${styles.badgeType}`}>
              {selectedReport.reportType}
            </span>
            {viewerGroupBy && (
              <span className={`${styles.badge} ${styles.badgeScope}`}>
                Grouped by: {viewerGroupBy}
              </span>
            )}
          </div>

          {isLoadingGateway ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="large" label="Loading report data..." />
            </div>
          ) : viewerColumns.length === 0 ? (
            <div className={styles.viewerEmpty} data-testid="viewer-empty">
              This report has no columns configured. Edit the report to select columns.
            </div>
          ) : processedRecords.length === 0 ? (
            <div className={styles.viewerEmpty} data-testid="viewer-no-data">
              No data found matching the report criteria.
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <div className={styles.tableScrollable}>
                <table
                  className={styles.dataTable}
                  role="grid"
                  aria-label={selectedReport.name}
                  data-testid="report-data-table"
                >
                  <thead>
                    <tr role="row">
                      {viewerColumns.map((col) => (
                        <th key={col.fieldName} role="columnheader" scope="col">
                          {col.label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  {groupedRecords ? renderGroupedTable() : renderFlatTable()}
                  {hasNumericColumns && (
                    <tfoot>
                      <tr className={styles.summaryRow} data-testid="summary-row">
                        {viewerColumns.map((col, cIdx) => (
                          <td key={col.fieldName}>
                            {cIdx === 0 && !isNumericType(col.type)
                              ? `Total (${processedRecords.length})`
                              : isNumericType(col.type)
                                ? (numericColumnSums[col.fieldName] ?? 0).toLocaleString()
                                : ''}
                          </td>
                        ))}
                      </tr>
                    </tfoot>
                  )}
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    )
  }

  /* Fallback — should not happen */
  return (
    <div className={styles.container} data-testid={testId}>
      <p>Unknown view mode.</p>
    </div>
  )
}

export default ReportsPage
