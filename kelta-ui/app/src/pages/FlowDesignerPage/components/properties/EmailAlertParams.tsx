import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { FieldLabel } from '@/components/kelta'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Wand2 } from 'lucide-react'
import { PayloadMapper } from '@/components/PayloadMapper'
import type { SourceVariable, TargetField } from '@/components/PayloadMapper'

interface EmailAlertParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

const EMAIL_TARGETS: TargetField[] = [
  {
    path: 'subject',
    label: 'Subject',
    type: 'string',
    required: true,
    description: 'Use ${$.path} placeholders or =jsonata expressions for dynamic content.',
  },
  {
    path: 'body',
    label: 'Body',
    type: 'string',
    required: false,
    description: 'HTML or plain text. Strings starting with = are evaluated as JSONata.',
  },
]

const DEFAULT_VARIABLES: SourceVariable[] = [
  { path: '$.record.data', label: 'Triggering record', type: 'object' },
  { path: '$.record.id', label: 'Triggering record id', type: 'string' },
  { path: '$.context.user.email', label: 'Acting user', type: 'string' },
  { path: '$.context.tenant.slug', label: 'Tenant slug', type: 'string' },
  { path: '$.context.now', label: 'Current timestamp', type: 'string' },
]

export function EmailAlertParams({ parameters, onUpdate }: EmailAlertParamsProps) {
  const params = parameters || {}
  const to = (params.to as string) || ''
  const subject = (params.subject as string) || ''
  const body = (params.body as string) || ''
  const templateId = (params.templateId as string) || ''
  const [builderOpen, setBuilderOpen] = useState(false)

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  // The mapper round-trips through a small template object whose keys match
  // the target schema. We treat subject + body as the only mapped fields here.
  const mapperValue = { subject, body }
  const onMapperChange = (next: Record<string, unknown>) => {
    onUpdate({
      ...params,
      subject: typeof next.subject === 'string' ? next.subject : params.subject,
      body: typeof next.body === 'string' ? next.body : params.body,
    })
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
        <div className="flex items-center justify-between">
          <FieldLabel className="text-[10px]">Subject</FieldLabel>
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="h-6 px-2 text-[10px]"
            onClick={() => setBuilderOpen(true)}
          >
            <Wand2 className="mr-1 h-3 w-3" />
            Body builder
          </Button>
        </div>
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

      <Sheet open={builderOpen} onOpenChange={setBuilderOpen}>
        <SheetContent side="right" className="w-full sm:max-w-2xl overflow-y-auto">
          <SheetHeader>
            <SheetTitle>Email body builder</SheetTitle>
            <SheetDescription>
              Map flow state variables into the email subject and body. Strings starting with{' '}
              <code>=</code> are evaluated as JSONata at send time; everything else flows through{' '}
              <code>${'${$.path}'}</code> substitution.
            </SheetDescription>
          </SheetHeader>
          <div className="mt-4">
            <PayloadMapper
              targetSchema={EMAIL_TARGETS}
              sourceVariables={DEFAULT_VARIABLES}
              value={mapperValue}
              onChange={onMapperChange}
              footer={
                <div className="mt-4 flex justify-end">
                  <Button onClick={() => setBuilderOpen(false)}>Done</Button>
                </div>
              }
            />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
