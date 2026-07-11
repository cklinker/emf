/**
 * Appointment scheduler built-in (telehealth slice 4) — the portal booking
 * surface. Three steps against /api/telehealth: pick a provider (or use the
 * widget's fixed providerId prop), pick a slot from the next 14 days, confirm
 * with an optional reason. Below the wizard: the portal user's upcoming
 * appointments with cancel. Editor mode renders a static sample — no fetches.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React, { useMemo, useState } from 'react'
import { CalendarClock, Loader2 } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import {
  useAppointmentActions,
  useAppointments,
  useBookAppointment,
  useProviders,
  useSlots,
  type Slot,
} from '@/hooks/useScheduling'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'

const RANGE_DAYS = 14

function SchedulerLive({
  fixedProviderId,
  visitType,
  durationMinutes,
}: {
  fixedProviderId?: string
  visitType: string
  durationMinutes: number
}): React.ReactElement {
  const { t, formatDate } = useI18n()
  const providers = useProviders(!fixedProviderId)
  const [providerId, setProviderId] = useState<string | null>(fixedProviderId ?? null)
  const [selected, setSelected] = useState<Slot | null>(null)
  const [reason, setReason] = useState('')
  const [confirmation, setConfirmation] = useState<string | null>(null)

  const { fromIso, toIso } = useMemo(() => {
    const now = new Date()
    const to = new Date(now.getTime() + RANGE_DAYS * 24 * 3600 * 1000)
    return { fromIso: now.toISOString(), toIso: to.toISOString() }
  }, [])

  const slots = useSlots(providerId, fromIso, toIso, durationMinutes)
  const appointments = useAppointments('mine')
  const book = useBookAppointment()
  const { cancel } = useAppointmentActions()

  const slotsByDay = useMemo(() => {
    const groups = new Map<string, Slot[]>()
    for (const slot of slots.data ?? []) {
      const day = new Date(slot.start).toDateString()
      const list = groups.get(day) ?? []
      list.push(slot)
      groups.set(day, list)
    }
    return [...groups.entries()]
  }, [slots.data])

  const upcoming = (appointments.data ?? []).filter(
    (a) => a.status === 'CONFIRMED' && new Date(a.scheduledEnd) > new Date()
  )

  const handleBook = () => {
    if (!providerId || !selected) return
    book.mutate(
      {
        providerId,
        start: selected.start,
        durationMinutes,
        visitType: visitType || undefined,
        reason: reason || undefined,
      },
      {
        onSuccess: () => {
          setConfirmation(selected.start)
          setSelected(null)
          setReason('')
        },
      }
    )
  }

  return (
    <div className="flex flex-col gap-4 p-4" data-testid="page-node-appointment-scheduler-live">
      {confirmation && (
        <div className="rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
          {t('scheduling.bookedConfirmation', 'Booked! A confirmation email is on its way.')}
        </div>
      )}
      {book.isError && (
        <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {(book.error as Error)?.message ||
            t('scheduling.bookFailed', 'That slot is no longer available — pick another.')}
        </div>
      )}

      {!fixedProviderId && (
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">{t('scheduling.provider', 'Provider')}</span>
          <select
            value={providerId ?? ''}
            onChange={(e) => {
              setProviderId(e.target.value || null)
              setSelected(null)
            }}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            data-testid="appointment-scheduler-provider"
          >
            <option value="">{t('scheduling.selectProvider', 'Select a provider…')}</option>
            {(providers.data ?? []).map((provider) => (
              <option key={provider.id} value={provider.id}>
                {provider.name}
              </option>
            ))}
          </select>
        </label>
      )}

      {providerId &&
        (slots.isLoading ? (
          <div className="flex items-center justify-center p-6">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : slotsByDay.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('scheduling.noSlots', 'No open times in the next two weeks.')}
          </p>
        ) : (
          <div className="flex max-h-72 flex-col gap-3 overflow-y-auto">
            {slotsByDay.map(([day, daySlots]) => (
              <div key={day}>
                <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {formatDate(new Date(daySlots[0].start), { dateStyle: 'full' })}
                </div>
                <div className="flex flex-wrap gap-2">
                  {daySlots.map((slot) => (
                    <button
                      key={slot.start}
                      type="button"
                      onClick={() => setSelected(slot)}
                      className={`rounded-md border px-3 py-1.5 text-sm ${
                        selected?.start === slot.start
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-border bg-card hover:bg-muted'
                      }`}
                      data-testid={`appointment-scheduler-slot-${slot.start}`}
                    >
                      {formatDate(new Date(slot.start), { timeStyle: 'short' })}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ))}

      {selected && (
        <div className="flex flex-col gap-2 rounded-md border border-border bg-muted/30 p-3">
          <span className="text-sm font-medium">
            {formatDate(new Date(selected.start), { dateStyle: 'full', timeStyle: 'short' })}
          </span>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('scheduling.reasonPlaceholder', 'What is the visit about? (optional)')}
            rows={2}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            data-testid="appointment-scheduler-reason"
          />
          <button
            type="button"
            onClick={handleBook}
            disabled={book.isPending}
            className="self-start rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            data-testid="appointment-scheduler-confirm"
          >
            {book.isPending
              ? t('common.saving', 'Saving…')
              : t('scheduling.confirmBooking', 'Confirm appointment')}
          </button>
        </div>
      )}

      {upcoming.length > 0 && (
        <div className="border-t border-border pt-3">
          <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t('scheduling.upcoming', 'Your upcoming appointments')}
          </div>
          <ul className="flex flex-col gap-2">
            {upcoming.map((appointment) => (
              <li
                key={appointment.id}
                className="flex items-center justify-between gap-2 rounded-md border border-border p-2 text-sm"
              >
                <span>
                  {formatDate(new Date(appointment.scheduledStart), {
                    dateStyle: 'medium',
                    timeStyle: 'short',
                  })}
                  {appointment.visitType ? ` · ${appointment.visitType}` : ''}
                </span>
                <button
                  type="button"
                  onClick={() => cancel.mutate(appointment.id)}
                  disabled={cancel.isPending}
                  className="rounded-md border border-border bg-card px-2 py-1 text-xs hover:bg-muted disabled:opacity-50"
                  data-testid={`appointment-scheduler-cancel-${appointment.id}`}
                >
                  {t('scheduling.cancel', 'Cancel')}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function SchedulerRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  const props = node.props ?? {}
  const fixedProviderId = asString(props.providerId) || undefined
  const visitType = asString(props.visitType) || ''
  const parsedDuration = Number(asString(props.durationMinutes) || '30')
  const durationMinutes =
    Number.isFinite(parsedDuration) && parsedDuration > 0 ? parsedDuration : 30

  if (mode === 'editor') {
    return (
      <div
        className="flex min-h-[220px] flex-col gap-3 rounded-md border border-dashed border-border p-4"
        data-testid="page-node-appointment-scheduler"
      >
        <div className="flex items-center gap-2 text-sm font-medium">
          <CalendarClock size={16} /> {visitType || 'Telehealth visit'} · {durationMinutes} min
        </div>
        <div className="flex flex-wrap gap-2">
          {['09:00', '09:30', '10:00', '14:00'].map((label) => (
            <span
              key={label}
              className="rounded-md border border-border bg-card px-3 py-1.5 text-sm"
            >
              {label}
            </span>
          ))}
        </div>
        <span className="text-xs text-muted-foreground">Live slots + booking at runtime</span>
      </div>
    )
  }

  return (
    <div
      className="flex flex-col overflow-hidden rounded-md border border-border bg-card"
      data-testid="page-node-appointment-scheduler"
    >
      <SchedulerLive
        fixedProviderId={fixedProviderId}
        visitType={visitType}
        durationMinutes={durationMinutes}
      />
    </div>
  )
}

export const appointmentSchedulerWidget: WidgetDescriptor = {
  type: 'appointment-scheduler',
  label: 'Appointment Scheduler',
  icon: CalendarClock,
  category: 'data',
  acceptsChildren: false,
  defaultProps: { providerId: '', visitType: '', durationMinutes: '30' },
  propSchema: [
    { key: 'providerId', label: 'Fixed provider id (optional)', kind: 'text', group: 'data' },
    { key: 'visitType', label: 'Visit type', kind: 'text', bindable: true, group: 'content' },
    {
      key: 'durationMinutes',
      label: 'Duration (minutes)',
      kind: 'select',
      group: 'content',
      options: [
        { label: '15', value: '15' },
        { label: '30', value: '30' },
        { label: '45', value: '45' },
        { label: '60', value: '60' },
      ],
    },
  ],
  Render: SchedulerRender,
}
