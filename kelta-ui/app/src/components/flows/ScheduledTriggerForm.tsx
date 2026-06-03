import React, { useCallback, useState } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { ScheduledTriggerConfig } from '@/pages/FlowDesignerPage/types'
import { useApi } from '@/context/ApiContext'

interface ScheduledTriggerFormProps {
  config: Partial<ScheduledTriggerConfig>
  onChange: (config: Partial<ScheduledTriggerConfig>) => void
}

interface CronValidation {
  valid: boolean
  nextRunAt?: string
  error?: string
}

const CRON_PRESETS = [
  { label: 'Every hour', value: '0 * * * *' },
  { label: 'Every day at midnight', value: '0 0 * * *' },
  { label: 'Every Monday at 9am', value: '0 9 * * 1' },
  { label: 'First of every month', value: '0 0 1 * *' },
  { label: 'Custom', value: '' },
]

export function ScheduledTriggerForm({ config, onChange }: ScheduledTriggerFormProps) {
  const { apiClient } = useApi()
  const [validation, setValidation] = useState<CronValidation | null>(null)
  const [validating, setValidating] = useState(false)

  const isPreset = CRON_PRESETS.some((p) => p.value === config.cron && p.value !== '')

  const validateCron = useCallback(
    async (expression: string, timezone?: string) => {
      if (!expression.trim()) {
        setValidation(null)
        return
      }
      setValidating(true)
      try {
        const resp = await apiClient.fetch('/api/scheduled-jobs/validate-cron', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ expression: expression.trim(), timezone: timezone || '' }),
        })
        const data = (await resp.json()) as { valid?: boolean; nextRunAt?: string; error?: string }
        if (resp.ok) {
          setValidation({ valid: true, nextRunAt: data.nextRunAt })
        } else {
          setValidation({ valid: false, error: data.error || 'Invalid cron expression' })
        }
      } catch {
        setValidation({ valid: false, error: 'Could not validate cron expression' })
      } finally {
        setValidating(false)
      }
    },
    [apiClient]
  )

  const handlePresetSelect = (value: string) => {
    onChange({ ...config, cron: value })
    if (value) validateCron(value, config.timezone)
    else setValidation(null)
  }

  const handleCronBlur = (e: React.FocusEvent<HTMLInputElement>) => {
    validateCron(e.target.value, config.timezone)
  }

  const handleTimezoneBlur = () => {
    if (config.cron) validateCron(config.cron, config.timezone)
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <Label className="text-sm">Schedule Preset</Label>
        <div className="mt-2 flex flex-col gap-1.5">
          {CRON_PRESETS.map((preset) => (
            <label
              key={preset.label}
              className="flex cursor-pointer items-center gap-2 rounded-md border border-border p-2.5 transition-colors hover:bg-muted has-[:checked]:border-primary has-[:checked]:bg-primary/5"
            >
              <input
                type="radio"
                name="cron-preset"
                value={preset.value}
                checked={
                  preset.value === '' ? !isPreset && !!config.cron : config.cron === preset.value
                }
                onChange={() => handlePresetSelect(preset.value)}
              />
              <span className="text-sm">{preset.label}</span>
              {preset.value && (
                <span className="ml-auto font-mono text-xs text-muted-foreground">
                  {preset.value}
                </span>
              )}
            </label>
          ))}
        </div>
      </div>

      {(!isPreset || !config.cron) && (
        <div>
          <Label htmlFor="cron-expression" className="text-sm">
            Cron Expression
          </Label>
          <Input
            id="cron-expression"
            value={config.cron || ''}
            onChange={(e) => {
              onChange({ ...config, cron: e.target.value })
              setValidation(null)
            }}
            onBlur={handleCronBlur}
            className="mt-1 font-mono"
            placeholder="0 */5 * * *"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            Standard cron format: minute hour day month weekday
          </p>
        </div>
      )}

      {validating && (
        <p className="text-xs text-muted-foreground">Validating...</p>
      )}
      {!validating && validation && (
        <p
          className={
            validation.valid
              ? 'text-xs text-emerald-600 dark:text-emerald-400'
              : 'text-xs text-destructive'
          }
        >
          {validation.valid
            ? `Next run: ${validation.nextRunAt ? new Date(validation.nextRunAt).toLocaleString() : 'unknown'}`
            : validation.error}
        </p>
      )}

      <div>
        <Label htmlFor="timezone" className="text-sm">
          Timezone (optional)
        </Label>
        <Input
          id="timezone"
          value={config.timezone || ''}
          onChange={(e) => {
            onChange({ ...config, timezone: e.target.value || undefined })
            setValidation(null)
          }}
          onBlur={handleTimezoneBlur}
          className="mt-1"
          placeholder="America/New_York"
        />
        <p className="mt-1 text-xs text-muted-foreground">Defaults to UTC if blank</p>
      </div>
    </div>
  )
}
