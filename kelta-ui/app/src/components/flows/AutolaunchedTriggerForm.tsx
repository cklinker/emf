import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Copy, Check } from 'lucide-react'
import type { AutolaunchedTriggerConfig } from '@/pages/FlowDesignerPage/types'
import { useApi } from '@/context/ApiContext'

interface AutolaunchedTriggerFormProps {
  config: Partial<AutolaunchedTriggerConfig>
  onChange: (config: Partial<AutolaunchedTriggerConfig>) => void
  flowId?: string
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

export function AutolaunchedTriggerForm({
  config,
  onChange,
  flowId,
}: AutolaunchedTriggerFormProps) {
  const { keltaClient } = useApi()
  const [copied, setCopied] = useState(false)

  const { data: webhookUrl, isLoading: loading } = useQuery({
    queryKey: ['webhook-url', flowId],
    queryFn: async () => {
      const res = await keltaClient.admin.flows.getWebhookUrl(flowId!)
      return res.webhookUrl
    },
    enabled: !!flowId,
  })

  const handleCopy = async () => {
    if (!webhookUrl) return
    await navigator.clipboard.writeText(webhookUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex flex-col gap-4">
      {flowId && (
        <div>
          <Label className="text-sm">Webhook URL</Label>
          {loading ? (
            <div className="mt-1 text-xs text-muted-foreground">Loading webhook URL...</div>
          ) : webhookUrl ? (
            <div className="mt-1 flex items-center gap-2">
              <code
                data-testid="webhook-url"
                className="flex-1 truncate rounded-md border bg-muted px-2 py-1.5 font-mono text-xs"
              >
                {webhookUrl}
              </code>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-8 w-8 shrink-0 p-0"
                onClick={handleCopy}
                data-testid="copy-webhook-url"
              >
                {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
              </Button>
            </div>
          ) : (
            <div className="mt-1 text-xs text-muted-foreground">
              Save the flow to generate a webhook URL
            </div>
          )}
          <p className="mt-1 text-xs text-muted-foreground">
            Send POST requests to this URL to trigger the flow
          </p>
        </div>
      )}

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
