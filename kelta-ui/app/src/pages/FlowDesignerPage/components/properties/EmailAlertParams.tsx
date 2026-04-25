import React from 'react'
import { Input } from '@/components/ui/input'
import { FieldLabel } from '@/components/kelta'
import { Textarea } from '@/components/ui/textarea'

interface EmailAlertParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

export function EmailAlertParams({ parameters, onUpdate }: EmailAlertParamsProps) {
  const params = parameters || {}
  const to = (params.to as string) || ''
  const subject = (params.subject as string) || ''
  const body = (params.body as string) || ''
  const templateId = (params.templateId as string) || ''

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Email Alert Config
      </span>

      <div>
        <FieldLabel className="text-[10px]">To</FieldLabel>
        <Input
          value={to}
          onChange={(e) => update('to', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="manager@example.com"
        />
      </div>

      <div>
        <FieldLabel className="text-[10px]">Subject</FieldLabel>
        <Input
          value={subject}
          onChange={(e) => update('subject', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="Order Approved"
        />
      </div>

      <div>
        <FieldLabel className="text-[10px]">Body</FieldLabel>
        <Textarea
          value={body}
          onChange={(e) => update('body', e.target.value)}
          className="mt-0.5 min-h-[56px] text-xs"
          placeholder="Order has been approved"
          rows={4}
        />
      </div>

      <div>
        <FieldLabel className="text-[10px]">Template ID (optional)</FieldLabel>
        <Input
          value={templateId}
          onChange={(e) => update('templateId', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="template-uuid"
        />
      </div>
    </div>
  )
}
