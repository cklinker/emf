import React, { useCallback, useRef, useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  type OnConnect,
  type Node,
  type Edge,
  type NodeTypes,
  type Connection,
  BackgroundVariant,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import { TaskNode } from './nodes/TaskNode'
import { ChoiceNode } from './nodes/ChoiceNode'
import { TerminalNode } from './nodes/TerminalNode'
import { ControlNode } from './nodes/ControlNode'

const NODE_TYPES: NodeTypes = {
  task: TaskNode,
  choice: ChoiceNode,
  terminal: TerminalNode,
  control: ControlNode,
}

interface FlowCanvasProps {
  initialNodes: Node[]
  initialEdges: Edge[]
  onNodesChange?: (nodes: Node[]) => void
  onEdgesChange?: (edges: Edge[]) => void
  onNodeSelect?: (node: Node | null) => void
}

let nodeIdCounter = 0

function getNodeType(stateType: string): string {
  switch (stateType) {
    case 'Task':
      return 'task'
    case 'Choice':
      return 'choice'
    case 'Succeed':
    case 'Fail':
      return 'terminal'
    case 'Wait':
    case 'Pass':
    case 'Parallel':
    case 'Map':
      return 'control'
    default:
      return 'task'
  }
}

export function FlowCanvas({
  initialNodes,
  initialEdges,
  onNodesChange: onNodesChangeProp,
  onEdgesChange: onEdgesChangeProp,
  onNodeSelect,
}: FlowCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null)
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges)

  // Sync external changes
  const nodesRef = useRef(nodes)
  nodesRef.current = nodes
  const edgesRef = useRef(edges)
  edgesRef.current = edges

  const onConnect: OnConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) => {
        const updated = addEdge({ ...connection, animated: false, style: { strokeWidth: 2 } }, eds)
        onEdgesChangeProp?.(updated)
        return updated
      })
    },
    [setEdges, onEdgesChangeProp]
  )

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault()
      const stateType = event.dataTransfer.getData('application/reactflow-type')
      if (!stateType) return

      const bounds = reactFlowWrapper.current?.getBoundingClientRect()
      if (!bounds) return

      const position = {
        x: event.clientX - bounds.left - 80,
        y: event.clientY - bounds.top - 20,
      }

      const id = `${stateType.toLowerCase()}_${++nodeIdCounter}`
      const newNode: Node = {
        id,
        type: getNodeType(stateType),
        position,
        data: {
          label: `${stateType} ${nodeIdCounter}`,
          stateType,
          ...(stateType === 'Succeed' || stateType === 'Fail' ? { stateType } : {}),
        },
      }

      setNodes((nds) => {
        const updated = [...nds, newNode]
        onNodesChangeProp?.(updated)
        return updated
      })
    },
    [setNodes, onNodesChangeProp]
  )

  const onSelectionChange = useCallback(
    ({ nodes: selectedNodes }: { nodes: Node[] }) => {
      onNodeSelect?.(selectedNodes.length === 1 ? selectedNodes[0] : null)
    },
    [onNodeSelect]
  )

  const defaultEdgeOptions = useMemo(
    () => ({
      style: { strokeWidth: 2, stroke: 'var(--color-border)' },
      type: 'smoothstep' as const,
    }),
    []
  )

  return (
    <div ref={reactFlowWrapper} className="flex-1">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDragOver={onDragOver}
        onDrop={onDrop}
        onSelectionChange={onSelectionChange}
        nodeTypes={NODE_TYPES}
        defaultEdgeOptions={defaultEdgeOptions}
        fitView
        snapToGrid
        snapGrid={[16, 16]}
        deleteKeyCode={['Backspace', 'Delete']}
        className="bg-background"
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
        <Controls className="!bg-card !border-border !shadow-sm" />
        <MiniMap
          className="!bg-card !border-border"
          nodeColor={(n) => {
            switch (n.type) {
              case 'task':
                return '#93c5fd'
              case 'choice':
                return '#fcd34d'
              case 'terminal':
                return n.data?.stateType === 'Succeed' ? '#86efac' : '#fca5a5'
              case 'control':
                return '#c4b5fd'
              default:
                return '#d1d5db'
            }
          }}
        />
      </ReactFlow>
    </div>
  )
}
