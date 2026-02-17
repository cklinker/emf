/**
 * AttachmentsSection Component
 *
 * Displays file attachments associated with a record on the detail page.
 *
 * Features:
 * - Fetches attachments from the API with graceful 404 fallback ("coming soon")
 * - Compact list view with file icon, name, size, and date
 * - Download link per attachment
 * - Delete action per attachment
 * - Upload button placeholder (pending backend support)
 * - Accessible with ARIA attributes and data-testid markers
 */

import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Paperclip, Download, Trash2, Upload } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import type { ApiClient } from '../../services/apiClient'

/**
 * An attachment associated with a record
 */
interface Attachment {
  id: string
  fileName: string
  fileSize: number
  contentType: string
  uploadedBy: string
  uploadedAt: string
}

/**
 * Props for the AttachmentsSection component
 */
export interface AttachmentsSectionProps {
  /** UUID of the collection */
  collectionId: string
  /** UUID of the record */
  recordId: string
  /** Authenticated API client instance */
  apiClient: ApiClient
}

/**
 * Format a file size in bytes to a human-readable string.
 */
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const size = (bytes / Math.pow(k, i)).toFixed(i > 0 ? 1 : 0)
  return `${size} ${units[i]}`
}

/**
 * Format a date string as a relative time (e.g., "3 hours ago", "2 days ago").
 */
function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return dateStr

  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffSeconds < 60) return 'just now'
  if (diffMinutes < 60) return diffMinutes === 1 ? '1 minute ago' : `${diffMinutes} minutes ago`
  if (diffHours < 24) return diffHours === 1 ? '1 hour ago' : `${diffHours} hours ago`
  if (diffDays < 7) return diffDays === 1 ? '1 day ago' : `${diffDays} days ago`

  return date.toLocaleDateString()
}

/**
 * AttachmentsSection Component
 *
 * Displays and manages file attachments for a specific record. If the
 * backend attachments API is not yet available (404), a "coming soon"
 * placeholder is shown instead.
 *
 * @example
 * ```tsx
 * <AttachmentsSection
 *   collectionId="abc-123"
 *   recordId="def-456"
 *   apiClient={apiClient}
 * />
 * ```
 */
export function AttachmentsSection({
  collectionId,
  recordId,
  apiClient,
}: AttachmentsSectionProps): React.ReactElement {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const [apiAvailable, setApiAvailable] = useState(true)

  // Fetch attachments for this record
  const { data: attachments, isLoading } = useQuery({
    queryKey: ['attachments', collectionId, recordId],
    queryFn: async () => {
      try {
        const result = await apiClient.get<Attachment[]>(
          `/control/attachments/${collectionId}/${recordId}`
        )
        setApiAvailable(true)
        return result || []
      } catch {
        setApiAvailable(false)
        return []
      }
    },
    enabled: !!collectionId && !!recordId,
  })

  // Delete attachment mutation
  const deleteMutation = useMutation({
    mutationFn: async (attachmentId: string) => {
      return apiClient.delete(`/control/attachments/${attachmentId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['attachments', collectionId, recordId],
      })
    },
  })

  const handleDelete = useCallback(
    (attachmentId: string) => {
      if (window.confirm(t('attachments.confirmDelete'))) {
        deleteMutation.mutate(attachmentId)
      }
    },
    [deleteMutation, t]
  )

  // Sort attachments by upload date (newest first)
  const attachmentsList = Array.isArray(attachments) ? attachments : []
  const sortedAttachments = [...attachmentsList].sort(
    (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime()
  )

  // If API is not available, show coming soon placeholder
  if (!apiAvailable && !isLoading) {
    return (
      <section
        className="bg-card border border-border rounded-lg overflow-hidden"
        aria-labelledby="attachments-heading"
        data-testid="attachments-section"
      >
        <div className="flex justify-between items-center p-4 border-b border-border bg-muted">
          <h3 id="attachments-heading" className="m-0 text-base font-semibold text-foreground">
            {t('attachments.title')}
          </h3>
        </div>
        <div
          className="text-center p-8 text-sm text-muted-foreground"
          data-testid="attachments-coming-soon"
        >
          <p>{t('attachments.comingSoon')}</p>
        </div>
      </section>
    )
  }

  return (
    <section
      className="bg-card border border-border rounded-lg overflow-hidden"
      aria-labelledby="attachments-heading"
      data-testid="attachments-section"
    >
      {/* Header */}
      <div className="flex justify-between items-center p-4 border-b border-border bg-muted">
        <h3 id="attachments-heading" className="m-0 text-base font-semibold text-foreground">
          {t('attachments.title')}
        </h3>
        <button
          type="button"
          className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-primary bg-transparent border border-primary rounded cursor-pointer transition-colors hover:bg-primary hover:text-primary-foreground"
          data-testid="attachments-upload-button"
          title={t('attachments.upload')}
        >
          <Upload size={14} aria-hidden="true" />
          {t('attachments.upload')}
        </button>
      </div>

      {/* Attachments List */}
      {sortedAttachments.length === 0 ? (
        <div
          className="flex flex-col items-center justify-center px-6 py-8 text-center text-sm text-muted-foreground"
          data-testid="attachments-empty"
        >
          <p>{t('attachments.empty')}</p>
        </div>
      ) : (
        <div className="flex flex-col" role="list" aria-label={t('attachments.title')}>
          {sortedAttachments.map((attachment) => (
            <div
              key={attachment.id}
              className="flex items-center gap-2 px-4 py-2 border-b border-border transition-colors last:border-b-0 hover:bg-accent"
              role="listitem"
              data-testid={`attachment-${attachment.id}`}
            >
              {/* File Icon */}
              <div
                className="flex items-center justify-center w-8 h-8 shrink-0 rounded bg-muted text-muted-foreground"
                aria-hidden="true"
              >
                <Paperclip size={16} />
              </div>

              {/* File Info */}
              <div className="flex flex-col gap-0.5 min-w-0 flex-1">
                <span className="text-sm font-medium text-foreground whitespace-nowrap overflow-hidden text-ellipsis">
                  {attachment.fileName}
                </span>
                <span className="flex items-center gap-1 text-xs text-muted-foreground">
                  {formatFileSize(attachment.fileSize)}
                  <span>&middot;</span>
                  <time dateTime={attachment.uploadedAt}>
                    {formatRelativeTime(attachment.uploadedAt)}
                  </time>
                  <span>&middot;</span>
                  <span>{attachment.uploadedBy}</span>
                </span>
              </div>

              {/* Actions */}
              <div className="flex gap-1 shrink-0">
                <button
                  type="button"
                  className="inline-flex items-center justify-center w-7 h-7 p-0 text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:text-primary hover:bg-muted"
                  aria-label={`${t('attachments.download')} ${attachment.fileName}`}
                  data-testid={`attachment-download-${attachment.id}`}
                  title={t('attachments.download')}
                >
                  <Download size={14} />
                </button>
                <button
                  type="button"
                  className="inline-flex items-center justify-center w-7 h-7 p-0 text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:text-destructive hover:bg-destructive/10"
                  onClick={() => handleDelete(attachment.id)}
                  aria-label={`${t('attachments.delete')} ${attachment.fileName}`}
                  data-testid={`attachment-delete-${attachment.id}`}
                  title={t('attachments.delete')}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

export default AttachmentsSection
