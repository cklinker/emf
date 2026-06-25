/**
 * Image widget (slice 2g polish). Bindable `src`/`alt` + an `objectFit` prop; `data-testid`s unchanged
 * from 2a. SECURITY: a `javascript:` / `data:` `src` is neutralized by the shared scheme allow-list
 * (`runtime/urlSafety`) before render — no `<img src>` ever carries it.
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderImage(node: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  return render(
    <I18nProvider>
      <RenderTree components={[node]} tenantSlug="acme" mode={mode} />
    </I18nProvider>
  )
}

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('image widget', () => {
  it('renders an img with src, alt and objectFit', () => {
    renderImage({
      id: 'im1',
      type: 'image',
      props: { src: 'https://cdn.example.com/logo.png', alt: 'Logo', objectFit: 'contain' },
    })
    const img = screen.getByTestId('page-node-image')
    expect(img.tagName).toBe('IMG')
    expect(img).toHaveAttribute('src', 'https://cdn.example.com/logo.png')
    expect(img).toHaveAttribute('alt', 'Logo')
    expect(img).toHaveStyle({ objectFit: 'contain' })
  })

  it('falls back to objectFit cover for an invalid value', () => {
    renderImage({
      id: 'im2',
      type: 'image',
      props: { src: 'https://cdn.example.com/a.png', objectFit: 'nonsense' },
    })
    expect(screen.getByTestId('page-node-image')).toHaveStyle({ objectFit: 'cover' })
  })

  it('shows the editor placeholder when no src is set in editor mode', () => {
    renderImage({ id: 'im3', type: 'image', props: {} }, 'editor')
    expect(screen.getByTestId('page-node-image-placeholder')).toHaveTextContent('No image source')
  })

  it('renders nothing for no src in runtime mode', () => {
    renderImage({ id: 'im4', type: 'image', props: {} }, 'runtime')
    expect(screen.queryByTestId('page-node-image')).not.toBeInTheDocument()
    expect(screen.queryByTestId('page-node-image-placeholder')).not.toBeInTheDocument()
  })

  it('SECURITY: neutralizes a javascript: src (no <img> rendered)', () => {
    renderImage({ id: 'im5', type: 'image', props: { src: 'javascript:alert(1)' } }, 'runtime')
    expect(screen.queryByTestId('page-node-image')).not.toBeInTheDocument()
  })

  it('SECURITY: neutralizes a data: src — falls through to the placeholder in editor mode', () => {
    renderImage({ id: 'im6', type: 'image', props: { src: 'data:image/svg+xml,<svg/>' } }, 'editor')
    expect(screen.queryByTestId('page-node-image')).not.toBeInTheDocument()
    expect(screen.getByTestId('page-node-image-placeholder')).toBeInTheDocument()
  })
})
