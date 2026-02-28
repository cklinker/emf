import React from 'react'
import type { Node } from '@xyflow/react'
import { X } from 'lucide-react'

interface PropertiesPanelProps {
  selectedNode: Node | null
  collapsed?: boolean
  onToggle?: () => void
  onNodeUpdate?: (nodeId: string, data: Record<string, unknown>) => void
}

export function PropertiesPanel({
  selectedNode,
  collapsed,
  onToggle,
  onNodeUpdate,
}: PropertiesPanelProps) {
  if (collapsed) {
    return null
  }

  return (
    <div className="flex w-[320px] shrink-0 flex-col border-l border-border bg-card">
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
      <div className="flex-1 overflow-y-auto p-4">
        {selectedNode ? (
          <NodeProperties
            node={selectedNode}
            onUpdate={(data) => onNodeUpdate?.(selectedNode.id, data)}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
            Select a step to view its properties
          </div>
        )}
      </div>
    </div>
  )
}

interface NodePropertiesProps {
  node: Node
  onUpdate: (data: Record<string, unknown>) => void
}

function NodeProperties({ node, onUpdate }: NodePropertiesProps) {
  const data = node.data as Record<string, unknown>
  const stateType = (data.stateType as string) || node.type || 'Unknown'

  return (
    <div className="flex flex-col gap-4">
      <div>
        <div className="mb-1 text-xs font-medium text-muted-foreground">State Type</div>
        <div className="text-sm font-semibold text-foreground">{stateType}</div>
      </div>

      <div>
        <label
          htmlFor={`node-name-${node.id}`}
          className="mb-1 block text-xs font-medium text-muted-foreground"
        >
          Name
        </label>
        <input
          id={`node-name-${node.id}`}
          type="text"
          value={(data.label as string) || ''}
          onChange={(e) => onUpdate({ ...data, label: e.target.value })}
          className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        />
      </div>

      {stateType === 'Task' && (
        <div>
          <label
            htmlFor={`node-resource-${node.id}`}
            className="mb-1 block text-xs font-medium text-muted-foreground"
          >
            Resource
          </label>
          <select
            id={`node-resource-${node.id}`}
            value={(data.resource as string) || ''}
            onChange={(e) => onUpdate({ ...data, resource: e.target.value })}
            className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          >
            <option value="">Select resource...</option>
            <optgroup label="Data">
              <option value="FIELD_UPDATE">Field Update</option>
              <option value="CREATE_RECORD">Create Record</option>
              <option value="UPDATE_RECORD">Update Record</option>
              <option value="DELETE_RECORD">Delete Record</option>
              <option value="QUERY_RECORDS">Query Records</option>
            </optgroup>
            <optgroup label="Communication">
              <option value="EMAIL_ALERT">Email Alert</option>
              <option value="SEND_NOTIFICATION">Send Notification</option>
              <option value="OUTBOUND_MESSAGE">Outbound Message</option>
            </optgroup>
            <optgroup label="Integration">
              <option value="HTTP_CALLOUT">HTTP Callout</option>
              <option value="PUBLISH_EVENT">Publish Event</option>
              <option value="INVOKE_SCRIPT">Invoke Script</option>
            </optgroup>
            <optgroup label="Utility">
              <option value="LOG_MESSAGE">Log Message</option>
              <option value="TRANSFORM_DATA">Transform Data</option>
            </optgroup>
          </select>
        </div>
      )}

      {stateType === 'Fail' && (
        <>
          <div>
            <label
              htmlFor={`node-error-${node.id}`}
              className="mb-1 block text-xs font-medium text-muted-foreground"
            >
              Error Code
            </label>
            <input
              id={`node-error-${node.id}`}
              type="text"
              value={(data.error as string) || ''}
              onChange={(e) => onUpdate({ ...data, error: e.target.value })}
              className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              placeholder="e.g., ValidationError"
            />
          </div>
          <div>
            <label
              htmlFor={`node-cause-${node.id}`}
              className="mb-1 block text-xs font-medium text-muted-foreground"
            >
              Cause
            </label>
            <textarea
              id={`node-cause-${node.id}`}
              value={(data.cause as string) || ''}
              onChange={(e) => onUpdate({ ...data, cause: e.target.value })}
              className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              rows={2}
              placeholder="Error message"
            />
          </div>
        </>
      )}

      <div className="border-t border-border pt-3">
        <div className="text-xs text-muted-foreground">
          Node ID: <span className="font-mono">{node.id}</span>
        </div>
      </div>
    </div>
  )
}
