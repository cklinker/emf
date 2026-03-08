import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { KafkaTriggerConfig } from '@/pages/FlowDesignerPage/types'

interface KafkaTriggerFormProps {
  config: Partial<KafkaTriggerConfig>
  onChange: (config: Partial<KafkaTriggerConfig>) => void
}

export function KafkaTriggerForm({ config, onChange }: KafkaTriggerFormProps) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <Label htmlFor="kafka-topic" className="text-sm">
          Topic
        </Label>
        <Input
          id="kafka-topic"
          value={config.topic || ''}
          onChange={(e) => onChange({ ...config, topic: e.target.value })}
          className="mt-1 font-mono"
          placeholder="my-events-topic"
        />
        <p className="mt-1 text-xs text-muted-foreground">Kafka topic to consume messages from</p>
      </div>

      <div>
        <Label htmlFor="kafka-key-filter" className="text-sm">
          Key Filter (optional)
        </Label>
        <Input
          id="kafka-key-filter"
          value={config.keyFilter || ''}
          onChange={(e) => onChange({ ...config, keyFilter: e.target.value || undefined })}
          className="mt-1 font-mono"
          placeholder="user.*"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          Only process messages with keys matching this pattern
        </p>
      </div>

      <div>
        <Label htmlFor="kafka-message-filter" className="text-sm">
          Message Filter (optional)
        </Label>
        <Input
          id="kafka-message-filter"
          value={config.messageFilter || ''}
          onChange={(e) => onChange({ ...config, messageFilter: e.target.value || undefined })}
          className="mt-1 font-mono"
          placeholder="$.type == 'ORDER_PLACED'"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          JSONPath expression to filter message content
        </p>
      </div>
    </div>
  )
}
