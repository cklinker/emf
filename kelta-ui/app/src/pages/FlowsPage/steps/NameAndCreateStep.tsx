import React from 'react'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { FieldLabel } from '@/components/kelta'
import { Database, Clock, Globe, Radio } from 'lucide-react'
import type { FlowType } from '@/pages/FlowDesignerPage/types'

interface NameAndCreateStepProps {
  name: string
  description: string
  flowType: FlowType
  onNameChange: (name: string) => void
  onDescriptionChange: (description: string) => void
}

const FLOW_TYPE_META: Record<FlowType, { label: string; icon: React.ElementType }> = {
  RECORD_TRIGGERED: { label: 'Record change', icon: Database },
  SCHEDULED: { label: 'Scheduled', icon: Clock },
  AUTOLAUNCHED: { label: 'API / webhook', icon: Globe },
  NATS_TRIGGERED: { label: 'NATS message', icon: Radio },
}

export function NameAndCreateStep({
  name,
  description,
  flowType,
  onNameChange,
  onDescriptionChange,
}: NameAndCreateStepProps) {
  const meta = FLOW_TYPE_META[flowType]
  const Icon = meta.icon

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h3 className="text-base font-semibold text-foreground">Name your flow</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Give your flow a descriptive name and optional description.
        </p>
      </div>

      <div className="flex items-center gap-2 rounded-md border border-border bg-muted/30 p-3">
        <Icon className="h-4 w-4 text-muted-foreground" />
        <span className="text-sm text-muted-foreground">{meta.label} flow</span>
      </div>

      <div>
        <FieldLabel htmlFor="wizard-flow-name">
          Name <span className="text-destructive">*</span>
        </FieldLabel>
        <Input
          id="wizard-flow-name"
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
          className="mt-1"
          placeholder="Enter flow name"
        />
      </div>

      <div>
        <FieldLabel htmlFor="wizard-flow-description">Description</FieldLabel>
        <Textarea
          id="wizard-flow-description"
          value={description}
          onChange={(e) => onDescriptionChange(e.target.value)}
          className="mt-1"
          placeholder="Optional description"
          rows={3}
        />
      </div>
    </div>
  )
}
