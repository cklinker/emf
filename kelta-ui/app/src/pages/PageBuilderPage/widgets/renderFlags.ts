/**
 * Rollout flag for the v2 registry-based render path.
 *
 * When true, `PageTreeRenderer` (runtime) and the builder preview render through the shared
 * `RenderTree`/widget registry. When false, the runtime falls back to the legacy per-type switch
 * renderer (kept in `PageTreeRenderer.tsx` until 2a has soaked). Centralizing the gate here lets the
 * runtime path be reverted without touching call sites; a later pass can wire it to a system feature.
 */
export const RENDER_TREE_V2 = true
