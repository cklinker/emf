/**
 * Image built-in (slice 2g) — polish over the bare 2a `image` (which had `src`/`alt` only). `src`/`alt`
 * are now bindable and an `objectFit` select controls CSS `object-fit`. `data-testid`s are unchanged
 * (`page-node-image` / `page-node-image-placeholder`) so the 2a parity tests stay green.
 *
 * SECURITY: `src` is author- AND data-controlled (a `{$bind}` can resolve to a hostile `javascript:` /
 * `data:` URL). It is scheme-validated against the shared allow-list ({http, https, mailto, tel, relative})
 * via {@link isSafeUrl} (slice 2e's `runtime/urlSafety`) BEFORE it reaches `<img src>`. A rejected scheme
 * collapses to `''` → the no-src branch (placeholder in editor, `null` in runtime), so a hostile URL can
 * never become a live `<img src>` attribute.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { Image as ImageIcon } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import { isSafeUrl } from '../../runtime/urlSafety'
import { asString } from '../util'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'

const OBJECT_FIT = ['cover', 'contain', 'fill', 'none'] as const
type ObjectFit = (typeof OBJECT_FIT)[number]

/** Return the URL if its scheme is allow-listed, else '' (neutralized — no <img src>). */
function safeSrc(value: unknown): string {
  return typeof value === 'string' && isSafeUrl(value) ? value : ''
}

function ImageRender({ node, mode }: WidgetRenderProps): React.ReactElement | null {
  const { t } = useI18n()
  const props = node.props ?? {}
  // `src` is data-controlled — scheme-validate before it reaches <img src>.
  const src = safeSrc(props.src)
  const alt = asString(props.alt, 'Image')
  const fit: ObjectFit = (OBJECT_FIT as readonly string[]).includes(props.objectFit as string)
    ? (props.objectFit as ObjectFit)
    : 'cover'

  // No src (or a rejected scheme): placeholder in editor (matches 2a), nothing in runtime.
  if (!src) {
    if (mode === 'editor') {
      return (
        <div
          className="flex h-32 w-full items-center justify-center rounded-lg border border-dashed border-border text-xs text-muted-foreground"
          data-testid="page-node-image-placeholder"
        >
          {t('builder.widget.image.empty')}
        </div>
      )
    }
    return null
  }
  return (
    <img
      src={src}
      alt={alt}
      className="max-w-full rounded-md"
      style={{ objectFit: fit }}
      data-testid="page-node-image"
    />
  )
}

export const imageWidget: WidgetDescriptor = {
  type: 'image',
  label: 'Image',
  icon: ImageIcon,
  category: 'content',
  acceptsChildren: false,
  defaultProps: { src: '', alt: 'Image', objectFit: 'cover' },
  propSchema: [
    { key: 'src', label: 'Source URL', kind: 'text', bindable: true, group: 'content' },
    { key: 'alt', label: 'Alt text', kind: 'text', bindable: true, group: 'content' },
    {
      key: 'objectFit',
      label: 'Fit',
      kind: 'select',
      options: OBJECT_FIT.map((v) => ({ value: v, label: v })),
      group: 'content',
    },
  ],
  Render: ImageRender,
}
