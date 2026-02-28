import React, { memo } from 'react'
import { Handle, Position } from '@xyflow/react'
import type { NodeProps, Node } from '@xyflow/react'
import { Cog } from 'lucide-react'

export type TaskNodeData = {
  label: string
  resource?: string
  stateType: string
}

type TaskNodeType = Node<TaskNodeData, 'task'>

function TaskNodeComponent({ data, selected }: NodeProps<TaskNodeType>) {
  return (
    <div
      className={`flex min-w-[180px] flex-col rounded-lg border-2 bg-blue-50 shadow-sm transition-all dark:bg-blue-950 ${
        selected
          ? 'border-blue-500 ring-2 ring-blue-500/30'
          : 'border-blue-300 dark:border-blue-700'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-blue-400" />
      <div className="flex items-center gap-2 px-3 py-2">
        <Cog className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium text-blue-900 dark:text-blue-100">
            {data.label}
          </div>
          {data.resource && (
            <div className="mt-0.5 truncate text-xs text-blue-600 dark:text-blue-400">
              {data.resource}
            </div>
          )}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
    </div>
  )
}

export const TaskNode = memo(TaskNodeComponent)
