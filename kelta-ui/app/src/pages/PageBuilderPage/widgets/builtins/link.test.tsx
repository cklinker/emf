/**
 * Link widget (slice 2g). External `href` → `<a target/rel>`; internal `to` (runtime) → router `Link`;
 * editor-mode internal `to` → inert anchor. SECURITY: a `javascript:` / `data:` `href` is neutralized by
 * the shared scheme allow-list (`runtime/urlSafety`) before render — no `<a>` ever carries it.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderLink(node: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  return render(
    <I18nProvider>
      <MemoryRouter>
        <RenderTree components={[node]} tenantSlug="acme" mode={mode} />
      </MemoryRouter>
    </I18nProvider>
  )
}

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('link widget', () => {
  it('renders an external href as an anchor', () => {
    renderLink({
      id: 'lk1',
      type: 'link',
      props: { label: 'Docs', href: 'https://kelta.io/docs' },
    })
    const link = screen.getByTestId('page-node-link')
    expect(link.tagName).toBe('A')
    expect(link).toHaveAttribute('href', 'https://kelta.io/docs')
    expect(link).toHaveTextContent('Docs')
  })

  it('opens in a new tab with rel=noopener when newTab is set', () => {
    renderLink({
      id: 'lk2',
      type: 'link',
      props: { label: 'Docs', href: 'https://kelta.io/docs', newTab: true },
    })
    const link = screen.getByTestId('page-node-link')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('renders an in-app router Link for an internal `to` in runtime mode', () => {
    renderLink({ id: 'lk3', type: 'link', props: { label: 'Orders', to: '/app/p/orders' } })
    const link = screen.getByTestId('page-node-link')
    expect(link).toHaveAttribute('href', '/app/p/orders')
  })

  it('is inert in editor mode for an internal `to` (click preventDefault, no navigation)', async () => {
    renderLink(
      { id: 'lk4', type: 'link', props: { label: 'Orders', to: '/app/p/orders' } },
      'editor'
    )
    const link = screen.getByTestId('page-node-link')
    const clicked = await userEvent.click(link)
    // preventDefault: no navigation away from the canvas. (No throw / route change.)
    expect(link).toBeInTheDocument()
    expect(clicked).toBeUndefined()
  })

  it('allows mailto: and tel: schemes', () => {
    renderLink({ id: 'lk5', type: 'link', props: { label: 'Mail', href: 'mailto:a@b.com' } })
    expect(screen.getByTestId('page-node-link')).toHaveAttribute('href', 'mailto:a@b.com')
  })

  it('SECURITY: neutralizes a javascript: href (no executable URL on the anchor)', () => {
    const warn = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    renderLink({
      id: 'lk6',
      type: 'link',
      props: { label: 'Evil', href: 'javascript:alert(1)' },
    })
    const link = screen.getByTestId('page-node-link')
    // safeHref rejected it → falls through to the inert internal branch with href '#', never javascript:.
    expect(link.getAttribute('href') ?? '').not.toContain('javascript:')
    expect(link).toHaveAttribute('href', '#')
    warn.mockRestore()
  })

  it('SECURITY: neutralizes a data: href', () => {
    renderLink({
      id: 'lk7',
      type: 'link',
      props: { label: 'Evil', href: 'data:text/html,<script>alert(1)</script>' },
    })
    const link = screen.getByTestId('page-node-link')
    expect(link.getAttribute('href') ?? '').not.toContain('data:')
  })
})
