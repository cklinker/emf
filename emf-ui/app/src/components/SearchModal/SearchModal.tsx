/**
 * SearchModal Component
 *
 * Command-palette style global search overlay (Cmd+K / Ctrl+K).
 * Searches across records, pages, and recent items.
 */

import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, Clock, FileText, ClipboardList } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useAuth } from '../../context/AuthContext'
import { useRecentRecords } from '../../hooks/useRecentRecords'
import { formatRelativeTime } from '../../utils/formatRelativeTime'
import styles from './SearchModal.module.css'

export interface SearchModalProps {
  open: boolean
  onClose: () => void
}

interface SearchResult {
  id: string
  type: 'record' | 'page' | 'recent'
  title: string
  subtitle: string
  path: string
}

const NAV_PAGES: SearchResult[] = [
  { id: 'nav-home', type: 'page', title: 'Home', subtitle: 'Home page', path: '/' },
  {
    id: 'nav-collections',
    type: 'page',
    title: 'Collections',
    subtitle: 'Manage collections',
    path: '/collections',
  },
  {
    id: 'nav-resources',
    type: 'page',
    title: 'Resource Browser',
    subtitle: 'Browse all resources',
    path: '/resources',
  },
  { id: 'nav-roles', type: 'page', title: 'Roles', subtitle: 'Manage roles', path: '/roles' },
  {
    id: 'nav-policies',
    type: 'page',
    title: 'Policies',
    subtitle: 'Manage policies',
    path: '/policies',
  },
  {
    id: 'nav-users',
    type: 'page',
    title: 'Users',
    subtitle: 'Manage users',
    path: '/users',
  },
  {
    id: 'nav-picklists',
    type: 'page',
    title: 'Picklists',
    subtitle: 'Manage picklists',
    path: '/picklists',
  },
  {
    id: 'nav-flows',
    type: 'page',
    title: 'Flows',
    subtitle: 'Flow engine',
    path: '/flows',
  },
  {
    id: 'nav-approvals',
    type: 'page',
    title: 'Approval Processes',
    subtitle: 'Manage approvals',
    path: '/approvals',
  },
  {
    id: 'nav-workflows',
    type: 'page',
    title: 'Workflow Rules',
    subtitle: 'Manage workflow rules',
    path: '/workflow-rules',
  },
  {
    id: 'nav-system-health',
    type: 'page',
    title: 'System Health',
    subtitle: 'System health dashboard',
    path: '/system-health',
  },
  {
    id: 'nav-audit',
    type: 'page',
    title: 'Audit Trail',
    subtitle: 'Setup audit trail',
    path: '/audit-trail',
  },
  {
    id: 'nav-sharing',
    type: 'page',
    title: 'Sharing Settings',
    subtitle: 'Record sharing configuration',
    path: '/sharing',
  },
  {
    id: 'nav-profiles',
    type: 'page',
    title: 'Profiles',
    subtitle: 'Permission profiles',
    path: '/profiles',
  },
  {
    id: 'nav-permission-sets',
    type: 'page',
    title: 'Permission Sets',
    subtitle: 'Permission set management',
    path: '/permission-sets',
  },
]

const SEARCH_HISTORY_KEY = 'emf_search_history'
const MAX_HISTORY = 10

function loadSearchHistory(): string[] {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveSearchHistory(history: string[]): void {
  try {
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(history.slice(0, MAX_HISTORY)))
  } catch {
    // ignore
  }
}

