/** `kind:'textarea'` → multiline text input. Writes a raw string scalar. */
import React from 'react'
import type { FieldEditorProps } from './types'

export function TextareaField({ value, onChange, fieldId }: FieldEditorProps): React.ReactElement {
  return (
    <textarea
      className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 resize-y min-h-[80px]"
      value={typeof value === 'string' ? value : ''}
      onChange={(e) => onChange(e.target.value)}
      rows={4}
      data-testid={fieldId}
    />
  )
}
