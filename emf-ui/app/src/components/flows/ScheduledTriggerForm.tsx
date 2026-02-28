import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { ScheduledTriggerConfig } from '@/pages/FlowDesignerPage/types'

interface ScheduledTriggerFormProps {
  config: Partial<ScheduledTriggerConfig>
  onChange: (config: Partial<ScheduledTriggerConfig>) => void
}

const CRON_PRESETS = [
  { label: 'Every hour', value: '0 * * * *' },
  { label: 'Every day at midnight', value: '0 0 * * *' },
  { label: 'Every Monday at 9am', value: '0 9 * * 1' },
  { label: 'First of every month', value: '0 0 1 * *' },
  { label: 'Custom', value: '' },
]

export function ScheduledTriggerForm({ config, onChange }: ScheduledTriggerFormProps) {
  const isPreset = CRON_PRESETS.some((p) => p.value === config.cron)

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
                onChange={() => {
                  if (preset.value) {
                    onChange({ ...config, cron: preset.value })
                  }
                }}
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
            onChange={(e) => onChange({ ...config, cron: e.target.value })}
            className="mt-1 font-mono"
            placeholder="0 */5 * * *"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            Standard cron format: minute hour day month weekday
          </p>
        </div>
      )}

      <div>
        <Label htmlFor="timezone" className="text-sm">
          Timezone (optional)
        </Label>
        <Input
          id="timezone"
          value={config.timezone || ''}
          onChange={(e) => onChange({ ...config, timezone: e.target.value || undefined })}
          className="mt-1"
          placeholder="America/New_York"
        />
      </div>
    </div>
  )
}
