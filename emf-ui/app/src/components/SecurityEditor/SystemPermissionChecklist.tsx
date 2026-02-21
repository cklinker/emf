/**
 * SystemPermissionChecklist Component
 *
 * Displays all 15 system permissions as checkboxes grouped by category.
 * Used within Profile and Permission Set editors to manage system-level access.
 *
 * Categories:
 * - Application Setup: VIEW_SETUP, CUSTOMIZE_APPLICATION
 * - User & Group Management: MANAGE_USERS, MANAGE_GROUPS, MANAGE_SHARING
 * - Automation & Workflows: MANAGE_WORKFLOWS, MANAGE_APPROVALS, MANAGE_EMAIL_TEMPLATES
 * - Data & Reporting: MANAGE_REPORTS, MANAGE_LISTVIEWS, MANAGE_DATA, VIEW_ALL_DATA, MODIFY_ALL_DATA
 * - Integration & API: MANAGE_CONNECTED_APPS, API_ACCESS
 */

import React, { useCallback } from 'react'
import { Settings, Users, Workflow, Database, Plug } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'

/** Permission category definition */
interface PermissionCategory {
  name: string
  icon: React.ReactNode
  permissions: Array<{ name: string; label: string; description: string }>
}

/** All 15 system permissions grouped by category */
const PERMISSION_CATEGORIES: PermissionCategory[] = [
  {
    name: 'Application Setup',
    icon: <Settings size={16} />,
    permissions: [
      {
        name: 'VIEW_SETUP',
        label: 'View Setup',
        description: 'View the setup and configuration area',
      },
      {
        name: 'CUSTOMIZE_APPLICATION',
        label: 'Customize Application',
        description: 'Modify application metadata, collections, and fields',
      },
    ],
  },
  {
    name: 'User & Group Management',
    icon: <Users size={16} />,
    permissions: [
      {
        name: 'MANAGE_USERS',
        label: 'Manage Users',
        description: 'Create, edit, and deactivate user accounts',
      },
      {
        name: 'MANAGE_GROUPS',
        label: 'Manage Groups',
        description: 'Create and manage public and private groups',
      },
      {
        name: 'MANAGE_SHARING',
        label: 'Manage Sharing',
        description: 'Configure organization-wide sharing defaults',
      },
    ],
  },
  {
    name: 'Automation & Workflows',
    icon: <Workflow size={16} />,
    permissions: [
      {
        name: 'MANAGE_WORKFLOWS',
        label: 'Manage Workflows',
        description: 'Create and modify workflow rules and automations',
      },
      {
        name: 'MANAGE_APPROVALS',
        label: 'Manage Approvals',
        description: 'Configure approval processes and chains',
      },
      {
        name: 'MANAGE_EMAIL_TEMPLATES',
        label: 'Manage Email Templates',
        description: 'Create and edit email notification templates',
      },
    ],
  },
  {
    name: 'Data & Reporting',
    icon: <Database size={16} />,
    permissions: [
      {
        name: 'MANAGE_REPORTS',
        label: 'Manage Reports',
        description: 'Create and manage reports and dashboards',
      },
      {
        name: 'MANAGE_LISTVIEWS',
        label: 'Manage List Views',
        description: 'Create and manage public list views',
      },
      {
        name: 'MANAGE_DATA',
        label: 'Manage Data',
        description: 'Import, export, and mass update records',
      },
      {
        name: 'VIEW_ALL_DATA',
        label: 'View All Data',
        description: 'View all records regardless of sharing rules',
      },
      {
        name: 'MODIFY_ALL_DATA',
        label: 'Modify All Data',
        description: 'Edit and delete all records regardless of sharing rules',
      },
    ],
  },
  {
    name: 'Integration & API',
    icon: <Plug size={16} />,
    permissions: [
      {
        name: 'MANAGE_CONNECTED_APPS',
        label: 'Manage Connected Apps',
        description: 'Configure OAuth and third-party integrations',
      },
      {
        name: 'API_ACCESS',
        label: 'API Access',
        description: 'Access the platform through REST and GraphQL APIs',
      },
    ],
  },
]

export interface SystemPermissionChecklistProps {
  /** Map of permission name to granted status */
  permissions: Record<string, boolean>
  /** Callback when a permission is toggled */
  onChange: (name: string, granted: boolean) => void
  /** Whether the checklist is read-only */
  readOnly?: boolean
  /** Test ID for the component */
  testId?: string
}

export function SystemPermissionChecklist({
  permissions,
  onChange,
  readOnly = false,
  testId = 'system-permission-checklist',
}: SystemPermissionChecklistProps): React.ReactElement {
  const handleCheckedChange = useCallback(
    (name: string, checked: boolean | 'indeterminate') => {
      if (!readOnly) {
        onChange(name, checked === true)
      }
    },
    [onChange, readOnly]
  )

  return (
    <div className="space-y-6" data-testid={testId}>
      {PERMISSION_CATEGORIES.map((category) => (
        <div
          key={category.name}
          className="rounded-lg border border-border bg-card"
          data-testid={`${testId}-category-${category.name.replace(/\s+/g, '-').toLowerCase()}`}
        >
          {/* Category header */}
          <div className="flex items-center gap-2 border-b border-border bg-muted px-4 py-3">
            <span className="text-muted-foreground">{category.icon}</span>
            <h4 className="text-sm font-semibold text-foreground">{category.name}</h4>
          </div>

          {/* Permission checkboxes */}
          <div className="divide-y divide-border">
            {category.permissions.map((perm) => {
              const isChecked = permissions[perm.name] ?? false
              const checkboxId = `perm-${perm.name}`

              return (
                <div
                  key={perm.name}
                  className={cn(
                    'flex items-start gap-3 px-4 py-3',
                    !readOnly && 'hover:bg-muted/50 transition-colors'
                  )}
                  data-testid={`${testId}-permission-${perm.name}`}
                >
                  <Checkbox
                    id={checkboxId}
                    checked={isChecked}
                    onCheckedChange={(checked) => handleCheckedChange(perm.name, checked)}
                    disabled={readOnly}
                    aria-describedby={`${checkboxId}-description`}
                    data-testid={`${testId}-checkbox-${perm.name}`}
                    className="mt-0.5"
                  />
                  <div className="flex-1 min-w-0">
                    <Label
                      htmlFor={checkboxId}
                      className={cn(
                        'text-sm font-medium text-foreground',
                        readOnly && 'cursor-default'
                      )}
                    >
                      {perm.label}
                    </Label>
                    <p
                      id={`${checkboxId}-description`}
                      className="mt-0.5 text-xs text-muted-foreground"
                    >
                      {perm.description}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}

export default SystemPermissionChecklist
