/**
 * The single shared render path used by BOTH the editor preview and the runtime renderer (which used
 * to duplicate per-type switches). `renderNode` looks up the descriptor, resolves the node's props
 * once (identity in 2a, real in 2d), and calls the descriptor's `Render`.
 */
/* eslint-disable react-refresh/only-export-components -- shared render path exports both RenderTree and renderNode */
import React from 'react'
import { resolveBindings, type BindingScope } from '../model/bindingScope'
import { isHiddenValue } from '../model/visibility'
import { widgetRegistry } from './registry'
import type { RenderNode } from './types'

interface NodeRendererProps {
  node: RenderNode
  scope: BindingScope
  mode: 'editor' | 'runtime'
  tenantSlug: string
}

function NodeRenderer({ node, scope, mode, tenantSlug }: NodeRendererProps): React.ReactElement {
  const descriptor = widgetRegistry.get(node.type)
  // Resolved-node invariant: props handed to Render are always already resolved.
  const resolvedNode: RenderNode = { ...node, props: resolveBindings(node.props ?? {}, scope) }
  // Conditional visibility (app-platform slice 1): at runtime a node whose `visible`
  // prop is present AND resolves hidden skips its whole subtree. The editor never
  // hides (SelectableNode ghosts/badges instead); absent prop = today's behavior.
  if (
    mode === 'runtime' &&
    node.props &&
    'visible' in node.props &&
    isHiddenValue(resolvedNode.props.visible)
  ) {
    return <></>
  }
  const Render = descriptor.Render
  const renderChild = (child: RenderNode, childScope?: BindingScope): React.ReactNode => (
    <NodeRenderer
      key={child.id}
      node={child}
      scope={childScope ?? scope}
      mode={mode}
      tenantSlug={tenantSlug}
    />
  )
  return (
    <Render
      node={resolvedNode}
      scope={scope}
      mode={mode}
      tenantSlug={tenantSlug}
      renderChild={renderChild}
    />
  )
}

/** Render a SINGLE node through the shared path (used by the canvas `SelectableNode`, slice 2c). */
export function renderNode(
  node: RenderNode,
  opts: { mode?: 'editor' | 'runtime'; tenantSlug: string; scope?: BindingScope }
): React.ReactElement {
  return (
    <NodeRenderer
      node={node}
      scope={opts.scope ?? {}}
      mode={opts.mode ?? 'runtime'}
      tenantSlug={opts.tenantSlug}
    />
  )
}

export interface RenderTreeProps {
  components: RenderNode[]
  scope?: BindingScope
  mode?: 'editor' | 'runtime'
  tenantSlug: string
}

/** Render a list of nodes through the shared path. */
export function RenderTree({
  components,
  scope = {},
  mode = 'runtime',
  tenantSlug,
}: RenderTreeProps): React.ReactElement {
  if (!components || components.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground" data-testid="page-empty">
        This page has no content yet.
      </p>
    )
  }
  return (
    <div className="flex flex-col gap-4" data-testid="page-tree">
      {components.map((node) => (
        <NodeRenderer key={node.id} node={node} scope={scope} mode={mode} tenantSlug={tenantSlug} />
      ))}
    </div>
  )
}
