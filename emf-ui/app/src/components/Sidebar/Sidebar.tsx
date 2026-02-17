/**
 * Sidebar Component
 *
 * Navigation sidebar with contextual sections: My Workspace, Tools, and Setup.
 * The Setup section is collapsed by default and contains all admin links.
 *
 * Requirements:
 * - 1.3: Configure navigation menus based on menu definitions
 * - 17.4: Collapse navigation menu into hamburger menu on mobile
 */

import { useState, useCallback, useMemo, useEffect } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import type { MenuConfig, MenuItemConfig } from '../../types/config'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useAuth } from '../../context/AuthContext'
import { useFavorites } from '../../hooks/useFavorites'
import { getTenantSlug } from '../../context/TenantContext'
import { cn } from '@/lib/utils'
import {
  Home,
  BarChart3,
  FolderOpen,
  Users,
  User,
  Settings,
  Lock,
  ClipboardList,
  Key,
  Wrench,
  FileText,
  Menu,
  Package,
  RefreshCw,
  Search,
  BookOpen,
  Plug,
  Mail,
  Zap,
  CheckCircle,
  Shuffle,
  Clock,
  Link,
  FileEdit,
  Handshake,
  ScrollText,
  Ruler,
  Building2,
  Puzzle,
  BarChart2,
  HelpCircle,
  LogOut,
  Star,
  Hammer,
  HeartPulse,
  Inbox,
  Server,
  ChevronRight,
  type LucideIcon,
} from 'lucide-react'

/**
 * Props for the Sidebar component
 */
export interface SidebarProps {
  /** Menu configurations to render (from bootstrap — used for Setup section) */
  menus: MenuConfig[]
  /** Whether the sidebar is collapsed (desktop/tablet) */
  collapsed: boolean
  /** Callback when toggle button is clicked */
  onToggle: () => void
  /** Callback when a menu item is clicked (useful for closing mobile menu) */
  onItemClick?: () => void
}

/**
 * Props for individual menu items
 */
interface MenuItemProps {
  /** Menu item configuration */
  item: MenuItemConfig
  /** Current nesting level (0 = top level) */
  level: number
  /** Whether the sidebar is collapsed */
  collapsed: boolean
  /** Callback when item is clicked */
  onItemClick?: () => void
}

const SETUP_COLLAPSED_KEY = 'emf_sidebar_setup_collapsed'

function getIcon(iconName?: string): React.ReactNode {
  if (!iconName) return null

  const iconMap: Record<string, LucideIcon> = {
    home: Home,
    dashboard: BarChart3,
    collections: FolderOpen,
    collection: FolderOpen,
    folder: FolderOpen,
    users: Users,
    user: User,
    settings: Settings,
    config: Settings,
    security: Lock,
    roles: Lock,
    policies: ClipboardList,
    policy: ClipboardList,
    oidc: Key,
    auth: Key,
    key: Key,
    builder: Wrench,
    pages: FileText,
    page: FileText,
    menus: Menu,
    menu: Menu,
    packages: Package,
    package: Package,
    migrations: RefreshCw,
    migration: RefreshCw,
    browser: Search,
    resources: BookOpen,
    resource: BookOpen,
    picklist: ClipboardList,
    plugins: Plug,
    plugin: Plug,
    extension: Plug,
    email: Mail,
    workflow: Zap,
    approval: CheckCircle,
    flow: Shuffle,
    schedule: Clock,
    webhook: Link,
    script: FileEdit,
    sharing: Handshake,
    audit: ScrollText,
    limits: Ruler,
    tenants: Building2,
    apps: Puzzle,
    report: BarChart2,
    help: HelpCircle,
    docs: BookOpen,
    logout: LogOut,
    star: Star,
    clock: Clock,
    search: Search,
    tools: Hammer,
    health: HeartPulse,
    bulk: Inbox,
    server: Server,
    workers: Server,
  }

  const IconComponent = iconMap[iconName.toLowerCase()]
  if (!IconComponent) return null
  return <IconComponent size={16} />
}

const levelClasses: Record<number, string> = {
  0: '',
  1: 'pl-4',
  2: 'pl-8',
  3: 'pl-12',
}

/**
 * MenuItem component renders a single menu item with optional children
 */
