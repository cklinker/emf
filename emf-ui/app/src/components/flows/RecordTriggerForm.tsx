import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { useApi } from '@/context/ApiContext'
import type { RecordTriggerConfig } from '@/pages/FlowDesignerPage/types'

interface RecordTriggerFormProps {
  config: Partial<RecordTriggerConfig>
  onChange: (config: Partial<RecordTriggerConfig>) => void
}

const EVENT_OPTIONS = [
  { value: 'CREATED' as const, label: 'Created' },
  { value: 'UPDATED' as const, label: 'Updated' },
  { value: 'DELETED' as const, label: 'Deleted' },
]

export function RecordTriggerForm({ config, onChange }: RecordTriggerFormProps) {
  const { apiClient } = useApi()

  const { data: collections } = useQuery({
    queryKey: ['collections-list'],
    queryFn: () =>
      apiClient.getAll<{ id: string; name: string; label: string }>('/api/collections'),
  })

  const events = config.events || []

  const toggleEvent = (event: 'CREATED' | 'UPDATED' | 'DELETED') => {
    const next = events.includes(event) ? events.filter((e) => e !== event) : [...events, event]
    onChange({ ...config, events: next })
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <Label htmlFor="trigger-collection" className="text-sm">
          Collection
        </Label>
        <select
          id="trigger-collection"
          value={config.collection || ''}
          onChange={(e) => onChange({ ...config, collection: e.target.value })}
          className="mt-1 h-9 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        >
          <option value="">Select a collection...</option>
          {(collections || []).map((c) => (
            <option key={c.id} value={c.name}>
              {c.label || c.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <Label className="text-sm">Events</Label>
        <div className="mt-2 flex flex-col gap-2">
          {EVENT_OPTIONS.map((opt) => (
            <div key={opt.value} className="flex items-center gap-2">
              <Checkbox
                id={`event-${opt.value}`}
                checked={events.includes(opt.value)}
                onCheckedChange={() => toggleEvent(opt.value)}
              />
              <Label htmlFor={`event-${opt.value}`} className="text-sm font-normal">
                {opt.label}
              </Label>
            </div>
          ))}
        </div>
      </div>

      <div>
        <Label htmlFor="trigger-fields" className="text-sm">
          Trigger Fields (comma-separated, optional)
        </Label>
        <Input
          id="trigger-fields"
          value={config.triggerFields?.join(', ') || ''}
          onChange={(e) =>
            onChange({
              ...config,
              triggerFields: e.target.value
                ? e.target.value.split(',').map((s) => s.trim())
                : undefined,
            })
          }
          className="mt-1"
          placeholder="name, status, amount"
        />
      </div>

      <div>
        <Label htmlFor="trigger-filter" className="text-sm">
          Filter Formula (optional)
        </Label>
        <Textarea
          id="trigger-filter"
          value={config.filterFormula || ''}
          onChange={(e) => onChange({ ...config, filterFormula: e.target.value || undefined })}
          className="mt-1"
          placeholder="status == 'active'"
          rows={2}
        />
      </div>
    </div>
  )
}
