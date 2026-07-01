/**
 * FieldControl registry — the single source of truth for how a field type renders in view, edit,
 * and inline-edit modes. See `.claude/docs/specs/unified-record/1-field-control-registry.md`.
 *
 * Slice 1 hosts this in `kelta-ui/app`, co-located with the reused controls; Slice 2 promotes it to
 * `@kelta/components` when `RecordShell` needs it cross-package.
 */
export type {
  FieldControl,
  FieldControlContext,
  FieldViewProps,
  FieldEditProps,
  FieldInlineProps,
  PartialFieldControl,
} from './types'
export { getFieldControl, registerFieldControl, resetFieldControls } from './registry'
export { CONTROLS } from './controls'
export { FieldControlView } from './shared'
