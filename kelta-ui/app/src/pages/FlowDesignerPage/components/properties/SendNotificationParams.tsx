import React from 'react'
import { Input } from '@/components/ui/input'
import { FieldLabel } from '@/components/kelta'
import { Textarea } from '@/components/ui/textarea'

const NOTIFICATION_LEVELS = ['INFO', 'WARNING', 'ERROR']

interface SendNotificationParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

export function SendNotificationParams({ parameters, onUpdate }: SendNotificationParamsProps) {
  const params = parameters || {}
  const userId = (params.userId as string) || ''
  const title = (params.title as string) || ''
  const message = (params.message as string) || ''
  const level = (params.level as string) || 'INFO'

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Send Notification Config
      </span>

      <div>
        <FieldLabel className="text-[10px]">User ID</FieldLabel>
        <Input
          value={userId}
          onChange={(e) => update('userId', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="${$.record.data.assigned_to}"
        />
      </div>

      <div>
        <FieldLabel className="text-[10px]">Title</FieldLabel>
        <Input
          value={title}
          onChange={(e) => update('title', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="New Task"
        />
      </div>

      <div>
        <FieldLabel className="text-[10px]">Message</FieldLabel>
        <Textarea
          value={message}
          onChange={(e) => update('message', e.target.value)}
          className="mt-0.5 min-h-[56px] text-xs"
          placeholder="You have a new task"
          rows={3}
        />
      </div>

      <div>
        <FieldLabel className="text-[10px]">Level</FieldLabel>
        <select
          value={level}
          onChange={(e) => update('level', e.target.value)}
          className="mt-0.5 h-7 w-full rounded border border-border bg-background px-2 text-xs"
        >
          {NOTIFICATION_LEVELS.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
