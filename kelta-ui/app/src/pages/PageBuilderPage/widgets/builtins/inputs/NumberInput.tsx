/**
 * `number-input` control (slice 2f). Maps `number`/`currency`/`percent` to a shadcn `Input type="number"`;
 * `currency` adds `step="0.01"`. Seeded from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { Input } from '@/components/ui/input'
import { useFieldDef } from './useFieldDef'
import { InputEmpty, InputField } from './InputShell'
import { defaultAsString, type InputControlProps } from './types'

const TESTID = 'page-input-number'

export function NumberInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, placeholder, mode } = props
  const { fieldDef } = useFieldDef(collection, field)
  const [value, setValue] = useState(() => defaultAsString(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor'
  const label = fieldDef?.displayName || field
  const step = fieldDef?.type === 'currency' ? '0.01' : undefined

  return (
    <InputField testid={TESTID} htmlFor={`page-input-${field}`} label={label} required={required}>
      <Input
        id={`page-input-${field}`}
        type="number"
        step={step}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => setValue(e.target.value)}
      />
    </InputField>
  )
}
