/**
 * The `fx` literal↔expression toggle SHELL. Wraps a literal field editor: in literal mode it shows the
 * literal editor (TextField/NumberField/…); in expression mode it shows `ExpressionField`, which writes a
 * `Binding`. The toggle decision is driven solely by `isBinding(value)`.
 *
 * Write contract (load-bearing):
 *  - literal mode → scalar PropValue
 *  - expression mode → Binding `{ $bind, mode:'expr' }`
 *  - toggle literal→expr → `{ $bind:'', mode:'expr' }` (picker then fills $bind)
 *  - toggle expr→literal → `literalDefault` (= descriptor.defaultProps[key] ?? '')
 *
 * Binding RESOLUTION/evaluation is out of scope (2d); this only authors + persists the value.
 */
import React from 'react'
import { useI18n } from '../../../context/I18nContext'
import { cn } from '@/lib/utils'
import { ExpressionField } from './fields/ExpressionField'
import type { Binding, PageComponent, PropValue } from '../model/pageModel'
import { isBinding } from '../model/pageModel'
import type { PropFieldSchema } from '../widgets/types'

export interface BindableFieldProps {
  schema: PropFieldSchema
  value: PropValue | undefined
  onChange: (value: PropValue) => void
  node: PageComponent
  fieldId: string
  /** The literal editor to show in literal mode, already bound to value/onChange. */
  renderLiteral: (args: {
    value: PropValue | undefined
    onChange: (v: PropValue) => void
  }) => React.ReactNode
  /** Fallback literal written when switching expr → literal (descriptor.defaultProps[key] ?? ''). */
  literalDefault: PropValue
}

export function BindableField({
  schema,
  value,
  onChange,
  node,
  fieldId,
  renderLiteral,
  literalDefault,
}: BindableFieldProps): React.ReactElement {
  const { t } = useI18n()
  const isExpr = isBinding(value)

  const toggle = () => {
    if (isExpr) {
      onChange(literalDefault) // expr → literal: drop the binding
    } else {
      onChange({ $bind: '', mode: 'expr' } as Binding) // literal → expr: start an empty binding
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-muted-foreground">{schema.label}</span>
        <button
          type="button"
          className={cn(
            'inline-flex h-5 items-center rounded px-1 text-[10px] font-medium text-muted-foreground transition-colors hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2',
            isExpr && 'bg-primary/10 text-primary'
          )}
          onClick={toggle}
          aria-pressed={isExpr}
          aria-label={t('builder.inspector.bindable.toggle')}
          data-testid={`bindable-fx-${schema.key}`}
        >
          fx
        </button>
      </div>
      {isExpr ? (
        <ExpressionField
          schema={schema}
          value={value}
          onChange={onChange}
          node={node}
          fieldId={fieldId}
        />
      ) : (
        renderLiteral({ value, onChange })
      )}
    </div>
  )
}
