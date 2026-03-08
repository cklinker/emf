import React from 'react'
import { Label } from '@/components/ui/label'
import type { RetryRule, CatchRule } from '../../types'
import { DataPathFields } from './DataPathFields'
import { RetryEditor } from './RetryEditor'
import { CatchEditor } from './CatchEditor'

interface ParallelPropertiesProps {
  nodeId: string
  data: Record<string, unknown>
  allNodeIds: string[]
  onUpdate: (data: Record<string, unknown>) => void
}

export function ParallelProperties({
  nodeId,
  data,
  allNodeIds,
  onUpdate,
}: ParallelPropertiesProps) {
  const branchCount = (data.branchCount as number) || 0

  return (
    <div className="flex flex-col gap-3">
      <div>
        <Label className="text-xs">Branches</Label>
        <div className="mt-1 rounded-md border border-border bg-muted/50 p-2 text-xs text-muted-foreground">
          {branchCount} branch{branchCount !== 1 ? 'es' : ''} defined
        </div>
      </div>

      <DataPathFields
        nodeId={nodeId}
        inputPath={(data.inputPath as string) || ''}
        outputPath={(data.outputPath as string) || ''}
        resultPath={(data.resultPath as string) || ''}
        onUpdate={onUpdate}
      />

      <RetryEditor
        retry={(data.retry as RetryRule[]) || []}
        onUpdate={(retry) => onUpdate({ retry })}
      />

      <CatchEditor
        catches={(data.catch as CatchRule[]) || []}
        allNodeIds={allNodeIds}
        onUpdate={(catches) => onUpdate({ catch: catches })}
      />
    </div>
  )
}
