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
    <div className="relative" ref={containerRef} data-testid={testId}>
      <button
        ref={buttonRef}
        type="button"
        className="flex items-center justify-center w-9 h-9 rounded-md hover:bg-accent cursor-pointer text-muted-foreground transition-colors border-0 bg-transparent"
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
        <div
          className="absolute right-0 top-full mt-1 w-80 max-h-[480px] bg-card border border-border rounded-lg shadow-lg z-50 overflow-hidden flex flex-col"
          role="menu"
          aria-label={t('recent.title')}
        >
          <div className="flex items-center justify-between px-4 py-2.5 border-b border-border">
            <span className="text-sm font-semibold text-foreground">{t('recent.title')}</span>
            {displayRecords.length > 0 && (
              <button
                type="button"
                className="text-xs text-primary hover:underline cursor-pointer bg-transparent border-0"
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
            <div className="px-4 py-8 text-center text-sm text-muted-foreground">
              {t('recent.noRecent')}
            </div>
          ) : (
            <ul className="flex-1 overflow-y-auto list-none m-0 p-0">
              {displayRecords.map((record, idx) => (
                <li key={`${record.collectionName}-${record.id}-${idx}`}>
                  <button
                    type="button"
                    className="flex items-center gap-3 w-full px-4 py-2.5 hover:bg-accent cursor-pointer border-0 bg-transparent text-left transition-colors"
                    onClick={() =>
                      handleNavigate(`/resources/${record.collectionName}/${record.id}`)
                    }
                    role="menuitem"
                  >
                    <div className="flex-1 min-w-0">
                      <span className="block text-sm font-medium text-foreground truncate">
                        {record.displayValue}
                      </span>
                      <span className="block text-xs text-muted-foreground truncate">
                        {record.collectionDisplayName || record.collectionName}
                      </span>
                    </div>
                    <span className="text-xs text-muted-foreground shrink-0 whitespace-nowrap">
                      {formatRelativeTime(record.viewedAt)}
                    </span>
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
