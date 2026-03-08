import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface TriggerFlowParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

export function TriggerFlowParams({ parameters, onUpdate }: TriggerFlowParamsProps) {
  const params = parameters || {}
  const flowId = (params.flowId as string) || ''

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Trigger Flow Config
      </span>

      <div>
        <Label className="text-[10px]">Flow ID</Label>
        <Input
          value={flowId}
          onChange={(e) => update('flowId', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="uuid-of-target-flow"
        />
      </div>
    </div>
  )
}
