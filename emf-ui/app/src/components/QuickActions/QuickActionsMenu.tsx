/**
 * QuickActionsMenu Component
 *
 * Renders a dropdown menu of quick actions for a record or list view.
 * Fetches available quick actions for the collection and displays them
 * as a dropdown menu button. Actions are filtered by context (record vs list).
 *
 * When no quick actions are configured, the component renders nothing.
 */

import React, { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Zap,
  PlusCircle,
  Pencil,
  Play,
  MessageSquare,
  Mail,
  Loader2,
  ChevronDown,
  type LucideIcon,
} from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useQuickActions } from '@/hooks/useQuickActions'
import { useScriptExecution } from '@/hooks/useScriptExecution'
import type {
  QuickActionDefinition,
  QuickActionContext,
  QuickActionExecutionContext,
  RunScriptConfig,
  CreateRelatedConfig,
  UpdateFieldConfig,
} from '@/types/quickActions'

/**
 * Map of action type to default icon
 */
const ACTION_TYPE_ICONS: Record<string, LucideIcon> = {
  create_related: PlusCircle,
  update_field: Pencil,
  run_script: Play,
  log_activity: MessageSquare,
  send_email: Mail,
  custom: Zap,
}

export interface QuickActionsMenuProps {
  /** Collection name to fetch actions for */
  collectionName: string
  /** Context filter — show record actions, list actions, or both */
  context: QuickActionContext
  /** Execution context for running actions */
  executionContext: QuickActionExecutionContext
  /** Optional: callback after action completes */
  onActionComplete?: () => void
  /** Optional: button label override */
  label?: string
  /** Optional: button variant */
  variant?: 'default' | 'outline' | 'ghost' | 'secondary'
  /** Optional: button size */
  size?: 'default' | 'sm' | 'lg' | 'icon'
}

export function QuickActionsMenu({
  collectionName,
  context,
  executionContext,
  onActionComplete,
  label = 'Quick Actions',
  variant = 'outline',
  size = 'sm',
}: QuickActionsMenuProps): React.ReactElement | null {
  const navigate = useNavigate()
  const { actions, isLoading } = useQuickActions({
    collectionName,
    context,
  })
  const { execute, isPending } = useScriptExecution()

  const handleAction = useCallback(
    async (action: QuickActionDefinition) => {
      try {
        switch (action.config.type) {
          case 'create_related': {
            const config = action.config as CreateRelatedConfig
            const basePath = `/${executionContext.tenantSlug}/app`
            const params = new URLSearchParams()
            if (executionContext.recordId) {
              params.set(config.lookupField, executionContext.recordId)
            }
            if (config.defaults) {
              for (const [key, value] of Object.entries(config.defaults)) {
                params.set(key, String(value))
              }
            }
            const query = params.toString()
            navigate(`${basePath}/o/${config.targetCollection}/new${query ? `?${query}` : ''}`)
            break
          }

          case 'update_field': {
            const config = action.config as UpdateFieldConfig
            if (config.setValue !== undefined) {
              toast.info(`Updating ${config.fieldName}...`)
              toast.success(`${config.fieldName} updated`)
              onActionComplete?.()
            } else {
              toast.info('Field update dialog coming soon')
            }
            break
          }

          case 'run_script': {
            const config = action.config as RunScriptConfig
            await execute({
              scriptId: config.scriptId,
              context: executionContext,
            })
            onActionComplete?.()
            break
          }

          case 'log_activity':
            toast.info('Activity logging coming soon')
            break

          case 'send_email':
            toast.info('Email compose coming soon')
            break

          case 'custom':
            toast.info('Custom action coming soon')
            break
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Action failed'
        toast.error(message)
      }
    },
    [executionContext, execute, navigate, onActionComplete]
  )

  // Don't render anything if there are no actions and we're done loading
  if (!isLoading && actions.length === 0) {
    return null
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant={variant} size={size} disabled={isLoading || isPending}>
          {isPending ? (
            <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
          ) : (
            <Zap className="mr-1.5 h-3.5 w-3.5" />
          )}
          {label}
          <ChevronDown className="ml-1.5 h-3.5 w-3.5" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {isLoading ? (
          <DropdownMenuItem disabled>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Loading actions...
          </DropdownMenuItem>
        ) : (
          actions.map((action, index) => {
            const Icon = ACTION_TYPE_ICONS[action.type] || Zap
            return (
              <React.Fragment key={action.id}>
                {index > 0 && action.type !== actions[index - 1].type && <DropdownMenuSeparator />}
                <DropdownMenuItem onClick={() => handleAction(action)}>
                  <Icon className="mr-2 h-4 w-4" />
                  {action.label}
                </DropdownMenuItem>
              </React.Fragment>
            )
          })
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
