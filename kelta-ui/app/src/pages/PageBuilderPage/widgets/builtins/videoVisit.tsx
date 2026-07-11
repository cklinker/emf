/**
 * Video visit built-in (telehealth slice 6) — the portal-facing video surface.
 * A tenant drops it on a page; the signed-in portal user sees their next
 * confirmed visit and can join inside the window (with a countdown when it's not
 * yet open, a waiting room, and an optional recording-consent screen). Reuses
 * the VisitPage `VisitJoinCard`; token/consent flow through /api/telehealth.
 *
 * Editor mode renders a static sample — no fetches, no token calls, no joins.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React, { useState } from 'react'
import { Loader2, Video } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import { useAppointments } from '@/hooks/useScheduling'
import { VisitJoinCard } from '@/pages/app/VisitPage/VisitJoinCard'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'

function VideoVisitLive({
  showUpcomingList,
  joinGraceMinutes,
  consentTextOverride,
}: {
  showUpcomingList: boolean
  joinGraceMinutes: number
  consentTextOverride?: string
}): React.ReactElement {
  const { t, formatDate } = useI18n()
  const appointments = useAppointments('mine')

  // Mount-time snapshot of "now" (lazy state initializer keeps render pure under
  // the react-hooks/purity rule). The list refetches on the scheduling poll, so a
  // coarse cutoff is fine for filtering out already-finished visits.
  const [mountNow] = useState(() => Date.now())
  const upcoming = (appointments.data ?? [])
    .filter((a) => a.status === 'CONFIRMED' && new Date(a.scheduledEnd).getTime() > mountNow)
    .sort((a, b) => new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime())

  if (appointments.isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  const next = upcoming[0] ?? null
  if (!next) {
    return (
      <div className="flex flex-col items-center gap-3 p-8 text-center">
        <Video size={22} className="text-muted-foreground" />
        <p className="text-sm text-muted-foreground">
          {t('telehealth.visit.noUpcoming', 'You have no upcoming video visits.')}
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 p-4" data-testid="page-node-video-visit-live">
      <VisitJoinCard
        target={{ kind: 'appointment', id: next.id }}
        scheduledStart={next.scheduledStart}
        scheduledEnd={next.scheduledEnd}
        joinGraceMinutes={joinGraceMinutes}
        title={next.visitType || t('telehealth.visit.title', 'Video visit')}
        recordingEnabled={consentTextOverride != null && consentTextOverride.length > 0}
        consentTextOverride={consentTextOverride}
      />

      {showUpcomingList && upcoming.length > 1 && (
        <div className="mx-auto w-full max-w-[560px] border-t border-border pt-3">
          <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t('telehealth.visit.laterVisits', 'Later visits')}
          </div>
          <ul className="flex flex-col gap-2">
            {upcoming.slice(1).map((appt) => (
              <li
                key={appt.id}
                className="rounded-md border border-border p-2 text-sm text-muted-foreground"
              >
                {formatDate(new Date(appt.scheduledStart), {
                  dateStyle: 'medium',
                  timeStyle: 'short',
                })}
                {appt.visitType ? ` · ${appt.visitType}` : ''}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function VideoVisitRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  const props = node.props ?? {}
  const showUpcomingList = props.showUpcomingList !== false
  const parsedGrace = Number(asString(props.joinGraceMinutes) || '5')
  const joinGraceMinutes = Number.isFinite(parsedGrace) && parsedGrace >= 0 ? parsedGrace : 5
  const consentTextOverride = asString(props.consentTextOverride) || undefined

  if (mode === 'editor') {
    return (
      <div
        className="flex min-h-[220px] flex-col gap-3 rounded-md border border-dashed border-border p-4"
        data-testid="page-node-video-visit"
      >
        <div className="flex items-center gap-2 text-sm font-medium">
          <Video size={16} /> Telehealth video visit
        </div>
        <div className="rounded-md border border-border bg-card p-3">
          <div className="text-sm font-medium">Upcoming visit · 10:00 AM</div>
          <div className="mt-2 inline-flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground">
            <Video size={12} /> Join
          </div>
        </div>
        <span className="text-xs text-muted-foreground">
          Live join, waiting room + consent at runtime
        </span>
      </div>
    )
  }

  return (
    <div
      className="flex flex-col overflow-hidden rounded-md border border-border bg-card"
      data-testid="page-node-video-visit"
    >
      <VideoVisitLive
        showUpcomingList={showUpcomingList}
        joinGraceMinutes={joinGraceMinutes}
        consentTextOverride={consentTextOverride}
      />
    </div>
  )
}

export const videoVisitWidget: WidgetDescriptor = {
  type: 'video-visit',
  label: 'Video Visit',
  icon: Video,
  category: 'data',
  acceptsChildren: false,
  defaultProps: { showUpcomingList: true, joinGraceMinutes: '5', consentTextOverride: '' },
  propSchema: [
    { key: 'showUpcomingList', label: 'Show later visits', kind: 'boolean', group: 'content' },
    {
      key: 'joinGraceMinutes',
      label: 'Join grace (minutes)',
      kind: 'select',
      group: 'content',
      options: [
        { label: '0', value: '0' },
        { label: '5', value: '5' },
        { label: '10', value: '10' },
        { label: '15', value: '15' },
      ],
    },
    {
      key: 'consentTextOverride',
      label: 'Recording consent text (enables consent screen)',
      kind: 'textarea',
      bindable: true,
      group: 'content',
    },
  ],
  Render: VideoVisitRender,
}
