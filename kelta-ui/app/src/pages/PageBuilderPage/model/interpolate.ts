/**
 * `{{…}}` merge-tag interpolation for literal string props (slice 2d). Lets authors mix literals and
 * bindings inline (`"Showing {{data.accounts[0].name}}"`) without flipping a whole prop to expr mode.
 *
 * A `{{ token }}` defaults to `mode:'path'`; a `{{= expr }}` token (leading `=`) is `mode:'expr'` and
 * runs through the formula engine. Both share {@link resolveBinding}. Non-string passthrough.
 */
import { resolveBinding } from './resolveBindings'
import type { BindingScope } from './bindingScope'
import type { Binding } from './pageModel'

const TEMPLATE_RE = /\{\{\s*([^}]+?)\s*\}\}/g

/**
 * Replace every `{{ expr }}` occurrence in a template string with its resolved value. Resolved values
 * are stringified (`null`/`undefined` → `''`). Plain strings (no `{{`) pass through unchanged.
 */
export function interpolate(template: string, scope: BindingScope): string {
  if (typeof template !== 'string' || !template.includes('{{')) return template
  return template.replace(TEMPLATE_RE, (_match, raw: string) => {
    const isExpr = raw.startsWith('=')
    const binding: Binding = {
      $bind: isExpr ? raw.slice(1).trim() : raw.trim(),
      mode: isExpr ? 'expr' : 'path',
    }
    const value = resolveBinding(binding, scope)
    return value == null ? '' : String(value)
  })
}

/** True when a value is a string containing at least one `{{…}}` token. */
export function isTemplate(value: unknown): value is string {
  return typeof value === 'string' && value.includes('{{')
}
