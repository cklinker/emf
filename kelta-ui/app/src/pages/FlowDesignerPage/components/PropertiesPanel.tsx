import React from 'react'
import type { Node } from '@xyflow/react'
import { X } from 'lucide-react'
import { ScrollArea } from '@/components/ui/scroll-area'
import type { Flow } from '../types'
import {
  CommonFields,
  TaskProperties,
  ChoiceProperties,
  WaitProperties,
  PassProperties,
  ParallelProperties,
  MapProperties,
  FailProperties,
  SucceedProperties,
  TriggerSummaryCard,
} from './properties'

interface PropertiesPanelProps {
  selectedNode: Node | null
  collapsed?: boolean
  onToggle?: () => void
  onNodeUpdate?: (nodeId: string, data: Record<string, unknown>) => void
  flow?: Flow
  allNodeIds?: string[]
  onEditTrigger?: () => void
}

export function PropertiesPanel({
  selectedNode,
  collapsed,
  onToggle,
  onNodeUpdate,
  flow,
  allNodeIds = [],
  onEditTrigger,
}: PropertiesPanelProps) {
  if (collapsed) {
    return null
  }

  return (
    <div className="flex w-[320px] shrink-0 flex-col overflow-hidden border-l border-border bg-card">
      <div className="flex items-center justify-between border-b border-border px-4 py-2">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Properties
        </h3>
        {onToggle && (
          <button
            onClick={onToggle}
            className="rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label="Close properties"
          >
            <X className="h-3 w-3" />
          </button>
        )}
      </div>
      <ScrollArea className="flex-1">
        <div className="p-4">
          {selectedNode ? (
            <NodeProperties
              node={selectedNode}
              allNodeIds={allNodeIds}
              onUpdate={(data) => onNodeUpdate?.(selectedNode.id, data)}
            />
          ) : flow ? (
            <FlowOverview flow={flow} onEditTrigger={onEditTrigger} />
          ) : (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              Select a step to view its properties
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}

interface FlowOverviewProps {
  flow: Flow
  onEditTrigger?: () => void
}

function FlowOverview({ flow, onEditTrigger }: FlowOverviewProps) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <div className="text-sm font-semibold text-foreground">{flow.name}</div>
        {flow.description && (
          <div className="mt-1 text-xs text-muted-foreground">{flow.description}</div>
        )}
      </div>

      <TriggerSummaryCard flow={flow} onEditTrigger={onEditTrigger} />

      <div className="flex flex-col gap-1 border-t border-border pt-3 text-[11px] text-muted-foreground">
        {flow.version != null && <div>Version: {flow.version}</div>}
        {flow.publishedVersion != null && <div>Published: v{flow.publishedVersion}</div>}
        <div>Status: {flow.active ? 'Active' : 'Inactive'}</div>
      </div>
    </div>
  )
}

interface NodePropertiesProps {
  node: Node
  allNodeIds: string[]
  onUpdate: (data: Record<string, unknown>) => void
}

function NodeProperties({ node, allNodeIds, onUpdate }: NodePropertiesProps) {
  const data = node.data as Record<string, unknown>
  const stateType = (data.stateType as string) || node.type || 'Unknown'

  return (
    <div className="flex flex-col gap-4">
      <CommonFields
        nodeId={node.id}
        stateType={stateType}
        label={(data.label as string) || ''}
        comment={(data.comment as string) || ''}
        onUpdate={onUpdate}
      />

      <div className="border-t border-border pt-3">
        <StateTypeProperties
          stateType={stateType}
          nodeId={node.id}
          data={data}
          allNodeIds={allNodeIds}
          onUpdate={onUpdate}
        />
      </div>
    </div>
  )
}

interface StateTypePropertiesProps {
  stateType: string
  nodeId: string
  data: Record<string, unknown>
  allNodeIds: string[]
  onUpdate: (data: Record<string, unknown>) => void
}

function StateTypeProperties({
  stateType,
  nodeId,
  data,
  allNodeIds,
  onUpdate,
}: StateTypePropertiesProps) {
  switch (stateType) {
    case 'Task':
      return (
        <TaskProperties nodeId={nodeId} data={data} allNodeIds={allNodeIds} onUpdate={onUpdate} />
      )
    case 'Choice':
      return (
        <ChoiceProperties nodeId={nodeId} data={data} allNodeIds={allNodeIds} onUpdate={onUpdate} />
      )
    case 'Wait':
      return <WaitProperties nodeId={nodeId} data={data} onUpdate={onUpdate} />
    case 'Pass':
      return <PassProperties nodeId={nodeId} data={data} onUpdate={onUpdate} />
    case 'Parallel':
      return (
        <ParallelProperties
          nodeId={nodeId}
          data={data}
          allNodeIds={allNodeIds}
          onUpdate={onUpdate}
        />
      )
    case 'Map':
      return <MapProperties nodeId={nodeId} data={data} onUpdate={onUpdate} />
    case 'Fail':
      return <FailProperties nodeId={nodeId} data={data} onUpdate={onUpdate} />
    case 'Succeed':
      return <SucceedProperties />
    default:
      return <div className="text-xs text-muted-foreground">Unknown state type: {stateType}</div>
  }
}
