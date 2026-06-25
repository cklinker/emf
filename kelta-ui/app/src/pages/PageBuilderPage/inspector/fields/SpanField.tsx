/**
 * `kind:'span'` → four 1..12 numeric inputs for the responsive column span ({ base, sm?, md?, lg? }).
 * Inspector special-cases this kind to target `node.span` (NOT `node.props`). The CANVAS resize
 * handles that ALSO write `node.span` ship in 2c; this is the inspector-side numeric editor only.
 */
import React from 'react'
import { useI18n } from '../../../../context/I18nContext'
import type { ResponsiveSpan } from '../../model/pageModel'
import type { FieldEditorProps } from './types'

const BREAKPOINTS: Array<keyof ResponsiveSpan> = ['base', 'sm', 'md', 'lg']

export function SpanField({
  value,
  onChange,
  fieldId,
}: FieldEditorProps<ResponsiveSpan>): React.ReactElement {
  const { t } = useI18n()
  const span = (value ?? { base: 12 }) as ResponsiveSpan

  const patch = (bp: keyof ResponsiveSpan, raw: string) => {
    const n = raw === '' ? undefined : Math.min(12, Math.max(1, Number(raw)))
    const next: ResponsiveSpan = { ...span }
    if (bp === 'base') {
      next.base = n ?? 12
    } else if (n === undefined) {
      delete next[bp]
    } else {
      next[bp] = n
    }
    onChange(next)
  }

  return (
    <div className="grid grid-cols-4 gap-2" data-testid={fieldId}>
      {BREAKPOINTS.map((bp) => (
        <div key={bp} className="flex flex-col gap-1">
          <label
            className="text-[10px] font-medium uppercase text-muted-foreground"
            htmlFor={`${fieldId}-${bp}`}
          >
            {t(`builder.inspector.span.${bp}`)}
          </label>
          <input
            id={`${fieldId}-${bp}`}
            type="number"
            min={1}
            max={12}
            className="p-1 text-sm text-foreground bg-background border border-border rounded focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
            value={typeof span[bp] === 'number' ? span[bp] : ''}
            onChange={(e) => patch(bp, e.target.value)}
            data-testid={`${fieldId}-${bp}`}
          />
        </div>
      ))}
    </div>
  )
}
