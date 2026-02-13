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
import { getTenantId } from '../../hooks'
import styles from './SetupHomePage.module.css'

export interface SetupHomePageProps {
  testId?: string
}

interface SetupItem {
  name: string
  path: string
  description: string
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
      },
      {
        name: 'Roles',
        path: '/roles',
        description: 'Define roles and assign to users',
      },
      {
        name: 'Profiles',
        path: '/profiles',
        description: 'Configure profile-based permissions',
      },
      {
        name: 'Permission Sets',
        path: '/permission-sets',
        description: 'Create additional permission grants',
      },
      {
        name: 'OIDC Providers',
        path: '/oidc-providers',
        description: 'Configure identity providers',
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
      },
      {
        name: 'Picklists',
        path: '/picklists',
        description: 'Manage global picklist values',
      },
      {
        name: 'Page Layouts',
        path: '/layouts',
        description: 'Configure record page layouts',
      },
      {
        name: 'List Views',
        path: '/listviews',
        description: 'Manage list view configurations',
      },
    ],
  },
  {
    key: 'security',
    titleKey: 'setup.categories.security',
    icon: <Shield size={20} />,
    items: [
      {
        name: 'Sharing Settings',
        path: '/sharing',
        description: 'Configure record-level sharing',
      },
      {
        name: 'Role Hierarchy',
        path: '/role-hierarchy',
        description: 'Define role inheritance',
      },
      {
        name: 'Policies',
        path: '/policies',
        description: 'Manage access control policies',
      },
    ],
  },
  {
    key: 'automation',
    titleKey: 'setup.categories.automation',
    icon: <Zap size={20} />,
    items: [
      {
        name: 'Workflow Rules',
        path: '/workflow-rules',
        description: 'Automate field updates and actions',
      },
      {
        name: 'Approval Processes',
        path: '/approvals',
        description: 'Configure approval workflows',
      },
      {
        name: 'Flows',
        path: '/flows',
        description: 'Build visual process flows',
      },
      {
        name: 'Scheduled Jobs',
        path: '/scheduled-jobs',
        description: 'Manage scheduled tasks',
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
      },
      {
        name: 'Webhooks',
        path: '/webhooks',
        description: 'Configure outbound webhooks',
      },
      {
        name: 'Email Templates',
        path: '/email-templates',
        description: 'Design email templates',
      },
      {
        name: 'Scripts',
        path: '/scripts',
        description: 'Manage server-side scripts',
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
      },
      {
        name: 'Dashboards',
        path: '/dashboards',
        description: 'Create visual dashboards',
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
      },
      {
        name: 'Menus',
        path: '/menus',
        description: 'Configure navigation menus',
      },
      {
        name: 'Plugins',
        path: '/plugins',
        description: 'Manage installed plugins',
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
      },
      {
        name: 'Migrations',
        path: '/migrations',
        description: 'View database migration history',
      },
      {
        name: 'Governor Limits',
        path: '/governor-limits',
        description: 'Monitor usage limits',
      },
      {
        name: 'Audit Trail',
        path: '/audit-trail',
        description: 'View configuration changes',
      },
      {
        name: 'Tenants',
        path: '/tenants',
        description: 'Platform-level tenant management',
      },
      {
        name: 'Bulk Jobs',
        path: '/bulk-jobs',
        description: 'Monitor bulk data operations',
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
  const [searchQuery, setSearchQuery] = useState('')

  // Fetch stats
  const { data: collectionsData } = useQuery({
    queryKey: ['setup-stats-collections'],
    queryFn: () => apiClient.get<unknown>('/control/collections?size=1'),
    staleTime: 300000,
  })

  const { data: usersData } = useQuery({
    queryKey: ['setup-stats-users'],
    queryFn: () => apiClient.get<unknown>('/control/users?size=1'),
    staleTime: 300000,
  })

  const { data: reportsData } = useQuery({
    queryKey: ['setup-stats-reports'],
    queryFn: () => apiClient.get<unknown>(`/control/reports?tenantId=${getTenantId()}`),
    staleTime: 300000,
  })

  const { data: dashboardsData } = useQuery({
    queryKey: ['setup-stats-dashboards'],
    queryFn: () => apiClient.get<unknown>(`/control/dashboards?tenantId=${getTenantId()}`),
    staleTime: 300000,
  })

  const stats = useMemo(
    () => [
      {
        label: t('setup.stats.collections'),
        value: extractCount(collectionsData),
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
    [collectionsData, usersData, reportsData, dashboardsData, t]
  )

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value)
  }, [])

  const handleClearSearch = useCallback(() => {
    setSearchQuery('')
  }, [])

  const filteredCategories = useMemo(() => {
    const query = searchQuery.toLowerCase().trim()
    if (!query) return CATEGORIES

    return CATEGORIES.map((category) => ({
      ...category,
      items: category.items.filter(
        (item) =>
          item.name.toLowerCase().includes(query) || item.description.toLowerCase().includes(query)
      ),
    })).filter((category) => category.items.length > 0)
  }, [searchQuery])

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Header */}
      <div className={styles.header}>
        <h1 className={styles.title}>{t('setup.title')}</h1>
        <div className={styles.searchContainer}>
          <Search size={16} className={styles.searchIcon} aria-hidden="true" />
          <input
            type="text"
            className={styles.searchInput}
            placeholder={t('setup.searchPlaceholder')}
            value={searchQuery}
            onChange={handleSearchChange}
            data-testid="setup-search-input"
            aria-label={t('setup.searchPlaceholder')}
          />
          {searchQuery && (
            <button
              className={styles.clearButton}
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
      <div className={styles.statsGrid} data-testid="setup-stats">
        {stats.map((stat) => (
          <div key={stat.label} className={styles.statCard}>
            <span className={styles.statValue}>{stat.value}</span>
            <span className={styles.statLabel}>{stat.label}</span>
          </div>
        ))}
      </div>

      {/* Category Grid */}
      {filteredCategories.length === 0 ? (
        <div className={styles.noResults} data-testid="setup-no-results">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.categoryGrid}>
          {filteredCategories.map((category) => (
            <div
              key={category.key}
              className={styles.categoryCard}
              data-testid={`setup-category-${category.key}`}
            >
              <div className={styles.categoryHeader}>
                <span className={styles.categoryIcon} aria-hidden="true">
                  {category.icon}
                </span>
                <h2 className={styles.categoryTitle}>{t(category.titleKey)}</h2>
              </div>
              <ul className={styles.itemList}>
                {category.items.map((item) => (
                  <li key={item.path} className={styles.itemRow}>
                    <Link
                      to={item.path}
                      className={styles.itemLink}
                      data-testid={`setup-item-${item.path.replace(/\//g, '').replace(/-/g, '')}`}
                    >
                      <div className={styles.itemInfo}>
                        <span className={styles.itemName}>{item.name}</span>
                        <span className={styles.itemDescription}>{item.description}</span>
                      </div>
                      <ChevronRight size={14} className={styles.itemArrow} aria-hidden="true" />
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
