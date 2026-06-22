/**
 * PageTreeRenderer
 *
 * Schema-driven renderer for a builder component tree (the `tree.components` of a page render
 * contract). Maps the base node types produced by the page builder (heading, text, button, image,
 * card, container, table, form) to React elements, and falls back to the plugin
 * {@link componentRegistry} for any other node type. This is the runtime counterpart of the
 * builder's preview — versioned via the render contract so the node schema can evolve.
 */
import React from 'react'
import { componentRegistry } from '@/services/componentRegistry'

export interface PageNode {
  id: string
  type: string
  props?: Record<string, unknown>
  children?: PageNode[]
}

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function renderChildren(node: PageNode, tenantSlug: string): React.ReactNode {
  return (node.children ?? []).map((child) => (
    <PageNodeRenderer key={child.id} node={child} tenantSlug={tenantSlug} />
  ))
}

function PageNodeRenderer({
  node,
  tenantSlug,
}: {
  node: PageNode
  tenantSlug: string
}): React.ReactElement {
  const props = node.props ?? {}

  switch (node.type) {
    case 'heading':
      return (
        <h2 className="text-2xl font-semibold text-foreground" data-testid="page-node-heading">
          {asString(props.text, 'Heading')}
        </h2>
      )
    case 'text':
      return (
        <p className="text-sm text-muted-foreground" data-testid="page-node-text">
          {asString(props.text)}
        </p>
      )
    case 'button': {
      const label = asString(props.label, 'Button')
      const href = asString(props.href)
      const className =
        'inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground'
      return href ? (
        <a href={href} className={className} data-testid="page-node-button">
          {label}
        </a>
      ) : (
        <button type="button" className={className} data-testid="page-node-button">
          {label}
        </button>
      )
    }
    case 'image':
      return (
        <img
          src={asString(props.src)}
          alt={asString(props.alt, 'Image')}
          className="max-w-full rounded-md"
          data-testid="page-node-image"
        />
      )
    case 'card':
      return (
        <div className="rounded-lg border border-border bg-card p-4" data-testid="page-node-card">
          {renderChildren(node, tenantSlug)}
        </div>
      )
    case 'container':
      return (
        <div className="space-y-4" data-testid="page-node-container">
          {renderChildren(node, tenantSlug)}
        </div>
      )
    case 'table':
    case 'form':
      // Data-bound nodes (DataView binding) land in a later slice; render a labelled placeholder.
      return (
        <div
          className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground"
          data-testid={`page-node-${node.type}`}
        >
          {node.type === 'table' ? 'Table' : 'Form'}
        </div>
      )
    default: {
      const Comp = componentRegistry.getPageComponent(node.type)
      if (Comp) {
        return React.createElement(Comp, { config: props, tenantSlug })
      }
      return (
        <div className="text-xs text-muted-foreground" data-testid="page-node-unknown">
          Unknown component: {node.type}
        </div>
      )
    }
  }
}

export function PageTreeRenderer({
  components,
  tenantSlug,
}: {
  components: PageNode[]
  tenantSlug: string
}): React.ReactElement {
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
        <PageNodeRenderer key={node.id} node={node} tenantSlug={tenantSlug} />
      ))}
    </div>
  )
}
