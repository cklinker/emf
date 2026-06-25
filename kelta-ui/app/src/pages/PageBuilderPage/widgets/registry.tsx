/**
 * Widget descriptor registry — the single source the palette, inspector, and both renderers consult.
 *
 * `get(type)` resolves, in order: a registered built-in, then a plugin page component (wrapped in a
 * synthetic descriptor so existing plugins keep working with ZERO changes), then an "unknown" default
 * descriptor. This is what lets the builder/runtime stop special-casing unknown types.
 */
import React from 'react'
import { HelpCircle, Puzzle } from 'lucide-react'
import { componentRegistry } from '@/services/componentRegistry'
import type { WidgetDescriptor, WidgetCategory } from './types'

class WidgetRegistry {
  private widgets = new Map<string, WidgetDescriptor>()
  private syntheticCache = new Map<string, WidgetDescriptor>()

  register(descriptor: WidgetDescriptor): void {
    this.widgets.set(descriptor.type, descriptor)
  }

  /** Resolve a descriptor for a type, falling back to a plugin component, then to the unknown default. */
  get(type: string): WidgetDescriptor {
    const builtin = this.widgets.get(type)
    if (builtin) return builtin

    const PluginComp = componentRegistry.getPageComponent(type)
    if (PluginComp) {
      const cached = this.syntheticCache.get(type)
      if (cached) return cached
      const descriptor = pluginDescriptor(type, PluginComp)
      this.syntheticCache.set(type, descriptor)
      return descriptor
    }

    return unknownDescriptor(type)
  }

  has(type: string): boolean {
    return this.widgets.has(type) || componentRegistry.hasPageComponent(type)
  }

  list(): WidgetDescriptor[] {
    return Array.from(this.widgets.values())
  }

  listByCategory(): Record<WidgetCategory, WidgetDescriptor[]> {
    const out = {} as Record<WidgetCategory, WidgetDescriptor[]>
    for (const w of this.widgets.values()) {
      ;(out[w.category] ??= []).push(w)
    }
    return out
  }

  /** Test-only: drop all built-in registrations. */
  clear(): void {
    this.widgets.clear()
    this.syntheticCache.clear()
  }
}

/** Wrap a plugin page component in a descriptor. It receives the (already-resolved) props as `config`. */
function pluginDescriptor(
  type: string,
  PluginComp: ReturnType<typeof componentRegistry.getPageComponent>
): WidgetDescriptor {
  return {
    type,
    label: type,
    icon: Puzzle,
    category: 'content',
    defaultProps: {},
    propSchema: [],
    Render: ({ node, tenantSlug }) =>
      PluginComp
        ? React.createElement(PluginComp, {
            config: node.props as Record<string, unknown>,
            tenantSlug,
          })
        : null,
  }
}

/** The default descriptor for a genuinely unknown type — renders a visible placeholder. */
function unknownDescriptor(type: string): WidgetDescriptor {
  return {
    type,
    label: type,
    icon: HelpCircle,
    category: 'content',
    defaultProps: {},
    propSchema: [],
    Render: () => (
      <div className="text-xs text-muted-foreground" data-testid="page-node-unknown">
        Unknown component: {type}
      </div>
    ),
  }
}

/** Global singleton. Built-ins register at import of `./builtins`. */
export const widgetRegistry = new WidgetRegistry()