function MenuItem({ item, level, collapsed, onItemClick }: MenuItemProps): JSX.Element {
  const location = useLocation()
  const hasChildren = item.children && item.children.length > 0

  const isActive = useMemo(() => {
    if (item.path && location.pathname === item.path) {
      return true
    }
    if (hasChildren) {
      return item.children!.some((child) => child.path && location.pathname === child.path)
    }
    return false
  }, [item, location.pathname, hasChildren])

  // Initialize expanded if child is active
  const [isExpanded, setIsExpanded] = useState(() => {
    if (!hasChildren) return false
    if (item.path && location.pathname === item.path) return true
    return item.children?.some((child) => child.path && location.pathname === child.path) ?? false
  })

  const handleToggle = useCallback(
    (e: React.MouseEvent) => {
      if (hasChildren) {
        e.preventDefault()
        setIsExpanded((prev) => !prev)
      }
    },
    [hasChildren]
  )

  const handleClick = useCallback(() => {
    if (!hasChildren && onItemClick) {
      onItemClick()
    }
  }, [hasChildren, onItemClick])

  const icon = getIcon(item.icon)
  const itemClasses = cn(
    'list-none relative mx-2 my-1',
    levelClasses[Math.min(level, 3)],
    isActive && 'menuItem--active',
    collapsed && 'mx-0'
  )

  const contentClasses = cn(
    'flex items-center gap-2 w-full px-4 py-2 bg-transparent border-0 rounded text-sm font-medium no-underline text-left cursor-pointer transition-colors text-foreground hover:bg-accent/50',
    collapsed && 'justify-center p-2 w-10 h-10'
  )

  if (item.path && !hasChildren) {
    return (
      <li className={itemClasses} data-testid={`menu-item-${item.id}`}>
        <NavLink
          to={item.path}
          className={({ isActive: linkActive }) =>
            cn(
              contentClasses,
              linkActive && 'bg-primary/10 text-primary font-semibold menuItemContent--active'
            )
          }
          onClick={handleClick}
          title={collapsed ? item.label : undefined}
          aria-current={isActive ? 'page' : undefined}
        >
          {icon && (
            <span className="flex items-center justify-center w-5 h-5 shrink-0" aria-hidden="true">
              {icon}
            </span>
          )}
          {!collapsed && <span className="flex-1 truncate">{item.label}</span>}
        </NavLink>
      </li>
    )
  }

  if (hasChildren) {
    return (
      <li className={itemClasses} data-testid={`menu-item-${item.id}`}>
        <button
          type="button"
          className={contentClasses}
          onClick={handleToggle}
          aria-expanded={isExpanded}
          aria-controls={`submenu-${item.id}`}
          title={collapsed ? item.label : undefined}
        >
          {icon && (
            <span className="flex items-center justify-center w-5 h-5 shrink-0" aria-hidden="true">
              {icon}
            </span>
          )}
          {!collapsed && (
            <>
              <span className="flex-1 truncate">{item.label}</span>
              <span
                className={cn(
                  'flex items-center justify-center w-4 h-4 text-[0.625rem] opacity-70 transition-transform duration-150 shrink-0',
                  isExpanded && 'rotate-90 expandIcon--expanded'
                )}
                aria-hidden="true"
              >
                <ChevronRight size={14} />
              </span>
            </>
          )}
        </button>

        {!collapsed && (
          <ul
            id={`submenu-${item.id}`}
            className={cn(
              'list-none m-0 p-0 max-h-0 overflow-hidden transition-all duration-200',
              isExpanded && 'max-h-[500px] submenu--expanded'
            )}
            role="group"
            aria-label={`${item.label} submenu`}
          >
            {item.children!.map((child) => (
              <MenuItem
                key={child.id}
                item={child}
                level={level + 1}
                collapsed={collapsed}
                onItemClick={onItemClick}
              />
            ))}
          </ul>
        )}
      </li>
    )
  }

  return (
    <li className={itemClasses} data-testid={`menu-item-${item.id}`}>
      <span className={contentClasses} title={collapsed ? item.label : undefined}>
        {icon && (
          <span className="flex items-center justify-center w-5 h-5 shrink-0" aria-hidden="true">
            {icon}
          </span>
        )}
        {!collapsed && <span className="flex-1 truncate">{item.label}</span>}
      </span>
    </li>
  )
}

