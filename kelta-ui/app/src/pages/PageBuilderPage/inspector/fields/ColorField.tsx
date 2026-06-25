/** `kind:'color'` → native color picker paired with a hex text input. Writes the hex string. */
import React from 'react'
import type { FieldEditorProps } from './types'

export function ColorField({ value, onChange, fieldId }: FieldEditorProps): React.ReactElement {
  const hex = typeof value === 'string' ? value : ''
  return (
    <div className="flex items-center gap-2" data-testid={fieldId}>
      <input
        type="color"
        className="h-8 w-10 cursor-pointer rounded border border-border bg-background"
        value={/^#[0-9a-fA-F]{6}$/.test(hex) ? hex : '#000000'}
        onChange={(e) => onChange(e.target.value)}
        data-testid={`${fieldId}-picker`}
        aria-label="Color picker"
      />
      <input
        type="text"
        className="flex-1 p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
        value={hex}
        onChange={(e) => onChange(e.target.value)}
        placeholder="#000000"
        data-testid={`${fieldId}-hex`}
      />
    </div>
  )
}
