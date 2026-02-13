/**
 * WorkersPage Component
 *
 * Displays a list of all registered workers with status indicators, capacity info,
 * and provides actions like viewing assignments and triggering rebalance.
 * Auto-refreshes every 15 seconds.
 *
 * Requirements:
 * - Display a list of all workers with status badges
 * - Click a worker row to see its assignments
 * - Rebalance button triggers POST /control/workers/rebalance
 * - Auto-refresh every 15 seconds
 * - Relative time display for Last Heartbeat
 */

import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './WorkersPage.module.css'

/**
 * Worker interface matching the API response
 */
export interface Worker {
  id: string
  podName: string
  host: string
  port: number
  pool: string
  status: 'READY' | 'STARTING' | 'DRAINING' | 'OFFLINE' | 'FAILED'
  capacity: number
  currentLoad: number
  lastHeartbeat: string
  createdAt: string
  updatedAt: string
}

/**
 * Worker assignment interface
 */
export interface WorkerAssignment {
  id: string
  workerId: string
  collectionName: string
  collectionDisplayName: string
  status: string
  assignedAt: string
}

/**
 * Rebalance result interface
 */
export interface RebalanceResult {
  moved: number
  message: string
}

/**
 * Props for WorkersPage component
 */
export interface WorkersPageProps {
  /** Optional test ID for testing */
  testId?: string
}

const AUTO_REFRESH_INTERVAL = 15000

/**
 * Format a timestamp to a relative time string
 */
