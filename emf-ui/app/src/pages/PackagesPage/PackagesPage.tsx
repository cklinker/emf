/**
 * PackagesPage Component
 *
 * Manages configuration package export and import.
 * Provides export wizard with item selection, import with file upload and preview,
 * dry-run validation, and package history.
 *
 * Requirements:
 * - 9.1: Display export and import options
 * - 9.10: Display package history showing previous exports and imports
 */

import React, { useState, useCallback, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './PackagesPage.module.css'

/**
 * Package interface matching the API response
 */
export interface Package {
  id: string
  name: string
  version: string
  items: PackageItem[]
  createdAt: string
  type: 'export' | 'import'
  status: 'success' | 'failed' | 'pending'
}

/**
 * Package item interface
 */
export interface PackageItem {
  type: 'collection' | 'role' | 'policy' | 'page' | 'menu'
  id: string
  name: string
  content?: unknown
}

/**
 * Export options interface
 */
export interface ExportOptions {
  name: string
  version: string
  description?: string
  collectionIds: string[]
  roleIds: string[]
  policyIds: string[]
  uiPageIds: string[]
  uiMenuIds: string[]
}

/**
 * Import preview interface
 */
export interface ImportPreview {
  creates: PackageItem[]
  updates: PackageItem[]
  conflicts: ImportConflict[]
}

/**
 * Import conflict interface
 */
export interface ImportConflict {
  item: PackageItem
  existingItem: PackageItem
  resolution?: 'skip' | 'overwrite'
}

/**
 * Import result interface
 */
export interface ImportResult {
  success: boolean
  created: number
  updated: number
  skipped: number
  errors: ImportError[]
}

/**
 * Import error interface
 */
export interface ImportError {
  item: PackageItem
  message: string
}

/**
 * Selectable item for export
 */
interface SelectableItem {
  id: string
  name: string
  type: string
}

/**
 * Props for PackagesPage component
 */
export interface PackagesPageProps {
  /** Optional test ID for testing */
  testId?: string
}

// API functions using apiClient
async function fetchPackageHistory(apiClient: any): Promise<Package[]> {
  return apiClient.get('/control/packages/history')
}

async function fetchCollections(apiClient: any): Promise<SelectableItem[]> {
  const data = await apiClient.get('/control/collections?size=1000')
  // Handle paginated response from Spring
  const collections = data.content || data
  return collections.map((c: { id: string; name: string }) => ({
    id: c.id,
    name: c.name,
    type: 'collection',
  }))
}

async function fetchRoles(apiClient: any): Promise<SelectableItem[]> {
  const data = await apiClient.get('/control/roles?size=1000')
  // Handle paginated response from Spring
  const roles = data.content || data
  return roles.map((r: { id: string; name: string }) => ({
    id: r.id,
    name: r.name,
    type: 'role',
  }))
}

async function fetchPolicies(apiClient: any): Promise<SelectableItem[]> {
  const data = await apiClient.get('/control/policies?size=1000')
  // Handle paginated response from Spring
  const policies = data.content || data
  return policies.map((p: { id: string; name: string }) => ({
    id: p.id,
    name: p.name,
    type: 'policy',
  }))
}

async function fetchPages(apiClient: any): Promise<SelectableItem[]> {
  const data = await apiClient.get('/control/ui/pages?size=1000')
  // Handle paginated response from Spring
  const pages = data.content || data
  return pages.map((p: { id: string; name: string }) => ({
    id: p.id,
    name: p.name,
    type: 'page',
  }))
}

async function fetchMenus(apiClient: any): Promise<SelectableItem[]> {
  const data = await apiClient.get('/control/ui/menus?size=1000')
  // Handle paginated response from Spring
  const menus = data.content || data
  return menus.map((m: { id: string; name: string }) => ({
    id: m.id,
    name: m.name,
    type: 'menu',
  }))
}

async function exportPackage(apiClient: any, options: ExportOptions): Promise<Blob> {
  return apiClient.post('/control/packages/export', options, { responseType: 'blob' })
}

async function previewImport(apiClient: any, file: File): Promise<ImportPreview> {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post('/control/packages/import/preview', formData)
}

async function executeImport(
  apiClient: any,
  file: File,
  dryRun: boolean = false
): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dryRun', String(dryRun))
  return apiClient.post('/control/packages/import', formData)
}

