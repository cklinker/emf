/**
 * Unit tests for the menu-config → nav-tab mapping (collections + custom pages).
 */
import { describe, it, expect } from 'vitest'
import { buildNavTabs, menuItemToTab } from './navTabs'
import type { MenuConfig } from '@/types/config'

describe('menuItemToTab', () => {
  it('maps a /resources/<collection> item to a collection tab', () => {
    expect(menuItemToTab({ id: '1', label: 'Titles', path: '/resources/titles' })).toEqual({
      key: '/resources/titles',
      kind: 'collection',
      target: 'titles',
      label: 'Titles',
      icon: undefined,
    })
  })

  it('maps a /app/p/<slug> item to a page tab', () => {
    expect(
      menuItemToTab({
        id: '2',
        label: 'Dashboard',
        path: '/app/p/dashboard',
        icon: 'LayoutDashboard',
      })
    ).toEqual({
      key: '/app/p/dashboard',
      kind: 'page',
      target: 'dashboard',
      label: 'Dashboard',
      icon: 'LayoutDashboard',
    })
  })

  it('also accepts the short /p/<slug> page form', () => {
    const tab = menuItemToTab({ id: '3', label: 'Reports', path: '/p/reports' })
    expect(tab).toMatchObject({ kind: 'page', target: 'reports' })
  })

  it('falls back to the collection name / slug when label is empty', () => {
    expect(menuItemToTab({ id: '4', label: '', path: '/resources/providers' })?.label).toBe(
      'providers'
    )
    expect(menuItemToTab({ id: '5', label: '', path: '/p/home' })?.label).toBe('home')
  })

  it('ignores items with no path or an unsupported path', () => {
    expect(menuItemToTab({ id: '6', label: 'External', path: 'https://example.com' })).toBeNull()
    expect(menuItemToTab({ id: '7', label: 'Settings', path: '/settings' })).toBeNull()
    expect(menuItemToTab({ id: '8', label: 'No path' })).toBeNull()
    expect(menuItemToTab({ id: '9', label: 'Empty resource', path: '/resources/' })).toBeNull()
  })
})

describe('buildNavTabs', () => {
  it('returns [] for undefined menus', () => {
    expect(buildNavTabs(undefined)).toEqual([])
  })

  it('flattens collection and page items across menus, preserving order', () => {
    const menus: MenuConfig[] = [
      {
        id: 'm1',
        name: 'Main',
        items: [
          { id: 'a', label: 'Titles', path: '/resources/titles' },
          { id: 'b', label: 'Dashboard', path: '/app/p/dashboard' },
          { id: 'c', label: 'Junk', path: '/nope' },
          { id: 'd', label: 'Providers', path: '/resources/providers' },
        ],
      },
    ]
    const tabs = buildNavTabs(menus)
    expect(tabs.map((t) => `${t.kind}:${t.target}`)).toEqual([
      'collection:titles',
      'page:dashboard',
      'collection:providers',
    ])
  })

  it('tolerates a menu with no items', () => {
    expect(buildNavTabs([{ id: 'm', name: 'Empty', items: [] }])).toEqual([])
  })

  it('maps dashboard and report paths to analytics tabs', () => {
    expect(menuItemToTab({ id: 'd', label: 'Sales', path: '/dashboards/dash-1' })).toEqual({
      key: '/dashboards/dash-1',
      kind: 'dashboard',
      target: 'dash-1',
      label: 'Sales',
      icon: undefined,
    })
    expect(menuItemToTab({ id: 'r', label: 'Pipeline', path: '/app/reports/rep-9' })).toEqual({
      key: '/app/reports/rep-9',
      kind: 'report',
      target: 'rep-9',
      label: 'Pipeline',
      icon: undefined,
    })
  })

  it('still ignores unrelated paths after the analytics kinds', () => {
    expect(menuItemToTab({ id: 'x', label: 'Nope', path: '/dashboards' })).toBeNull()
    expect(menuItemToTab({ id: 'y', label: 'Nope', path: '/reporting/thing' })).toBeNull()
  })
})
