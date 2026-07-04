import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { NatsTriggerConfig } from '@/pages/FlowDesignerPage/types'

interface NatsTriggerFormProps {
  config: Partial<NatsTriggerConfig>
  onChange: (config: Partial<NatsTriggerConfig>) => void
}

export function NatsTriggerForm({ config, onChange }: NatsTriggerFormProps) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <Label htmlFor="nats-topic" className="text-sm">
          Topic <span className="text-destructive">*</span>
        </Label>
        <Input
          id="nats-topic"
          required
          value={config.topic || ''}
          onChange={(e) => onChange({ ...config, topic: e.target.value })}
          className="mt-1 font-mono"
          placeholder="order-events"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          Messages published to{' '}
          <code className="font-mono">
            kelta.trigger.{'{tenant}'}.{config.topic || '{topic}'}
          </code>{' '}
          start this flow
        </p>
      </div>
    </div>
  )
}
