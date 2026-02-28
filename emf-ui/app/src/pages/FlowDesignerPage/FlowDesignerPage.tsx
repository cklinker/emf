import React, { useState, useCallback, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ReactFlowProvider } from '@xyflow/react'
import type { Node, Edge } from '@xyflow/react'

import { useApi } from '../../context/ApiContext'
import { useToast, LoadingSpinner, ErrorMessage } from '../../components'
import { FlowToolbar } from './components/FlowToolbar'
import { StepsPalette } from './components/StepsPalette'
import { FlowCanvas } from './components/FlowCanvas'
import { PropertiesPanel } from './components/PropertiesPanel'

interface Flow {
  id: string
  name: string
  description: string | null
  flowType: string
  active: boolean
  definition: string | null
  triggerConfig: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface FlowDefinition {
  Comment?: string
  StartAt: string
  States: Record<string, StateConfig>
  _metadata?: {
    nodePositions?: Record<string, { x: number; y: number }>
  }
}

interface StateConfig {
  Type: string
  Comment?: string
  Next?: string
  End?: boolean
  Resource?: string
  Choices?: Array<{ Next: string; [key: string]: unknown }>
  Default?: string
  Error?: string
  Cause?: string
  Branches?: FlowDefinition[]
  [key: string]: unknown
}

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

function parseFlowDefinition(definition: string | null): FlowDefinition | null {
  if (!definition) return null
  try {
    return JSON.parse(definition) as FlowDefinition
  } catch {
    return null
  }
}

function definitionToNodesAndEdges(definition: FlowDefinition | null): {
  nodes: Node[]
  edges: Edge[]
} {
  if (!definition || !definition.States) {
    return { nodes: [], edges: [] }
  }

  const positions = definition._metadata?.nodePositions || {}
  const stateIds = Object.keys(definition.States)
  const nodes: Node[] = []
  const edges: Edge[] = []

  stateIds.forEach((stateId, index) => {
    const state = definition.States[stateId]
    const pos = positions[stateId] || { x: 250, y: index * 120 }

    nodes.push({
      id: stateId,
      type: getNodeType(state.Type),
      position: pos,
      data: {
        label: stateId,
        stateType: state.Type,
        resource: state.Resource,
        ruleCount: state.Choices?.length,
        error: state.Error,
        cause: state.Cause,
        branchCount: state.Branches?.length,
      },
    })

    // Normal Next transitions
    if (state.Next) {
      edges.push({
        id: `${stateId}->${state.Next}`,
        source: stateId,
        target: state.Next,
        type: 'smoothstep',
        style: { strokeWidth: 2 },
      })
    }

    // Choice branches
    if (state.Choices) {
      state.Choices.forEach((choice, ci) => {
        if (choice.Next) {
          edges.push({
            id: `${stateId}->choice-${ci}->${choice.Next}`,
            source: stateId,
            target: choice.Next as string,
            type: 'smoothstep',
            label: `Rule ${ci + 1}`,
            style: { strokeWidth: 2 },
          })
        }
      })
    }
    if (state.Default) {
      edges.push({
        id: `${stateId}->default->${state.Default}`,
        source: stateId,
        target: state.Default,
        type: 'smoothstep',
        label: 'Default',
        style: { strokeWidth: 2, strokeDasharray: '5,5' },
      })
    }
  })

  return { nodes, edges }
}

export function FlowDesignerPage() {
  const { flowId } = useParams<{ flowId: string }>()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [paletteCollapsed, setPaletteCollapsed] = useState(false)
  const [propertiesCollapsed, setPropertiesCollapsed] = useState(false)
  const [showJson, setShowJson] = useState(false)
  const [isDirty, setIsDirty] = useState(false)
  const [currentNodes, setCurrentNodes] = useState<Node[]>([])
  const [currentEdges, setCurrentEdges] = useState<Edge[]>([])

  const {
    data: flow,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['flow', flowId],
    queryFn: () => apiClient.getOne<Flow>(`/api/flows/${flowId}`),
    enabled: !!flowId,
  })

  const parsedDefinition = useMemo(
    () => parseFlowDefinition(flow?.definition ?? null),
    [flow?.definition]
  )

