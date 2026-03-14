import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Database, Clock, Globe, Radio, Settings, Copy, Check } from 'lucide-react'
import { useApi } from '@/context/ApiContext'
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

function safeParseTriggerConfig(triggerConfig: unknown): Record<string, unknown> | null {
  if (!triggerConfig) return null
  if (typeof triggerConfig === 'string') {
    try {
      return JSON.parse(triggerConfig) as Record<string, unknown>
    } catch {
      return null
    }
  }
  if (typeof triggerConfig === 'object') return triggerConfig as Record<string, unknown>
  return null
}

function parseTriggerConfig(flow: Flow): Record<string, string> {
  const config = safeParseTriggerConfig(flow.triggerConfig)
  if (!config) return {}
  try {
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
  const { keltaClient } = useApi()
  const Icon = FLOW_TYPE_ICONS[flow.flowType] || Settings
  const label = FLOW_TYPE_LABELS[flow.flowType] || flow.flowType
  const trigger = parseTriggerConfig(flow)
  const [copied, setCopied] = useState(false)

  const { data: webhookUrl } = useQuery({
    queryKey: ['webhook-url', flow.id],
    queryFn: async () => {
      const res = await keltaClient.admin.flows.getWebhookUrl(flow.id)
      return res.webhookUrl
    },
    enabled: flow.flowType === 'AUTOLAUNCHED',
  })

  const handleCopy = async () => {
    if (!webhookUrl) return
    await navigator.clipboard.writeText(webhookUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

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

      {flow.flowType === 'AUTOLAUNCHED' && webhookUrl && (
        <div className="mt-2 border-t border-border pt-2">
          <div className="flex items-center gap-1">
            <span className="text-[11px] text-muted-foreground">Webhook URL</span>
            <button
              onClick={handleCopy}
              className="ml-auto text-muted-foreground hover:text-foreground"
              data-testid="copy-trigger-webhook-url"
            >
              {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
            </button>
          </div>
          <code
            data-testid="trigger-webhook-url"
            className="mt-0.5 block truncate font-mono text-[10px] text-foreground"
          >
            {webhookUrl}
          </code>
        </div>
      )}
    </div>
  )
}
