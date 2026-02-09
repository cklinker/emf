/**
 * useGlobalShortcuts Hook
 *
 * Registers all global keyboard shortcuts for the EMF UI.
 * Should be called once at the App level.
 *
 * Shortcuts:
 * - ? → Show keyboard shortcuts help
 * - Ctrl+K / Cmd+K → Open global search
 * - / → Focus filter input
 * - g h → Go to Home
 * - g c → Go to Collections
 * - g r → Go to Resources
 * - n → New record (on list page)
 * - e → Edit record (on detail page)
 * - Backspace → Go back
 * - Escape → Close help overlay
 */

import { useState, useCallback, useRef } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useKeyboardShortcuts } from './useKeyboardShortcuts'

export function useGlobalShortcuts() {
  const navigate = useNavigate()
  const location = useLocation()
  const [helpOpen, setHelpOpen] = useState(false)

  // Track "g" prefix for two-key combos (g h, g c, g r)
  const gPrefixRef = useRef(false)
  const gTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Determine context
  const isDetailPage = /^\/resources\/[^/]+\/[^/]+$/.test(location.pathname)
  const isListPage =
    /^\/resources\/[^/]+$/.test(location.pathname) && !location.pathname.endsWith('/new')

  const clearGPrefix = useCallback(() => {
    gPrefixRef.current = false
    if (gTimeoutRef.current) {
      clearTimeout(gTimeoutRef.current)
      gTimeoutRef.current = null
    }
  }, [])

  useKeyboardShortcuts([
    // ? → Show help
    {
      key: '?',
      handler: () => setHelpOpen(true),
      description: 'Show keyboard shortcuts',
    },
    // Ctrl+K → Global search
    {
      key: 'k',
      modifiers: { ctrl: true },
      handler: () => {
        const searchInput = document.querySelector(
          '[data-testid="global-search-input"]'
        ) as HTMLInputElement
        if (searchInput) searchInput.focus()
      },
      description: 'Open global search',
      preventDefault: true,
    },
    // Meta+K → Global search (Mac)
    {
      key: 'k',
      modifiers: { meta: true },
      handler: () => {
        const searchInput = document.querySelector(
          '[data-testid="global-search-input"]'
        ) as HTMLInputElement
        if (searchInput) searchInput.focus()
      },
      description: 'Open global search',
      preventDefault: true,
    },
    // / → Focus filter input
    {
      key: '/',
      handler: () => {
        const filterInput = document.querySelector(
          '[data-testid="filter-input"]'
        ) as HTMLInputElement
        if (filterInput) filterInput.focus()
      },
      description: 'Focus filter input',
      preventDefault: true,
    },
    // g → Start two-key navigation combo
    {
      key: 'g',
      handler: () => {
        gPrefixRef.current = true
        // Reset after 1 second if no follow-up key
        if (gTimeoutRef.current) clearTimeout(gTimeoutRef.current)
        gTimeoutRef.current = setTimeout(() => {
          gPrefixRef.current = false
        }, 1000)
      },
      description: 'Navigation prefix',
    },
    // h → Go to Home (after g)
    {
      key: 'h',
      handler: () => {
        if (gPrefixRef.current) {
          clearGPrefix()
          navigate('/')
        }
      },
      description: 'Go to Home',
    },
    // c → Go to Collections (after g)
    {
      key: 'c',
      handler: () => {
        if (gPrefixRef.current) {
          clearGPrefix()
          navigate('/collections')
        }
      },
      description: 'Go to Collections',
    },
    // r → Go to Resources (after g)
    {
      key: 'r',
      handler: () => {
        if (gPrefixRef.current) {
          clearGPrefix()
          navigate('/resources')
        }
      },
      description: 'Go to Resources',
    },
    // n → New record (on list page)
    {
      key: 'n',
      handler: () => {
        if (isListPage) {
          const match = location.pathname.match(/^\/resources\/([^/]+)$/)
          if (match) navigate(`/resources/${match[1]}/new`)
        }
      },
      description: 'New record',
    },
    // e → Edit record (on detail page)
    {
      key: 'e',
      handler: () => {
        if (isDetailPage) {
          const match = location.pathname.match(/^\/resources\/([^/]+)\/([^/]+)$/)
          if (match) navigate(`/resources/${match[1]}/${match[2]}/edit`)
        }
      },
      description: 'Edit record',
    },
    // Backspace → Go back
    {
      key: 'Backspace',
      handler: () => {
        navigate(-1)
      },
      description: 'Go back',
    },
    // Escape → close help
    {
      key: 'Escape',
      handler: () => setHelpOpen(false),
      allowInInput: true,
      description: 'Close dialog',
    },
  ])

  return { helpOpen, setHelpOpen }
}

export default useGlobalShortcuts