  const { nodes: initialNodes, edges: initialEdges } = useMemo(
    () => definitionToNodesAndEdges(parsedDefinition),
    [parsedDefinition]
  )

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!flowId || !flow) return
      // Build definition back from nodes/edges
      const definition = nodesToDefinition(currentNodes, currentEdges, parsedDefinition)
      await apiClient.putResource(`/api/flows/${flowId}`, {
        ...flow,
        definition: JSON.stringify(definition),
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flow', flowId] })
      showToast('Flow saved successfully', 'success')
      setIsDirty(false)
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to save flow', 'error')
    },
  })

  const handleNodesChange = useCallback((nodes: Node[]) => {
    setCurrentNodes(nodes)
    setIsDirty(true)
  }, [])

  const handleEdgesChange = useCallback((edges: Edge[]) => {
    setCurrentEdges(edges)
    setIsDirty(true)
  }, [])

  const handleNodeSelect = useCallback(
    (node: Node | null) => {
      setSelectedNode(node)
      if (node && propertiesCollapsed) {
        setPropertiesCollapsed(false)
      }
    },
    [propertiesCollapsed]
  )

  const handleNodeUpdate = useCallback((nodeId: string, data: Record<string, unknown>) => {
    setCurrentNodes((prev) =>
      prev.map((n) => (n.id === nodeId ? { ...n, data: { ...n.data, ...data } } : n))
    )
    setIsDirty(true)
  }, [])

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <LoadingSpinner size="large" label="Loading flow..." />
      </div>
    )
  }

  if (error || !flow) {
    return (
      <div className="flex h-screen items-center justify-center p-8">
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Flow not found')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className="flex h-screen flex-col">
      <FlowToolbar
        flowName={flow.name}
        flowType={flow.flowType}
        isActive={flow.active}
        isDirty={isDirty}
        isSaving={saveMutation.isPending}
        showJson={showJson}
        onSave={() => saveMutation.mutate()}
        onToggleJson={() => setShowJson((v) => !v)}
      />

      <div className="flex flex-1 overflow-hidden">
        <StepsPalette
          collapsed={paletteCollapsed}
          onToggle={() => setPaletteCollapsed((v) => !v)}
        />

        <ReactFlowProvider>
          {showJson ? (
            <div className="flex-1 overflow-auto bg-background p-4">
              <pre className="whitespace-pre-wrap rounded-lg border border-border bg-muted p-4 font-mono text-xs text-foreground">
                {JSON.stringify(
                  nodesToDefinition(
                    currentNodes.length > 0 ? currentNodes : initialNodes,
                    currentEdges.length > 0 ? currentEdges : initialEdges,
                    parsedDefinition
                  ),
                  null,
                  2
                )}
              </pre>
            </div>
          ) : (
            <FlowCanvas
              initialNodes={initialNodes}
              initialEdges={initialEdges}
              onNodesChange={handleNodesChange}
              onEdgesChange={handleEdgesChange}
              onNodeSelect={handleNodeSelect}
            />
          )}
        </ReactFlowProvider>

        <PropertiesPanel
          selectedNode={selectedNode}
          collapsed={propertiesCollapsed}
          onToggle={() => setPropertiesCollapsed((v) => !v)}
          onNodeUpdate={handleNodeUpdate}
        />
      </div>
    </div>
  )
}

/**
 * Converts React Flow nodes and edges back into a FlowDefinition JSON.
 */
function nodesToDefinition(
  nodes: Node[],
  edges: Edge[],
  existing: FlowDefinition | null
): FlowDefinition {
  if (nodes.length === 0) {
    return existing || { StartAt: '', States: {} }
  }

  const states: Record<string, StateConfig> = {}
  const positions: Record<string, { x: number; y: number }> = {}

  // Build edge map: source -> target edges
  const edgeMap = new Map<string, Edge[]>()
  for (const edge of edges) {
    const existing = edgeMap.get(edge.source) || []
    existing.push(edge)
    edgeMap.set(edge.source, existing)
  }

  for (const node of nodes) {
    const data = node.data as Record<string, unknown>
    const stateType = (data.stateType as string) || 'Task'
    positions[node.id] = { x: node.position.x, y: node.position.y }

    const state: StateConfig = { Type: stateType }

    const outEdges = edgeMap.get(node.id) || []

    if (stateType === 'Task') {
      if (data.resource) state.Resource = data.resource as string
      const next = outEdges.find((e) => !e.label)
      if (next) {
        state.Next = next.target
      } else if (outEdges.length === 0) {
        state.End = true
      }
    } else if (stateType === 'Choice') {
      const defaultEdge = outEdges.find((e) => e.label === 'Default')
      const ruleEdges = outEdges.filter((e) => e.label !== 'Default')
      state.Choices = ruleEdges.map((e) => ({ Next: e.target }))
      if (defaultEdge) state.Default = defaultEdge.target
    } else if (stateType === 'Succeed' || stateType === 'Fail') {
      state.End = true
      if (stateType === 'Fail') {
        if (data.error) state.Error = data.error as string
        if (data.cause) state.Cause = data.cause as string
      }
    } else {
      // Wait, Pass, Parallel, Map
      const next = outEdges[0]
      if (next) {
        state.Next = next.target
      } else {
        state.End = true
      }
    }

    states[node.id] = state
  }

  // Determine StartAt: first node (topmost)
  const topNode = [...nodes].sort((a, b) => a.position.y - b.position.y)[0]
  const startAt =
    existing?.StartAt && states[existing.StartAt] ? existing.StartAt : topNode?.id || ''

  return {
    Comment: existing?.Comment,
    StartAt: startAt,
    States: states,
    _metadata: { nodePositions: positions },
  }
}

export default FlowDesignerPage
