/**
 * Video-visit widget tests (telehealth slice 6) — editor sample without fetches,
 * runtime rendering the shared VisitJoinCard for the next confirmed visit, and
 * registry registration.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { videoVisitWidget } from './videoVisit'
import { registerBuiltinWidgets } from '../builtins'
import { widgetRegistry } from '../registry'
import type { RenderNode } from '../types'

const useAppointmentsMock = vi.fn()

vi.mock('@/hooks/useScheduling', () => ({
  useAppointments: (...args: unknown[]) => useAppointmentsMock(...args),
}))
vi.mock('@/context/I18nContext', () => ({
  useI18n: () => ({
    t: (_key: string, fallback: string) => fallback,
    formatDate: (d: Date) => d.toISOString(),
  }),
}))
// The card pulls in the useVideo hooks + lazy LiveKit room; stub it to a marker
// so the widget test stays focused on wiring, not the join flow (covered in
// VisitJoinCard.test.tsx).
vi.mock('@/pages/app/VisitPage/VisitJoinCard', () => ({
  VisitJoinCard: (props: { target: { id: string }; joinGraceMinutes?: number }) => (
    <div
      data-testid="visit-join-card"
      data-appointment={props.target.id}
      data-grace={String(props.joinGraceMinutes)}
    />
  ),
}))

const node = (props: Record<string, unknown> = {}): RenderNode => ({
  id: 'v1',
  type: 'video-visit',
  props,
})

function renderWidget(nodeArg: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  const Render = videoVisitWidget.Render
  return render(
    <Render node={nodeArg} scope={{}} mode={mode} tenantSlug="acme" renderChild={() => null} />
  )
}

describe('video-visit widget', () => {
  beforeEach(() => {
    useAppointmentsMock.mockReset().mockReturnValue({ data: [], isLoading: false })
  })

  it('is registered in the widget registry under its type', () => {
    // Exercise the real registration path (builtins/index.ts → registry.tsx).
    registerBuiltinWidgets()
    expect(widgetRegistry.get('video-visit')).toBe(videoVisitWidget)
    expect(videoVisitWidget.category).toBe('data')
  })

  it('renders a static sample in editor mode with no data hooks firing', () => {
    renderWidget(node(), 'editor')
    expect(screen.getByTestId('page-node-video-visit')).toHaveTextContent(/Telehealth video visit/i)
    expect(useAppointmentsMock).not.toHaveBeenCalled()
  })

  it('shows the empty state at runtime when there are no upcoming visits', () => {
    useAppointmentsMock.mockReturnValue({ data: [], isLoading: false })
    renderWidget(node())
    expect(screen.getByTestId('page-node-video-visit')).toHaveTextContent(
      /no upcoming video visits/i
    )
  })

  it('renders the join card for the next confirmed visit and passes the grace prop', () => {
    useAppointmentsMock.mockReturnValue({
      data: [
        {
          id: 'appt-1',
          providerId: 'p',
          portalUserId: 'u',
          scheduledStart: '2999-01-01T15:00:00Z',
          scheduledEnd: '2999-01-01T15:30:00Z',
          status: 'CONFIRMED',
        },
      ],
      isLoading: false,
    })

    renderWidget(node({ joinGraceMinutes: '10' }))

    const card = screen.getByTestId('visit-join-card')
    expect(card).toHaveAttribute('data-appointment', 'appt-1')
    expect(card).toHaveAttribute('data-grace', '10')
  })
})
