/** Content built-ins: heading, text, button, image. Markup matches the runtime renderer for parity. */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { Heading, Type, MousePointerClick, Image as ImageIcon } from 'lucide-react'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'
import { interpolate } from '../../model/interpolate'
import type { BindingScope } from '../../model/bindingScope'

const HEADING_LEVELS = ['h1', 'h2', 'h3', 'h4']

/**
 * Coerce a resolved prop to text, then run `{{…}}` interpolation against the scope so authors can mix
 * literals + merge tags in free-text props (`"Showing {{data.accounts[0].name}}"`). The structured
 * `$bind` form is already resolved by `renderNode`; this only handles inline tags in literal strings.
 */
function asText(value: unknown, scope: BindingScope, fallback = ''): string {
  return interpolate(asString(value, fallback), scope)
}

function HeadingRender({ node, scope }: WidgetRenderProps): React.ReactElement {
  const rawLevel = asString(node.props?.level, 'h2')
  // 2a deliberately honors `level` (the runtime previously hardcoded <h2>).
  const tag = HEADING_LEVELS.includes(rawLevel) ? rawLevel : 'h2'
  return React.createElement(
    tag,
    { className: 'text-2xl font-semibold text-foreground', 'data-testid': 'page-node-heading' },
    asText(node.props?.text, scope, 'Heading')
  )
}

const heading: WidgetDescriptor = {
  type: 'heading',
  label: 'Heading',
  icon: Heading,
  category: 'content',
  defaultProps: { text: 'Heading', level: 'h2' },
  propSchema: [
    { key: 'text', label: 'Text', kind: 'text', bindable: true, group: 'content' },
    {
      key: 'level',
      label: 'Level',
      kind: 'select',
      group: 'content',
      options: [
        { label: 'H1', value: 'h1' },
        { label: 'H2', value: 'h2' },
        { label: 'H3', value: 'h3' },
        { label: 'H4', value: 'h4' },
      ],
    },
  ],
  Render: HeadingRender,
}

const text: WidgetDescriptor = {
  type: 'text',
  label: 'Text',
  icon: Type,
  category: 'content',
  defaultProps: { content: '' },
  propSchema: [
    { key: 'content', label: 'Content', kind: 'textarea', bindable: true, group: 'content' },
  ],
  Render: ({ node, scope }) => (
    <p className="text-sm text-muted-foreground" data-testid="page-node-text">
      {asText(node.props?.content ?? node.props?.text, scope)}
    </p>
  ),
}

const button: WidgetDescriptor = {
  type: 'button',
  label: 'Button',
  icon: MousePointerClick,
  category: 'content',
  defaultProps: { label: 'Button' },
  propSchema: [
    { key: 'label', label: 'Label', kind: 'text', bindable: true, group: 'content' },
    {
      key: 'variant',
      label: 'Variant',
      kind: 'select',
      group: 'content',
      options: [
        { label: 'Primary', value: 'primary' },
        { label: 'Secondary', value: 'secondary' },
        { label: 'Danger', value: 'danger' },
      ],
    },
    { key: 'events', label: 'Events', kind: 'event-list', group: 'events' },
  ],
  supportedEvents: ['onClick'],
  Render: ({ node, scope }) => {
    const label = asText(node.props?.label, scope, 'Button')
    const href = asString(node.props?.href)
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
  },
}

const image: WidgetDescriptor = {
  type: 'image',
  label: 'Image',
  icon: ImageIcon,
  category: 'content',
  defaultProps: {},
  propSchema: [
    { key: 'src', label: 'Source URL', kind: 'text', bindable: true, group: 'content' },
    { key: 'alt', label: 'Alt text', kind: 'text', bindable: true, group: 'content' },
  ],
  Render: ({ node }) => (
    <img
      src={asString(node.props?.src)}
      alt={asString(node.props?.alt, 'Image')}
      className="max-w-full rounded-md"
      data-testid="page-node-image"
    />
  ),
}

export const contentWidgets: WidgetDescriptor[] = [heading, text, button, image]
