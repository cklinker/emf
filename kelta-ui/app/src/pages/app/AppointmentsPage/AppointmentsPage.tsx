import { CalendarClock } from 'lucide-react'
import { useI18n } from '../../../context/I18nContext'
import { useToast } from '../../../components/Toast'
import { useAppointmentActions, useAppointments } from '../../../hooks/useScheduling'
import { cn } from '@/lib/utils'

/**
 * Staff appointments list (telehealth slice 4): the provider's own schedule
 * over /api/telehealth/appointments?view=provider, with cancel / complete /
 * no-show actions. Deliberately minimal — the calendar view + richer visit UX
 * land with slice 6; this page makes today's schedule actionable now.
 */
export function AppointmentsPage({ testId = 'appointments-page' }: { testId?: string }) {
  const { t, formatDate } = useI18n()
  const { showToast } = useToast()
  const appointments = useAppointments('provider')
  const { cancel, complete } = useAppointmentActions()

  const statusStyles: Record<string, string> = {
    CONFIRMED: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
    REQUESTED: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
    CANCELLED: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300',
    COMPLETED: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-300',
    NO_SHOW: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  }

  const onError = (err: Error) => showToast(err.message, 'error')

  return (
    <div className="mx-auto max-w-[900px] p-6" data-testid={testId}>
      <header className="mb-6 flex items-center gap-3">
        <CalendarClock size={20} />
        <h1 className="m-0 text-2xl font-semibold">
          {t('scheduling.myScheduleTitle', 'My Appointments')}
        </h1>
      </header>

      <div className="overflow-hidden rounded-[10px] border border-border bg-card">
        {appointments.isLoading ? (
          <div className="p-8 text-center text-sm text-muted-foreground">
            {t('common.loading', 'Loading…')}
          </div>
        ) : (appointments.data ?? []).length === 0 ? (
          <div className="p-8 text-center text-sm text-muted-foreground">
            {t('scheduling.emptySchedule', 'No appointments yet')}
          </div>
        ) : (
          (appointments.data ?? []).map((appointment) => {
            const active = appointment.status === 'CONFIRMED' || appointment.status === 'REQUESTED'
            return (
              <div
                key={appointment.id}
                className="flex items-center justify-between gap-4 border-b border-border px-4 py-3 last:border-b-0"
                data-testid={`${testId}-row-${appointment.id}`}
              >
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium">
                    {formatDate(new Date(appointment.scheduledStart), {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    })}
                    {appointment.visitType ? ` · ${appointment.visitType}` : ''}
                  </div>
                  {appointment.reason && (
                    <div className="truncate text-xs text-muted-foreground">
                      {appointment.reason}
                    </div>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <span
                    className={cn(
                      'rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide',
                      statusStyles[appointment.status] ?? statusStyles.CANCELLED
                    )}
                  >
                    {appointment.status}
                  </span>
                  {active && (
                    <>
                      <button
                        className="cursor-pointer rounded-md border-none bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                        onClick={() => complete.mutate({ id: appointment.id }, { onError })}
                        disabled={complete.isPending}
                      >
                        {t('scheduling.complete', 'Complete')}
                      </button>
                      <button
                        className="cursor-pointer rounded-md border border-border bg-card px-2 py-1 text-xs hover:bg-muted disabled:opacity-50"
                        onClick={() =>
                          complete.mutate({ id: appointment.id, noShow: true }, { onError })
                        }
                        disabled={complete.isPending}
                      >
                        {t('scheduling.noShow', 'No-show')}
                      </button>
                      <button
                        className="cursor-pointer rounded-md border border-border bg-card px-2 py-1 text-xs hover:bg-muted disabled:opacity-50"
                        onClick={() => cancel.mutate(appointment.id, { onError })}
                        disabled={cancel.isPending}
                      >
                        {t('scheduling.cancel', 'Cancel')}
                      </button>
                    </>
                  )}
                </div>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
