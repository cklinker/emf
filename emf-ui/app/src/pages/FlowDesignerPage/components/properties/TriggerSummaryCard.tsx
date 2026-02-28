import React from 'react'
import { Button } from '@/components/ui/button'
import { Database, Clock, Globe, Radio, Settings } from 'lucide-react'
import type { Flow } from '../../types'

interface TriggerSummaryCardProps {
  flow: Flow
  onEditTrigger?: () => void
}

const FLOW_TYPE_ICONS: Record<string, React.ElementType> = {
  RECORD_TRIGGERED: Database,
  SCHEDULED: Clock,
  AUTOLAUNCHED: Globe,
  KAFKA_TRIGGERED: Radio,
}

const FLOW_TYPE_LABELS: Record<string, string> = {
  RECORD_TRIGGERED: 'Record Change',
  SCHEDULED: 'Scheduled',
  AUTOLAUNCHED: 'API / Webhook',
  KAFKA_TRIGGERED: 'Kafka Event',
}

function parseTriggerConfig(flow: Flow): Record<string, string> {
  if (!flow.triggerConfig) return {}
  try {
    const config = JSON.parse(flow.triggerConfig) as Record<string, unknown>
    const summary: Record<string, string> = {}

    switch (flow.flowType) {
      case 'RECORD_TRIGGERED':
        if (config.collection) summary['Collection'] = config.collection as string
        if (Array.isArray(config.events)) summary['Events'] = (config.events as string[]).join(', ')
        break
      case 'SCHEDULED':
        if (config.cron) summary['Schedule'] = config.cron as string
        if (config.timezone) summary['Timezone'] = config.timezone as string
        break
      case 'AUTOLAUNCHED':
        if (config.webhookPath) summary['Webhook'] = config.webhookPath as string
        if (config.authentication) summary['Auth'] = config.authentication as string
        break
      case 'KAFKA_TRIGGERED':
        if (config.topic) summary['Topic'] = config.topic as string
        if (config.keyFilter) summary['Key Filter'] = config.keyFilter as string
        break
    }
    return summary
  } catch {
    return {}
  }
}

export function TriggerSummaryCard({ flow, onEditTrigger }: TriggerSummaryCardProps) {
  const Icon = FLOW_TYPE_ICONS[flow.flowType] || Settings
  const label = FLOW_TYPE_LABELS[flow.flowType] || flow.flowType
  const trigger = parseTriggerConfig(flow)

  return (
    <div className="rounded-lg border border-border bg-muted/30 p-3">
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Icon className="h-4 w-4 text-muted-foreground" />
          <span className="text-xs font-semibold">{label} Trigger</span>
        </div>
        {onEditTrigger && (
          <Button variant="ghost" size="sm" className="h-6 text-[10px]" onClick={onEditTrigger}>
            Edit
          </Button>
        )}
      </div>

      {Object.keys(trigger).length > 0 ? (
        <div className="flex flex-col gap-1">
          {Object.entries(trigger).map(([key, value]) => (
            <div key={key} className="flex justify-between text-[11px]">
              <span className="text-muted-foreground">{key}</span>
              <span className="font-mono text-foreground">{value}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-[11px] text-muted-foreground">No trigger configured</div>
      )}
    </div>
  )
}
