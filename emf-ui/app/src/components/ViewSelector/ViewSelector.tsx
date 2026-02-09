/**
 * ViewSelector Component
 *
 * Dropdown for switching between saved list views of a collection.
 * Shows the active view name (or "All Records"), a list of saved views,
 * and an inline input to save the current configuration as a new view.
 *
 * Features:
 * - "All Records" option always shown first
 * - Star icon on the default view
 * - Delete button (X) on each saved view
 * - Inline text input to save current view
 * - Keyboard accessible (Enter to select, Escape to close)
 * - Click outside to close
 */

import { useState, useCallback, useRef, useEffect } from 'react'
import type { SavedView } from '../../hooks/useSavedViews'
import styles from './ViewSelector.module.css'

export interface ViewSelectorProps {
  views: SavedView[]
  activeView: SavedView | null
  onSelectView: (viewId: string | null) => void
  onSaveView: (name: string) => void
  onDeleteView: (viewId: string) => void
  onRenameView: (viewId: string, newName: string) => void
  onSetDefault: (viewId: string) => void
}

export function ViewSelector({
  views,
  activeView,
  onSelectView,
  onSaveView,
  onDeleteView,
  onRenameView,
  onSetDefault,
}: ViewSelectorProps): JSX.Element {
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveName, setSaveName] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const saveInputRef = useRef<HTMLInputElement>(null)

  // Suppress unused variable warning — onRenameView is part of the public
  // API and will be wired up by the integrating page. We accept it here
  // so the component contract stays stable.
  void onRenameView

  const toggleOpen = useCallback(() => {
    setOpen((prev) => {
      if (prev) {
        // Closing — reset save mode
        setSaving(false)
        setSaveName('')
      }
      return !prev
    })
  }, [])

  const close = useCallback(() => {
    setOpen(false)
    setSaving(false)
    setSaveName('')
  }, [])

  // Close on outside click and Escape
  useEffect(() => {
    if (!open) return

    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        close()
      }
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close()
        buttonRef.current?.focus()
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open, close])

  // Focus save input when entering save mode
  useEffect(() => {
    if (saving) {
      saveInputRef.current?.focus()
    }
  }, [saving])

  const handleSelectAllRecords = useCallback(() => {
    onSelectView(null)
    close()
  }, [onSelectView, close])

  const handleSelectView = useCallback(
    (viewId: string) => {
      onSelectView(viewId)
      close()
    },
    [onSelectView, close]
  )

  const handleDeleteView = useCallback(
    (event: React.MouseEvent, viewId: string) => {
      event.stopPropagation()
      onDeleteView(viewId)
    },
    [onDeleteView]
  )

  const handleSetDefault = useCallback(
    (event: React.MouseEvent, viewId: string) => {
      event.stopPropagation()
      onSetDefault(viewId)
    },
    [onSetDefault]
  )

  const handleStartSave = useCallback(() => {
    setSaving(true)
    setSaveName('')
  }, [])

  const handleConfirmSave = useCallback(() => {
    const trimmed = saveName.trim()
    if (!trimmed) return
    onSaveView(trimmed)
    setSaving(false)
    setSaveName('')
    close()
  }, [saveName, onSaveView, close])

  const handleSaveKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (event.key === 'Enter') {
        event.preventDefault()
        handleConfirmSave()
      } else if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        setSaving(false)
        setSaveName('')
      }
    },
    [handleConfirmSave]
  )

  const displayLabel = activeView?.name ?? 'All Records'

  return (
    <div className={styles.viewSelector} ref={containerRef} data-testid="view-selector">
      <button
        ref={buttonRef}
        type="button"
        className={styles.viewButton}
        onClick={toggleOpen}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label={`Current view: ${displayLabel}`}
        data-testid="view-selector-trigger"
      >
        <span>{displayLabel}</span>
        <span className={styles.viewButtonIcon} aria-hidden="true">
          {open ? '\u25B2' : '\u25BC'}
        </span>
      </button>

      {open && (
        <div
          className={styles.viewDropdown}
          role="listbox"
          aria-label="Saved views"
          data-testid="view-selector-dropdown"
        >
          {/* All Records option */}
          <button
            type="button"
            className={`${styles.viewOption} ${activeView === null ? styles.viewOptionActive : ''}`}
            role="option"
            aria-selected={activeView === null}
            onClick={handleSelectAllRecords}
            data-testid="view-option-all"
          >
            <span className={styles.viewOptionName}>All Records</span>
          </button>

          {views.length > 0 && <hr className={styles.divider} />}

          {/* Saved views list */}
          {views.map((view) => (
            <button
              type="button"
              key={view.id}
              className={`${styles.viewOption} ${activeView?.id === view.id ? styles.viewOptionActive : ''}`}
              role="option"
              aria-selected={activeView?.id === view.id}
              onClick={() => handleSelectView(view.id)}
              data-testid={`view-option-${view.id}`}
            >
              {view.isDefault && (
                <span
                  className={styles.viewOptionDefault}
                  title="Default view"
                  role="button"
                  tabIndex={0}
                  onClick={(e) => handleSetDefault(e, view.id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.stopPropagation()
                      onSetDefault(view.id)
                    }
                  }}
                  aria-label={`${view.name} is the default view`}
                >
                  &#9733;
                </span>
              )}
              {!view.isDefault && (
                <span
                  className={styles.viewOptionDefault}
                  title="Set as default"
                  role="button"
                  tabIndex={0}
                  onClick={(e) => handleSetDefault(e, view.id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.stopPropagation()
                      onSetDefault(view.id)
                    }
                  }}
                  style={{ opacity: 0.3 }}
                  aria-label={`Set ${view.name} as default view`}
                >
                  &#9734;
                </span>
              )}
              <span className={styles.viewOptionName}>{view.name}</span>
              <button
                type="button"
                className={styles.viewDeleteButton}
                onClick={(e) => handleDeleteView(e, view.id)}
                aria-label={`Delete view ${view.name}`}
                title={`Delete ${view.name}`}
                data-testid={`view-delete-${view.id}`}
              >
                &#x2715;
              </button>
            </button>
          ))}

          <hr className={styles.divider} />

          {/* Save current view */}
          {!saving && (
            <button
              type="button"
              className={styles.saveViewButton}
              onClick={handleStartSave}
              data-testid="view-save-button"
            >
              <span aria-hidden="true">+</span>
              Save Current View
            </button>
          )}

          {saving && (
            <div className={styles.saveViewRow}>
              <input
                ref={saveInputRef}
                type="text"
                className={styles.saveViewInput}
                value={saveName}
                onChange={(e) => setSaveName(e.target.value)}
                onKeyDown={handleSaveKeyDown}
                placeholder="View name..."
                aria-label="New view name"
                data-testid="view-save-input"
              />
              <button
                type="button"
                className={styles.saveViewConfirm}
                onClick={handleConfirmSave}
                disabled={!saveName.trim()}
                aria-label="Confirm save view"
                data-testid="view-save-confirm"
              >
                Save
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default ViewSelector
