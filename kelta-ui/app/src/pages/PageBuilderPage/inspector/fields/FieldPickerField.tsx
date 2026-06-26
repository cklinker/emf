/**
 * `kind:'field-picker'` (dependsOnCollection) → multi-check of a sibling collection's fields. Reads the
 * sibling collection value at the parent dot-path's `.collection` (for key `dataView.fields` the
 * collection lives at `dataView.collection`) and lists checkboxes from `useCollectionSchema`. Writes a
 * `string[]` of field names. Renders a disabled hint when no collection is selected.
 */
import React from 'react'
import { useI18n } from '../../../../context/I18nContext'
import { useCollectionSchema } from '../../../../hooks/useCollectionSchema'
import { getByPath } from '../propPath'
import type { FieldEditorProps } from './types'

export function FieldPickerField({
  schema,
  value,
  onChange,
  node,
  fieldId,
}: FieldEditorProps<string[]>): React.ReactElement {
  const { t } = useI18n()
  // Sibling collection lives at the parent dot-path's `.collection`.
  const parent = schema.key.includes('.') ? schema.key.slice(0, schema.key.lastIndexOf('.')) : ''
  const collectionKey = parent ? `${parent}.collection` : 'collection'
  const collectionName = getByPath(node.props, collectionKey) as string | undefined
  const { fields, isLoading } = useCollectionSchema(collectionName)

  const selected = Array.isArray(value) ? value : []
  const toggle = (name: string) =>
    onChange(selected.includes(name) ? selected.filter((f) => f !== name) : [...selected, name])

  if (!collectionName) {
    return (
      <p className="text-xs text-muted-foreground" data-testid={`${fieldId}-empty`}>
        {t('builder.inspector.fieldPicker.empty')}
      </p>
    )
  }
  if (isLoading) {
    return <p className="text-xs text-muted-foreground">{t('common.loading')}</p>
  }
  return (
    <div data-testid={fieldId} className="flex flex-col gap-1">
      {fields.map((f) => (
        <label key={f.name} className="flex items-center gap-2 text-xs">
          <input
            type="checkbox"
            className="size-3.5 rounded border-border accent-primary"
            checked={selected.includes(f.name)}
            onChange={() => toggle(f.name)}
            data-testid={`${fieldId}-${f.name}`}
          />
          {f.displayName ?? f.name}
        </label>
      ))}
    </div>
  )
}
