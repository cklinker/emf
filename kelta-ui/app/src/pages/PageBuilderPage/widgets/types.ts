/**
 * Widget descriptor types — the single registration shape that replaces the five hardcoded per-type
 * switch sites (palette, inspector, editor preview, runtime renderer). Palette, inspector, and both
 * renderers become schema-driven loops over these descriptors. See the parent parity spec.
 */
import type { ComponentType, ReactNode } from 'react'
import type { BindingScope } from '../model/bindingScope'
import type { EventName, PageComponent, PropValue } from '../model/pageModel'

/** A node as rendered — structurally the builder `PageComponent` / runtime `PageNode`. */
export type RenderNode = Pick<PageComponent, 'id' | 'type'> & {
  props?: Record<string, unknown>
  events?: PageComponent['events']
  span?: PageComponent['span']
  children?: RenderNode[]
}

export type WidgetCategory = 'layout' | 'content' | 'data' | 'input' | 'navigation' | 'chart'

/** The kind of inspector control a prop is edited with (consumed by the schema-driven inspector, 2b). */
export type PropFieldKind =
  | 'text'
  | 'textarea'
  | 'number'
  | 'boolean'
  | 'select'
  | 'color'
  | 'collection-picker'
  | 'field-picker'
  | 'expression'
  | 'event-list'
  | 'span'
  | 'children'

/** One inspector-editable prop on a widget. */
export interface PropFieldSchema {
  key: string
  label: string
  kind: PropFieldKind
  options?: { label: string; value: string }[]
  bindable?: boolean
  group?: string
  dependsOnCollection?: boolean
  /**
   * 2f: when `kind:'field-picker'`, restrict the field list to these UI `FieldType`s (the 2b inspector
   * reads this hint). Additive — descriptors without it offer every field.
   */
  fieldTypeFilter?: import('@/hooks/useCollectionSchema').FieldType[]
}

/**
 * Props passed to a widget's `Render`. `node.props` are ALWAYS already binding-resolved (identity in
 * 2a, real in 2d) — descriptors must not resolve again. `renderChild` recurses through the same
 * shared path; the optional `scope` lets a repeater pass an `item`-augmented scope.
 */
export interface WidgetRenderProps {
  node: RenderNode
  scope: BindingScope
  mode: 'editor' | 'runtime'
  tenantSlug: string
  renderChild: (child: RenderNode, scope?: BindingScope) => ReactNode
}

/** A registered widget. One descriptor replaces all five per-type switch arms for that type. */
export interface WidgetDescriptor {
  type: string
  label: string
  icon: ComponentType<{ className?: string; size?: number | string }>
  category: WidgetCategory
  defaultProps: Record<string, PropValue>
  propSchema: PropFieldSchema[]
  acceptsChildren?: boolean
  /**
   * Registered but excluded from the palette (e.g. the internal `tab-panel` container). The
   * registry-driven palette (2b) filters these out; `widgetRegistry.get` still resolves them so the
   * render path never falls through to the unknown descriptor.
   */
  paletteHidden?: boolean
  /** Events this widget exposes in the inspector's `event-list` field (wired in 2e). */
  supportedEvents?: EventName[]
  /** The single render function used by BOTH the editor preview and the runtime. */
  Render: ComponentType<WidgetRenderProps>
}