/**
 * Sidebar component with contextual navigation sections.
 *
 * Sections:
 * 1. My Workspace - Home, favorite collections, All Collections
 * 2. Tools - Reports, Dashboards
 * 3. Setup (collapsed by default) - All admin pages grouped by category
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function Sidebar({ menus, collapsed, onToggle, onItemClick }: SidebarProps): JSX.Element {
  const { t } = useI18n()
  const { user } = useAuth()
  const userId = user?.id ?? 'anonymous'
  const { favorites } = useFavorites(userId)

  let apiClient: ReturnType<typeof useApi>['apiClient'] | null = null
  try {
    const api = useApi()
    apiClient = api.apiClient
  } catch {
    // Not in ApiProvider
  }

  // Fetch collections for dynamic sidebar links
  const { data: collections } = useQuery({
    queryKey: ['sidebar-collections'],
    queryFn: () =>
      apiClient!.get<Array<{ name: string; displayName: string }>>('/control/collections'),
    enabled: !!apiClient,
    staleTime: 300000,
  })

  const collectionsList = useMemo(
    () => (Array.isArray(collections) ? collections : []),
    [collections]
  )

  // Favorited collections
  const favoriteCollections = useMemo(
    () => favorites.filter((f) => f.type === 'collection').slice(0, 10),
    [favorites]
  )

  // Setup section collapsed state (persisted)
  const [setupExpanded, setSetupExpanded] = useState(() => {
    try {
      return localStorage.getItem(SETUP_COLLAPSED_KEY) === 'true'
    } catch {
      return false
    }
  })

  const toggleSetup = useCallback(() => {
    setSetupExpanded((prev) => {
      const next = !prev
      try {
        localStorage.setItem(SETUP_COLLAPSED_KEY, String(next))
      } catch {
        // ignore
      }
      return next
    })
  }, [])

  // Check if any setup path is active to auto-expand
  const location = useLocation()
  useEffect(() => {
    const slug = getTenantSlug()
    const base = `/${slug}`
    const setupPaths = [
      `${base}/collections`,
      `${base}/oidc-providers`,
      `${base}/users`,
      `${base}/picklists`,
      `${base}/layouts`,
      `${base}/listviews`,
      `${base}/pages`,
      `${base}/menus`,
      `${base}/workflow-rules`,
      `${base}/approvals`,
      `${base}/flows`,
      `${base}/scheduled-jobs`,
      `${base}/email-templates`,
      `${base}/scripts`,
      `${base}/webhooks`,
      `${base}/connected-apps`,
      `${base}/plugins`,
      `${base}/packages`,
      `${base}/migrations`,
      `${base}/bulk-jobs`,
      `${base}/tenants`,
      `${base}/tenant-dashboard`,
      `${base}/system-health`,
      `${base}/workers`,
      `${base}/audit-trail`,
      `${base}/governor-limits`,
    ]
    if (setupPaths.some((p) => location.pathname.startsWith(p))) {
      setSetupExpanded(true)
    }
  }, [location.pathname])

  return (
    <nav
      className={cn(
        'flex flex-col w-full h-full py-2 overflow-y-auto overflow-x-hidden',
        collapsed && 'p-2 items-center'
      )}
      aria-label={t('navigation.main')}
      data-testid="sidebar"
    >
      {/* ==================== MY WORKSPACE ==================== */}
      <div className="mb-4 last:mb-0" data-testid="workspace-section">
        {!collapsed && (
          <h2 className="m-0 px-4 py-2 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-foreground truncate">
            {t('sidebar.workspace')}
          </h2>
        )}
        <ul className="list-none m-0 p-0" role="menubar" aria-label={t('sidebar.workspace')}>
          <MenuItem
            item={{
              id: 'home',
              label: t('navigation.home'),
              path: `/${getTenantSlug()}`,
              icon: 'home',
            }}
            level={0}
            collapsed={collapsed}
            onItemClick={onItemClick}
          />

          {/* Favorite collections */}
          {favoriteCollections.map((fav) => (
            <MenuItem
              key={`fav-${fav.id}`}
              item={{
                id: `fav-${fav.id}`,
                label: fav.displayValue,
                path: `/${getTenantSlug()}/resources/${fav.id}`,
                icon: 'star',
              }}
              level={0}
              collapsed={collapsed}
              onItemClick={onItemClick}
            />
          ))}

          <MenuItem
            item={{
              id: 'all-collections',
              label: t('sidebar.allCollections'),
              path: `/${getTenantSlug()}/resources`,
              icon: 'resources',
            }}
            level={0}
            collapsed={collapsed}
            onItemClick={onItemClick}
          />
        </ul>
      </div>

      {/* ==================== TOOLS ==================== */}
      <div className="mb-4 last:mb-0" data-testid="tools-section">
        {!collapsed && (
          <h2 className="m-0 px-4 py-2 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-foreground truncate">
            {t('sidebar.tools')}
          </h2>
        )}
        <ul className="list-none m-0 p-0" role="menubar" aria-label={t('sidebar.tools')}>
          <MenuItem
            item={{
              id: 'reports',
              label: t('sidebar.reports'),
              path: `/${getTenantSlug()}/reports`,
              icon: 'report',
            }}
            level={0}
            collapsed={collapsed}
            onItemClick={onItemClick}
          />
          <MenuItem
            item={{
              id: 'dashboards',
              label: t('sidebar.dashboards'),
              path: `/${getTenantSlug()}/dashboards`,
              icon: 'dashboard',
            }}
            level={0}
            collapsed={collapsed}
            onItemClick={onItemClick}
          />
        </ul>
      </div>

      {/* ==================== SETUP (Collapsed by default) ==================== */}
      <div className="mb-4 last:mb-0" data-testid="setup-section">
        {!collapsed && (
          <button
            type="button"
            className="flex items-center gap-2 w-full px-4 py-2 bg-transparent border-0 cursor-pointer text-muted-foreground transition-colors hover:text-foreground"
            onClick={toggleSetup}
            aria-expanded={setupExpanded}
          >
            <span className="text-sm" aria-hidden="true">
              {getIcon('settings')}
            </span>
            <span className="m-0 p-0 flex-1 text-left text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-foreground truncate">
              {t('sidebar.setup')}
            </span>
            <span
              className={cn(
                'flex items-center justify-center w-4 h-4 text-[0.625rem] opacity-70 transition-transform duration-150 shrink-0',
                setupExpanded && 'rotate-90 expandIcon--expanded'
              )}
              aria-hidden="true"
            >
              <ChevronRight size={14} />
            </span>
          </button>
        )}
        {collapsed && (
          <button
            type="button"
            className="flex items-center gap-2 w-full px-4 py-2 bg-transparent border-0 rounded text-sm font-medium no-underline text-left cursor-pointer transition-colors text-foreground hover:bg-accent/50 justify-center p-2 w-10 h-10"
            onClick={toggleSetup}
            title={t('sidebar.setup')}
          >
            <span className="flex items-center justify-center w-5 h-5 shrink-0" aria-hidden="true">
              {getIcon('settings')}
            </span>
          </button>
        )}

        {setupExpanded && !collapsed && (
          <div className="border-t border-border pt-2 mt-1">
            {/* Render bootstrap menus as setup subsections */}
            {menus.map((menu) => (
              <div key={menu.id} className="mb-2">
                {menu.name && (
                  <h3 className="m-0 px-4 py-1 text-[0.625rem] font-semibold uppercase tracking-wider text-muted-foreground">
                    {menu.name}
                  </h3>
                )}
                <ul
                  className="list-none m-0 p-0"
                  role="menubar"
                  aria-label={menu.name || t('navigation.menu')}
                >
                  {menu.items.map((item) => {
                    const slug = getTenantSlug()
                    const prefixedItem = {
                      ...item,
                      path:
                        item.path && !item.path.startsWith(`/${slug}`)
                          ? `/${slug}${item.path}`
                          : item.path,
                      children: item.children?.map((child) => ({
                        ...child,
                        path:
                          child.path && !child.path.startsWith(`/${slug}`)
                            ? `/${slug}${child.path}`
                            : child.path,
                      })),
                    }
                    return (
                      <MenuItem
                        key={item.id}
                        item={prefixedItem}
                        level={0}
                        collapsed={collapsed}
                        onItemClick={onItemClick}
                      />
                    )
                  })}
                </ul>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Empty state */}
      {menus.length === 0 && collectionsList.length === 0 && (
        <div
          className="flex items-center justify-center px-6 py-6 text-center"
          data-testid="sidebar-empty"
        >
          {!collapsed && (
            <p className="m-0 text-sm text-muted-foreground">{t('navigation.noMenus')}</p>
          )}
        </div>
      )}
    </nav>
  )
}

export default Sidebar
