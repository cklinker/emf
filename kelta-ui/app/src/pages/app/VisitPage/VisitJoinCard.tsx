import { useEffect, useReducer, useState } from 'react'
import { CalendarClock, ShieldCheck, Users, Video } from 'lucide-react'
import { useI18n } from '@/context/I18nContext'
import { useMyIdentity } from '@/hooks/useMyIdentity'
import { usePresence } from '@/realtime/usePresence'
import {
  classifyVideoTokenError,
  useConsent,
  useVideoToken,
  type VideoToken,
  type VideoTokenErrorReason,
  type VideoTokenTarget,
} from '@/hooks/useVideo'
import {
  consentReducer,
  consentSatisfied,
  initialConsentState,
  isRecordedConsent,
} from './consentMachine'
import { canJoin, formatCountdown, msUntilOpen, windowState } from './visitWindow'
import { LazyVideoRoom } from './LazyVideoRoom'

/** Friendly, non-leaking copy for each token-refusal reason. */
function tokenErrorMessage(
  reason: VideoTokenErrorReason,
  t: (key: string, fallback: string) => string
): string {
  switch (reason) {
    case 'feature-off':
      return t('telehealth.visit.errorFeatureOff', 'Video visits are not enabled for this account.')
    case 'outside-window':
      return t(
        'telehealth.visit.errorOutsideWindow',
        'This visit isn’t open for video right now. Please join at the scheduled time.'
      )
    case 'budget-exhausted':
      return t(
        'telehealth.visit.errorBudget',
        'The video minutes for this account have run out. Please contact support.'
      )
    case 'not-participant':
      return t('telehealth.visit.errorNotParticipant', 'You’re not a participant on this visit.')
    default:
      return t(
        'telehealth.visit.errorGeneric',
        'We couldn’t start the video visit. Please try again.'
      )
  }
}

export interface VisitJoinCardProps {
  /** Whose visit — appointment (patient/provider) or conversation (staff chat escalation). */
  target: VideoTokenTarget
  /** Visit window edges (ISO). */
  scheduledStart: string
  scheduledEnd: string
  /** Grace minutes on both window edges (default 5). */
  joinGraceMinutes?: number
  /** Optional heading/subtitle for the upcoming-visit card. */
  title?: string
  subtitle?: string
  /** When true, show the recording-consent screen before joining. */
  recordingEnabled?: boolean
  /** Override the default consent body copy. */
  consentTextOverride?: string
  testId?: string
}

/**
 * The patient/provider visit surface (telehealth slice 6): an upcoming-visit
 * card with a window gate + countdown, a waiting room (presence), a recording
 * consent screen, then the live VideoRoom. Shared by the VisitPage route and the
 * `video-visit` page-builder widget. Presentational data flows through the
 * useVideo hooks; the token/url are never persisted.
 */