/**
 * Tab type for the packages page
 */
type TabType = 'export' | 'import' | 'history'

/**
 * Status Badge Component
 */
interface StatusBadgeProps {
  status: 'success' | 'failed' | 'pending'
}

function StatusBadge({ status }: StatusBadgeProps): React.ReactElement {
  const { t } = useI18n()
  const statusLabels: Record<string, string> = {
    success: t('packages.statusSuccess'),
    failed: t('packages.statusFailed'),
    pending: t('packages.statusPending'),
  }
  return (
    <span
      className={`${styles.statusBadge} ${styles[`status${status.charAt(0).toUpperCase() + status.slice(1)}`]}`}
      data-testid="status-badge"
    >
      {statusLabels[status] || status}
    </span>
  )
}

/**
 * Type Badge Component
 */
interface TypeBadgeProps {
  type: 'export' | 'import'
}

function TypeBadge({ type }: TypeBadgeProps): React.ReactElement {
  const { t } = useI18n()
  return (
    <span
      className={`${styles.typeBadge} ${styles[`type${type.charAt(0).toUpperCase() + type.slice(1)}`]}`}
      data-testid="type-badge"
    >
      {type === 'export' ? t('packages.export') : t('packages.import')}
    </span>
  )
}

/**
 * Item Selection Component for Export
 */
interface ItemSelectionProps {
  title: string
  items: SelectableItem[]
  selectedIds: string[]
  onSelectionChange: (ids: string[]) => void
  isLoading: boolean
}

