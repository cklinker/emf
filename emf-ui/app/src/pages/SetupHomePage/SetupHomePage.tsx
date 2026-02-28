/**
 * SetupHomePage Component
 *
 * A categorized directory of all admin/configuration pages with search
 * functionality. Displays quick stats at the top and organizes setup items
 * into 8 category cards with real-time filtering.
 */

import { useState, useCallback, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Users,
  Database,
  Shield,
  Zap,
  Link as LinkIcon,
  BarChart3,
  Palette,
  Settings,
  Search,
  X,
  ChevronRight,
} from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { useSystemPermissions } from '../../hooks/useSystemPermissions'
import { useCollectionSummaries } from '../../hooks/useCollectionSummaries'
import { cn } from '@/lib/utils'

export interface SetupHomePageProps {
  testId?: string
}

interface SetupItem {
  name: string
  path: string
  description: string
  permission?: string
}

interface SetupCategory {
  key: string
  titleKey: string
  icon: React.ReactNode
  items: SetupItem[]
}

interface PaginatedResponse {
  totalElements?: number
  content?: unknown[]
}

const CATEGORIES: SetupCategory[] = [
  {
    key: 'administration',
    titleKey: 'setup.categories.administration',
    icon: <Users size={20} />,
    items: [
      {
        name: 'Users',
        path: '/users',
        description: 'Manage user accounts and permissions',
        permission: 'MANAGE_USERS',
      },
      {
        name: 'Profiles',
        path: '/profiles',
        description: 'Configure profile-based permissions',
        permission: 'MANAGE_USERS',
      },
      {
        name: 'Permission Sets',
        path: '/permission-sets',
        description: 'Create additional permission grants',
        permission: 'MANAGE_USERS',
      },
      {
        name: 'OIDC Providers',
        path: '/oidc-providers',
        description: 'Configure identity providers',
        permission: 'MANAGE_CONNECTED_APPS',
      },
    ],
  },
  {
    key: 'dataModel',
    titleKey: 'setup.categories.dataModel',
    icon: <Database size={20} />,
    items: [
      {
        name: 'Collections',
        path: '/collections',
        description: 'Define data objects and schemas',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'Picklists',
        path: '/picklists',
        description: 'Manage global picklist values',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'Page Layouts',
        path: '/layouts',
        description: 'Configure record page layouts',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'List Views',
        path: '/listviews',
        description: 'Manage list view configurations',
        permission: 'MANAGE_LISTVIEWS',
      },
    ],
  },
  {
    key: 'security',
    titleKey: 'setup.categories.security',
    icon: <Shield size={20} />,
    items: [
      {
        name: 'Login History',
        path: '/login-history',
        description: 'View user login activity',
        permission: 'MANAGE_USERS',
      },
      {
        name: 'Security Audit',
        path: '/security-audit',
        description: 'Review security event trail',
        permission: 'MANAGE_USERS',
      },
      {
        name: 'Audit Trail',
        path: '/audit-trail',
        description: 'View configuration change history',
        permission: 'VIEW_SETUP',
      },
    ],
  },
  {
    key: 'automation',
    titleKey: 'setup.categories.automation',
    icon: <Zap size={20} />,
    items: [
      {
        name: 'Approval Processes',
        path: '/approvals',
        description: 'Configure approval workflows',
        permission: 'MANAGE_APPROVALS',
      },
      {
        name: 'Flows',
        path: '/flows',
        description: 'Build visual process flows',
        permission: 'MANAGE_WORKFLOWS',
      },
      {
        name: 'Scheduled Jobs',
        path: '/scheduled-jobs',
        description: 'Manage scheduled tasks',
        permission: 'MANAGE_WORKFLOWS',
      },
    ],
  },
  {
    key: 'integration',
    titleKey: 'setup.categories.integration',
    icon: <LinkIcon size={20} />,
    items: [
      {
        name: 'Connected Apps',
        path: '/connected-apps',
        description: 'Manage OAuth connected apps',
        permission: 'MANAGE_CONNECTED_APPS',
      },
      {
        name: 'Webhooks',
        path: '/webhooks',
        description: 'Configure outbound webhooks',
        permission: 'MANAGE_CONNECTED_APPS',
      },
      {
        name: 'Email Templates',
        path: '/email-templates',
        description: 'Design email templates',
        permission: 'MANAGE_EMAIL_TEMPLATES',
      },
      {
        name: 'Scripts',
        path: '/scripts',
        description: 'Manage server-side scripts',
        permission: 'MANAGE_CONNECTED_APPS',
      },
    ],
  },
  {
    key: 'uiCustomization',
    titleKey: 'setup.categories.uiCustomization',
    icon: <Palette size={20} />,
    items: [
      {
        name: 'Pages',
        path: '/pages',
        description: 'Build custom UI pages',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'Menus',
        path: '/menus',
        description: 'Configure navigation menus',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'Plugins',
        path: '/plugins',
        description: 'Manage installed plugins',
        permission: 'CUSTOMIZE_APPLICATION',
      },
    ],
  },
  {
    key: 'analytics',
    titleKey: 'setup.categories.analytics',
    icon: <BarChart3 size={20} />,
    items: [
      {
        name: 'Reports',
        path: '/reports',
        description: 'Build and manage reports',
        permission: 'MANAGE_REPORTS',
      },
      {
        name: 'Dashboards',
        path: '/dashboards',
        description: 'Create visual dashboards',
        permission: 'MANAGE_REPORTS',
      },
    ],
  },
  {
    key: 'platform',
    titleKey: 'setup.categories.platform',
    icon: <Settings size={20} />,
    items: [
      {
        name: 'Packages',
        path: '/packages',
        description: 'Import/export configuration packages',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'Migrations',
        path: '/migrations',
        description: 'View database migration history',
        permission: 'CUSTOMIZE_APPLICATION',
      },
      {
        name: 'Governor Limits',
        path: '/governor-limits',
        description: 'Monitor usage limits',
        permission: 'VIEW_SETUP',
      },
      {
        name: 'Tenants',
        path: '/tenants',
        description: 'Platform-level tenant management',
        permission: 'PLATFORM_ADMIN',
      },
      {
        name: 'Bulk Jobs',
        path: '/bulk-jobs',
        description: 'Monitor bulk data operations',
        permission: 'MANAGE_DATA',
      },
    ],
  },
]

