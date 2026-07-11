/**
 * Appointment-scheduler widget tests (telehealth slice 4) — editor sample
 * without fetches, provider→slot→confirm flow, and the fixed-provider prop.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { appointmentSchedulerWidget } from './appointmentScheduler'
import type { RenderNode } from '../types'

const useProvidersMock = vi.fn()
const useSlotsMock = vi.fn()
const useAppointmentsMock = vi.fn()
const bookMutate = vi.fn()
const cancelMutate = vi.fn()

vi.mock('@/hooks/useScheduling', () => ({
  useProviders: (...args: unknown[]) => useProvidersMock(...args),
  useSlots: (...args: unknown[]) => useSlotsMock(...args),
  useAppointments: (...args: unknown[]) => useAppointmentsMock(...args),
  useBookAppointment: () => ({ mutate: bookMutate, isPending: false, isError: false }),
  useAppointmentActions: () => ({ cancel: { mutate: cancelMutate, isPending: false } }),
}))
vi.mock('@/context/I18nContext', () => ({
  useI18n: () => ({
    t: (_key: string, fallback: string) => fallback,
    formatDate: (d: Date) => d.toISOString(),
  }),
}))

const node = (props: Record<string, unknown> = {}): RenderNode => ({
  id: 's1',
  type: 'appointment-scheduler',
  props,
})

function renderScheduler(nodeArg: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  const Render = appointmentSchedulerWidget.Render
  return render(
    <Render node={nodeArg} scope={{}} mode={mode} tenantSlug="acme" renderChild={() => null} />
  )
}

describe('appointment-scheduler widget', () => {
  beforeEach(() => {
    useProvidersMock.mockReset().mockReturnValue({ data: [] })
    useSlotsMock.mockReset().mockReturnValue({ data: [], isLoading: false })
    useAppointmentsMock.mockReset().mockReturnValue({ data: [] })
    bookMutate.mockReset()
    cancelMutate.mockReset()
  })

  it('renders a static sample in editor mode with no data hooks firing', () => {
    renderScheduler(node({ visitType: 'Consult' }), 'editor')
    expect(screen.getByTestId('page-node-appointment-scheduler')).toHaveTextContent('Consult')
    expect(useProvidersMock).not.toHaveBeenCalled()
  })

  it('books the selected slot with the widget props', async () => {
    useProvidersMock.mockReturnValue({ data: [{ id: 'prov-1', name: 'Dr. K' }] })
    useSlotsMock.mockReturnValue({
      data: [{ start: '2026-07-13T15:00:00Z', end: '2026-07-13T15:30:00Z' }],
      isLoading: false,
    })

    renderScheduler(node({ visitType: 'Checkup', durationMinutes: '30' }))

    await userEvent.selectOptions(screen.getByTestId('appointment-scheduler-provider'), 'prov-1')
    await userEvent.click(screen.getByTestId('appointment-scheduler-slot-2026-07-13T15:00:00Z'))
    await userEvent.type(screen.getByTestId('appointment-scheduler-reason'), 'knee pain')
    await userEvent.click(screen.getByTestId('appointment-scheduler-confirm'))

    expect(bookMutate).toHaveBeenCalledWith(
      {
        providerId: 'prov-1',
        start: '2026-07-13T15:00:00Z',
        durationMinutes: 30,
        visitType: 'Checkup',
        reason: 'knee pain',
      },
      expect.anything()
    )
  })

  it('skips the provider select when providerId is fixed and lists cancellable upcoming visits', async () => {
    useAppointmentsMock.mockReturnValue({
      data: [
        {
          id: 'appt-1',
          providerId: 'prov-1',
          portalUserId: 'u1',
          scheduledStart: '2999-01-01T15:00:00Z',
          scheduledEnd: '2999-01-01T15:30:00Z',
          status: 'CONFIRMED',
        },
      ],
    })

    renderScheduler(node({ providerId: 'prov-1' }))

    expect(screen.queryByTestId('appointment-scheduler-provider')).not.toBeInTheDocument()
    expect(useProvidersMock).toHaveBeenCalledWith(false) // fixed provider → no fetch

    await userEvent.click(screen.getByTestId('appointment-scheduler-cancel-appt-1'))
    expect(cancelMutate).toHaveBeenCalledWith('appt-1')
  })
})