function ItemSelection({
  title,
  items,
  selectedIds,
  onSelectionChange,
  isLoading,
}: ItemSelectionProps): React.ReactElement {
  const { t } = useI18n()

  const handleToggle = useCallback(
    (id: string) => {
      if (selectedIds.includes(id)) {
        onSelectionChange(selectedIds.filter((i) => i !== id))
      } else {
        onSelectionChange([...selectedIds, id])
      }
    },
    [selectedIds, onSelectionChange]
  )

  const handleSelectAll = useCallback(() => {
    if (selectedIds.length === items.length) {
      onSelectionChange([])
    } else {
      onSelectionChange(items.map((i) => i.id))
    }
  }, [items, selectedIds, onSelectionChange])

  if (isLoading) {
    return (
      <div className={styles.itemSection}>
        <h4 className={styles.itemSectionTitle}>{title}</h4>
        <div className={styles.itemSectionLoading}>
          <LoadingSpinner size="small" />
        </div>
      </div>
    )
  }

  return (
    <div className={styles.itemSection} data-testid={`item-section-${title.toLowerCase()}`}>
      <div className={styles.itemSectionHeader}>
        <h4 className={styles.itemSectionTitle}>{title}</h4>
        {items.length > 0 && (
          <button
            type="button"
            className={styles.selectAllButton}
            onClick={handleSelectAll}
            data-testid={`select-all-${title.toLowerCase()}`}
          >
            {selectedIds.length === items.length ? t('common.deselectAll') : t('common.selectAll')}
          </button>
        )}
      </div>
      {items.length === 0 ? (
        <p className={styles.noItems}>{t('packages.noItemsAvailable')}</p>
      ) : (
        <div className={styles.itemList} role="group" aria-label={title}>
          {items.map((item) => (
            <label key={item.id} className={styles.itemCheckbox} data-testid={`item-${item.id}`}>
              <input
                type="checkbox"
                checked={selectedIds.includes(item.id)}
                onChange={() => handleToggle(item.id)}
                data-testid={`checkbox-${item.id}`}
              />
              <span className={styles.itemName}>{item.name}</span>
            </label>
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Export Panel Component
 * Requirement 9.1: Display export options
 */
interface ExportPanelProps {
  onExportComplete: () => void
}

function ExportPanel({ onExportComplete }: ExportPanelProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const [packageName, setPackageName] = useState<string>('')
  const [packageVersion, setPackageVersion] = useState<string>('1.0.0')
  const [packageDescription, setPackageDescription] = useState<string>('')
  const [selectedCollections, setSelectedCollections] = useState<string[]>([])
  const [selectedRoles, setSelectedRoles] = useState<string[]>([])
  const [selectedPolicies, setSelectedPolicies] = useState<string[]>([])
  const [selectedPages, setSelectedPages] = useState<string[]>([])
  const [selectedMenus, setSelectedMenus] = useState<string[]>([])

  const { data: collections = [], isLoading: collectionsLoading } = useQuery({
    queryKey: ['export-collections'],
    queryFn: () => fetchCollections(apiClient),
  })

  const { data: roles = [], isLoading: rolesLoading } = useQuery({
    queryKey: ['export-roles'],
    queryFn: () => fetchRoles(apiClient),
  })

  const { data: policies = [], isLoading: policiesLoading } = useQuery({
    queryKey: ['export-policies'],
    queryFn: () => fetchPolicies(apiClient),
  })

  const { data: pages = [], isLoading: pagesLoading } = useQuery({
    queryKey: ['export-pages'],
    queryFn: () => fetchPages(apiClient),
  })

  const { data: menus = [], isLoading: menusLoading } = useQuery({
    queryKey: ['export-menus'],
    queryFn: () => fetchMenus(apiClient),
  })

  const exportMutation = useMutation({
    mutationFn: (options: ExportOptions) => exportPackage(apiClient, options),
    onSuccess: (blob) => {
      // Download the file
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${packageName}-${packageVersion}.json`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
      showToast(t('packages.exportSuccess'), 'success')
      onExportComplete()
    },
    onError: (error: Error) => {
      showToast(error.message, 'error')
    },
  })

  const hasSelection =
    selectedCollections.length > 0 ||
    selectedRoles.length > 0 ||
    selectedPolicies.length > 0 ||
    selectedPages.length > 0 ||
    selectedMenus.length > 0

  const canExport = packageName.trim() !== '' && packageVersion.trim() !== '' && hasSelection

  const handleExport = useCallback(() => {
    exportMutation.mutate({
      name: packageName,
      version: packageVersion,
      description: packageDescription || undefined,
      collectionIds: selectedCollections,
      roleIds: selectedRoles,
      policyIds: selectedPolicies,
      uiPageIds: selectedPages,
      uiMenuIds: selectedMenus,
    })
  }, [
    packageName,
    packageVersion,
    packageDescription,
    selectedCollections,
    selectedRoles,
    selectedPolicies,
    selectedPages,
    selectedMenus,
    exportMutation,
  ])

  return (
    <div className={styles.panel} data-testid="export-panel">
      <h3 className={styles.panelTitle}>{t('packages.packageDetails')}</h3>
      <div className={styles.formGroup}>
        <label htmlFor="package-name" className={styles.label}>
          {t('packages.packageName')} <span className={styles.required}>*</span>
        </label>
        <input
          id="package-name"
          type="text"
          className={styles.input}
          value={packageName}
          onChange={(e) => setPackageName(e.target.value)}
          placeholder={t('packages.packageNamePlaceholder')}
          required
        />
      </div>
      <div className={styles.formGroup}>
        <label htmlFor="package-version" className={styles.label}>
          {t('packages.packageVersion')} <span className={styles.required}>*</span>
        </label>
        <input
          id="package-version"
          type="text"
          className={styles.input}
          value={packageVersion}
          onChange={(e) => setPackageVersion(e.target.value)}
          placeholder="1.0.0"
          required
        />
      </div>
      <div className={styles.formGroup}>
        <label htmlFor="package-description" className={styles.label}>
          {t('packages.packageDescription')}
        </label>
        <textarea
          id="package-description"
          className={styles.textarea}
          value={packageDescription}
          onChange={(e) => setPackageDescription(e.target.value)}
          placeholder={t('packages.packageDescriptionPlaceholder')}
          rows={3}
        />
      </div>

      <h3 className={styles.panelTitle}>{t('packages.selectItems')}</h3>
      <p className={styles.panelDescription}>{t('packages.selectItemsDescription')}</p>

      <div className={styles.itemSections}>
        <ItemSelection
          title={t('navigation.collections')}
          items={collections}
          selectedIds={selectedCollections}
          onSelectionChange={setSelectedCollections}
          isLoading={collectionsLoading}
        />
        <ItemSelection
          title={t('navigation.roles')}
          items={roles}
          selectedIds={selectedRoles}
          onSelectionChange={setSelectedRoles}
          isLoading={rolesLoading}
        />
        <ItemSelection
          title={t('navigation.policies')}
          items={policies}
          selectedIds={selectedPolicies}
          onSelectionChange={setSelectedPolicies}
          isLoading={policiesLoading}
        />
        <ItemSelection
          title={t('navigation.pages')}
          items={pages}
          selectedIds={selectedPages}
          onSelectionChange={setSelectedPages}
          isLoading={pagesLoading}
        />
        <ItemSelection
          title={t('navigation.menus')}
          items={menus}
          selectedIds={selectedMenus}
          onSelectionChange={setSelectedMenus}
          isLoading={menusLoading}
        />
      </div>

      <div className={styles.panelActions}>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={handleExport}
          disabled={!canExport || exportMutation.isPending}
          data-testid="export-button"
        >
          {exportMutation.isPending ? t('common.loading') : t('packages.exportPackage')}
        </button>
      </div>
    </div>
  )
}

/**
 * Import Panel Component
 * Requirement 9.1: Display import options
 */
interface ImportPanelProps {
  onImportComplete: () => void
}

function ImportPanel({ onImportComplete }: ImportPanelProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)

  const previewMutation = useMutation({
    mutationFn: (file: File) => previewImport(apiClient, file),
    onSuccess: (data) => {
      setPreview(data)
    },
    onError: (error: Error) => {
      showToast(error.message, 'error')
    },
  })

  const importMutation = useMutation({
    mutationFn: ({ file, dryRun }: { file: File; dryRun: boolean }) =>
      executeImport(apiClient, file, dryRun),
    onSuccess: (data, variables) => {
      setImportResult(data)
      if (!variables.dryRun && data.success) {
        showToast(t('packages.importSuccess'), 'success')
        onImportComplete()
      }
    },
    onError: (error: Error) => {
      showToast(error.message, 'error')
    },
  })

  const handleFileSelect = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (file) {
        setSelectedFile(file)
        setPreview(null)
        setImportResult(null)
        previewMutation.mutate(file)
      }
    },
    [previewMutation]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      const file = e.dataTransfer.files?.[0]
      if (file && file.name.endsWith('.json')) {
        setSelectedFile(file)
        setPreview(null)
        setImportResult(null)
        previewMutation.mutate(file)
      }
    },
    [previewMutation]
  )

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
  }, [])

  const handleDryRun = useCallback(() => {
    if (selectedFile) {
      importMutation.mutate({ file: selectedFile, dryRun: true })
    }
  }, [selectedFile, importMutation])

  const handleImport = useCallback(() => {
    if (selectedFile) {
      importMutation.mutate({ file: selectedFile, dryRun: false })
    }
  }, [selectedFile, importMutation])

  const handleReset = useCallback(() => {
    setSelectedFile(null)
    setPreview(null)
    setImportResult(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }, [])

  return (
    <div className={styles.panel} data-testid="import-panel">
      <h3 className={styles.panelTitle}>{t('packages.uploadPackage')}</h3>
      <p className={styles.panelDescription}>{t('packages.uploadDescription')}</p>

      <div
        className={`${styles.dropZone} ${selectedFile ? styles.dropZoneActive : ''}`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onClick={() => fileInputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => e.key === 'Enter' && fileInputRef.current?.click()}
        aria-label={t('packages.dropZoneLabel')}
        data-testid="drop-zone"
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".json"
          onChange={handleFileSelect}
          className={styles.fileInput}
          data-testid="file-input"
        />
        {selectedFile ? (
          <div className={styles.selectedFile}>
            <span className={styles.fileIcon}>📦</span>
            <span className={styles.fileName}>{selectedFile.name}</span>
            <button
              type="button"
              className={styles.clearFileButton}
              onClick={(e) => {
                e.stopPropagation()
                handleReset()
              }}
              aria-label={t('common.clear')}
              data-testid="clear-file-button"
            >
              ×
            </button>
          </div>
        ) : (
          <div className={styles.dropZoneContent}>
            <span className={styles.dropZoneIcon}>📁</span>
            <span className={styles.dropZoneText}>{t('packages.dropZoneText')}</span>
            <span className={styles.dropZoneHint}>{t('packages.dropZoneHint')}</span>
          </div>
        )}
      </div>

      {previewMutation.isPending && (
        <div className={styles.previewLoading}>
          <LoadingSpinner size="small" label={t('packages.analyzingPackage')} />
        </div>
      )}

      {preview && (
        <div className={styles.previewSection} data-testid="import-preview">
          <h4 className={styles.previewTitle}>{t('packages.preview')}</h4>
          <div className={styles.previewStats}>
            <div className={styles.previewStat}>
              <span className={styles.previewStatLabel}>{t('packages.toCreate')}</span>
              <span className={styles.previewStatValue}>{preview.creates.length}</span>
            </div>
            <div className={styles.previewStat}>
              <span className={styles.previewStatLabel}>{t('packages.toUpdate')}</span>
              <span className={styles.previewStatValue}>{preview.updates.length}</span>
            </div>
            <div className={styles.previewStat}>
              <span className={styles.previewStatLabel}>{t('packages.conflicts')}</span>
              <span className={styles.previewStatValue}>{preview.conflicts.length}</span>
            </div>
          </div>
        </div>
      )}

      {importResult && (
        <div
          className={`${styles.resultSection} ${importResult.success ? styles.resultSuccess : styles.resultError}`}
          data-testid="import-result"
        >
          <h4 className={styles.resultTitle}>
            {importResult.success
              ? t('packages.importResultSuccess')
              : t('packages.importResultFailed')}
          </h4>
          <div className={styles.resultStats}>
            <span>
              {t('packages.created')}: {importResult.created}
            </span>
            <span>
              {t('packages.updated')}: {importResult.updated}
            </span>
            <span>
              {t('packages.skipped')}: {importResult.skipped}
            </span>
          </div>
          {importResult.errors.length > 0 && (
            <div className={styles.resultErrors}>
              {importResult.errors.map((err, idx) => (
                <div key={idx} className={styles.resultError}>
                  {err.item.name}: {err.message}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className={styles.panelActions}>
        <button
          type="button"
          className={styles.secondaryButton}
          onClick={handleDryRun}
          disabled={!selectedFile || importMutation.isPending}
          data-testid="dry-run-button"
        >
          {t('packages.dryRun')}
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={handleImport}
          disabled={!selectedFile || importMutation.isPending}
          data-testid="import-button"
        >
          {importMutation.isPending ? t('common.loading') : t('packages.apply')}
        </button>
      </div>
    </div>
  )
}

/**
 * History Panel Component
 * Requirement 9.10: Display package history
 */
interface HistoryPanelProps {
  packages: Package[]
  isLoading: boolean
  error: Error | null
  onRetry: () => void
}

function HistoryPanel({
  packages,
  isLoading,
  error,
  onRetry,
}: HistoryPanelProps): React.ReactElement {
  const { t, formatDate } = useI18n()

  if (isLoading) {
    return (
      <div className={styles.loadingContainer}>
        <LoadingSpinner label={t('common.loading')} />
      </div>
    )
  }

  if (error) {
    // Check if it's a 404 error (endpoint not implemented)
    const errorMessage = error.message || ''
    const isNotImplemented = errorMessage.includes('404') || errorMessage.includes('Not Found')

    if (isNotImplemented) {
      return (
        <div className={styles.emptyState} data-testid="history-not-available">
          <p>{t('packages.historyNotAvailable')}</p>
          <p className={styles.emptyStateHint}>{t('packages.historyNotAvailableHint')}</p>
        </div>
      )
    }

    return (
      <div className={styles.errorContainer}>
        <ErrorMessage error={error} onRetry={onRetry} />
      </div>
    )
  }

  if (packages.length === 0) {
    return (
      <div className={styles.emptyState} data-testid="history-empty">
        <p>{t('packages.noHistory')}</p>
        <p className={styles.emptyStateHint}>{t('packages.noHistoryHint')}</p>
      </div>
    )
  }

  return (
    <div className={styles.tableContainer} data-testid="history-table">
      <table className={styles.table}>
        <thead>
          <tr>
            <th>{t('packages.packageName')}</th>
            <th>{t('packages.type')}</th>
            <th>{t('packages.status')}</th>
            <th>{t('packages.items')}</th>
            <th>{t('packages.date')}</th>
          </tr>
        </thead>
        <tbody>
          {packages.map((pkg) => (
            <tr key={pkg.id} className={styles.tableRow} data-testid={`history-row-${pkg.id}`}>
              <td className={styles.nameCell}>{pkg.name}</td>
              <td>
                <TypeBadge type={pkg.type} />
              </td>
              <td>
                <StatusBadge status={pkg.status} />
              </td>
              <td className={styles.countCell}>{pkg.items.length}</td>
              <td className={styles.dateCell}>
                {formatDate(new Date(pkg.createdAt), {
                  year: 'numeric',
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * PackagesPage Component
 *
 * Main component for package management with tabs for export, import, and history.
 *
 * Requirements:
 * - 9.1: Display export and import options
 * - 9.10: Display package history
 */
export function PackagesPage({ testId }: PackagesPageProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<TabType>('export')

  const {
    data: packages = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['package-history'],
    queryFn: () => fetchPackageHistory(apiClient),
    enabled: activeTab === 'history', // Only fetch when history tab is active
    retry: false, // Don't retry if endpoint doesn't exist
  })

  const handleExportComplete = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['package-history'] })
    setActiveTab('history')
  }, [queryClient])

  const handleImportComplete = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['package-history'] })
    setActiveTab('history')
  }, [queryClient])

  return (
    <div className={styles.container} data-testid={testId || 'packages-page'}>
      <header className={styles.header}>
        <h1 className={styles.title}>{t('packages.title')}</h1>
      </header>

      <div className={styles.tabs} role="tablist" aria-label={t('packages.title')}>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'export'}
          aria-controls="export-panel"
          className={`${styles.tab} ${activeTab === 'export' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('export')}
          data-testid="tab-export"
        >
          {t('packages.export')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'import'}
          aria-controls="import-panel"
          className={`${styles.tab} ${activeTab === 'import' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('import')}
          data-testid="tab-import"
        >
          {t('packages.import')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'history'}
          aria-controls="history-panel"
          className={`${styles.tab} ${activeTab === 'history' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('history')}
          data-testid="tab-history"
        >
          {t('packages.history')}
        </button>
      </div>

      <div className={styles.tabContent}>
        {activeTab === 'export' && (
          <div id="export-panel" role="tabpanel" aria-labelledby="tab-export">
            <ExportPanel onExportComplete={handleExportComplete} />
          </div>
        )}
        {activeTab === 'import' && (
          <div id="import-panel" role="tabpanel" aria-labelledby="tab-import">
            <ImportPanel onImportComplete={handleImportComplete} />
          </div>
        )}
        {activeTab === 'history' && (
          <div id="history-panel" role="tabpanel" aria-labelledby="tab-history">
            <HistoryPanel
              packages={packages}
              isLoading={isLoading}
              error={error as Error | null}
              onRetry={() => refetch()}
            />
          </div>
        )}
      </div>
    </div>
  )
}

export default PackagesPage
