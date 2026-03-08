import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { WaitMode } from '../../types'

interface WaitPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  onUpdate: (data: Record<string, unknown>) => void
}

const WAIT_MODES: { value: WaitMode; label: string; description: string }[] = [
  { value: 'seconds', label: 'Fixed Seconds', description: 'Wait for a fixed number of seconds' },
  { value: 'timestamp', label: 'Timestamp', description: 'Wait until a specific ISO timestamp' },
  {
    value: 'timestampPath',
    label: 'Timestamp Path',
    description: 'Wait until a timestamp from state data',
  },
  {
    value: 'eventName',
    label: 'External Event',
    description: 'Wait for an external event to resume',
  },
]

export function WaitProperties({ nodeId, data, onUpdate }: WaitPropertiesProps) {
  const waitMode = (data.waitMode as WaitMode) || 'seconds'

  return (
    <div className="flex flex-col gap-3">
      <div>
        <Label className="text-xs">Wait Mode</Label>
        <div className="mt-1 flex flex-col gap-1.5">
          {WAIT_MODES.map((mode) => (
            <Label
              key={mode.value}
              htmlFor={`wait-mode-${nodeId}-${mode.value}`}
              className="flex cursor-pointer items-start gap-2 rounded-md border border-border p-2 font-normal transition-colors hover:bg-muted has-[:checked]:border-primary has-[:checked]:bg-primary/5"
            >
              <input
                id={`wait-mode-${nodeId}-${mode.value}`}
                type="radio"
                name={`wait-mode-${nodeId}`}
                value={mode.value}
                checked={waitMode === mode.value}
                onChange={() => onUpdate({ waitMode: mode.value })}
                className="mt-0.5"
              />
              <span>
                <span className="text-xs font-medium">{mode.label}</span>
                <span className="block text-[10px] text-muted-foreground">{mode.description}</span>
              </span>
            </Label>
          ))}
        </div>
      </div>

      {waitMode === 'seconds' && (
        <div>
          <Label htmlFor={`wait-seconds-${nodeId}`} className="text-xs">
            Seconds
          </Label>
          <Input
            id={`wait-seconds-${nodeId}`}
            type="number"
            value={(data.seconds as number) ?? ''}
            onChange={(e) =>
              onUpdate({ seconds: e.target.value ? parseInt(e.target.value) : undefined })
            }
            className="mt-1 h-8 text-sm"
            placeholder="10"
            min={1}
          />
        </div>
      )}

      {waitMode === 'timestamp' && (
        <div>
          <Label htmlFor={`wait-timestamp-${nodeId}`} className="text-xs">
            Timestamp (ISO 8601)
          </Label>
          <Input
            id={`wait-timestamp-${nodeId}`}
            value={(data.timestamp as string) || ''}
            onChange={(e) => onUpdate({ timestamp: e.target.value })}
            className="mt-1 h-8 text-sm"
            placeholder="2024-01-01T00:00:00Z"
          />
        </div>
      )}

      {waitMode === 'timestampPath' && (
        <div>
          <Label htmlFor={`wait-timestamp-path-${nodeId}`} className="text-xs">
            Timestamp Path
          </Label>
          <Input
            id={`wait-timestamp-path-${nodeId}`}
            value={(data.timestampPath as string) || ''}
            onChange={(e) => onUpdate({ timestampPath: e.target.value })}
            className="mt-1 h-8 font-mono text-xs"
            placeholder="$.scheduledTime"
          />
        </div>
      )}

      {waitMode === 'eventName' && (
        <div>
          <Label htmlFor={`wait-event-${nodeId}`} className="text-xs">
            Event Name
          </Label>
          <Input
            id={`wait-event-${nodeId}`}
            value={(data.eventName as string) || ''}
            onChange={(e) => onUpdate({ eventName: e.target.value })}
            className="mt-1 h-8 text-sm"
            placeholder="approval_received"
          />
        </div>
      )}
    </div>
  )
}
