/**
 * ActivityTimeline Component
 *
 * Displays a chronological activity feed on the record detail page.
 * Merges multiple data sources (record lifecycle, approvals, sharing)
 * into a single reverse-chronological timeline.
 *
 * Features:
 * - Color-coded timeline entries by activity type
 * - Relative time formatting for timestamps
 * - Collapsible with "Show More" / "Show Less" toggle
 * - Graceful handling of missing or erroring API endpoints
 * - Accessible with ARIA attributes
 */

import React, { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { cn } from '@/lib/utils'

import type { ApiClient } from '../../services/apiClient'

/**
 * Approval step instance from the control plane
 */
interface ApprovalStepInstance {
  id: string
  status: string
  assignedTo: string
  comments: string | null
  actedAt: string | null
}

/**
 * Approval instance from the control plane
 */
interface ApprovalInstance {
  id: string
  approvalProcessId: string
  collectionId: string
  recordId: string
  submittedBy: string
  status: string
  submittedAt: string
  completedAt: string | null
  stepInstances: ApprovalStepInstance[]
}

/**
 * Record share from the control plane
 */
interface RecordShare {
  id: string
  sharedWithId: string
  sharedWithType: string
  accessLevel: string
  reason: string
  createdAt: string
}

/**
 * Timeline entry types with visual indicators
 */
type TimelineEntryType =
  | 'CREATED'
  | 'UPDATED'
  | 'APPROVAL_SUBMITTED'
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_REJECTED'
  | 'APPROVAL_RECALLED'
  | 'SHARED'

/**
 * Internal timeline entry for rendering
 */
interface TimelineEntry {
  id: string
  type: TimelineEntryType
  description: string
  timestamp: string
}

/**
 * Props for the ActivityTimeline component
 */
export interface ActivityTimelineProps {
  /** UUID of the collection */
  collectionId: string
  /** Name of the collection */
  collectionName: string
  /** UUID of the record */
  recordId: string
  /** ISO timestamp when the record was created */
  recordCreatedAt?: string
  /** ISO timestamp when the record was last updated */
  recordUpdatedAt?: string
  /** Authenticated API client instance */
  apiClient: ApiClient
}

/**
 * Default number of entries to show before requiring "Show More"
 */
const DEFAULT_VISIBLE_COUNT = 5

/**
 * Format a date string as a relative time (e.g., "3 hours ago", "2 days ago").
 * Uses simple threshold-based formatting without external dependencies.
 */
function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)
  const diffWeeks = Math.floor(diffDays / 7)
  const diffMonths = Math.floor(diffDays / 30)
  const diffYears = Math.floor(diffDays / 365)

  if (diffSeconds < 60) {
    return 'just now'
  }
  if (diffMinutes < 60) {
    return diffMinutes === 1 ? '1 minute ago' : `${diffMinutes} minutes ago`
  }
  if (diffHours < 24) {
    return diffHours === 1 ? '1 hour ago' : `${diffHours} hours ago`
  }
  if (diffDays < 7) {
    return diffDays === 1 ? '1 day ago' : `${diffDays} days ago`
  }
  if (diffWeeks < 5) {
    return diffWeeks === 1 ? '1 week ago' : `${diffWeeks} weeks ago`
  }
  if (diffMonths < 12) {
    return diffMonths === 1 ? '1 month ago' : `${diffMonths} months ago`
  }
  return diffYears === 1 ? '1 year ago' : `${diffYears} years ago`
}

/**
 * Get Tailwind classes for a timeline entry type icon
 */
function getIconClasses(type: TimelineEntryType): string {
  const classMap: Record<TimelineEntryType, string> = {
    CREATED: 'bg-green-50 text-green-700 border-2 border-green-700',
    UPDATED: 'bg-blue-50 text-blue-700 border-2 border-blue-700',
    APPROVAL_SUBMITTED: 'bg-amber-50 text-amber-700 border-2 border-amber-700',
    APPROVAL_APPROVED: 'bg-green-50 text-green-700 border-2 border-green-700',
    APPROVAL_REJECTED: 'bg-red-50 text-red-700 border-2 border-red-700',
    APPROVAL_RECALLED: 'bg-muted text-muted-foreground border-2 border-muted-foreground',
    SHARED: 'bg-blue-50 text-blue-700 border-2 border-blue-700',
  }
  return classMap[type] || ''
}

