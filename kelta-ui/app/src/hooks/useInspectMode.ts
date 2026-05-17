/**
 * useInspectMode
 *
 * Toggleable "show component labels" overlay for runtime detail pages,
 * matching the design handoff at `design_handoff_kelta_detail_layout/`.
 *
 * Activation sources (any one of these enables inspect mode):
 *   - URL query param `?inspect=1` (or any truthy value)
 *   - localStorage `kelta_inspect_mode` = "1"
 *   - Keyboard shortcut Cmd+Shift+L (Mac) / Ctrl+Shift+L (everywhere else)
 *     toggles and persists to localStorage so it survives navigation.
 *
 * When enabled, callers should apply the `kelta-inspect` class to a root
 * wrapper — the CSS in `styles/kelta-design.css` does the rest by outlining
 * every `[data-component]` element and labeling it via a ::before badge.
 */

import { useCallback, useEffect, useState } from 'react'

const STORAGE_KEY = 'kelta_inspect_mode'
const QUERY_PARAM = 'inspect'

function readInitial(): boolean {
  if (typeof window === 'undefined') return false
  const url = new URL(window.location.href)
  const param = url.searchParams.get(QUERY_PARAM)
  if (param !== null && param !== '' && param !== '0' && param.toLowerCase() !== 'false') {
    return true
  }
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export interface UseInspectModeResult {
  enabled: boolean
  toggle: () => void
  set: (next: boolean) => void
}

export function useInspectMode(): UseInspectModeResult {
  const [enabled, setEnabled] = useState<boolean>(() => readInitial())

  const persist = useCallback((next: boolean) => {
    if (typeof window === 'undefined') return
    try {
      if (next) {
        window.localStorage.setItem(STORAGE_KEY, '1')
      } else {
        window.localStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // localStorage may be disabled — toggle still works for the session
    }
  }, [])

  const set = useCallback(
    (next: boolean) => {
      setEnabled(next)
      persist(next)
    },
    [persist]
  )

  const toggle = useCallback(() => {
    setEnabled((prev) => {
      const next = !prev
      persist(next)
      return next
    })
  }, [persist])

  // Keyboard shortcut. We listen on the window so the toggle works regardless
  // of focus target — including on input fields where it'd otherwise be
  // swallowed, since the chord is non-printable.
  useEffect(() => {
    const handler = (event: KeyboardEvent): void => {
      const isCmd = event.metaKey || event.ctrlKey
      if (isCmd && event.shiftKey && event.key.toLowerCase() === 'l') {
        event.preventDefault()
        toggle()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [toggle])

  return { enabled, toggle, set }
}
