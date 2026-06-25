/**
 * `lookup` control (slice 2f). Maps `reference`/`lookup`/`master_detail` to the searchable
 * `LookupSelect`, fed by `useLookupOptions` (reference resolution against the target collection).
 * Seeded from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { LookupSelect } from '@/components/LookupSelect'
import { useI18n } from '@/context/I18nContext'
import { useFieldDef } from './useFieldDef'
import { useLookupOptions } from './useLookupOptions'
import { InputEmpty, InputField } from './InputShell'
import { defaultAsString, type InputControlProps } from './types'

const TESTID = 'page-input-lookup'

export function LookupInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, placeholder, mode } = props
  const { t } = useI18n()
  const { fieldDef } = useFieldDef(collection, field)
  const { options } = useLookupOptions(fieldDef, mode === 'runtime')
  const [value, setValue] = useState(() => defaultAsString(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor'
  const label = fieldDef?.displayName || field

  return (
    <InputField testid={TESTID} htmlFor={`page-input-${field}`} label={label} required={required}>
      <LookupSelect
        id={`page-input-${field}`}
        name={field}
        value={value}
        options={options}
        onChange={setValue}
        placeholder={placeholder || t('builder.input.selectPlaceholder')}
        required={required}
        disabled={disabled}
        data-testid={`${TESTID}-select`}
      />
    </InputField>
  )
}