export function SearchModal({ open, onClose }: SearchModalProps): JSX.Element | null {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { user } = useAuth()
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const [searchHistory, setSearchHistory] = useState<string[]>(loadSearchHistory)
  const [recordResults, setRecordResults] = useState<SearchResult[]>([])
  const [searching, setSearching] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  const userId = user?.id ?? 'anonymous'
  const { recentRecords } = useRecentRecords(userId)

  // Focus input when opened
  useEffect(() => {
    if (open) {
      setQuery('')
      setActiveIndex(0)
      setRecordResults([])
      setTimeout(() => inputRef.current?.focus(), 50)
    }
  }, [open])

  // Debounced record search
  useEffect(() => {
    if (!query.trim()) {
      setRecordResults([])
      setSearching(false)
      return
    }

    setSearching(true)
    if (debounceRef.current) clearTimeout(debounceRef.current)

    debounceRef.current = setTimeout(async () => {
      try {
        // Search across collections - fetch collections first, then search each
        const collections =
          await apiClient.get<Array<{ name: string; displayName: string }>>('/control/collections')
        const colList = Array.isArray(collections) ? collections.slice(0, 5) : []

        const results: SearchResult[] = []
        await Promise.all(
          colList.map(async (col) => {
            try {
              const params = new URLSearchParams({ q: query, limit: '3' })
              const records = await apiClient.get<Array<Record<string, unknown>>>(
                `/api/${col.name}?${params.toString()}`
              )
              const recordList = Array.isArray(records) ? records : []
              recordList.forEach((rec) => {
                const id = String(rec.id ?? '')
                const displayVal = String(
                  rec.name ?? rec.title ?? rec.displayName ?? rec.display_name ?? id
                )
                results.push({
                  id: `rec-${col.name}-${id}`,
                  type: 'record',
                  title: displayVal,
                  subtitle: col.displayName || col.name,
                  path: `/resources/${col.name}/${id}`,
                })
              })
            } catch {
              // Collection search failed, skip
            }
          })
        )
        setRecordResults(results)
      } catch {
        setRecordResults([])
      } finally {
        setSearching(false)
      }
    }, 300)

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [query, apiClient])

  // Build results list
  const results = useMemo((): SearchResult[] => {
    const q = query.trim().toLowerCase()

    if (!q) {
      // Show recent records when no query
      return recentRecords.slice(0, 8).map((r) => ({
        id: `recent-${r.collectionName}-${r.id}`,
        type: 'recent' as const,
        title: r.displayValue,
        subtitle: `${r.collectionDisplayName || r.collectionName} · ${formatRelativeTime(r.viewedAt)}`,
        path: `/resources/${r.collectionName}/${r.id}`,
      }))
    }

    // Filter pages
    const pageResults = NAV_PAGES.filter(
      (p) => p.title.toLowerCase().includes(q) || p.subtitle.toLowerCase().includes(q)
    )

    // Filter recent records
    const recentResults = recentRecords
      .filter(
        (r) =>
          r.displayValue.toLowerCase().includes(q) ||
          r.collectionName.toLowerCase().includes(q) ||
          (r.collectionDisplayName && r.collectionDisplayName.toLowerCase().includes(q))
      )
      .slice(0, 5)
      .map((r) => ({
        id: `recent-${r.collectionName}-${r.id}`,
        type: 'recent' as const,
        title: r.displayValue,
        subtitle: r.collectionDisplayName || r.collectionName,
        path: `/resources/${r.collectionName}/${r.id}`,
      }))

    return [...recordResults, ...pageResults, ...recentResults]
  }, [query, recordResults, recentRecords])

  // Reset active index when results change
  useEffect(() => {
    setActiveIndex(0)
  }, [results.length])

  const handleSelect = useCallback(
    (result: SearchResult) => {
      // Save to search history
      if (query.trim()) {
        const newHistory = [query.trim(), ...searchHistory.filter((h) => h !== query.trim())]
        setSearchHistory(newHistory)
        saveSearchHistory(newHistory)
      }
      onClose()
      navigate(result.path)
    },
    [navigate, onClose, query, searchHistory]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onClose()
      } else if (e.key === 'ArrowDown') {
        e.preventDefault()
        setActiveIndex((prev) => Math.min(prev + 1, results.length - 1))
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        setActiveIndex((prev) => Math.max(prev - 1, 0))
      } else if (e.key === 'Enter' && results[activeIndex]) {
        e.preventDefault()
        handleSelect(results[activeIndex])
      }
    },
    [results, activeIndex, handleSelect, onClose]
  )

  const clearHistory = useCallback(() => {
    setSearchHistory([])
    saveSearchHistory([])
  }, [])

  if (!open) return null

  return (
    <div
      className={styles.overlay}
      role="presentation"
      onClick={onClose}
      data-testid="search-modal-overlay"
    >
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
      <div className={styles.modal} onClick={(e) => e.stopPropagation()} data-testid="search-modal">
        <div className={styles.inputContainer}>
          <span className={styles.searchIcon} aria-hidden="true">
            <Search size={18} />
          </span>
          <input
            ref={inputRef}
            type="text"
            className={styles.input}
            placeholder={t('search.placeholder')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            aria-label={t('search.label')}
            aria-autocomplete="list"
            aria-controls="search-results"
            aria-activedescendant={
              results[activeIndex] ? `search-result-${activeIndex}` : undefined
            }
            data-testid="search-input"
          />
          <kbd className={styles.kbd}>ESC</kbd>
        </div>

        <ul id="search-results" className={styles.results} role="listbox">
          {/* Search history when no query */}
          {!query.trim() && searchHistory.length > 0 && (
            <>
              <li className={styles.sectionLabel} role="presentation">
                <span>{t('search.recentSearches')}</span>
                <button className={styles.clearHistory} onClick={clearHistory} type="button">
                  {t('common.clear')}
                </button>
              </li>
              {searchHistory.map((term) => (
                <li key={`history-${term}`}>
                  <button
                    className={styles.historyItem}
                    onClick={() => setQuery(term)}
                    type="button"
                  >
                    <span className={styles.historyIcon} aria-hidden="true">
                      <Clock size={14} />
                    </span>
                    {term}
                  </button>
                </li>
              ))}
            </>
          )}

          {/* Results */}
          {!query.trim() && results.length > 0 && (
            <li className={styles.sectionLabel} role="presentation">
              {t('search.recentlyViewed')}
            </li>
          )}
          {query.trim() && results.length > 0 && (
            <li className={styles.sectionLabel} role="presentation">
              {t('search.results')}
              {searching && (
                <span className={styles.searchingIndicator}>{t('common.loading')}</span>
              )}
            </li>
          )}
          {results.map((result, idx) => (
            <li
              key={result.id}
              id={`search-result-${idx}`}
              role="option"
              aria-selected={idx === activeIndex}
              className={`${styles.resultItem} ${idx === activeIndex ? styles.resultItemActive : ''}`}
              onClick={() => handleSelect(result)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSelect(result)
              }}
              onMouseEnter={() => setActiveIndex(idx)}
            >
              <span className={styles.resultIcon} aria-hidden="true">
                {result.type === 'page' ? (
                  <FileText size={16} />
                ) : result.type === 'recent' ? (
                  <Clock size={16} />
                ) : (
                  <ClipboardList size={16} />
                )}
              </span>
              <div className={styles.resultInfo}>
                <span className={styles.resultTitle}>{result.title}</span>
                <span className={styles.resultSubtitle}>{result.subtitle}</span>
              </div>
              <span className={styles.resultType}>
                {result.type === 'page'
                  ? t('search.typePage')
                  : result.type === 'recent'
                    ? t('search.typeRecent')
                    : t('search.typeRecord')}
              </span>
            </li>
          ))}

          {query.trim() && results.length === 0 && !searching && (
            <li className={styles.noResults}>{t('search.noResults')}</li>
          )}
          {query.trim() && searching && results.length === 0 && (
            <li className={styles.noResults}>{t('common.loading')}</li>
          )}
        </ul>
      </div>
    </div>
  )
}

export default SearchModal