function formatRelativeTime(timestamp: string): string {
  const now = Date.now()
  const then = new Date(timestamp).getTime()
  const diffMs = now - then

  if (diffMs < 0 || isNaN(diffMs)) {
    return '-'
  }

  const seconds = Math.floor(diffMs / 1000)
  if (seconds < 5) return 'just now'
  if (seconds < 60) return `${seconds}s ago`

  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`

  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

/**
 * Status Badge Component for worker status
 */
interface WorkerStatusBadgeProps {
  status: Worker['status']
}

function WorkerStatusBadge({ status }: WorkerStatusBadgeProps): React.ReactElement {
  const statusClass = (() => {
    switch (status) {
      case 'READY':
        return styles.statusReady
      case 'STARTING':
        return styles.statusStarting
      case 'DRAINING':
        return styles.statusDraining
      case 'OFFLINE':
        return styles.statusOffline
      case 'FAILED':
        return styles.statusFailed
      default:
        return styles.statusOffline
    }
  })()

  return (
    <span className={`${styles.statusBadge} ${statusClass}`} data-testid="worker-status-badge">
      {status}
    </span>
  )
}

/**
 * Capacity bar showing current load vs capacity
 */
interface CapacityBarProps {
  current: number
  capacity: number
}

function CapacityBar({ current, capacity }: CapacityBarProps): React.ReactElement {
  const percentage = capacity > 0 ? Math.min((current / capacity) * 100, 100) : 0
  const barClass =
    percentage >= 90
      ? styles.capacityBarCritical
      : percentage >= 70
        ? styles.capacityBarWarning
        : styles.capacityBarNormal

  return (
    <div className={styles.capacityContainer} data-testid="capacity-bar">
      <div className={styles.capacityBar}>
        <div className={`${styles.capacityFill} ${barClass}`} style={{ width: `${percentage}%` }} />
      </div>
      <span className={styles.capacityText}>
        {current}/{capacity}
      </span>
    </div>
  )
}

/**
 * Worker Assignments Modal
 */
interface AssignmentsModalProps {
  worker: Worker
  assignments: WorkerAssignment[]
  isLoading: boolean
  onClose: () => void
}

function AssignmentsModal({
  worker,
  assignments,
  isLoading,
  onClose,
}: AssignmentsModalProps): React.ReactElement {
  const { t } = useI18n()

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    },
    [onClose]
  )

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      data-testid="assignments-modal-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="assignments-modal-title"
        data-testid="assignments-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="assignments-modal-title" className={styles.modalTitle}>
            {t('workers.assignmentsFor', { name: worker.podName })}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="assignments-modal-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          {isLoading ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : assignments.length === 0 ? (
            <div className={styles.emptyState} data-testid="no-assignments">
              <p>{t('workers.noAssignments')}</p>
            </div>
          ) : (
            <div className={styles.tableContainer}>
              <table
                className={styles.table}
                role="grid"
                aria-label={t('workers.assignments')}
                data-testid="assignments-table"
              >
                <thead>
                  <tr role="row">
                    <th role="columnheader" scope="col">
                      {t('workers.collection')}
                    </th>
                    <th role="columnheader" scope="col">
                      {t('workers.assignmentStatus')}
                    </th>
                    <th role="columnheader" scope="col">
                      {t('workers.assignedAt')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {assignments.map((assignment, index) => (
                    <tr
                      key={assignment.id}
                      role="row"
                      className={styles.tableRow}
                      data-testid={`assignment-row-${index}`}
                    >
                      <td role="gridcell" className={styles.nameCell}>
                        {assignment.collectionDisplayName || assignment.collectionName}
                      </td>
                      <td role="gridcell">
                        <span className={styles.assignmentStatusBadge}>{assignment.status}</span>
                      </td>
                      <td role="gridcell" className={styles.dateCell}>
                        {formatRelativeTime(assignment.assignedAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={onClose}
            data-testid="assignments-modal-done"
          >
            {t('common.close')}
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * WorkersPage Component
 *
 * Main page for viewing and managing workers in the EMF Admin UI.
 */
export function WorkersPage({ testId = 'workers-page' }: WorkersPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Modal state for viewing assignments
  const [selectedWorker, setSelectedWorker] = useState<Worker | null>(null)

  // Relative time updater
  const [, setTick] = useState(0)
  const tickRef = useRef<ReturnType<typeof setInterval>>()

  useEffect(() => {
    tickRef.current = setInterval(() => {
      setTick((prev) => prev + 1)
    }, 5000)
    return () => {
      if (tickRef.current) clearInterval(tickRef.current)
    }
  }, [])

  // Fetch workers query with auto-refresh
  const {
    data: workersData,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['workers'],
    queryFn: () => apiClient.get<Worker[]>('/control/workers'),
    refetchInterval: AUTO_REFRESH_INTERVAL,
  })

  const workers: Worker[] = Array.isArray(workersData)
    ? workersData
    : ((workersData as unknown as { content?: Worker[] })?.content ?? [])

  // Fetch assignments when a worker is selected
  const { data: assignmentsData, isLoading: assignmentsLoading } = useQuery({
    queryKey: ['worker-assignments', selectedWorker?.id],
    queryFn: () =>
      apiClient.get<WorkerAssignment[]>(`/control/workers/${selectedWorker!.id}/assignments`),
    enabled: !!selectedWorker,
  })

  const assignments: WorkerAssignment[] = Array.isArray(assignmentsData)
    ? assignmentsData
    : ((assignmentsData as unknown as { content?: WorkerAssignment[] })?.content ?? [])

  // Rebalance mutation
  const rebalanceMutation = useMutation({
    mutationFn: () => apiClient.post<RebalanceResult>('/control/workers/rebalance', {}),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['workers'] })
      const message = result?.message || t('workers.rebalanceSuccess')
      showToast(message, 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  // Handle clicking a worker row
  const handleWorkerClick = useCallback((worker: Worker) => {
    setSelectedWorker(worker)
  }, [])

  // Handle closing the assignments modal
  const handleCloseAssignments = useCallback(() => {
    setSelectedWorker(null)
  }, [])

  // Handle rebalance
  const handleRebalance = useCallback(() => {
    rebalanceMutation.mutate()
  }, [rebalanceMutation])

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>{t('workers.title')}</h1>
          <span className={styles.workerCount}>
            {t('workers.count', { count: String(workers.length) })}
          </span>
        </div>
        <div className={styles.headerActions}>
          <span className={styles.autoRefreshLabel}>{t('workers.autoRefresh')}</span>
          <button
            type="button"
            className={styles.rebalanceButton}
            onClick={handleRebalance}
            disabled={rebalanceMutation.isPending}
            aria-label={t('workers.rebalance')}
            data-testid="rebalance-button"
          >
            {rebalanceMutation.isPending ? t('common.loading') : t('workers.rebalance')}
          </button>
        </div>
      </header>

      {/* Workers Table */}
      {workers.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('workers.noWorkers')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('workers.title')}
            data-testid="workers-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('workers.podName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('workers.host')}
                </th>
                <th role="columnheader" scope="col">
                  {t('workers.port')}
                </th>
                <th role="columnheader" scope="col">
                  {t('workers.pool')}
                </th>
                <th role="columnheader" scope="col">
                  {t('workers.status')}
                </th>
                <th role="columnheader" scope="col">
                  {t('workers.capacityLabel')}
                </th>
                <th role="columnheader" scope="col">
                  {t('workers.lastHeartbeat')}
                </th>
                <th role="columnheader" scope="col">
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {workers.map((worker, index) => (
                <tr
                  key={worker.id}
                  role="row"
                  className={`${styles.tableRow} ${styles.clickableRow}`}
                  onClick={() => handleWorkerClick(worker)}
                  data-testid={`worker-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    <code>{worker.podName}</code>
                  </td>
                  <td role="gridcell" className={styles.hostCell}>
                    {worker.host}
                  </td>
                  <td role="gridcell" className={styles.portCell}>
                    {worker.port}
                  </td>
                  <td role="gridcell" className={styles.poolCell}>
                    <span className={styles.poolBadge}>{worker.pool}</span>
                  </td>
                  <td role="gridcell" className={styles.statusCell}>
                    <WorkerStatusBadge status={worker.status} />
                  </td>
                  <td role="gridcell" className={styles.capacityCell}>
                    <CapacityBar current={worker.currentLoad} capacity={worker.capacity} />
                  </td>
                  <td role="gridcell" className={styles.heartbeatCell}>
                    {formatRelativeTime(worker.lastHeartbeat)}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={(e) => {
                          e.stopPropagation()
                          handleWorkerClick(worker)
                        }}
                        aria-label={`${t('workers.viewAssignments')} ${worker.podName}`}
                        data-testid={`view-assignments-button-${index}`}
                      >
                        {t('workers.viewAssignments')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Assignments Modal */}
      {selectedWorker && (
        <AssignmentsModal
          worker={selectedWorker}
          assignments={assignments}
          isLoading={assignmentsLoading}
          onClose={handleCloseAssignments}
        />
      )}
    </div>
  )
}

export default WorkersPage
