import React from 'react'
import { Database, Clock, Globe, Radio } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { FlowType } from '@/pages/FlowDesignerPage/types'

interface FlowTypeStepProps {
  value: FlowType | null
  onChange: (type: FlowType) => void
}

const FLOW_TYPES: {
  value: FlowType
  label: string
  description: string
  icon: React.ElementType
}[] = [
  {
    value: 'RECORD_TRIGGERED',
    label: 'Record Change',
    description: 'Triggered when a record is created, updated, or deleted',
    icon: Database,
  },
  {
    value: 'SCHEDULED',
    label: 'Scheduled',
    description: 'Runs on a recurring schedule using a cron expression',
    icon: Clock,
  },
  {
    value: 'AUTOLAUNCHED',
    label: 'API / Webhook',
    description: 'Invoked via API call or incoming webhook',
    icon: Globe,
  },
  {
    value: 'KAFKA_TRIGGERED',
    label: 'Kafka Event',
    description: 'Triggered by messages from a Kafka topic',
    icon: Radio,
  },
]

export function FlowTypeStep({ value, onChange }: FlowTypeStepProps) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <h3 className="text-base font-semibold text-foreground">How should this flow start?</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Choose what triggers the flow to begin executing.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {FLOW_TYPES.map((type) => {
          const Icon = type.icon
          const isSelected = value === type.value
          return (
            <button
              key={type.value}
              type="button"
              onClick={() => onChange(type.value)}
              className={cn(
                'flex flex-col items-start gap-2 rounded-lg border-2 p-4 text-left transition-all hover:bg-muted/50',
                isSelected ? 'border-primary bg-primary/5 ring-2 ring-primary/20' : 'border-border'
              )}
            >
              <Icon
                className={cn('h-6 w-6', isSelected ? 'text-primary' : 'text-muted-foreground')}
              />
              <div>
                <div className="text-sm font-semibold text-foreground">{type.label}</div>
                <div className="mt-0.5 text-xs text-muted-foreground">{type.description}</div>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
