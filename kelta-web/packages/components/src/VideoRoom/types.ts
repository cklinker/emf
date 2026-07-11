import type { ReactNode } from 'react';

/**
 * VideoRoom (telehealth slice 6) — presentational LiveKit room wrapper.
 *
 * The token and server URL are minted by the caller (the app fetches them from
 * `/api/telehealth/.../video-token`); this component never talks to the token
 * endpoint. It renders three states around `@livekit/components-react`:
 * pre-join (device pickers + preview + permissions help), in-room (participant
 * grid + control bar + connection quality), and an ended summary.
 *
 * Lives OUTSIDE the main `@kelta/components` barrel and ships as the `./video`
 * subpath entry so the LiveKit bundle stays a separate lazy chunk (the base app
 * eagerly imports `@kelta/components`).
 */
export interface VideoRoomProps {
  /** LiveKit server URL (`wss://…`) from the video-token response. */
  serverUrl: string;
  /** Room-scoped JWT from the video-token response. */
  token: string;
  /** Fired when the local participant leaves (control-bar leave or disconnect). */
  onLeave?: () => void;
  /** Fired on a connection/media error; the caller surfaces a friendly message. */
  onError?: (error: Error) => void;
  /** Shows an always-visible "recording" indicator while true. */
  recordingActive?: boolean;
  /**
   * Optional side-panel slot rendered next to the conference (e.g. staff chat).
   * Presentational only — the caller owns its content.
   */
  children?: ReactNode;
  /** Localized copy overrides — every user-facing string is injectable. */
  labels?: VideoRoomLabels;
  /** Test id root (defaults to `kelta-video-room`). */
  testId?: string;
}

/** User-facing strings, injected by the app's i18n layer. */
export interface VideoRoomLabels {
  join?: string;
  joining?: string;
  cameraLabel?: string;
  micLabel?: string;
  permissionsBlocked?: string;
  permissionsHelp?: string;
  connecting?: string;
  recording?: string;
  ended?: string;
  endedSummary?: string;
  rejoin?: string;
  connectionError?: string;
}
