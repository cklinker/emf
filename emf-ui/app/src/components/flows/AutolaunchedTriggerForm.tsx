import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { AutolaunchedTriggerConfig } from '@/pages/FlowDesignerPage/types'

interface AutolaunchedTriggerFormProps {
  config: Partial<AutolaunchedTriggerConfig>
  onChange: (config: Partial<AutolaunchedTriggerConfig>) => void
}

const AUTH_OPTIONS = [
  { value: 'NONE' as const, label: 'None', description: 'No authentication required' },
  { value: 'API_KEY' as const, label: 'API Key', description: 'Require API key in header' },
  {
    value: 'WEBHOOK_SECRET' as const,
    label: 'Webhook Secret',
    description: 'HMAC signature verification',
  },
]

export function AutolaunchedTriggerForm({ config, onChange }: AutolaunchedTriggerFormProps) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <Label htmlFor="webhook-path" className="text-sm">
          Webhook Path (optional)
        </Label>
        <Input
          id="webhook-path"
          value={config.webhookPath || ''}
          onChange={(e) => onChange({ ...config, webhookPath: e.target.value || undefined })}
          className="mt-1 font-mono"
          placeholder="/webhooks/my-flow"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          Custom path for incoming webhook requests
        </p>
      </div>

      <div>
        <Label className="text-sm">Authentication</Label>
        <div className="mt-2 flex flex-col gap-1.5">
          {AUTH_OPTIONS.map((opt) => (
            <Label
              key={opt.value}
              htmlFor={`auth-type-${opt.value}`}
              className="flex cursor-pointer items-start gap-2 rounded-md border border-border p-2.5 font-normal transition-colors hover:bg-muted has-[:checked]:border-primary has-[:checked]:bg-primary/5"
            >
              <input
                id={`auth-type-${opt.value}`}
                type="radio"
                name="auth-type"
                value={opt.value}
                checked={(config.authentication || 'NONE') === opt.value}
                onChange={() => onChange({ ...config, authentication: opt.value })}
                className="mt-0.5"
              />
              <span>
                <span className="text-sm font-medium">{opt.label}</span>
                <span className="block text-xs text-muted-foreground">{opt.description}</span>
              </span>
            </Label>
          ))}
        </div>
      </div>
    </div>
  )
}
