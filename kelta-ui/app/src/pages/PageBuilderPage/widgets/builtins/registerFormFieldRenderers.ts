/**
 * Wire the rich typed controls into `@kelta/components`' `ResourceForm` via its public
 * `setComponentRegistry` seam (slice 2f). `ResourceForm`'s built-in `renderDefaultFieldInput` renders
 * picklist/lookup/multi-picklist/rich-text as plain text/`<input>`; this registry upgrades them to the
 * SAME controls the standalone inputs use (native `<select>` / `LookupSelect` / `MultiPicklistSelect` /
 * `RichTextEditor`) — reusing the existing pluggable registry rather than forking `ResourceForm`.
 *
 * Coexistence: if the app ever calls `@kelta/components`' `setComponentRegistry` elsewhere, this
 * delegates unknown field types to the prior registry (merge guard). Idempotent — guarded so repeated
 * page-builder bootstraps install once.
 */
import { setComponentRegistry, getComponentRegistry } from '@kelta/components'
import type { FieldRendererComponent } from '@kelta/components'
import { FIELD_RENDERERS } from './formFieldRenderers'

const HANDLED = new Set(Object.keys(FIELD_RENDERERS))

let installed = false

/** Idempotently install the page-builder field renderers into `ResourceForm`'s registry. */
export function registerFormFieldRenderers(): void {
  if (installed) return
  installed = true

  // Capture any registry that was already installed so we can delegate unknown types to it.
  const prior = getComponentRegistry()

  setComponentRegistry({
    hasFieldRenderer: (fieldType: string): boolean =>
      HANDLED.has(fieldType) || (prior?.hasFieldRenderer(fieldType) ?? false),
    getFieldRenderer: (fieldType: string): FieldRendererComponent | undefined =>
      FIELD_RENDERERS[fieldType] ?? prior?.getFieldRenderer(fieldType),
  })
}