/**
 * Get the icon symbol for a timeline entry type
 */
function getIconSymbol(type: TimelineEntryType): string {
  const symbolMap: Record<TimelineEntryType, string> = {
    CREATED: '+',
    UPDATED: '~',
    APPROVAL_SUBMITTED: '!',
    APPROVAL_APPROVED: '\u2713',
    APPROVAL_REJECTED: '\u2717',
    APPROVAL_RECALLED: '\u21A9',
    SHARED: '\u21C4',
  }
  return symbolMap[type] || '\u2022'
}

/**
 * Map an approval instance status to the corresponding timeline entry type
 */
function approvalStatusToEntryType(status: string): TimelineEntryType {
  const statusUpper = status.toUpperCase()
  if (statusUpper === 'APPROVED') return 'APPROVAL_APPROVED'
  if (statusUpper === 'REJECTED') return 'APPROVAL_REJECTED'
  if (statusUpper === 'RECALLED') return 'APPROVAL_RECALLED'
  return 'APPROVAL_SUBMITTED'
}

/**
 * ActivityTimeline Component
 *
 * Displays a merged, reverse-chronological activity feed combining
 * record lifecycle events, approval events, and sharing changes.
 *
 * @example
 * ```tsx
 * <ActivityTimeline
 *   collectionId="abc-123"
 *   collectionName="accounts"
 *   recordId="def-456"
 *   recordCreatedAt="2025-01-15T10:00:00Z"
 *   recordUpdatedAt="2025-01-16T14:30:00Z"
 *   apiClient={apiClient}
 * />
 * ```
 */
