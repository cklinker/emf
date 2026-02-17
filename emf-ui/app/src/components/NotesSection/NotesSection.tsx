/**
 * NotesSection Component
 *
 * Displays a notes feed on the record detail page, allowing users to
 * add, edit, and delete text notes associated with a record.
 *
 * Features:
 * - Fetches notes from the API with graceful 404 fallback ("coming soon")
 * - Reverse-chronological display of notes
 * - Inline textarea for adding new notes (no modal)
 * - Edit and delete actions on each note
 * - Relative time formatting for timestamps
 * - Accessible with ARIA attributes and data-testid markers
 */

import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { MessageSquarePlus, Pencil, Trash2 } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import type { ApiClient } from '../../services/apiClient'

/**
 * A note associated with a record
 */
interface Note {
  id: string
  content: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

/**
 * Props for the NotesSection component
 */
export interface NotesSectionProps {
  /** UUID of the collection */
  collectionId: string
  /** UUID of the record */
  recordId: string
  /** Authenticated API client instance */
  apiClient: ApiClient
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
 * NotesSection Component
 *
 * Displays and manages notes for a specific record. If the backend
 * notes API is not yet available (404), a "coming soon" placeholder
 * is shown instead.
 *
 * @example
 * ```tsx
 * <NotesSection
 *   collectionId="abc-123"
 *   recordId="def-456"
 *   apiClient={apiClient}
 * />
 * ```
 */
export function NotesSection({
  collectionId,
  recordId,
  apiClient,
}: NotesSectionProps): React.ReactElement {
  const { t } = useI18n()
  const queryClient = useQueryClient()

  // UI state
  const [isAdding, setIsAdding] = useState(false)
  const [newContent, setNewContent] = useState('')
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null)
  const [editContent, setEditContent] = useState('')
  const [apiAvailable, setApiAvailable] = useState(true)

  // Fetch notes for this record
  const { data: notes, isLoading } = useQuery({
    queryKey: ['notes', collectionId, recordId],
    queryFn: async () => {
      try {
        const result = await apiClient.get<Note[]>(`/control/notes/${collectionId}/${recordId}`)
        setApiAvailable(true)
        return result || []
      } catch {
        setApiAvailable(false)
        return []
      }
    },
    enabled: !!collectionId && !!recordId,
  })

