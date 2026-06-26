/**
 * Tabs built-in + internal `tab-panel` container (slice 2g).
 *
 * A `tabs` node owns N `tab-panel` child nodes, one per entry in `props.tabs[]`, matched by `value`.
 * Each `tab-panel` is an ordinary container node (its own `children` are a normal sub-tree). The `tabs`
 * `Render` renders the radix tab list from `props.tabs[]` and, per tab, looks up the matching `tab-panel`
 * and maps its children through the shared `renderChild`, so the whole structure stays inside the standard
 * `PageComponent[]` tree. `tab-panel` is registered (so `widgetRegistry.get` resolves it) but is
 * palette-hidden via `paletteHidden` (consumed by 2b's registry-driven palette).
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { PanelTop, Square } from 'lucide-react'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { useI18n } from '@/context/I18nContext'
import type { WidgetDescriptor, WidgetRenderProps, RenderNode } from '../types'

interface TabDef {
  value: string
  label: string
}

function readTabs(props: Record<string, unknown> | undefined): TabDef[] {
  return Array.isArray(props?.tabs)
    ? (props.tabs as unknown as TabDef[]).filter((tab) => tab && typeof tab.value === 'string')
    : []
}

/** Find the tab-panel child whose props.value matches a tab. */
function panelFor(children: RenderNode[] | undefined, value: string): RenderNode | undefined {
  return (children ?? []).find(
    (child) =>
      child.type === 'tab-panel' && (child.props as Record<string, unknown>)?.value === value
  )
}

function TabsRender({ node, renderChild }: WidgetRenderProps): React.ReactElement {
  const { t } = useI18n()
  const props = node.props ?? {}
  const tabs = readTabs(props)
  if (tabs.length === 0) {
    return (
      <div className="text-xs text-muted-foreground" data-testid="page-node-tabs-empty">
        {t('builder.widget.tabs.empty')}
      </div>
    )
  }
  const defaultTab =
    typeof props.defaultTab === 'string' && props.defaultTab ? props.defaultTab : tabs[0].value
  return (
    <Tabs defaultValue={defaultTab} className="w-full" data-testid="page-node-tabs">
      <TabsList>
        {tabs.map((tab) => (
          <TabsTrigger key={tab.value} value={tab.value} data-testid={`tab-trigger-${tab.value}`}>
            {tab.label}
          </TabsTrigger>
        ))}
      </TabsList>
      {tabs.map((tab) => {
        const panel = panelFor(node.children, tab.value)
        return (
          <TabsContent key={tab.value} value={tab.value} data-testid={`tab-content-${tab.value}`}>
            {panel ? (panel.children ?? []).map((child) => renderChild(child)) : null}
          </TabsContent>
        )
      })}
    </Tabs>
  )
}

export const tabsWidget: WidgetDescriptor = {
  type: 'tabs',
  label: 'Tabs',
  icon: PanelTop,
  category: 'navigation',
  acceptsChildren: true, // children are tab-panel nodes
  defaultProps: {
    tabs: [
      { value: 'tab-1', label: 'Tab one' },
      { value: 'tab-2', label: 'Tab two' },
    ],
    defaultTab: 'tab-1',
  },
  propSchema: [
    // 2b renders a tab-list editor for kind:'expression' (add/remove/rename/reorder tabs, keeping a
    // matching tab-panel child in sync via treeOps).
    { key: 'tabs', label: 'Tabs', kind: 'expression', group: 'content' },
    { key: 'defaultTab', label: 'Default tab', kind: 'text', group: 'content' },
  ],
  Render: TabsRender,
}

/**
 * Internal container node, one per tab. NOT shown in the palette (`paletteHidden`). The `tabs` Render
 * reaches into `panel.children` directly; this standalone Render only matters for treeOps moves on the
 * canvas. Registered so `widgetRegistry.get('tab-panel')` never falls through to the unknown descriptor.
 */
export const tabPanelWidget: WidgetDescriptor = {
  type: 'tab-panel',
  label: 'Tab panel',
  icon: Square,
  category: 'layout',
  acceptsChildren: true,
  paletteHidden: true,
  defaultProps: { value: '' },
  propSchema: [{ key: 'value', label: 'Tab value', kind: 'text', group: 'content' }],
  Render: ({ node, renderChild }: WidgetRenderProps) => (
    <div data-testid="page-node-tab-panel">
      {(node.children ?? []).map((child) => renderChild(child))}
    </div>
  ),
}
