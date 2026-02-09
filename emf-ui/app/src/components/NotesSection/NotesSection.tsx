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
import styles from './NotesSection.module.css'

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
        className={styles.section}
        aria-labelledby="notes-heading"
        data-testid="notes-section"
      >
        <div className={styles.sectionHeader}>
          <h3 id="notes-heading" className={styles.sectionTitle}>
            {t('notes.title')}
          </h3>
        </div>
        <div className={styles.comingSoon} data-testid="notes-coming-soon">
          <p>{t('notes.comingSoon')}</p>
        </div>
      </section>
    )
  }

  return (
    <section className={styles.section} aria-labelledby="notes-heading" data-testid="notes-section">
      {/* Header */}
      <div className={styles.sectionHeader}>
        <h3 id="notes-heading" className={styles.sectionTitle}>
          {t('notes.title')}
        </h3>
        {!isAdding && (
          <button
            type="button"
            className={styles.addButton}
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
        <div className={styles.editorContainer} data-testid="notes-editor">
          <textarea
            className={styles.editor}
            value={newContent}
            onChange={(e) => setNewContent(e.target.value)}
            placeholder={t('notes.placeholder')}
            autoFocus
            data-testid="notes-editor-textarea"
          />
          <div className={styles.editorActions}>
            <button
              type="button"
              className={styles.cancelButton}
              onClick={handleCancelAdd}
              data-testid="notes-cancel-button"
            >
              {t('notes.cancel')}
            </button>
            <button
              type="button"
              className={styles.saveButton}
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
        <div className={styles.emptyState} data-testid="notes-empty">
          <p>{t('notes.empty')}</p>
        </div>
      ) : (
        <div className={styles.notesList} role="list" aria-label={t('notes.title')}>
          {sortedNotes.map((note) => (
            <div
              key={note.id}
              className={styles.noteCard}
              role="listitem"
              data-testid={`note-${note.id}`}
            >
              {editingNoteId === note.id ? (
                /* Edit mode */
                <div data-testid={`note-edit-${note.id}`}>
                  <textarea
                    className={styles.editor}
                    value={editContent}
                    onChange={(e) => setEditContent(e.target.value)}
                    autoFocus
                    data-testid={`note-edit-textarea-${note.id}`}
                  />
                  <div className={styles.editorActions}>
                    <button
                      type="button"
                      className={styles.cancelButton}
                      onClick={handleCancelEdit}
                    >
                      {t('notes.cancel')}
                    </button>
                    <button
                      type="button"
                      className={styles.saveButton}
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
                  <p className={styles.noteContent}>{note.content}</p>
                  <div className={styles.noteMeta}>
                    <span>{note.createdBy}</span>
                    <span>&middot;</span>
                    <time dateTime={note.createdAt}>{formatRelativeTime(note.createdAt)}</time>
                  </div>
                  <div className={styles.noteActions}>
                    <button
                      type="button"
                      className={styles.actionButton}
                      onClick={() => handleEditClick(note)}
                      aria-label={t('notes.edit')}
                      data-testid={`note-edit-button-${note.id}`}
                    >
                      <Pencil size={12} aria-hidden="true" />
                      {t('notes.edit')}
                    </button>
                    <button
                      type="button"
                      className={`${styles.actionButton} ${styles.deleteAction}`}
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
