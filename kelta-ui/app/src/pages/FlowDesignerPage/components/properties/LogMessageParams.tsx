import React from 'react'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

const LOG_LEVELS = ['DEBUG', 'INFO', 'WARNING', 'ERROR']

interface LogMessageParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

export function LogMessageParams({ parameters, onUpdate }: LogMessageParamsProps) {
  const params = parameters || {}
  const message = (params.message as string) || ''
  const level = (params.level as string) || 'INFO'

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Log Message Config
      </span>

      <div>
        <Label className="text-[10px]">Message</Label>
        <Textarea
          value={message}
          onChange={(e) => update('message', e.target.value)}
          className="mt-0.5 min-h-[56px] text-xs"
          placeholder="Order ${$.record.data.id} approved"
          rows={3}
        />
      </div>

      <div>
        <Label className="text-[10px]">Level</Label>
        <select
          value={level}
          onChange={(e) => update('level', e.target.value)}
          className="mt-0.5 h-7 w-full rounded border border-border bg-background px-2 text-xs"
        >
          {LOG_LEVELS.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