  // Create note mutation
  const createMutation = useMutation({
    mutationFn: async (content: string) => {
      return apiClient.post<Note>(`/control/notes/${collectionId}/${recordId}`, {
        content,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notes', collectionId, recordId] })
      setNewContent('')
      setIsAdding(false)
    },
  })

  // Update note mutation
  const updateMutation = useMutation({
    mutationFn: async ({ noteId, content }: { noteId: string; content: string }) => {
      return apiClient.put<Note>(`/control/notes/${noteId}`, { content })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notes', collectionId, recordId] })
      setEditingNoteId(null)
      setEditContent('')
    },
  })

  // Delete note mutation
  const deleteMutation = useMutation({
    mutationFn: async (noteId: string) => {
      return apiClient.delete(`/control/notes/${noteId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notes', collectionId, recordId] })
    },
  })

  // Handlers
  const handleAddClick = useCallback(() => {
    setIsAdding(true)
    setNewContent('')
  }, [])

  const handleCancelAdd = useCallback(() => {
    setIsAdding(false)
    setNewContent('')
  }, [])

  const handleSaveNew = useCallback(() => {
    const trimmed = newContent.trim()
    if (!trimmed) return
    createMutation.mutate(trimmed)
  }, [newContent, createMutation])

  const handleEditClick = useCallback((note: Note) => {
    setEditingNoteId(note.id)
    setEditContent(note.content)
  }, [])

  const handleCancelEdit = useCallback(() => {
    setEditingNoteId(null)
    setEditContent('')
  }, [])

  const handleSaveEdit = useCallback(() => {
    if (!editingNoteId) return
    const trimmed = editContent.trim()
    if (!trimmed) return
    updateMutation.mutate({ noteId: editingNoteId, content: trimmed })
  }, [editingNoteId, editContent, updateMutation])

  const handleDelete = useCallback(
    (noteId: string) => {
      if (window.confirm(t('notes.confirmDelete'))) {
        deleteMutation.mutate(noteId)
      }
    },
    [deleteMutation, t]
  )

  // Sort notes reverse chronological
  const notesList = Array.isArray(notes) ? notes : []
  const sortedNotes = [...notesList].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )

  // If API is not available, show coming soon placeholder
  if (!apiAvailable && !isLoading) {
    return (
      <section
        className="bg-card border border-border rounded-lg overflow-hidden"
        aria-labelledby="notes-heading"
        data-testid="notes-section"
      >
        <div className="flex justify-between items-center p-4 border-b border-border bg-muted">
          <h3 id="notes-heading" className="m-0 text-base font-semibold text-foreground">
            {t('notes.title')}
          </h3>
        </div>
        <div
          className="text-center p-8 text-sm text-muted-foreground"
          data-testid="notes-coming-soon"
        >
          <p>{t('notes.comingSoon')}</p>
        </div>
      </section>
    )
  }

  return (
    <section
      className="bg-card border border-border rounded-lg overflow-hidden"
      aria-labelledby="notes-heading"
      data-testid="notes-section"
    >
      {/* Header */}
      <div className="flex justify-between items-center p-4 border-b border-border bg-muted">
        <h3 id="notes-heading" className="m-0 text-base font-semibold text-foreground">
          {t('notes.title')}
        </h3>
        {!isAdding && (
          <button
            type="button"
            className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-primary bg-transparent border border-primary rounded cursor-pointer transition-colors hover:bg-primary hover:text-primary-foreground"
            onClick={handleAddClick}
            data-testid="notes-add-button"
          >
            <MessageSquarePlus size={14} aria-hidden="true" />
            {t('notes.addNote')}
          </button>
        )}
      </div>

      {/* Add Note Editor */}
      {isAdding && (
        <div className="p-4 border-b border-border" data-testid="notes-editor">
          <textarea
            className="w-full min-h-[80px] p-2 border border-border rounded text-sm font-[inherit] text-foreground bg-background resize-y box-border focus:outline-none focus:border-primary focus:ring-2 focus:ring-ring"
            value={newContent}
            onChange={(e) => setNewContent(e.target.value)}
            placeholder={t('notes.placeholder')}
            autoFocus
            data-testid="notes-editor-textarea"
          />
          <div className="flex gap-2 mt-2 justify-end">
            <button
              type="button"
              className="px-4 py-1 text-sm font-medium text-muted-foreground bg-transparent border border-border rounded cursor-pointer transition-colors hover:bg-accent"
              onClick={handleCancelAdd}
              data-testid="notes-cancel-button"
            >
              {t('notes.cancel')}
            </button>
            <button
              type="button"
              className="px-4 py-1 text-sm font-medium text-primary-foreground bg-primary border border-primary rounded cursor-pointer transition-colors hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed"
              onClick={handleSaveNew}
              disabled={!newContent.trim() || createMutation.isPending}
              data-testid="notes-save-button"
            >
              {createMutation.isPending ? t('common.saving') : t('notes.save')}
            </button>
          </div>
        </div>
      )}

      {/* Notes List */}
      {sortedNotes.length === 0 && !isAdding ? (
        <div
          className="flex flex-col items-center justify-center px-6 py-8 text-center text-sm text-muted-foreground"
          data-testid="notes-empty"
        >
          <p>{t('notes.empty')}</p>
        </div>
      ) : (
        <div className="flex flex-col" role="list" aria-label={t('notes.title')}>
          {sortedNotes.map((note) => (
            <div
              key={note.id}
              className="p-4 border-b border-border last:border-b-0"
              role="listitem"
              data-testid={`note-${note.id}`}
            >
              {editingNoteId === note.id ? (
                /* Edit mode */
                <div data-testid={`note-edit-${note.id}`}>
                  <textarea
                    className="w-full min-h-[80px] p-2 border border-border rounded text-sm font-[inherit] text-foreground bg-background resize-y box-border focus:outline-none focus:border-primary focus:ring-2 focus:ring-ring"
                    value={editContent}
                    onChange={(e) => setEditContent(e.target.value)}
                    autoFocus
                    data-testid={`note-edit-textarea-${note.id}`}
                  />
                  <div className="flex gap-2 mt-2 justify-end">
                    <button
                      type="button"
                      className="px-4 py-1 text-sm font-medium text-muted-foreground bg-transparent border border-border rounded cursor-pointer transition-colors hover:bg-accent"
                      onClick={handleCancelEdit}
                    >
                      {t('notes.cancel')}
                    </button>
                    <button
                      type="button"
                      className="px-4 py-1 text-sm font-medium text-primary-foreground bg-primary border border-primary rounded cursor-pointer transition-colors hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed"
                      onClick={handleSaveEdit}
                      disabled={!editContent.trim() || updateMutation.isPending}
                    >
                      {updateMutation.isPending ? t('common.saving') : t('notes.save')}
                    </button>
                  </div>
                </div>
              ) : (
                /* View mode */
                <>
                  <p className="m-0 text-sm text-foreground whitespace-pre-wrap leading-relaxed">
                    {note.content}
                  </p>
                  <div className="flex items-center gap-1 mt-2 text-xs text-muted-foreground">
                    <span>{note.createdBy}</span>
                    <span>&middot;</span>
                    <time dateTime={note.createdAt}>{formatRelativeTime(note.createdAt)}</time>
                  </div>
                  <div className="flex gap-2 mt-2">
                    <button
                      type="button"
                      className="inline-flex items-center gap-1 px-1 py-0.5 text-xs font-medium text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:text-primary"
                      onClick={() => handleEditClick(note)}
                      aria-label={t('notes.edit')}
                      data-testid={`note-edit-button-${note.id}`}
                    >
                      <Pencil size={12} aria-hidden="true" />
                      {t('notes.edit')}
                    </button>
                    <button
                      type="button"
                      className="inline-flex items-center gap-1 px-1 py-0.5 text-xs font-medium text-muted-foreground bg-transparent border-none rounded cursor-pointer transition-colors hover:text-destructive"
                      onClick={() => handleDelete(note.id)}
                      aria-label={t('notes.delete')}
                      data-testid={`note-delete-button-${note.id}`}
                    >
                      <Trash2 size={12} aria-hidden="true" />
                      {t('notes.delete')}
                    </button>
                  </div>
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

export default NotesSection
