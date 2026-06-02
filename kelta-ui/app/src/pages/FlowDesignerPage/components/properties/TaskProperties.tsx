import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { FieldLabel } from '@/components/kelta'
import { Input } from '@/components/ui/input'
import { RESOURCE_GROUPS } from '../../types'
import type { RetryRule, CatchRule } from '../../types'
import { DataPathFields } from './DataPathFields'
import { RetryEditor } from './RetryEditor'
import { CatchEditor } from './CatchEditor'
import { QueryRecordsParams } from './QueryRecordsParams'
import { SqlQueryParams } from './SqlQueryParams'
import { UpdateRecordParams } from './UpdateRecordParams'
import { CreateRecordParams } from './CreateRecordParams'
import { DeleteRecordParams } from './DeleteRecordParams'
import { FieldUpdateParams } from './FieldUpdateParams'
import { LogMessageParams } from './LogMessageParams'
import { HttpCalloutParams } from './HttpCalloutParams'
import { CallApiParams } from './CallApiParams'
import { SendNotificationParams } from './SendNotificationParams'
import { EmailAlertParams } from './EmailAlertParams'
import { TriggerFlowParams } from './TriggerFlowParams'
import { PublishEventParams } from './PublishEventParams'
import { ModuleActionParams } from './ModuleActionParams'
import { useApi } from '@/context/ApiContext'

interface ModuleAction {
  id: string
  actionKey: string
  name: string
  category: string | null
  configSchema: string | null
}

interface TenantModule {
  id: string
  moduleId: string
  name: string
  status: string
  actions: ModuleAction[]
}

interface TaskPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  allNodeIds: string[]
  onUpdate: (data: Record<string, unknown>) => void
}

export function TaskProperties({ nodeId, data, allNodeIds, onUpdate }: TaskPropertiesProps) {
  const resource = (data.resource as string) || ''
  const { apiClient } = useApi()

  const { data: modules } = useQuery({
    queryKey: ['modules'],
    queryFn: () => apiClient.get<TenantModule[]>('/api/modules'),
    staleTime: 30_000,
  })

  const activeModules = (modules ?? []).filter(
    (m) => m.status === 'ACTIVE' && m.actions.length > 0
  )

  const moduleActionMap = React.useMemo(() => {
    const map = new Map<string, ModuleAction>()
    for (const m of activeModules) {
      for (const a of m.actions) {
        map.set(a.actionKey, a)
      }
    }
    return map
  }, [activeModules])

  const selectedModuleAction = moduleActionMap.get(resource) ?? null

  return (
    <div className="flex flex-col gap-3">
      <div>
        <FieldLabel htmlFor={`resource-${nodeId}`} className="text-xs">
          Resource
        </FieldLabel>
        <select
          id={`resource-${nodeId}`}
          value={resource}
          onChange={(e) => onUpdate({ resource: e.target.value })}
          className="mt-1 h-8 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        >
          <option value="">Select resource...</option>
          {RESOURCE_GROUPS.map((group) => (
            <optgroup key={group.label} label={group.label}>
              {group.options.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </optgroup>
          ))}
          {activeModules.map((m) => (
            <optgroup key={m.moduleId} label={`Module: ${m.name}`}>
              {m.actions.map((a) => (
                <option key={a.actionKey} value={a.actionKey}>
                  {a.name}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
      </div>

      {/* Resource-specific parameter editors */}
      {resource === 'QUERY_RECORDS' && (
        <QueryRecordsParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'SQL_QUERY' && (
        <SqlQueryParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'UPDATE_RECORD' && (
        <UpdateRecordParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'CREATE_RECORD' && (
        <CreateRecordParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'DELETE_RECORD' && (
        <DeleteRecordParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'FIELD_UPDATE' && (
        <FieldUpdateParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'LOG_MESSAGE' && (
        <LogMessageParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'HTTP_CALLOUT' && (
        <HttpCalloutParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'CALL_API' && (
        <CallApiParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'SEND_NOTIFICATION' && (
        <SendNotificationParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'EMAIL_ALERT' && (
        <EmailAlertParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'TRIGGER_FLOW' && (
        <TriggerFlowParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {resource === 'PUBLISH_EVENT' && (
        <PublishEventParams
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}
      {selectedModuleAction && (
        <ModuleActionParams
          configSchema={selectedModuleAction.configSchema}
          parameters={data.parameters as Record<string, unknown> | undefined}
          onUpdate={(params) => onUpdate({ parameters: params })}
        />
      )}

      <div>
        <FieldLabel htmlFor={`timeout-${nodeId}`} className="text-xs">
          Timeout (seconds)
        </FieldLabel>
        <Input
          id={`timeout-${nodeId}`}
          type="number"
          value={(data.timeoutSeconds as number) ?? ''}
          onChange={(e) =>
            onUpdate({
              timeoutSeconds: e.target.value ? parseInt(e.target.value) : undefined,
            })
          }
          className="mt-1 h-8 text-sm"
          placeholder="No timeout"
          min={1}
        />
      </div>

      <DataPathFields
        nodeId={nodeId}
        inputPath={(data.inputPath as string) || ''}
        outputPath={(data.outputPath as string) || ''}
        resultPath={(data.resultPath as string) || ''}
        onUpdate={onUpdate}
      />

      <RetryEditor
        retry={(data.retry as RetryRule[]) || []}
        onUpdate={(retry) => onUpdate({ retry })}
      />

      <CatchEditor
        catches={(data.catch as CatchRule[]) || []}
        allNodeIds={allNodeIds}
        onUpdate={(catches) => onUpdate({ catch: catches })}
      />
    </div>
  )
}
