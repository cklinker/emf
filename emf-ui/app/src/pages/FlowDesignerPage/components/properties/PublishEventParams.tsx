import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

interface PublishEventParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

export function PublishEventParams({ parameters, onUpdate }: PublishEventParamsProps) {
  const params = parameters || {}
  const topic = (params.topic as string) || ''
  const eventType = (params.eventType as string) || ''
  const dataPayload = params.dataPayload

  // Serialize the data payload to a JSON string for editing
  const dataPayloadStr =
    dataPayload !== undefined && dataPayload !== null
      ? typeof dataPayload === 'string'
        ? dataPayload
        : JSON.stringify(dataPayload, null, 2)
      : ''

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  const updateDataPayload = (raw: string) => {
    if (!raw.trim()) {
      update('dataPayload', {})
      return
    }
    try {
      const parsed = JSON.parse(raw)
      update('dataPayload', parsed)
    } catch {
      // Keep the raw string so the user can keep editing; store as string until valid
      update('dataPayload', raw)
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Publish Event Config
      </span>

      <div>
        <Label className="text-[10px]">Topic</Label>
        <Input
          value={topic}
          onChange={(e) => update('topic', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="custom.events"
        />
      </div>

      <div>
        <Label className="text-[10px]">Event Type</Label>
        <Input
          value={eventType}
          onChange={(e) => update('eventType', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="order.approved"
        />
      </div>

      <div>
        <Label className="text-[10px]">Data Payload (JSON)</Label>
        <Textarea
          value={dataPayloadStr}
          onChange={(e) => updateDataPayload(e.target.value)}
          className="mt-0.5 min-h-[56px] font-mono text-xs"
          placeholder="{}"
          rows={4}
        />
      </div>
    </div>
  )
}