export function VisitJoinCard({
  target,
  scheduledStart,
  scheduledEnd,
  joinGraceMinutes = 5,
  title,
  subtitle,
  recordingEnabled = false,
  consentTextOverride,
  testId = 'visit-join',
}: VisitJoinCardProps) {
  const { t, formatDate } = useI18n()
  const { identity } = useMyIdentity()

  const win = {
    start: new Date(scheduledStart),
    end: new Date(scheduledEnd),
    graceMinutes: joinGraceMinutes,
  }

  // Re-tick every second so the gate opens and the countdown advances live.
  const [now, setNow] = useState<Date>(() => new Date())
  const [grantedToken, setGrantedToken] = useState<VideoToken | null>(null)
  const [consent, dispatchConsent] = useReducer(
    consentReducer,
    recordingEnabled,
    initialConsentState
  )

  const videoToken = useVideoToken()
  const consentMutation = useConsent()

  // Waiting room — other people present on this visit resource.
  const presence = usePresence(grantedToken ? null : `visit:${target.id}`)
  const othersWaiting = presence.filter((u) => u.id !== identity?.userId)

  useEffect(() => {
    if (grantedToken) return
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [grantedToken])

  const state = windowState(win, now)
  const joinable = canJoin(win, now)

  // Once we hold a token, render the live room.
  if (grantedToken) {
    return (
      <div data-testid={`${testId}-live`}>
        <LazyVideoRoom
          serverUrl={grantedToken.url}
          token={grantedToken.token}
          recordingActive={recordingEnabled && isRecordedConsent(consent)}
          onLeave={() => setGrantedToken(null)}
        />
      </div>
    )
  }

  const doJoin = () => {
    videoToken.mutate(target, {
      onSuccess: (grant) => {
        // Record the consent decision against the session before entering (best-effort;
        // decline still proceeds — unrecorded — per the default policy).
        if (recordingEnabled) {
          consentMutation.mutate({
            sessionId: grant.sessionId,
            accepted: isRecordedConsent(consent),
          })
        }
        setGrantedToken(grant)
      },
    })
  }

  // Consent gate: recording on and the patient hasn't decided yet.
  const consentPending = recordingEnabled && !consentSatisfied(consent)

  return (
    <div
      className="mx-auto flex max-w-[560px] flex-col gap-4 rounded-[10px] border border-border bg-card p-6"
      data-testid={testId}
    >
      <div className="flex items-center gap-3">
        <Video size={20} className="text-primary" />
        <div className="min-w-0">
          <h1 className="m-0 truncate text-lg font-semibold">
            {title ?? t('telehealth.visit.title', 'Video visit')}
          </h1>
          <p className="m-0 text-sm text-muted-foreground">
            {subtitle ?? formatDate(win.start, { dateStyle: 'full', timeStyle: 'short' })}
          </p>
        </div>
      </div>

      {/* Waiting room */}
      {othersWaiting.length > 0 && (
        <div
          className="flex items-center gap-2 rounded-md border border-border bg-muted/40 p-2.5 text-sm text-muted-foreground"
          data-testid={`${testId}-waiting`}
        >
          <Users size={16} />
          {t('telehealth.visit.othersWaiting', '{{count}} waiting in the room').replace(
            '{{count}}',
            String(othersWaiting.length)
          )}
        </div>
      )}

      {/* Consent screen */}
      {consentPending ? (
        <div
          className="flex flex-col gap-3 rounded-md border border-amber-300 bg-amber-50 p-4 dark:border-amber-900 dark:bg-amber-950"
          data-testid={`${testId}-consent`}
        >
          <div className="flex items-center gap-2 text-sm font-medium text-amber-900 dark:text-amber-200">
            <ShieldCheck size={16} />
            {t('telehealth.visit.consentTitle', 'This visit may be recorded')}
          </div>
          <p className="m-0 text-sm text-amber-900/90 dark:text-amber-200/90">
            {consentTextOverride ??
              t(
                'telehealth.visit.consentBody',
                'Your provider may record this visit for your medical record. You can decline and still join — your visit just won’t be recorded.'
              )}
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => dispatchConsent({ type: 'accept' })}
              className="cursor-pointer rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              data-testid={`${testId}-consent-accept`}
            >
              {t('telehealth.visit.consentAccept', 'Accept & continue')}
            </button>
            <button
              type="button"
              onClick={() => dispatchConsent({ type: 'decline' })}
              className="cursor-pointer rounded-md border border-border bg-card px-4 py-2 text-sm hover:bg-muted"
              data-testid={`${testId}-consent-decline`}
            >
              {t('telehealth.visit.consentDecline', 'Decline')}
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* Token-fetch error surface (403 / 409 / 429 → friendly copy). */}
          {videoToken.isError && (
            <div
              className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
              role="alert"
              data-testid={`${testId}-error`}
            >
              {tokenErrorMessage(classifyVideoTokenError(videoToken.error), t)}
            </div>
          )}

          {state === 'after' ? (
            <div
              className="rounded-md border border-border bg-muted/40 p-3 text-sm text-muted-foreground"
              data-testid={`${testId}-closed`}
            >
              {t('telehealth.visit.windowClosed', 'This visit has ended.')}
            </div>
          ) : joinable ? (
            <button
              type="button"
              onClick={doJoin}
              disabled={videoToken.isPending}
              className="flex items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              data-testid={`${testId}-join`}
            >
              <Video size={16} />
              {videoToken.isPending
                ? t('telehealth.visit.joining', 'Joining…')
                : t('telehealth.visit.join', 'Join')}
            </button>
          ) : (
            <div
              className="flex items-center gap-2 rounded-md border border-border bg-muted/40 p-3 text-sm text-muted-foreground"
              data-testid={`${testId}-countdown`}
            >
              <CalendarClock size={16} />
              {t('telehealth.visit.opensIn', 'Join opens in {{time}}').replace(
                '{{time}}',
                formatCountdown(msUntilOpen(win, now))
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
