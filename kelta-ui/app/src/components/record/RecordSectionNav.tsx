/**
 * RecordSectionNav — sticky left-hand section navigator for a record detail page.
 *
 * Lists the resolved page-layout sections (label + field count) and, when the
 * page renders an activity timeline, an "Activity" entry below the list. Each
 * entry smooth-scrolls to its section anchor in the main column; the entry
 * whose section is currently in view is highlighted via an
 * IntersectionObserver scrollspy.
 *
 * Rendered through `RecordShell`'s `sectionNav` slot so both record stacks
 * (end-user `/app/o` + admin `/resources`) share it. Hidden below `lg` — on
 * small screens the main column already reads top-to-bottom.
 *
 * Part of the unified record experience — see
 * `.claude/docs/specs/unified-record/2-record-shell.md`.
 */

import React, { useCallback, useEffect, useState } from 'react'
import { History, LayoutList } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import { cn } from '@/lib/utils'

/** DOM anchor id for the activity timeline block on record detail pages. */
export const ACTIVITY_ANCHOR_ID = 'record-activity'

export interface RecordSectionNavItem {
  /** DOM id of the section's anchor element in the main column. */
  anchorId: string
  /** Section heading shown in the nav. */
  label: string
  /** Resolved field count (matches the section header's badge). */
  count?: number
}

export interface RecordSectionNavProps {
  /** Section entries, in render order. When empty and no activity anchor, renders nothing. */
  items: RecordSectionNavItem[]
  /** When set, an "Activity" entry below the section list scrolls to this anchor. */
  activityAnchorId?: string
  className?: string
}

/**
 * Observes the nav's target anchors and reports the one currently in the
 * reading band (top ~35% of the viewport). Falls back to no highlight where
 * IntersectionObserver is unavailable (jsdom).
 */
function useScrollSpy(anchorIds: string[]): [string | null, (id: string) => void] {
  const [activeId, setActiveId] = useState<string | null>(null)

  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return undefined
    const targets = anchorIds
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null)
    if (targets.length === 0) return undefined

    const visible = new Map<string, number>()
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            visible.set(entry.target.id, entry.boundingClientRect.top)
          } else {
            visible.delete(entry.target.id)
          }
        }
        if (visible.size > 0) {
          // Highlight the topmost section currently inside the reading band.
          const [topmost] = [...visible.entries()].sort((a, b) => a[1] - b[1])
          setActiveId(topmost[0])
        }
      },
      { rootMargin: '-10% 0px -55% 0px' }
    )
    targets.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [anchorIds])

  return [activeId, setActiveId]
}

function NavButton({
  anchorId,
  active,
  onNavigate,
  icon,
  label,
  count,
}: {
  anchorId: string
  active: boolean
  onNavigate: (anchorId: string) => void
  icon: React.ReactNode
  label: string
  count?: number
}): React.ReactElement {
  return (
    <button
      type="button"
      onClick={() => onNavigate(anchorId)}
      aria-current={active ? 'location' : undefined}
      data-testid={`section-nav-${anchorId}`}
      className={cn(
        'flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-sm transition-colors',
        active
          ? 'bg-accent font-medium text-foreground'
          : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground'
      )}
    >
      <span className="text-muted-foreground" aria-hidden="true">
        {icon}
      </span>
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {typeof count === 'number' && (
        <span className="inline-flex items-center rounded-full bg-muted px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground tabular-nums">
          {count}
        </span>
      )}
    </button>
  )
}

export function RecordSectionNav({
  items,
  activityAnchorId,
  className,
}: RecordSectionNavProps): React.ReactElement | null {
  const { t } = useI18n()
  const anchorIds = React.useMemo(
    () => [...items.map((i) => i.anchorId), ...(activityAnchorId ? [activityAnchorId] : [])],
    [items, activityAnchorId]
  )
  const [activeId, setActiveId] = useScrollSpy(anchorIds)

  const handleNavigate = useCallback(
    (anchorId: string) => {
      setActiveId(anchorId)
      document.getElementById(anchorId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    },
    [setActiveId]
  )

  if (items.length === 0 && !activityAnchorId) return null

  return (
    <nav
      aria-label={t('recordNav.sections', 'Sections')}
      data-testid="record-section-nav"
      className={cn('sticky top-6', className)}
    >
      {items.length > 0 && (
        <>
          <div className="px-2.5 pb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
            {t('recordNav.sections', 'Sections')}
          </div>
          <ul className="m-0 list-none space-y-0.5 p-0">
            {items.map((item) => (
              <li key={item.anchorId}>
                <NavButton
                  anchorId={item.anchorId}
                  active={activeId === item.anchorId}
                  onNavigate={handleNavigate}
                  icon={<LayoutList className="h-3.5 w-3.5" />}
                  label={item.label}
                  count={item.count}
                />
              </li>
            ))}
          </ul>
        </>
      )}
      {activityAnchorId && (
        <div className={cn(items.length > 0 && 'mt-3 border-t border-border pt-3')}>
          <NavButton
            anchorId={activityAnchorId}
            active={activeId === activityAnchorId}
            onNavigate={handleNavigate}
            icon={<History className="h-3.5 w-3.5" />}
            label={t('activity.title', 'Activity')}
          />
        </div>
      )}
    </nav>
  )
}

export default RecordSectionNav
