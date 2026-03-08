import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DataPathFields } from './DataPathFields'

interface MapPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  onUpdate: (data: Record<string, unknown>) => void
}

export function MapProperties({ nodeId, data, onUpdate }: MapPropertiesProps) {
  return (
    <div className="flex flex-col gap-3">
      <div>
        <Label htmlFor={`items-path-${nodeId}`} className="text-xs">
          Items Path
        </Label>
        <Input
          id={`items-path-${nodeId}`}
          value={(data.itemsPath as string) || ''}
          onChange={(e) => onUpdate({ itemsPath: e.target.value })}
          className="mt-1 h-8 font-mono text-xs"
          placeholder="$.items"
        />
      </div>

      <div>
        <Label htmlFor={`max-concurrency-${nodeId}`} className="text-xs">
          Max Concurrency
        </Label>
        <Input
          id={`max-concurrency-${nodeId}`}
          type="number"
          value={(data.maxConcurrency as number) ?? ''}
          onChange={(e) =>
            onUpdate({
              maxConcurrency: e.target.value ? parseInt(e.target.value) : undefined,
            })
          }
          className="mt-1 h-8 text-sm"
          placeholder="Unlimited"
          min={1}
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
