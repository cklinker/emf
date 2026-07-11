import { useCallback, useState, type ReactElement } from 'react';
import {
  ConnectionQualityIndicator,
  ControlBar,
  GridLayout,
  LiveKitRoom,
  ParticipantTile,
  PreJoin,
  RoomAudioRenderer,
  useTracks,
  type LocalUserChoices,
} from '@livekit/components-react';
import { Track } from 'livekit-client';
import '@livekit/components-styles';
import type { VideoRoomLabels, VideoRoomProps } from './types';

const DEFAULT_LABELS: Required<VideoRoomLabels> = {
  join: 'Join',
  joining: 'Joining…',
  cameraLabel: 'Camera',
  micLabel: 'Microphone',
  permissionsBlocked: 'Your browser is blocking the camera or microphone.',
  permissionsHelp:
    'Allow camera and microphone access in your browser’s site settings, then reload this page.',
  connecting: 'Connecting…',
  recording: 'Recording',
  ended: 'Call ended',
  endedSummary: 'You have left the visit.',
  rejoin: 'Rejoin',
  connectionError: 'We could not connect to the visit. Please try again.',
};

type Phase = 'prejoin' | 'in-room' | 'ended';

/**
 * Conference surface rendered once connected — a participant grid over camera +
 * screen-share tracks, a control bar (mute / camera / screen-share / leave), a
 * per-tile connection-quality indicator, and the audio renderer. Must be a child
 * of `LiveKitRoom` so the LiveKit room context is available to the hooks.
 */
function Conference({ testId }: { testId: string }): ReactElement {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { onlySubscribed: false }
  );

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid={`${testId}-conference`}>
      <div className="min-h-0 flex-1">
        <GridLayout tracks={tracks} data-testid={`${testId}-grid`}>
          <ParticipantTile>
            <ConnectionQualityIndicator />
          </ParticipantTile>
        </GridLayout>
      </div>
      <RoomAudioRenderer />
      <ControlBar
        variation="verbose"
        controls={{ microphone: true, camera: true, screenShare: true, leave: true }}
        data-testid={`${testId}-controls`}
      />
    </div>
  );
}

/**
 * VideoRoom — presentational LiveKit wrapper (telehealth slice 6). See ./types.
 * Keeps a small phase state machine (prejoin → in-room → ended); the token and
 * server URL are supplied by the caller.
 */
export function VideoRoom({
  serverUrl,
  token,
  onLeave,
  onError,
  recordingActive = false,
  children,
  labels,
  testId = 'kelta-video-room',
}: VideoRoomProps): ReactElement {
  const l = { ...DEFAULT_LABELS, ...labels };
  const [phase, setPhase] = useState<Phase>('prejoin');
  const [choices, setChoices] = useState<LocalUserChoices | undefined>(undefined);
  const [permissionError, setPermissionError] = useState<Error | null>(null);

  const handlePreJoinError = useCallback(
    (error: Error) => {
      setPermissionError(error);
      onError?.(error);
    },
    [onError]
  );

  const handleSubmit = useCallback((values: LocalUserChoices) => {
    setPermissionError(null);
    setChoices(values);
    setPhase('in-room');
  }, []);

  const handleDisconnected = useCallback(() => {
    setPhase('ended');
    onLeave?.();
  }, [onLeave]);

  const handleRoomError = useCallback(
    (error: Error) => {
      onError?.(error);
    },
    [onError]
  );

  const recordingBadge = recordingActive ? (
    <div
      className="absolute right-3 top-3 z-10 flex items-center gap-1.5 rounded-full bg-red-600 px-2.5 py-1 text-xs font-semibold text-white shadow"
      role="status"
      data-testid={`${testId}-recording`}
    >
      <span className="h-2 w-2 animate-pulse rounded-full bg-white" aria-hidden="true" />
      {l.recording}
    </div>
  ) : null;

  if (phase === 'ended') {
    return (
      <div
        className="flex min-h-[420px] flex-col items-center justify-center gap-3 rounded-lg border border-border bg-card p-8 text-center"
        data-testid={`${testId}-ended`}
      >
        <h2 className="m-0 text-lg font-semibold">{l.ended}</h2>
        <p className="text-sm text-muted-foreground">{l.endedSummary}</p>
        <button
          type="button"
          onClick={() => {
            setChoices(undefined);
            setPhase('prejoin');
          }}
          className="cursor-pointer rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          data-testid={`${testId}-rejoin`}
        >
          {l.rejoin}
        </button>
      </div>
    );
  }

  if (phase === 'prejoin') {
    return (
      <div
        className="relative flex min-h-[420px] flex-col gap-3 rounded-lg border border-border bg-card p-4"
        data-testid={`${testId}-prejoin`}
      >
        {recordingBadge}
        {permissionError && (
          <div
            className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
            role="alert"
            data-testid={`${testId}-permissions`}
          >
            <p className="m-0 font-medium">{l.permissionsBlocked}</p>
            <p className="m-0 mt-1 text-xs">{l.permissionsHelp}</p>
          </div>
        )}
        <div className="min-h-0 flex-1" data-lk-theme="default">
          <PreJoin
            onSubmit={handleSubmit}
            onError={handlePreJoinError}
            joinLabel={l.join}
            micLabel={l.micLabel}
            camLabel={l.cameraLabel}
            persistUserChoices={false}
            data-testid={`${testId}-prejoin-inner`}
          />
        </div>
      </div>
    );
  }

  // phase === 'in-room'
  return (
    <div
      className="relative flex min-h-[420px] gap-3"
      data-lk-theme="default"
      data-testid={`${testId}-inroom`}
    >
      {recordingBadge}
      <div className="min-h-0 min-w-0 flex-1 overflow-hidden rounded-lg border border-border bg-black">
        <LiveKitRoom
          serverUrl={serverUrl}
          token={token}
          connect
          video={choices?.videoEnabled ?? true}
          audio={choices?.audioEnabled ?? true}
          onDisconnected={handleDisconnected}
          onError={handleRoomError}
          data-lk-theme="default"
          style={{ height: '100%' }}
        >
          <Conference testId={testId} />
        </LiveKitRoom>
      </div>
      {children != null && (
        <aside
          className="hidden w-[320px] shrink-0 overflow-hidden rounded-lg border border-border bg-card md:block"
          data-testid={`${testId}-panel`}
        >
          {children}
        </aside>
      )}
    </div>
  );
}
