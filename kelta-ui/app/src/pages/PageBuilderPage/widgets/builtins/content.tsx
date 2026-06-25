/**
 * Content built-ins: heading, text, button. (The `image` built-in moved to `./image.tsx` in slice 2g,
 * where it gains bindable `src`/`alt`, an `objectFit` prop, and `src` scheme-validation.) Markup matches
 * the runtime renderer for parity.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { Heading, Type, MousePointerClick } from 'lucide-react'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'
import { interpolate } from '../../model/interpolate'
import type { BindingScope } from '../../model/bindingScope'
import type { PageAction } from '../../model/pageModel'
import { useActionRunner } from '../../runtime/useActionRunner'

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
  Render: ButtonRender,
}

const BUTTON_CLASS =
  'inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground'

/**
 * Button render. In `mode:'editor'` it is INERT (a plain button/link — clicking selects the node on the
 * canvas, never runs actions). In `mode:'runtime'` it mounts {@link RuntimeButton}, which wires the
 * action runner so `events.onClick` fires on click. An href-only button (no onClick) is unchanged.
 */
function ButtonRender({ node, scope, mode }: WidgetRenderProps): React.ReactElement {
  const label = asText(node.props?.label, scope, 'Button')
  const href = asString(node.props?.href)
  const onClick = node.events?.onClick

  if (mode === 'runtime' && onClick && onClick.length > 0) {
    return <RuntimeButton label={label} href={href || undefined} actions={onClick} scope={scope} />
  }

  return href ? (
    <a href={href} className={BUTTON_CLASS} data-testid="page-node-button">
      {label}
    </a>
  ) : (
    <button type="button" className={BUTTON_CLASS} data-testid="page-node-button">
      {label}
    </button>
  )
}

/** Runtime-only button that fires its onClick action list through the shared action runner. */
function RuntimeButton({
  label,
  href,
  actions,
  scope,
}: {
  label: string
  href?: string
  actions: PageAction[]
  scope: BindingScope
}): React.ReactElement {
  const { run } = useActionRunner()
  const handleClick = (e: React.MouseEvent) => {
    // An onClick action takes over from a plain href navigation (the actions decide where to go).
    if (href) e.preventDefault()
    void run(actions, scope)
  }
  return href ? (
    <a href={href} className={BUTTON_CLASS} onClick={handleClick} data-testid="page-node-button">
      {label}
    </a>
  ) : (
    <button
      type="button"
      className={BUTTON_CLASS}
      onClick={handleClick}
      data-testid="page-node-button"
    >
      {label}
    </button>
  )
}

export const contentWidgets: WidgetDescriptor[] = [heading, text, button]
