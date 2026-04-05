import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useSystemPermissions } from '../../hooks/useSystemPermissions'
import { useToast } from '../../components/Toast'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Bot, Zap, Settings } from 'lucide-react'
import { cn } from '@/lib/utils'

interface AiConfig {
  model: string
  maxTokens: string
  temperature: string
  aiTokensPerMonth: string
  aiEnabled: string
}

const AVAILABLE_MODELS = [
  { value: 'claude-sonnet-4-20250514', label: 'Claude Sonnet 4' },
  { value: 'claude-opus-4-20250514', label: 'Claude Opus 4' },
  { value: 'claude-haiku-4-20250514', label: 'Claude Haiku 4' },
]

function formatTokenCount(count: number): string {
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1)}M`
  if (count >= 1_000) return `${(count / 1_000).toFixed(0)}K`
  return String(count)
}

export interface AiSettingsPageProps {
  className?: string
}

export function AiSettingsPage({ className }: AiSettingsPageProps): React.ReactElement {
  const { keltaClient } = useApi()
  const { hasPermission } = useSystemPermissions()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const canManage = hasPermission('MANAGE_TENANTS')
  const [editConfig, setEditConfig] = useState<AiConfig | null>(null)

  const {
    data: config,
    isLoading: configLoading,
    error: configError,
  } = useQuery({
    queryKey: ['ai-config'],
    queryFn: () => keltaClient.admin.ai.config.get(),
  })

  const { data: usage, isLoading: usageLoading } = useQuery({
    queryKey: ['ai-usage'],
    queryFn: () => keltaClient.admin.ai.usage(),
    refetchInterval: 60000,
  })

  const saveMutation = useMutation({
    mutationFn: (updates: Partial<AiConfig>) => keltaClient.admin.ai.config.update(updates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-config'] })
      queryClient.invalidateQueries({ queryKey: ['ai-usage'] })
      showToast('AI settings saved successfully', 'success')
      setEditConfig(null)
    },
    onError: () => {
      showToast('Failed to save AI settings', 'error')
    },
  })

  if (configLoading) return <LoadingSpinner />
  if (configError) return <ErrorMessage message="Failed to load AI configuration" />

  const currentConfig = editConfig ?? config
  const isEditing = editConfig !== null

  return (
    <div className={cn('space-y-6', className)}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Bot className="h-6 w-6 text-primary" />
          <div>
            <h1 className="text-2xl font-bold">AI Settings</h1>
            <p className="text-sm text-muted-foreground">
              Configure AI assistant model, limits, and monitoring
            </p>
          </div>
        </div>
        {canManage && !isEditing && (
          <Button onClick={() => setEditConfig(config ?? null)}>
            <Settings className="mr-2 h-4 w-4" />
            Edit Settings
          </Button>
        )}
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Model Configuration */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Model Configuration</CardTitle>
            <CardDescription>Select the AI model and parameters</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Model</Label>
              {isEditing ? (
                <Select
                  value={currentConfig?.model}
                  onValueChange={(v) =>
                    setEditConfig((prev) => (prev ? { ...prev, model: v } : null))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {AVAILABLE_MODELS.map((m) => (
                      <SelectItem key={m.value} value={m.value}>
                        {m.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <p className="text-sm font-medium">
                  {AVAILABLE_MODELS.find((m) => m.value === currentConfig?.model)?.label ??
                    currentConfig?.model}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label>Max Tokens</Label>
              {isEditing ? (
                <Input
                  type="number"
                  value={currentConfig?.maxTokens}
                  onChange={(e) =>
                    setEditConfig((prev) => (prev ? { ...prev, maxTokens: e.target.value } : null))
                  }
                />
              ) : (
                <p className="text-sm font-medium">{currentConfig?.maxTokens}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label>Temperature</Label>
              {isEditing ? (
                <Input
                  type="number"
                  step="0.1"
                  min="0"
                  max="1"
                  value={currentConfig?.temperature}
                  onChange={(e) =>
                    setEditConfig((prev) =>
                      prev ? { ...prev, temperature: e.target.value } : null
                    )
                  }
                />
              ) : (
                <p className="text-sm font-medium">{currentConfig?.temperature}</p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Token Limits */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Token Limits</CardTitle>
            <CardDescription>Configure monthly token usage limits</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <Label>AI Enabled</Label>
              {isEditing ? (
                <Switch
                  checked={currentConfig?.aiEnabled === 'true'}
                  onCheckedChange={(checked) =>
                    setEditConfig((prev) => (prev ? { ...prev, aiEnabled: String(checked) } : null))
                  }
                />
              ) : (
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-xs font-medium',
                    currentConfig?.aiEnabled === 'true'
                      ? 'bg-green-100 text-green-800'
                      : 'bg-red-100 text-red-800'
                  )}
                >
                  {currentConfig?.aiEnabled === 'true' ? 'Enabled' : 'Disabled'}
                </span>
              )}
            </div>

            <div className="space-y-2">
              <Label>Monthly Token Limit</Label>
              {isEditing ? (
                <Input
                  type="number"
                  value={currentConfig?.aiTokensPerMonth}
                  onChange={(e) =>
                    setEditConfig((prev) =>
                      prev ? { ...prev, aiTokensPerMonth: e.target.value } : null
                    )
                  }
                />
              ) : (
                <p className="text-sm font-medium">
                  {formatTokenCount(Number(currentConfig?.aiTokensPerMonth ?? 0))} tokens/month
                </p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Usage Dashboard */}
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Zap className="h-4 w-4" />
              Current Month Usage
            </CardTitle>
          </CardHeader>
          <CardContent>
            {usageLoading ? (
              <LoadingSpinner />
            ) : usage ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted-foreground">Tokens Used</span>
                  <span className="text-lg font-semibold">
                    {formatTokenCount(usage.currentMonthUsage)} /{' '}
                    {formatTokenCount(usage.tokenLimit)}
                  </span>
                </div>
                {/* Progress bar */}
                <div className="h-3 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn(
                      'h-full rounded-full transition-all',
                      usage.currentMonthUsage / usage.tokenLimit >= 1
                        ? 'bg-red-500'
                        : usage.currentMonthUsage / usage.tokenLimit >= 0.8
                          ? 'bg-yellow-500'
                          : 'bg-primary'
                    )}
                    style={{
                      width: `${Math.min((usage.currentMonthUsage / usage.tokenLimit) * 100, 100)}%`,
                    }}
                  />
                </div>

                {/* Monthly history */}
                {Object.keys(usage.history).length > 0 && (
                  <div className="mt-4">
                    <h4 className="text-sm font-medium mb-2">Monthly History</h4>
                    <div className="grid grid-cols-3 gap-2 text-xs">
                      <div className="font-medium text-muted-foreground">Month</div>
                      <div className="font-medium text-muted-foreground">Tokens</div>
                      <div className="font-medium text-muted-foreground">Requests</div>
                      {Object.entries(usage.history).map(([month, data]) => (
                        <React.Fragment key={month}>
                          <div>{month}</div>
                          <div>{formatTokenCount(data.inputTokens + data.outputTokens)}</div>
                          <div>{data.requestCount}</div>
                        </React.Fragment>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No usage data available</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Edit actions */}
      {isEditing && (
        <div className="flex gap-2 justify-end">
          <Button variant="outline" onClick={() => setEditConfig(null)}>
            Cancel
          </Button>
          <Button
            onClick={() => editConfig && saveMutation.mutate(editConfig)}
            disabled={saveMutation.isPending}
          >
            {saveMutation.isPending ? 'Saving...' : 'Save Changes'}
          </Button>
        </div>
      )}
    </div>
  )
}
