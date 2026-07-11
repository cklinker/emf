/**
 * `@kelta/components/video` — the LiveKit-backed video subpath entry.
 *
 * Deliberately SEPARATE from the main `src/index.ts` barrel: the base app
 * eagerly imports `@kelta/components` (DataTable etc.), and LiveKit +
 * `livekit-client` are heavy. Exposing `VideoRoom` here as its own Vite lib
 * entry lets the app `import('@kelta/components/video')` so LiveKit lands in a
 * dedicated lazy chunk instead of the base bundle.
 */
export { VideoRoom } from './VideoRoom/VideoRoom';
export type { VideoRoomProps, VideoRoomLabels } from './VideoRoom/types';
