/**
 * Pure mapping from bootstrap menu config to end-user navigation tabs.
 *
 * Two kinds of menu item are surfaced in the top nav:
 *  - collection lists — path `/resources/<collection>` → routes to `…/o/<collection>`
 *  - custom pages      — path `/p/<slug>` or `/app/p/<slug>` → routes to `…/p/<slug>`
 *
 * Any other path is ignored. Kept as a standalone, dependency-free module so it is unit-testable
 * without pulling the shell's React/context graph.
 */
import type { NavTab } from './TopNavBar'
import type { MenuConfig, MenuItemConfig } from '@/types/config'

/** Matches a custom-page menu path: `/p/<slug>` or `/app/p/<slug>` → captures the slug. */
const PAGE_PATH_RE = /^\/(?:app\/)?p\/([^/]+)/
/** Matches an analytics menu path: `/dashboards/<id>` or `/reports/<id>` (± `/app` prefix). */
const DASHBOARD_PATH_RE = /^\/(?:app\/)?dashboards\/([^/]+)/
const REPORT_PATH_RE = /^\/(?:app\/)?reports\/([^/]+)/

/**
 * Map a single menu item to a nav tab, or `null` if its path is not a surfaceable target.
 */
export function menuItemToTab(item: MenuItemConfig): NavTab | null {
  const path = item.path
  if (!path) return null

  if (path.startsWith('/resources/')) {
    const collectionName = path.replace('/resources/', '').split('/')[0]
    if (!collectionName) return null
    return {
      key: path,
      kind: 'collection',
      target: collectionName,
      label: item.label || collectionName,
      icon: item.icon,
    }
  }

  const pageMatch = PAGE_PATH_RE.exec(path)
  if (pageMatch) {
    const slug = pageMatch[1]
    return { key: path, kind: 'page', target: slug, label: item.label || slug, icon: item.icon }
  }

  const dashboardMatch = DASHBOARD_PATH_RE.exec(path)
  if (dashboardMatch) {
    const id = dashboardMatch[1]
    return { key: path, kind: 'dashboard', target: id, label: item.label || id, icon: item.icon }
  }

  const reportMatch = REPORT_PATH_RE.exec(path)
  if (reportMatch) {
    const id = reportMatch[1]
    return { key: path, kind: 'report', target: id, label: item.label || id, icon: item.icon }
  }

  return null
}

/**
 * Extract navigation tabs (collections + custom pages) from menu config, preserving menu and
 * `displayOrder` order (the bootstrap loader already sorts items by `displayOrder`).
 * Pure function — safe for React compiler optimization.
 */
export function buildNavTabs(menus: MenuConfig[] | undefined): NavTab[] {
  if (!menus) return []

  const tabs: NavTab[] = []
  for (const menu of menus) {
    for (const item of menu.items ?? []) {
      const tab = menuItemToTab(item)
      if (tab) tabs.push(tab)
    }
  }
  return tabs
}
