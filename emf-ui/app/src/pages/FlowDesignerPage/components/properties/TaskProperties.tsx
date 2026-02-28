import React from 'react'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { RESOURCE_GROUPS } from '../../types'
import type { RetryRule, CatchRule } from '../../types'
import { DataPathFields } from './DataPathFields'
import { RetryEditor } from './RetryEditor'
import { CatchEditor } from './CatchEditor'
import { QueryRecordsParams } from './QueryRecordsParams'
import { UpdateRecordParams } from './UpdateRecordParams'

interface TaskPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  allNodeIds: string[]
  onUpdate: (data: Record<string, unknown>) => void
}

export function TaskProperties({ nodeId, data, allNodeIds, onUpdate }: TaskPropertiesProps) {
  const resource = (data.resource as string) || ''

  return (
    <div className="flex flex-col gap-3">
      <div>
        <Label htmlFor={`resource-${nodeId}`} className="text-xs">
          Resource
        </Label>
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
        </select>
      </div>

      {/* Resource-specific parameter editors */}
      {resource === 'QUERY_RECORDS' && (
        <QueryRecordsParams
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

      <div>
        <Label htmlFor={`timeout-${nodeId}`} className="text-xs">
          Timeout (seconds)
        </Label>
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
