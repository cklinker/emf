import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Loader2, Video } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import { useAppointments } from '@/hooks/useScheduling'
import { VisitJoinCard } from './VisitJoinCard'
import { EncounterRecordCard } from './EncounterRecordCard'

/**
 * Patient/provider video-visit page (telehealth slice 6). Resolves
 * `:appointmentId` from the route, finds the appointment in the caller's list,
 * and renders the join card (window gate + countdown, waiting room, consent,
 * live VideoRoom). Mounted under EndUserShell → RealtimeProvider so presence
 * works. Recording enablement isn't surfaced in the bootstrap config, so the
 * route defaults recording OFF; the `video-visit` widget can turn it on per page.
 */
export function VisitPage({ testId = 'visit-page' }: { testId?: string }) {
  const { t } = useI18n()
  const { tenantSlug, appointmentId } = useParams<{ tenantSlug: string; appointmentId: string }>()
  const basePath = `/${tenantSlug}/app`

  // The caller's own appointments; find the one this route addresses.
  const appointments = useAppointments('mine')
  const appointment = useMemo(
    () => (appointments.data ?? []).find((a) => a.id === appointmentId) ?? null,
    [appointments.data, appointmentId]
  )

  if (appointments.isLoading) {
    return (
      <div
        className="flex min-h-[50vh] items-center justify-center"
        data-testid={`${testId}-loading`}
      >
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!appointment) {
    return (
      <div
        className="mx-auto flex max-w-[560px] flex-col items-center gap-3 p-10 text-center"
        data-testid={`${testId}-notfound`}
      >
        <Video size={24} className="text-muted-foreground" />
        <h1 className="m-0 text-lg font-semibold">
          {t('telehealth.visit.notFoundTitle', 'Visit not found')}
        </h1>
        <p className="text-sm text-muted-foreground">
          {t(
            'telehealth.visit.notFoundBody',
            'We couldn’t find this visit. It may have been cancelled or belongs to someone else.'
          )}
        </p>
        <Link
          to={`${basePath}/appointments`}
          className="rounded-md border border-border bg-card px-4 py-2 text-sm hover:bg-muted"
          data-testid={`${testId}-back`}
        >
          {t('telehealth.visit.backToAppointments', 'Back to appointments')}
        </Link>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 p-6" data-testid={testId}>
      <VisitJoinCard
        target={{ kind: 'appointment', id: appointment.id }}
        scheduledStart={appointment.scheduledStart}
        scheduledEnd={appointment.scheduledEnd}
        title={appointment.visitType || t('telehealth.visit.title', 'Video visit')}
        subtitle={appointment.reason || undefined}
      />
      <EncounterRecordCard appointmentId={appointment.id} />
    </div>
  )
}
