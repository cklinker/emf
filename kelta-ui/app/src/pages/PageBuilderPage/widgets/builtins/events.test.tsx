/**
 * Behavior tests for event firing on the `button` (and `form`) built-ins. The action runner is mocked so
 * we assert the WIRING (which actions fire, with which scope) without exercising the real runtime — that
 * is covered by executeAction.test.ts. The load-bearing rule: events fire in `mode:'runtime'`, never in
 * `mode:'editor'`.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

const runSpy = vi.fn().mockResolvedValue(undefined)
vi.mock('../../runtime/useActionRunner', () => ({
  useActionRunner: () => ({ run: runSpy }),
}))

import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'
import type { PageAction } from '../../model/pageModel'

const onClick: PageAction[] = [{ action: 'showToast', level: 'success', message: 'Hi' }]

describe('button onClick wiring', () => {
  beforeEach(() => {
    registerBuiltinWidgets()
    runSpy.mockClear()
  })

  it('runs events.onClick in runtime mode when the button is clicked', async () => {
    const nodes: RenderNode[] = [
      { id: 'b', type: 'button', props: { label: 'Go' }, events: { onClick } },
    ]
    render(
      <RenderTree components={nodes} tenantSlug="acme" mode="runtime" scope={{ vars: { x: 1 } }} />
    )
    await userEvent.click(screen.getByTestId('page-node-button'))
    expect(runSpy).toHaveBeenCalledTimes(1)
    expect(runSpy).toHaveBeenCalledWith(onClick, { vars: { x: 1 } })
  })

  it('is INERT in editor mode — clicking does not run actions', async () => {
    const nodes: RenderNode[] = [
      { id: 'b', type: 'button', props: { label: 'Go' }, events: { onClick } },
    ]
    render(<RenderTree components={nodes} tenantSlug="acme" mode="editor" />)
    await userEvent.click(screen.getByTestId('page-node-button'))
    expect(runSpy).not.toHaveBeenCalled()
  })

  it('an href-only button (no onClick) renders an anchor and does not run actions', async () => {
    const nodes: RenderNode[] = [
      { id: 'b', type: 'button', props: { label: 'Link', href: 'https://example.com' } },
    ]
    render(<RenderTree components={nodes} tenantSlug="acme" mode="runtime" />)
    const el = screen.getByTestId('page-node-button')
    expect(el.tagName).toBe('A')
    expect(el).toHaveAttribute('href', 'https://example.com')
    expect(runSpy).not.toHaveBeenCalled()
  })
})
