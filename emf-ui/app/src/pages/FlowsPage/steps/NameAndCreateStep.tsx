import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
  RECORD_TRIGGERED: { label: 'Record Change', icon: Database },
  SCHEDULED: { label: 'Scheduled', icon: Clock },
  AUTOLAUNCHED: { label: 'API / Webhook', icon: Globe },
  KAFKA_TRIGGERED: { label: 'Kafka Event', icon: Radio },
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
        <span className="text-sm text-muted-foreground">{meta.label} Flow</span>
      </div>

      <div>
        <Label htmlFor="wizard-flow-name" className="text-sm">
          Name <span className="text-destructive">*</span>
        </Label>
        <Input
          id="wizard-flow-name"
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
          className="mt-1"
          placeholder="Enter flow name"
        />
      </div>

      <div>
        <Label htmlFor="wizard-flow-description" className="text-sm">
          Description
        </Label>
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
