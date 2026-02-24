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
import { cn } from '@/lib/utils'

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
    queryFn: () => apiClient.getList<Report>(`/api/reports`),
  })

  const reportList = useMemo<Report[]>(() => reports ?? [], [reports])
  const selectedReport = useMemo(
    () => reportList.find((r) => r.id === selectedReportId) ?? null,
    [reportList, selectedReportId]
  )

  /* ────── Collections query (for builder) ────── */
  const { data: collectionsData } = useQuery({
    queryKey: ['collections-for-reports'],
    queryFn: () => apiClient.getList<CollectionSummary>('/api/collections?page[size]=100'),
    enabled: viewMode === 'builder',
  })

  const collections = useMemo<CollectionSummary[]>(() => collectionsData ?? [], [collectionsData])

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
      apiClient.getList<FieldDefinition>(
        `/api/fields?filter[collectionId][eq]=${builder.collectionId}`
      ),
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
      apiClient.getList<Record<string, unknown>>(`/api/${collectionNameForViewer}?page[size]=1000`),
    enabled: viewMode === 'viewer' && !!selectedReportId && !!collectionNameForViewer,
  })

  /* Also fetch collections list in viewer mode so we can resolve names */
  useQuery({
    queryKey: ['collections-for-reports'],
    queryFn: () => apiClient.getList<CollectionSummary>('/api/collections?page[size]=100'),
    enabled: viewMode === 'viewer',
  })

  /* ────── Mutations ────── */
  const createMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      apiClient.postResource<Report>(`/api/reports`, data),
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
      apiClient.putResource<Report>(`/api/reports/${id}`, data),
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
    mutationFn: (id: string) => apiClient.delete(`/api/reports/${id}`),
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
    const raw = gatewayData ?? []
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
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <div className="flex items-center justify-center min-h-[400px]">
          <LoadingSpinner size="large" label="Loading reports..." />
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

  const isSaving = createMutation.isPending || updateMutation.isPending

  /* ═══════════════════════ REPORT LIST VIEW ═══════════════════════ */

  if (viewMode === 'list') {
    return (
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <header className="flex items-center justify-between mb-8 max-md:flex-col max-md:items-start max-md:gap-4">
          <h1 className="text-3xl font-semibold text-foreground m-0">Reports</h1>
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="inline-flex items-center gap-2 px-6 py-3 text-base font-medium text-white bg-primary border-none rounded-md cursor-pointer transition-colors duration-200 hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 max-md:w-full max-md:justify-center"
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
          <div
            className="py-16 px-8 text-center text-muted-foreground bg-card border border-border rounded-lg"
            data-testid="empty-state"
          >
            <div className="text-muted-foreground mb-4 opacity-50">
              <FileSpreadsheet size={48} />
            </div>
            <p>No reports found. Create your first report to get started.</p>
          </div>
        ) : (
          <div
            className="grid grid-cols-[repeat(auto-fill,minmax(380px,1fr))] gap-4 max-md:grid-cols-1"
            data-testid="reports-grid"
          >
            {reportList.map((report, index) => {
              const collectionMatch = collections.find((c) => c.id === report.primaryCollectionId)
              const collectionLabel = collectionMatch?.displayName ?? report.primaryCollectionId
              return (
                <div
                  key={report.id}
                  className="flex flex-col gap-3 bg-card border border-border rounded-lg p-5 transition-all duration-200 hover:border-primary hover:shadow-[0_2px_8px_rgba(0,0,0,0.06)]"
                  data-testid={`report-card-${index}`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <h3 className="text-base font-semibold text-foreground m-0 leading-[1.4]">
                      {report.name}
                    </h3>
                  </div>
                  {report.description && (
                    <p className="text-[0.8125rem] text-muted-foreground m-0 leading-relaxed line-clamp-2">
                      {report.description}
                    </p>
                  )}
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-block px-2.5 py-0.5 text-[0.6875rem] font-semibold rounded-full uppercase tracking-wide text-primary bg-muted">
                      {report.reportType}
                    </span>
                    <span className="inline-block px-2.5 py-0.5 text-[0.6875rem] font-semibold rounded-full uppercase tracking-wide text-emerald-800 bg-emerald-100 dark:text-emerald-300 dark:bg-emerald-900">
                      {report.scope}
                    </span>
                    <span className="inline-block px-2.5 py-0.5 text-[0.6875rem] font-semibold rounded-full uppercase tracking-wide text-amber-800 bg-amber-100 dark:text-amber-200 dark:bg-amber-900">
                      {report.accessLevel}
                    </span>
                    <span className="text-xs text-muted-foreground">{collectionLabel}</span>
                    <span className="text-xs text-muted-foreground">
                      Modified{' '}
                      {formatDate(new Date(report.updatedAt), {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </span>
                  </div>
                  <div className="flex gap-2 border-t border-border pt-3 mt-auto">
                    <button
                      type="button"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[0.8125rem] font-medium rounded-md cursor-pointer transition-all duration-200 text-white bg-primary border border-primary hover:bg-primary/90 hover:border-primary/90 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
                      onClick={() => handleRunReport(report)}
                      aria-label={`Run ${report.name}`}
                      data-testid={`run-button-${index}`}
                    >
                      <Play size={14} />
                      Run
                    </button>
                    <button
                      type="button"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[0.8125rem] font-medium rounded-md cursor-pointer transition-all duration-200 border border-border bg-transparent text-foreground hover:bg-muted hover:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
                      onClick={() => handleEditReport(report)}
                      aria-label={`Edit ${report.name}`}
                      data-testid={`edit-button-${index}`}
                    >
                      <Pencil size={14} />
                      Edit
                    </button>
                    <button
                      type="button"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[0.8125rem] font-medium rounded-md cursor-pointer transition-all duration-200 border border-border bg-transparent text-destructive ml-auto hover:border-destructive hover:bg-destructive/10 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
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
      <div
        className="flex items-center justify-center gap-0 px-8 py-6 bg-muted border-b border-border overflow-x-auto max-md:px-4 max-md:py-4"
        data-testid="step-indicator"
      >
        {WIZARD_STEPS.map((step, i) => {
          const stepNum = i + 1
          const isActive = stepNum === builderStep
          const isComplete = stepNum < builderStep
          return (
            <React.Fragment key={step.key}>
              {i > 0 && (
                <div
                  className={cn(
                    'w-8 h-0.5 mx-2 shrink-0 max-md:w-6',
                    isComplete ? 'bg-emerald-600' : 'bg-border'
                  )}
                />
              )}
              <div className="flex items-center gap-0 shrink-0">
                <div
                  className={cn(
                    'w-8 h-8 rounded-full flex items-center justify-center text-[0.8125rem] font-semibold border-2 shrink-0 transition-all duration-200',
                    isActive
                      ? 'border-primary bg-primary text-white'
                      : isComplete
                        ? 'border-emerald-600 bg-emerald-600 text-white'
                        : 'border-border text-muted-foreground bg-card'
                  )}
                  data-testid={`step-circle-${stepNum}`}
                >
                  {isComplete ? <Check size={14} /> : stepNum}
                </div>
                <span
                  className={cn(
                    'text-xs font-medium ml-2 whitespace-nowrap max-md:hidden',
                    isActive
                      ? 'text-primary font-semibold'
                      : isComplete
                        ? 'text-emerald-600'
                        : 'text-muted-foreground'
                  )}
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
        <h2 className="text-xl font-semibold text-foreground mb-2">Select a Collection</h2>
        <p className="text-sm text-muted-foreground mb-6">
          Choose the collection to build your report on.
        </p>
        <input
          type="text"
          className="w-full px-3.5 py-2.5 text-sm border border-border rounded-md mb-4 text-foreground bg-card transition-colors duration-200 focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10"
          placeholder="Search collections..."
          value={collectionSearch}
          onChange={(e) => setCollectionSearch(e.target.value)}
          data-testid="collection-search-input"
        />
        <div
          className="flex flex-col gap-2 max-h-[400px] overflow-y-auto"
          data-testid="collections-list"
        >
          {filteredCollections.length === 0 && (
            <p className="text-xs text-muted-foreground">No collections found.</p>
          )}
          {filteredCollections.map((col) => {
            const isSelected = col.id === builder.collectionId
            return (
              <div
                key={col.id}
                className={cn(
                  'flex items-center px-4 py-3.5 bg-card border-2 rounded-lg cursor-pointer transition-all duration-150',
                  isSelected
                    ? 'border-primary bg-muted'
                    : 'border-border hover:border-primary hover:bg-accent'
                )}
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
                  className={cn(
                    'w-[1.125rem] h-[1.125rem] rounded-full border-2 mr-3.5 shrink-0 flex items-center justify-center transition-all duration-150',
                    isSelected ? 'border-primary bg-primary' : 'border-border'
                  )}
                >
                  {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-card" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-[0.9375rem] font-semibold text-foreground">
                    {col.displayName || col.name}
                  </div>
                  {col.description && (
                    <div className="text-[0.8125rem] text-muted-foreground mt-0.5">
                      {col.description}
                    </div>
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
        <h2 className="text-xl font-semibold text-foreground mb-2">Select Columns</h2>
        <p className="text-sm text-muted-foreground mb-6">
          Choose which fields to display in the report. At least one column is required.
        </p>
        {fields.length === 0 ? (
          <p className="text-xs text-muted-foreground">No fields found for this collection.</p>
        ) : (
          <>
            <div className="flex items-center gap-2 py-2 mb-2 border-b border-border">
              <input
                type="checkbox"
                className="w-4 h-4 cursor-pointer accent-primary shrink-0"
                checked={builder.selectedColumns.length === fields.length && fields.length > 0}
                onChange={handleSelectAllColumns}
                data-testid="select-all-columns"
              />
              <span className="text-[0.8125rem] font-medium text-muted-foreground">
                Select All ({builder.selectedColumns.length} / {fields.length})
              </span>
            </div>
            <div
              className="flex flex-col gap-1.5 max-h-[400px] overflow-y-auto"
              data-testid="columns-list"
            >
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
                    className="flex items-center gap-3 px-3.5 py-2.5 bg-card border border-border rounded-md transition-colors duration-150 hover:bg-muted"
                    data-testid={`column-item-${field.name}`}
                  >
                    <input
                      type="checkbox"
                      className="w-4 h-4 cursor-pointer accent-primary shrink-0"
                      checked={selected}
                      onChange={() => handleToggleColumn(field)}
                      data-testid={`column-checkbox-${field.name}`}
                    />
                    <span className="flex-1 text-sm text-foreground">
                      {field.displayName || field.name}
                    </span>
                    <span className="text-xs text-muted-foreground px-2 py-0.5 bg-muted rounded">
                      {field.type}
                    </span>
                    {selected && (
                      <div className="flex gap-1">
                        <button
                          type="button"
                          className="inline-flex items-center justify-center w-6 h-6 p-0 border border-border rounded bg-card text-muted-foreground cursor-pointer transition-all duration-150 hover:bg-muted hover:border-primary hover:text-primary disabled:opacity-30 disabled:cursor-not-allowed"
                          onClick={() => handleMoveColumn(selectedIndex, 'up')}
                          disabled={selectedIndex <= 0}
                          aria-label={`Move ${field.name} up`}
                          data-testid={`move-up-${field.name}`}
                        >
                          <ArrowUp size={12} />
                        </button>
                        <button
                          type="button"
                          className="inline-flex items-center justify-center w-6 h-6 p-0 border border-border rounded bg-card text-muted-foreground cursor-pointer transition-all duration-150 hover:bg-muted hover:border-primary hover:text-primary disabled:opacity-30 disabled:cursor-not-allowed"
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
        <h2 className="text-xl font-semibold text-foreground mb-2">Add Filters</h2>
        <p className="text-sm text-muted-foreground mb-6">
          Optionally filter the data. Multiple filters use AND logic.
        </p>
        <div className="flex flex-col gap-3" data-testid="filters-container">
          {builder.filters.length === 0 && (
            <p className="text-sm text-muted-foreground py-4">
              No filters added. Click the button below to add a filter condition.
            </p>
          )}
          {builder.filters.map((filter, index) => (
            <div
              key={index}
              className="flex items-start gap-2 flex-wrap max-md:flex-col"
              data-testid={`filter-row-${index}`}
            >
              <select
                className="px-3 py-2 text-sm border border-border rounded-md bg-card text-foreground min-w-[150px] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10 max-md:min-w-full"
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
                className="px-3 py-2 text-sm border border-border rounded-md bg-card text-foreground min-w-[150px] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10 max-md:min-w-full"
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
                className="flex-1 min-w-[120px] px-3 py-2 text-sm border border-border rounded-md bg-card text-foreground focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10 max-md:min-w-full"
                value={filter.value}
                onChange={(e) => handleUpdateFilter(index, 'value', e.target.value)}
                placeholder="Value"
                data-testid={`filter-value-${index}`}
              />
              <button
                type="button"
                className="inline-flex items-center justify-center w-8 h-8 p-0 border border-border rounded-md bg-card text-destructive cursor-pointer shrink-0 transition-all duration-150 hover:bg-destructive/10 hover:border-destructive"
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
            className="inline-flex items-center gap-1.5 px-4 py-2 text-[0.8125rem] font-medium text-primary bg-transparent border border-dashed border-primary rounded-md cursor-pointer transition-colors duration-150 self-start mt-1 hover:bg-muted"
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
          <h2 className="text-xl font-semibold text-foreground mb-2">Group By</h2>
          <p className="text-sm text-muted-foreground mb-6">
            Optionally group records by a field. Numeric columns will show subtotals.
          </p>
          <div
            className="flex flex-col gap-2 max-h-[400px] overflow-y-auto"
            data-testid="groupby-container"
          >
            <div
              className={cn(
                'flex items-center px-4 py-3 bg-card border-2 rounded-md cursor-pointer transition-all duration-150',
                builder.groupByField === ''
                  ? 'border-primary bg-muted'
                  : 'border-border hover:border-primary'
              )}
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
                className={cn(
                  'w-[1.125rem] h-[1.125rem] rounded-full border-2 mr-3.5 shrink-0 flex items-center justify-center transition-all duration-150',
                  builder.groupByField === '' ? 'border-primary bg-primary' : 'border-border'
                )}
              >
                {builder.groupByField === '' && (
                  <div className="w-1.5 h-1.5 rounded-full bg-card" />
                )}
              </div>
              <span className="flex-1 text-sm text-foreground">None (no grouping)</span>
            </div>
            {groupableFields.map((field) => {
              const isSelected = builder.groupByField === field.name
              return (
                <div
                  key={field.id}
                  className={cn(
                    'flex items-center px-4 py-3 bg-card border-2 rounded-md cursor-pointer transition-all duration-150',
                    isSelected ? 'border-primary bg-muted' : 'border-border hover:border-primary'
                  )}
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
                    className={cn(
                      'w-[1.125rem] h-[1.125rem] rounded-full border-2 mr-3.5 shrink-0 flex items-center justify-center transition-all duration-150',
                      isSelected ? 'border-primary bg-primary' : 'border-border'
                    )}
                  >
                    {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-card" />}
                  </div>
                  <span className="flex-1 text-sm text-foreground">
                    {field.displayName || field.name}
                  </span>
                  <span className="text-xs text-muted-foreground px-2 py-0.5 bg-muted rounded">
                    {field.type}
                  </span>
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
          <h2 className="text-xl font-semibold text-foreground mb-2">Sort & Save</h2>
          <p className="text-sm text-muted-foreground mb-6">
            Configure sorting and save your report.
          </p>
          <div className="flex flex-col gap-5">
            {/* Sort */}
            <div className="flex gap-4 max-md:flex-col max-md:gap-5">
              <div className="flex-1">
                <div className="flex flex-col gap-2">
                  <label htmlFor="sort-by-field" className="text-sm font-medium text-foreground">
                    Sort By
                  </label>
                  <select
                    id="sort-by-field"
                    className="w-full px-3.5 py-2.5 text-sm text-foreground bg-card border border-border rounded-md transition-colors duration-200 focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10"
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
              <div className="flex-1">
                <div className="flex flex-col gap-2">
                  <label htmlFor="sort-direction" className="text-sm font-medium text-foreground">
                    Direction
                  </label>
                  <select
                    id="sort-direction"
                    className="w-full px-3.5 py-2.5 text-sm text-foreground bg-card border border-border rounded-md transition-colors duration-200 focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10"
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
            <div className="flex flex-col gap-2">
              <label htmlFor="report-name" className="text-sm font-medium text-foreground">
                Report Name
                <span className="text-destructive ml-1" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="report-name"
                type="text"
                className={cn(
                  'w-full px-3.5 py-2.5 text-sm text-foreground bg-card border border-border rounded-md transition-colors duration-200 focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10 disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed',
                  builder.reportName.trim() === '' && 'border-destructive'
                )}
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
                <span className="text-xs text-destructive" role="alert">
                  Name is required
                </span>
              )}
            </div>

            {/* Description */}
            <div className="flex flex-col gap-2">
              <label htmlFor="report-description" className="text-sm font-medium text-foreground">
                Description
              </label>
              <textarea
                id="report-description"
                className="w-full px-3.5 py-2.5 text-sm text-foreground bg-card border border-border rounded-md transition-colors duration-200 resize-y min-h-[70px] font-[inherit] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10"
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
            <div className="flex gap-4 max-md:flex-col max-md:gap-5">
              <div className="flex-1">
                <div className="flex flex-col gap-2">
                  <label htmlFor="report-type" className="text-sm font-medium text-foreground">
                    Report Type
                  </label>
                  <select
                    id="report-type"
                    className="w-full px-3.5 py-2.5 text-sm text-foreground bg-card border border-border rounded-md transition-colors duration-200 focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10"
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
              <div className="flex-1">
                <div className="flex flex-col gap-2">
                  <label htmlFor="report-access" className="text-sm font-medium text-foreground">
                    Access Level
                  </label>
                  <select
                    id="report-access"
                    className="w-full px-3.5 py-2.5 text-sm text-foreground bg-card border border-border rounded-md transition-colors duration-200 focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/10"
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
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <header className="flex items-center justify-between mb-8 max-md:flex-col max-md:items-start max-md:gap-4">
          <h1 className="text-3xl font-semibold text-foreground m-0">
            {editingReportId ? 'Edit Report' : 'Create Report'}
          </h1>
          <button
            type="button"
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-foreground bg-card border border-border rounded-md cursor-pointer transition-all duration-200 hover:bg-muted"
            onClick={handleBackToList}
            data-testid="back-to-list-button"
          >
            <ArrowLeft size={16} />
            Back to Reports
          </button>
        </header>

        <div
          className="bg-card border border-border rounded-lg overflow-hidden"
          data-testid="report-builder"
        >
          {renderStepIndicator()}

          <div className="p-8 min-h-[360px]" data-testid="step-content">
            {builderStep === 1 && renderStep1Source()}
            {builderStep === 2 && renderStep2Columns()}
            {builderStep === 3 && renderStep3Filters()}
            {builderStep === 4 && renderStep4GroupBy()}
            {builderStep === 5 && renderStep5Save()}
          </div>

          <div className="flex items-center justify-between px-8 py-4 border-t border-border bg-muted">
            <div className="flex gap-3">
              {builderStep > 1 && (
                <button
                  type="button"
                  className="inline-flex items-center gap-1.5 px-5 py-2.5 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 text-foreground bg-card border border-border hover:bg-muted"
                  onClick={handlePrevStep}
                  data-testid="step-back-button"
                >
                  <ArrowLeft size={14} />
                  Back
                </button>
              )}
            </div>
            <div className="flex gap-3">
              {builderStep < 5 && (
                <button
                  type="button"
                  className="inline-flex items-center gap-1.5 px-5 py-2.5 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 text-white bg-primary border-none hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
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
                    className="inline-flex items-center gap-1.5 px-5 py-2.5 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 text-foreground bg-card border border-border hover:bg-muted"
                    onClick={() => handleSave(false)}
                    disabled={isSaving || !canProceedStep(5)}
                    data-testid="save-report-button"
                  >
                    {isSaving ? 'Saving...' : 'Save'}
                  </button>
                  <button
                    type="button"
                    className="inline-flex items-center gap-1.5 px-5 py-2.5 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 text-white bg-emerald-600 border-none hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
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
                  className="bg-muted hover:bg-accent"
                  onClick={() => handleToggleGroup(groupKey)}
                  data-testid={`group-header-${groupKey}`}
                >
                  <td
                    className="!px-4 !py-3 font-semibold text-sm text-foreground cursor-pointer select-none"
                    colSpan={viewerColumns.length}
                  >
                    <span className="inline-flex items-center gap-2">
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
                  <tr
                    className="!bg-muted [&_td]:font-semibold [&_td]:text-[0.8125rem] [&_td]:text-muted-foreground [&_td]:italic [&_td]:!border-b-2 [&_td]:!border-border"
                    data-testid={`group-subtotal-${groupKey}`}
                  >
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
      <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
        <div className="flex flex-col gap-4">
          <div className="flex items-start justify-between flex-wrap gap-4 max-md:flex-col">
            <div className="flex-1 min-w-0">
              <h1 className="text-2xl font-semibold text-foreground m-0">{selectedReport.name}</h1>
              {selectedReport.description && (
                <p className="text-sm text-muted-foreground mt-1 mb-0">
                  {selectedReport.description}
                </p>
              )}
            </div>
            <div className="flex items-center gap-3 shrink-0 max-md:w-full max-md:flex-wrap">
              <button
                type="button"
                className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 border border-border bg-card text-foreground hover:bg-muted hover:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
                onClick={handleBackToList}
                data-testid="viewer-back-button"
              >
                <ArrowLeft size={14} />
                Back
              </button>
              <button
                type="button"
                className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 border border-border bg-card text-foreground hover:bg-muted hover:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
                onClick={() => handleEditReport(selectedReport)}
                data-testid="viewer-edit-button"
              >
                <Pencil size={14} />
                Edit
              </button>
              <button
                type="button"
                className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md cursor-pointer transition-all duration-200 border border-border bg-card text-foreground hover:bg-muted hover:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
                onClick={handleExportCsv}
                disabled={processedRecords.length === 0}
                data-testid="viewer-export-button"
              >
                <Download size={14} />
                Export CSV
              </button>
            </div>
          </div>

          <div className="flex items-center flex-wrap gap-4">
            <span
              className="text-[0.8125rem] text-muted-foreground font-medium"
              data-testid="row-count"
            >
              {processedRecords.length} records
            </span>
            <span className="inline-block px-2.5 py-0.5 text-[0.6875rem] font-semibold rounded-full uppercase tracking-wide text-primary bg-muted">
              {selectedReport.reportType}
            </span>
            {viewerGroupBy && (
              <span className="inline-block px-2.5 py-0.5 text-[0.6875rem] font-semibold rounded-full uppercase tracking-wide text-emerald-800 bg-emerald-100 dark:text-emerald-300 dark:bg-emerald-900">
                Grouped by: {viewerGroupBy}
              </span>
            )}
          </div>

          {isLoadingGateway ? (
            <div className="flex items-center justify-center min-h-[400px]">
              <LoadingSpinner size="large" label="Loading report data..." />
            </div>
          ) : viewerColumns.length === 0 ? (
            <div
              className="py-12 px-8 text-center text-muted-foreground text-sm"
              data-testid="viewer-empty"
            >
              This report has no columns configured. Edit the report to select columns.
            </div>
          ) : processedRecords.length === 0 ? (
            <div
              className="py-12 px-8 text-center text-muted-foreground text-sm"
              data-testid="viewer-no-data"
            >
              No data found matching the report criteria.
            </div>
          ) : (
            <div className="bg-card border border-border rounded-lg overflow-hidden">
              <div className="overflow-x-auto">
                <table
                  className="w-full border-collapse [&_thead]:bg-muted [&_th]:px-4 [&_th]:py-3 [&_th]:text-left [&_th]:text-[0.8125rem] [&_th]:font-semibold [&_th]:text-muted-foreground [&_th]:uppercase [&_th]:tracking-wide [&_th]:border-b [&_th]:border-border [&_th]:whitespace-nowrap [&_td]:px-4 [&_td]:py-2.5 [&_td]:text-sm [&_td]:text-foreground [&_td]:border-b [&_td]:border-border [&_td]:max-w-[300px] [&_td]:overflow-hidden [&_td]:text-ellipsis [&_td]:whitespace-nowrap [&_tbody_tr:last-child_td]:border-b-0 [&_tbody_tr:hover]:bg-accent"
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
                      <tr
                        className="!bg-muted hover:!bg-muted [&_td]:font-semibold [&_td]:text-[0.8125rem] [&_td]:text-primary [&_td]:border-t-2 [&_td]:border-border [&_td]:border-b-0"
                        data-testid="summary-row"
                      >
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
    <div className="mx-auto max-w-[1400px] p-8 max-md:p-4" data-testid={testId}>
      <p>Unknown view mode.</p>
    </div>
  )
}

export default ReportsPage
