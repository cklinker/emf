/**
 * Icon built-in (slice 2g). Renders a lucide icon by NAME from the static `icons` map (keyed by
 * PascalCase). An unknown name degrades to `HelpCircle` (no crash). `props.name` is bindable and arrives
 * already resolved. `dynamicIconImports` is NOT exported in `lucide-react@^0.563.0`, so the static `icons`
 * map is the correct by-name lookup (no async path).
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { icons, HelpCircle, Star } from 'lucide-react'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'

/** lucide exports an `icons` map keyed by PascalCase name; convert kebab/snake/space input. */
function toPascal(name: string): string {
  return name
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

function IconRender({ node }: WidgetRenderProps): React.ReactElement {
  const props = node.props ?? {}
  const rawName = typeof props.name === 'string' && props.name ? props.name : 'star'
  const size = typeof props.size === 'number' ? props.size : 20
  const color = typeof props.color === 'string' && props.color ? props.color : undefined
  // icons is keyed by PascalCase; fall back to HelpCircle for an unknown name (no crash).
  const LucideIcon = icons[toPascal(rawName) as keyof typeof icons] ?? HelpCircle
  return (
    <LucideIcon
      size={size}
      color={color}
      className={color ? undefined : 'text-foreground'}
      data-testid="page-node-icon"
      aria-hidden="true"
    />
  )
}

export const iconWidget: WidgetDescriptor = {
  type: 'icon',
  label: 'Icon',
  icon: Star,
  category: 'content',
  acceptsChildren: false,
  defaultProps: { name: 'star', size: 20, color: '' },
  propSchema: [
    { key: 'name', label: 'Icon name', kind: 'text', bindable: true, group: 'content' },
    { key: 'size', label: 'Size (px)', kind: 'number', group: 'content' },
    { key: 'color', label: 'Color', kind: 'color', group: 'content' },
  ],
  Render: IconRender,
}
