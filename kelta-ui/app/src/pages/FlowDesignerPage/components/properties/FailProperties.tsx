import React from 'react'
import { Input } from '@/components/ui/input'
import { FieldLabel } from '@/components/kelta'
import { Textarea } from '@/components/ui/textarea'

interface FailPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  onUpdate: (data: Record<string, unknown>) => void
}

export function FailProperties({ nodeId, data, onUpdate }: FailPropertiesProps) {
  return (
    <div className="flex flex-col gap-3">
      <div>
        <FieldLabel htmlFor={`error-code-${nodeId}`} className="text-xs">
          Error Code
        </FieldLabel>
        <Input
          id={`error-code-${nodeId}`}
          value={(data.error as string) || ''}
          onChange={(e) => onUpdate({ error: e.target.value })}
          className="mt-1 h-8 text-sm"
          placeholder="e.g., ValidationError"
        />
      </div>

      <div>
        <FieldLabel htmlFor={`cause-${nodeId}`} className="text-xs">
          Cause
        </FieldLabel>
        <Textarea
          id={`cause-${nodeId}`}
          value={(data.cause as string) || ''}
          onChange={(e) => onUpdate({ cause: e.target.value })}
          className="mt-1 text-sm"
          rows={3}
          placeholder="Error message or description"
        />
      </div>
    </div>
  )
}