function extractCount(data: unknown): number {
  if (data == null) return 0
  if (typeof data === 'object' && !Array.isArray(data)) {
    const paginated = data as PaginatedResponse
    if (typeof paginated.totalElements === 'number') {
      return paginated.totalElements
    }
    if (Array.isArray(paginated.content)) {
      return paginated.content.length
    }
  }
  if (Array.isArray(data)) {
    return data.length
  }
  return 0
}

export function SetupHomePage({ testId = 'setup-home-page' }: SetupHomePageProps): JSX.Element {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()
  const { hasPermission } = useSystemPermissions()
  const [searchQuery, setSearchQuery] = useState('')

  // Fetch stats
  const { summaries: collectionSummaries } = useCollectionSummaries()

  const { data: usersData } = useQuery({
    queryKey: ['setup-stats-users'],
    queryFn: () => apiClient.getPage<unknown>('/api/users?page[size]=1'),
    staleTime: 300000,
  })

  const { data: reportsData } = useQuery({
    queryKey: ['setup-stats-reports'],
    queryFn: () => apiClient.getList<unknown>(`/api/reports`),
    staleTime: 300000,
  })

  const { data: dashboardsData } = useQuery({
    queryKey: ['setup-stats-dashboards'],
    queryFn: () => apiClient.getList<unknown>(`/api/dashboards`),
    staleTime: 300000,
  })

  const stats = useMemo(
    () => [
      {
        label: t('setup.stats.collections'),
        value: collectionSummaries.length,
      },
      {
        label: t('setup.stats.users'),
        value: extractCount(usersData),
      },
      {
        label: t('setup.stats.reports'),
        value: extractCount(reportsData),
      },
      {
        label: t('setup.stats.dashboards'),
        value: extractCount(dashboardsData),
      },
    ],
    [collectionSummaries, usersData, reportsData, dashboardsData, t]
  )

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value)
  }, [])

  const handleClearSearch = useCallback(() => {
    setSearchQuery('')
  }, [])

  const filteredCategories = useMemo(() => {
    const query = searchQuery.toLowerCase().trim()

    // First filter by permission, then by search query
    const permissionFiltered = CATEGORIES.map((category) => ({
      ...category,
      items: category.items.filter((item) => !item.permission || hasPermission(item.permission)),
    })).filter((category) => category.items.length > 0)

    if (!query) return permissionFiltered

    return permissionFiltered
      .map((category) => ({
        ...category,
        items: category.items.filter(
          (item) =>
            item.name.toLowerCase().includes(query) ||
            item.description.toLowerCase().includes(query)
        ),
      }))
      .filter((category) => category.items.length > 0)
  }, [searchQuery, hasPermission])

  return (
    <div className="mx-auto max-w-[1400px] p-8 max-[767px]:p-4" data-testid={testId}>
      {/* Header */}
      <div className="mb-6 flex items-center justify-between gap-4 max-[767px]:flex-col max-[767px]:items-stretch">
        <h1 className="m-0 text-[1.75rem] font-bold text-foreground max-[767px]:text-[1.375rem]">
          {t('setup.title')}
        </h1>
        <div className="relative flex w-80 items-center max-[767px]:w-full">
          <Search
            size={16}
            className="pointer-events-none absolute left-3 text-muted-foreground"
            aria-hidden="true"
          />
          <input
            type="text"
            className={cn(
              'w-full rounded-md border border-border bg-card py-2 pl-9 pr-8 text-sm text-foreground',
              'placeholder:text-muted-foreground',
              'outline-none transition-[border-color] duration-150',
              'focus:border-primary focus:shadow-[0_0_0_2px_rgba(0,102,204,0.15)]'
            )}
            placeholder={t('setup.searchPlaceholder')}
            value={searchQuery}
            onChange={handleSearchChange}
            data-testid="setup-search-input"
            aria-label={t('setup.searchPlaceholder')}
          />
          {searchQuery && (
            <button
              className={cn(
                'absolute right-2 flex h-5 w-5 items-center justify-center rounded-full border-none bg-muted-foreground text-white',
                'cursor-pointer transition-colors duration-150',
                'hover:bg-foreground',
                'focus:outline-2 focus:outline-offset-2 focus:outline-primary',
                'focus:not(:focus-visible):outline-none'
              )}
              onClick={handleClearSearch}
              data-testid="setup-search-clear"
              aria-label={t('common.clear')}
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Quick Stats */}
      <div
        className="mb-8 grid grid-cols-4 gap-4 max-[767px]:grid-cols-2"
        data-testid="setup-stats"
      >
        {stats.map((stat) => (
          <div
            key={stat.label}
            className="flex flex-col items-center gap-1 rounded-md border border-border bg-card px-4 py-5"
          >
            <span className="text-[1.75rem] font-bold leading-tight text-primary">
              {stat.value}
            </span>
            <span className="text-center text-[0.8125rem] font-medium text-muted-foreground">
              {stat.label}
            </span>
          </div>
        ))}
      </div>

      {/* Category Grid */}
      {filteredCategories.length === 0 ? (
        <div
          className="flex items-center justify-center rounded-md border border-dashed border-border bg-card p-12"
          data-testid="setup-no-results"
        >
          <p className="m-0 text-sm text-muted-foreground">{t('common.noResults')}</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-6 max-[767px]:grid-cols-1">
          {filteredCategories.map((category) => (
            <div
              key={category.key}
              className="overflow-hidden rounded-md border border-border bg-card"
              data-testid={`setup-category-${category.key}`}
            >
              <div className="flex items-center gap-2.5 border-b border-border bg-muted px-5 py-4">
                <span className="flex items-center justify-center text-primary" aria-hidden="true">
                  {category.icon}
                </span>
                <h2 className="m-0 text-[0.9375rem] font-semibold text-foreground">
                  {t(category.titleKey)}
                </h2>
              </div>
              <ul className="m-0 list-none p-0">
                {category.items.map((item) => (
                  <li key={item.path} className="border-b border-border last:border-b-0">
                    <Link
                      to={`/${tenantSlug}${item.path}`}
                      className={cn(
                        'flex items-center justify-between px-5 py-3 text-inherit no-underline',
                        'transition-colors duration-150',
                        'hover:bg-muted',
                        'focus:outline-2 focus:outline-offset-[-2px] focus:outline-primary',
                        'focus:not(:focus-visible):outline-none',
                        'group'
                      )}
                      data-testid={`setup-item-${item.path.replace(/\//g, '').replace(/-/g, '')}`}
                    >
                      <div className="flex min-w-0 flex-col gap-0.5">
                        <span className="text-sm font-medium text-primary">{item.name}</span>
                        <span className="text-xs text-muted-foreground">{item.description}</span>
                      </div>
                      <ChevronRight
                        size={14}
                        className="shrink-0 text-muted-foreground transition-transform duration-150 group-hover:translate-x-0.5 group-hover:text-primary"
                        aria-hidden="true"
                      />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default SetupHomePage
