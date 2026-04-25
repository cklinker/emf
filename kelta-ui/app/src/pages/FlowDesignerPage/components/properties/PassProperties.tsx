import React from 'react'
import { FieldLabel } from '@/components/kelta'
import { Textarea } from '@/components/ui/textarea'
import { DataPathFields } from './DataPathFields'

interface PassPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  onUpdate: (data: Record<string, unknown>) => void
}

export function PassProperties({ nodeId, data, onUpdate }: PassPropertiesProps) {
  return (
    <div className="flex flex-col gap-3">
      <div>
        <FieldLabel htmlFor={`result-json-${nodeId}`} className="text-xs">
          Result (JSON)
        </FieldLabel>
        <Textarea
          id={`result-json-${nodeId}`}
          value={(data.result as string) || ''}
          onChange={(e) => onUpdate({ result: e.target.value })}
          className="mt-1 min-h-[100px] font-mono text-xs"
          placeholder='{ "key": "value" }'
          rows={4}
        />
      </div>

      <DataPathFields
        nodeId={nodeId}
        inputPath={(data.inputPath as string) || ''}
        outputPath={(data.outputPath as string) || ''}
        resultPath={(data.resultPath as string) || ''}
        onUpdate={onUpdate}
      />
    </div>
  )
}
