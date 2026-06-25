/**
 * `kind:'expression'` → a first-class expression editor that ALWAYS writes a Binding
 * (`{ $bind, mode:'expr' }`). It is also the editor `BindableField` delegates to in expression mode.
 *
 * Consumed-from 2d marker: this field PRODUCES `$bind` tokens only; resolving/evaluating them is 2d.
 * `onInsert` from `FieldExpressionPicker` emits a BARE token (dot-path or fn stub) — the caller wraps
 * `{{…}}` for display only and stores the bare token as `binding.$bind`. In 2b the picker is fed the
 * static `record`/`vars`/`page` namespaces (no rootCollectionId until 2d wires a page data source).
 */
import React, { useState } from 'react'
import { FieldExpressionPicker } from '../../../../components/FieldExpressionPicker'
import { VariablePicker } from '../../../../components/VariablePicker/VariablePicker'
import type { StaticNamespace } from '../../../../components/FieldExpressionPicker/types'
import type { VariableNode } from '../../../../components/VariablePicker/VariablePicker'
import { useI18n } from '../../../../context/I18nContext'
import type { Binding, PropValue } from '../../model/pageModel'
import { isBinding } from '../../model/pageModel'
import type { FieldEditorProps } from './types'

/**
 * The page-builder binding roots available in 2b. 2d replaces these with the page's real variables +
 * on-load data sources (and a concrete `rootCollectionId` for cascading field discovery).
 */
const PAGE_STATIC_NAMESPACES: StaticNamespace[] = [
  { name: 'record', label: 'Record', fields: [] },
  { name: 'vars', label: 'Variables', fields: [] },
  { name: 'page', label: 'Page', fields: [] },
]

const PAGE_SCOPE_VARIABLES: VariableNode[] = [
  { path: 'record', label: 'Record' },
  { path: 'vars', label: 'Variables' },
  { path: 'page', label: 'Page' },
]

export function ExpressionField({
  value,
  onChange,
  fieldId,
}: FieldEditorProps): React.ReactElement {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const binding: Binding | null = isBinding(value) ? value : null

  const writeBinding = (token: string) => onChange({ $bind: token, mode: 'expr' } as PropValue)

  return (
    <div className="flex items-center gap-2" data-testid={fieldId}>
      <code
        className="flex-1 truncate rounded bg-muted px-2 py-1 font-mono text-xs"
        data-testid={`bindable-expr-${fieldId}`}
      >
        {binding && binding.$bind ? `{{${binding.$bind}}}` : '—'}
      </code>
      <VariablePicker
        variables={PAGE_SCOPE_VARIABLES}
        onPick={(token) => writeBinding(token)}
        raw
      />
      <button
        type="button"
        className="inline-flex h-7 items-center rounded border border-border px-2 text-xs font-medium text-foreground transition-colors hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2"
        onClick={() => setOpen(true)}
        data-testid={`bindable-edit-${fieldId}`}
        aria-label={t('builder.inspector.expression.edit')}
      >
        fx
      </button>
      <FieldExpressionPicker
        open={open}
        onOpenChange={setOpen}
        rootCollectionId={null}
        staticNamespaces={PAGE_STATIC_NAMESPACES}
        mode="expression"
        onInsert={(token) => writeBinding(token)}
        title={t('builder.inspector.expression.pickerTitle')}
      />
    </div>
  )
}
