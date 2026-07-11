import React from 'react'
import { Loader2 } from 'lucide-react'
import type { VideoRoomProps } from '@kelta/components/video'
import { useI18n } from '@/context/I18nContext'

/**
 * Lazy boundary for the LiveKit-backed VideoRoom (telehealth slice 6).
 *
 * `@kelta/components/video` is a SEPARATE subpath entry from the eagerly-loaded
 * `@kelta/components` barrel, so `React.lazy(() => import(...))` here keeps the
 * whole LiveKit bundle in its own on-demand chunk — it never lands in the base
 * app bundle. Both the patient VisitPage and the staff hosts render through this
 * one wrapper so the i18n → VideoRoom label mapping lives in a single place.
 */
const VideoRoom = React.lazy(() =>
  import('@kelta/components/video').then((m) => ({ default: m.VideoRoom }))
)

export type LazyVideoRoomProps = Omit<VideoRoomProps, 'labels'>

export function LazyVideoRoom(props: LazyVideoRoomProps): React.ReactElement {
  const { t } = useI18n()
  const labels: VideoRoomProps['labels'] = {
    join: t('telehealth.visit.join', 'Join'),
    joining: t('telehealth.visit.joining', 'Joining…'),
    cameraLabel: t('telehealth.visit.camera', 'Camera'),
    micLabel: t('telehealth.visit.microphone', 'Microphone'),
    permissionsBlocked: t(
      'telehealth.visit.permissionsBlocked',
      'Your browser is blocking the camera or microphone.'
    ),
    permissionsHelp: t(
      'telehealth.visit.permissionsHelp',
      'Allow camera and microphone access in your browser’s site settings, then reload this page.'
    ),
    connecting: t('telehealth.visit.connecting', 'Connecting…'),
    recording: t('telehealth.visit.recording', 'Recording'),
    ended: t('telehealth.visit.callEnded', 'Call ended'),
    endedSummary: t('telehealth.visit.callEndedSummary', 'You have left the visit.'),
    rejoin: t('telehealth.visit.rejoin', 'Rejoin'),
    connectionError: t(
      'telehealth.visit.connectionError',
      'We could not connect to the visit. Please try again.'
    ),
  }

  return (
    <React.Suspense
      fallback={
        <div
          className="flex min-h-[420px] items-center justify-center rounded-lg border border-border bg-card"
          data-testid="video-room-loading"
        >
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      }
    >
      <VideoRoom {...props} labels={labels} />
    </React.Suspense>
  )
}
