/**
 * `kind:'expression'` editor (finalized in slice 2d). Renders a `{{token}}` chip + an Edit button that
 * opens `FieldExpressionPicker`, seeded with the `record` / `vars` / `page` / `data` namespace roots, and
 * stores the picked BARE token as a {@link Binding}. A picked field-path → `mode:'path'`; a picked
 * function stub (or a typed expression) → `mode:'expr'`. "Clear" reverts to a literal.
 *
 * `onInsert(token)` from the picker emits a bare token (dot-path or fn stub); the caller wraps `{{…}}`
 * for display only. Binding RESOLUTION is the render path's job (`resolveBindings`, slice 2d).
 */
import React, { useState } from 'react'
import { FieldExpressionPicker } from '../../../../components/FieldExpressionPicker'
import type { StaticNamespace } from '../../../../components/FieldExpressionPicker/types'
import { useI18n } from '../../../../context/I18nContext'
import type { Binding, PropValue } from '../../model/pageModel'
import { isBinding } from '../../model/pageModel'
import type { FieldEditorProps } from './types'

/**
 * The page-builder binding roots. `record` cascades into the bound collection via `rootCollectionId`
 * (null here until the page declares a record collection — a later slice wires it); `vars`/`page`/`data`
 * are static namespaces reflecting what the page already declares (built by `buildScopeNamespaces`).
 */
const DEFAULT_NAMESPACES: StaticNamespace[] = [
  { name: 'record', label: 'Record', fields: [] },
  { name: 'vars', label: 'Page variables', fields: [] },
  { name: 'page', label: 'Route / page', fields: [] },
  { name: 'data', label: 'Data sources', fields: [] },
]

/** A picker token is a function call (expr mode) when it contains a parenthesis, e.g. `IF(…)`. */
function isFunctionToken(token: string): boolean {
  return /\([^)]*\)?/.test(token) && /[A-Za-z_]+\s*\(/.test(token)
}

export interface ExpressionFieldProps extends FieldEditorProps {
  /** Collection the page's `record` is bound to, for cascading columns. null ⇒ namespaces only. */
  rootCollectionId?: string | null
  /** vars/page/data (and item inside a repeat) as static namespaces; falls back to the default roots. */
  namespaces?: StaticNamespace[]
}

export function ExpressionField({
  value,
  onChange,
  fieldId,
  rootCollectionId = null,
  namespaces = DEFAULT_NAMESPACES,
}: ExpressionFieldProps): React.ReactElement {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const binding: Binding | null = isBinding(value) ? value : null

  const writeBinding = (token: string) => {
    const mode: Binding['mode'] = isFunctionToken(token) ? 'expr' : 'path'
    onChange({ $bind: token, mode } as PropValue)
  }

  const clear = () => onChange('' as PropValue)

  return (
    <div className="flex items-center gap-2" data-testid={fieldId}>
      <code
        className="flex-1 truncate rounded bg-muted px-2 py-1 font-mono text-xs"
        data-testid={`bindable-expr-${fieldId}`}
      >
        {binding && binding.$bind ? `{{${binding.$bind}}}` : '—'}
      </code>
      <button
        type="button"
        className="inline-flex h-7 items-center rounded border border-border px-2 text-xs font-medium text-foreground transition-colors hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
        onClick={() => setOpen(true)}
        data-testid={`bindable-edit-${fieldId}`}
        aria-label={t('builder.binding.edit')}
      >
        {t('builder.binding.edit')}
      </button>
      {binding && binding.$bind && (
        <button
          type="button"
          className="inline-flex h-7 items-center rounded border border-border px-2 text-xs font-medium text-muted-foreground transition-colors hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
          onClick={clear}
          data-testid={`bindable-clear-${fieldId}`}
          aria-label={t('builder.binding.clear')}
        >
          {t('builder.binding.clear')}
        </button>
      )}
      <FieldExpressionPicker
        open={open}
        onOpenChange={setOpen}
        rootCollectionId={rootCollectionId}
        staticNamespaces={namespaces}
        mode="expression"
        onInsert={(token) => writeBinding(token)}
        title={t('builder.inspector.expression.pickerTitle')}
      />
    </div>
  )
}
