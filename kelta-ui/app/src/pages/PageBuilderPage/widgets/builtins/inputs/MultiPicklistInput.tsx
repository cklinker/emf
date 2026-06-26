/**
 * `multi-picklist` control (slice 2f). Maps `multi_picklist` to `MultiPicklistSelect`, fed by
 * `usePicklistOptions`. The default value is normalized via `normalizeMultiPicklistValue` (array / JSON
 * string / PG literal / CSV). Seeded from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { MultiPicklistSelect, normalizeMultiPicklistValue } from '@/components/MultiPicklistSelect'
import { useI18n } from '@/context/I18nContext'
import { useFieldDef } from './useFieldDef'
import { usePicklistOptions } from './usePicklistOptions'
import { InputEmpty, InputField } from './InputShell'
import type { InputControlProps } from './types'

const TESTID = 'page-input-multipicklist'

export function MultiPicklistInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, placeholder, mode } = props
  const { t } = useI18n()
  const { fieldDef } = useFieldDef(collection, field)
  const { options } = usePicklistOptions(fieldDef, mode === 'runtime')
  const [value, setValue] = useState<string[]>(() => normalizeMultiPicklistValue(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor'
  const label = fieldDef?.displayName || field

  return (
    <InputField testid={TESTID} htmlFor={`page-input-${field}`} label={label} required={required}>
      <MultiPicklistSelect
        id={`page-input-${field}`}
        name={field}
        value={value}
        options={options}
        onChange={setValue}
        placeholder={placeholder || t('builder.input.selectPlaceholder')}
        required={required}
        disabled={disabled}
      />
    </InputField>
  )
}
