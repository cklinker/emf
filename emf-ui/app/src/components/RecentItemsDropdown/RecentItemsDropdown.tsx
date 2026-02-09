/**
 * RecentItemsDropdown Component
 *
 * Dropdown in the header showing recently viewed records.
 * Appears when clicking the clock icon button.
 */

import { useState, useCallback, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useAuth } from '../../context/AuthContext'
import { useRecentRecords } from '../../hooks/useRecentRecords'
import { formatRelativeTime } from '../../utils/formatRelativeTime'
import styles from './RecentItemsDropdown.module.css'

export interface RecentItemsDropdownProps {
  testId?: string
}

export function RecentItemsDropdown({
  testId = 'recent-items-dropdown',
}: RecentItemsDropdownProps): JSX.Element {
  const { t } = useI18n()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  const userId = user?.id ?? 'anonymous'
  const { recentRecords, clearRecentRecords } = useRecentRecords(userId)
  const displayRecords = recentRecords.slice(0, 15)

  const toggleOpen = useCallback(() => {
    setOpen((prev) => !prev)
  }, [])

  const close = useCallback(() => {
    setOpen(false)
  }, [])

  // Close on outside click
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

  const handleNavigate = useCallback(
    (path: string) => {
      close()
      navigate(path)
    },
    [close, navigate]
  )

  return (
    <div className={styles.container} ref={containerRef} data-testid={testId}>
      <button
        ref={buttonRef}
        type="button"
        className={styles.trigger}
        onClick={toggleOpen}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label={t('recent.title')}
        title={t('recent.title')}
      >
        <span aria-hidden="true">
          <Clock size={16} />
        </span>
      </button>

      {open && (
        <div className={styles.dropdown} role="menu" aria-label={t('recent.title')}>
          <div className={styles.dropdownHeader}>
            <span className={styles.dropdownTitle}>{t('recent.title')}</span>
            {displayRecords.length > 0 && (
              <button
                type="button"
                className={styles.clearButton}
                onClick={() => {
                  clearRecentRecords()
                  close()
                }}
              >
                {t('common.clear')}
              </button>
            )}
          </div>

          {displayRecords.length === 0 ? (
            <div className={styles.emptyState}>{t('recent.noRecent')}</div>
          ) : (
            <ul className={styles.list}>
              {displayRecords.map((record, idx) => (
                <li key={`${record.collectionName}-${record.id}-${idx}`}>
                  <button
                    type="button"
                    className={styles.item}
                    onClick={() =>
                      handleNavigate(`/resources/${record.collectionName}/${record.id}`)
                    }
                    role="menuitem"
                  >
                    <div className={styles.itemInfo}>
                      <span className={styles.itemName}>{record.displayValue}</span>
                      <span className={styles.itemCollection}>
                        {record.collectionDisplayName || record.collectionName}
                      </span>
                    </div>
                    <span className={styles.itemTime}>{formatRelativeTime(record.viewedAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

export default RecentItemsDropdown
