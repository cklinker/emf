/**
 * Nav built-in (slice 2g). Wraps `@kelta/components`' `Navigation`, which integrates with
 * `react-router-dom` and filters by `currentUser.roles`. 2g passes static (or 2d-resolved bound) `items`
 * and an `orientation`; `currentUser` is left default (`null` → no role filtering) since page-builder nav
 * is public-page chrome. `props.items` arrives ALREADY resolved (the resolved-node invariant); this Render
 * never calls `resolveBindings`.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { Menu } from 'lucide-react'
import { Navigation, type MenuItem } from '@kelta/components'
import { useI18n } from '@/context/I18nContext'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'

function readItems(props: Record<string, unknown> | undefined): MenuItem[] {
  // `items` may be a literal authored array or a 2d-resolved bound array.
  return Array.isArray(props?.items) ? (props.items as unknown as MenuItem[]) : []
}

function NavRender({ node }: WidgetRenderProps): React.ReactElement {
  const { t } = useI18n()
  const props = node.props ?? {}
  const items = readItems(props)
  const orientation = props.orientation === 'vertical' ? 'vertical' : 'horizontal'
  if (items.length === 0) {
    return (
      <div className="text-xs text-muted-foreground" data-testid="page-node-nav-empty">
        {t('builder.widget.nav.empty')}
      </div>
    )
  }
  return <Navigation items={items} orientation={orientation} testId="page-node-nav" />
}

export const navWidget: WidgetDescriptor = {
  type: 'nav',
  label: 'Nav',
  icon: Menu,
  category: 'navigation',
  acceptsChildren: false,
  defaultProps: {
    orientation: 'horizontal',
    items: [],
  },
  propSchema: [
    {
      key: 'orientation',
      label: 'Orientation',
      kind: 'select',
      options: [
        { value: 'horizontal', label: 'Horizontal' },
        { value: 'vertical', label: 'Vertical' },
      ],
      group: 'content',
    },
    // 2b renders a menu-items editor for kind:'expression' (add/remove items with id/label/path).
    { key: 'items', label: 'Menu items', kind: 'expression', bindable: true, group: 'content' },
  ],
  Render: NavRender,
}