export function ActivityTimeline({
  collectionId,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  collectionName,
  recordId,
  recordCreatedAt,
  recordUpdatedAt,
  apiClient,
}: ActivityTimelineProps): React.ReactElement {
  const { t } = useI18n()
  const [expanded, setExpanded] = useState(false)

  // Fetch approval instances for the record
  const { data: approvalInstances } = useQuery({
    queryKey: ['activity-approvals', collectionId, recordId],
    queryFn: async () => {
      try {
        const instances = await apiClient.getList<ApprovalInstance>(`/api/approval-instances`)
        // Filter client-side for instances matching this record
        return (instances || []).filter((instance) => instance.recordId === recordId)
      } catch {
        // Gracefully handle 404 or other errors
        return []
      }
    },
    enabled: !!collectionId && !!recordId,
  })

  // Fetch sharing records for this record
  const { data: shares } = useQuery({
    queryKey: ['activity-shares', collectionId, recordId],
    queryFn: async () => {
      try {
        const result = await apiClient.getList<RecordShare>(
          `/api/record-shares?filter[collectionId][eq]=${collectionId}&filter[recordId][eq]=${recordId}`
        )
        return result || []
      } catch {
        // Gracefully handle 404 or other errors
        return []
      }
    },
    enabled: !!collectionId && !!recordId,
  })

  // Merge all data sources into a single timeline
  const entries: TimelineEntry[] = useMemo(() => {
    const allEntries: TimelineEntry[] = []

    // Record creation event
    if (recordCreatedAt) {
      allEntries.push({
        id: 'record-created',
        type: 'CREATED',
        description: t('activity.recordCreated'),
        timestamp: recordCreatedAt,
      })
    }

    // Record update event (only if different from created)
    if (recordUpdatedAt && recordCreatedAt && recordUpdatedAt !== recordCreatedAt) {
      allEntries.push({
        id: 'record-updated',
        type: 'UPDATED',
        description: t('activity.recordUpdated'),
        timestamp: recordUpdatedAt,
      })
    }

    // Approval events
    if (approvalInstances) {
      for (const instance of approvalInstances) {
        // Submitted event
        allEntries.push({
          id: `approval-submitted-${instance.id}`,
          type: 'APPROVAL_SUBMITTED',
          description: t('activity.approvalSubmitted'),
          timestamp: instance.submittedAt,
        })

        // Completed event (approved, rejected, recalled)
        if (
          instance.completedAt &&
          instance.status.toUpperCase() !== 'PENDING' &&
          instance.status.toUpperCase() !== 'SUBMITTED'
        ) {
          const entryType = approvalStatusToEntryType(instance.status)
          const descriptionKey =
            entryType === 'APPROVAL_APPROVED'
              ? 'activity.approvalApproved'
              : entryType === 'APPROVAL_REJECTED'
                ? 'activity.approvalRejected'
                : 'activity.approvalRecalled'

          allEntries.push({
            id: `approval-${instance.status.toLowerCase()}-${instance.id}`,
            type: entryType,
            description: t(descriptionKey),
            timestamp: instance.completedAt,
          })
        }
      }
    }

    // Sharing events
    if (shares) {
      for (const share of shares) {
        allEntries.push({
          id: `share-${share.id}`,
          type: 'SHARED',
          description: t('activity.sharedWith', {
            id: share.sharedWithId,
            accessLevel: share.accessLevel,
          }),
          timestamp: share.createdAt,
        })
      }
    }

    // Sort reverse chronological (newest first)
    allEntries.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())

    return allEntries
  }, [recordCreatedAt, recordUpdatedAt, approvalInstances, shares, t])

  const visibleEntries = expanded ? entries : entries.slice(0, DEFAULT_VISIBLE_COUNT)
  const hasMore = entries.length > DEFAULT_VISIBLE_COUNT
  const totalCount = entries.length

  return (
    <section
      className="bg-background border border-border rounded-lg overflow-hidden"
      aria-labelledby="activity-timeline-heading"
      data-testid="activity-timeline"
    >
      {/* Header */}
      <div className="flex justify-between items-center p-4 border-b border-border bg-muted/50 max-md:flex-col max-md:items-start max-md:gap-1">
        <h3 id="activity-timeline-heading" className="m-0 text-lg font-semibold text-foreground">
          {t('activity.title')}
        </h3>
        <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded bg-background text-muted-foreground">
          {totalCount}
        </span>
      </div>

      {/* Timeline or Empty State */}
      {entries.length === 0 ? (
        <div
          className="flex flex-col items-center justify-center px-6 py-8 text-center"
          data-testid="activity-timeline-empty"
        >
          <p className="m-0 text-sm text-muted-foreground">{t('activity.empty')}</p>
        </div>
      ) : (
        <div className="relative p-4" role="list" aria-label={t('activity.title')}>
          {visibleEntries.map((entry, index) => (
            <div
              key={entry.id}
              className={cn(
                'flex items-start gap-4 relative max-md:gap-2',
                index < visibleEntries.length - 1 ? 'pb-4' : 'pb-0'
              )}
              role="listitem"
              data-testid={`activity-entry-${entry.id}`}
            >
              {/* Icon */}
              <div
                className={cn(
                  'relative flex items-center justify-center w-8 h-8 rounded-full shrink-0 text-sm z-[1]',
                  'max-md:w-7 max-md:h-7 max-md:text-xs',
                  getIconClasses(entry.type)
                )}
                aria-hidden="true"
              >
                {getIconSymbol(entry.type)}
              </div>

              {/* Connector line */}
              {index < visibleEntries.length - 1 && (
                <div
                  className="absolute left-[15px] top-8 bottom-0 w-0.5 bg-border max-md:left-[13px] max-md:top-7"
                  aria-hidden="true"
                />
              )}

              {/* Content */}
              <div className="flex flex-col gap-1 pt-1 min-w-0 flex-1">
                <p className="m-0 text-sm font-medium text-foreground leading-snug">
                  {entry.description}
                </p>
                <p className="m-0 text-xs text-muted-foreground leading-snug">
                  <time dateTime={entry.timestamp}>{formatRelativeTime(entry.timestamp)}</time>
                </p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Show More / Show Less toggle */}
      {hasMore && (
        <button
          type="button"
          className={cn(
            'flex items-center justify-center w-full px-4 py-2 m-0',
            'bg-transparent border-0 border-t border-border',
            'text-sm font-medium text-primary cursor-pointer',
            'transition-colors motion-reduce:transition-none',
            'hover:bg-muted/50',
            'focus:outline-2 focus:outline-primary focus:-outline-offset-2'
          )}
          onClick={() => setExpanded((prev) => !prev)}
          aria-expanded={expanded}
          data-testid="activity-timeline-toggle"
        >
          {expanded
            ? t('activity.showLess')
            : t('activity.showMore', {
                count: String(entries.length - DEFAULT_VISIBLE_COUNT),
              })}
        </button>
      )}
    </section>
  )
}

export default ActivityTimeline
