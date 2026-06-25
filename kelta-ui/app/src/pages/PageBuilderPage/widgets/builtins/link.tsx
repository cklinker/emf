/**
 * Link built-in (slice 2g). Navigation-by-target only: a `react-router-dom` `Link` for an internal `to`,
 * or an `<a>` for an external `href`. `label`/`to`/`href` are bindable and arrive already resolved. Wiring
 * a click to a `PageAction` is 2e (out of scope here). In editor mode the link never navigates.
 *
 * SECURITY: `href` is author- AND data-controlled (a `{$bind}` can resolve to a hostile `javascript:` /
 * `data:` URL). It is scheme-validated against the shared allow-list ({http, https, mailto, tel, relative})
 * via {@link isSafeUrl} (slice 2e's `runtime/urlSafety`) BEFORE rendering `<a href>`. A rejected scheme
 * collapses to `''`, neutralizing the link (it routes through the inert internal branch — no executable URL
 * ever reaches the DOM).
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { Link } from 'react-router-dom'
import { ExternalLink } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import { isSafeUrl } from '../../runtime/urlSafety'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'

const LINK_CLASS = 'text-primary underline-offset-2 hover:underline focus-visible:outline-ring'

/** Return the URL if its scheme is allow-listed, else '' (neutralized — inert link). */
function safeHref(value: unknown): string {
  return typeof value === 'string' && isSafeUrl(value) ? value : ''
}

function LinkRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  const { t } = useI18n()
  const props = node.props ?? {}
  const label =
    typeof props.label === 'string' && props.label ? props.label : t('builder.widget.link.default')
  const to = typeof props.to === 'string' ? props.to : ''
  // `href` is data-controlled — scheme-validate before it reaches <a href>.
  const href = safeHref(props.href)
  const newTab = props.newTab === true

  // External href wins when set (and passed the scheme check); else an internal router link.
  if (href) {
    return (
      <a
        href={href}
        target={newTab ? '_blank' : undefined}
        rel={newTab ? 'noopener noreferrer' : undefined}
        className={LINK_CLASS}
        data-testid="page-node-link"
      >
        {label}
      </a>
    )
  }
  // In editor mode (or with no internal target), render a non-navigating anchor so canvas clicks select.
  if (mode === 'editor' || !to) {
    return (
      <a
        href={to || '#'}
        className={LINK_CLASS}
        data-testid="page-node-link"
        onClick={(e) => e.preventDefault()}
      >
        {label}
      </a>
    )
  }
  return (
    <Link
      to={to}
      target={newTab ? '_blank' : undefined}
      className={LINK_CLASS}
      data-testid="page-node-link"
    >
      {label}
    </Link>
  )
}

export const linkWidget: WidgetDescriptor = {
  type: 'link',
  label: 'Link',
  icon: ExternalLink,
  category: 'content',
  acceptsChildren: false,
  defaultProps: { label: 'Link', to: '', href: '', newTab: false },
  propSchema: [
    { key: 'label', label: 'Label', kind: 'text', bindable: true, group: 'content' },
    { key: 'to', label: 'Internal route', kind: 'text', bindable: true, group: 'content' },
    { key: 'href', label: 'External URL', kind: 'text', bindable: true, group: 'content' },
    { key: 'newTab', label: 'Open in new tab', kind: 'boolean', group: 'content' },
  ],
  Render: LinkRender,
}
