/**
 * Field-renderer adapters for `ResourceForm` (slice 2f). Each adapts the `@kelta/components`
 * `FieldRendererProps` ({ value, field, onChange, readOnly }) to one of the rich controls the standalone
 * inputs use, so the `form` widget's picklist / lookup / multi-picklist / rich-text fields render the
 * same way. Keyed by UI `FieldType` and installed via `registerFormFieldRenderers.ts`.
 *
 * SECURITY: the rich-text renderer's READ-ONLY display goes through `FieldRenderer type="rich_text"`
 * (the same `stripHtml` sanitizer the `FieldRenderer` rich_text path uses) — never `dangerouslySetInnerHTML`.
 */
/* eslint-disable react-refresh/only-export-components -- field-renderer registry module, not an HMR component file */
import React from 'react'
import type { FieldDefinition as SdkFieldDefinition } from '@kelta/sdk'
import type { FieldRendererProps, FieldRendererComponent } from '@kelta/components'
import { LookupSelect } from '@/components/LookupSelect'
import { MultiPicklistSelect, normalizeMultiPicklistValue } from '@/components/MultiPicklistSelect'
import { RichTextEditor } from '@/components/RichTextEditor'
import { FieldRenderer } from '@/components/FieldRenderer/FieldRenderer'
import type { FieldDefinition as UiFieldDefinition } from '@/hooks/useCollectionSchema'
import { usePicklistOptions } from './inputs/usePicklistOptions'
import { useLookupOptions } from './inputs/useLookupOptions'

/** Bridge the SDK FieldDefinition (ResourceForm) to the UI FieldDefinition the lookup hook expects. */
function asUiFieldDef(field: SdkFieldDefinition): UiFieldDefinition {
  return {
    id: field.id ?? field.name,
    name: field.name,
    displayName: field.displayName,
    type: field.type as UiFieldDefinition['type'],
    required: !!field.required,
    referenceTarget: field.referenceTarget,
    referenceCollectionId: field.referenceCollectionId,
    fieldTypeConfig: field.fieldTypeConfig as UiFieldDefinition['fieldTypeConfig'],
  }
}

function DropdownRenderer({
  value,
  field,
  onChange,
  readOnly,
}: FieldRendererProps): React.ReactElement {
  const { options } = usePicklistOptions(
    { id: field.id ?? field.name, fieldTypeConfig: field.fieldTypeConfig as never },
    true
  )
  return (
    <select
      id={`field-${field.name}`}
      className="kelta-resource-form__input kelta-resource-form__input--picklist"
      value={typeof value === 'string' ? value : ''}
      disabled={readOnly}
      onChange={(e) => onChange?.(e.target.value)}
    >
      <option value="" />
      {options.map((opt) => (
        <option key={opt} value={opt}>
          {opt}
        </option>
      ))}
    </select>
  )
}

function MultiPicklistRenderer({
  value,
  field,
  onChange,
  readOnly,
}: FieldRendererProps): React.ReactElement {
  const { options } = usePicklistOptions(
    { id: field.id ?? field.name, fieldTypeConfig: field.fieldTypeConfig as never },
    true
  )
  return (
    <MultiPicklistSelect
      id={`field-${field.name}`}
      name={field.name}
      value={normalizeMultiPicklistValue(value)}
      options={options}
      onChange={(vals) => onChange?.(vals)}
      disabled={readOnly}
    />
  )
}

function LookupRenderer({
  value,
  field,
  onChange,
  readOnly,
}: FieldRendererProps): React.ReactElement {
  const { options } = useLookupOptions(asUiFieldDef(field), true)
  return (
    <LookupSelect
      id={`field-${field.name}`}
      name={field.name}
      value={typeof value === 'string' ? value : ''}
      options={options}
      onChange={(v) => onChange?.(v)}
      disabled={readOnly}
      data-testid={`field-${field.name}-lookup`}
    />
  )
}

function RichTextRenderer({
  value,
  field,
  onChange,
  readOnly,
}: FieldRendererProps): React.ReactElement {
  const html = typeof value === 'string' ? value : ''
  if (readOnly) {
    // SANITIZED display path (FieldRenderer strips tags) — never dangerouslySetInnerHTML.
    return (
      <div className="kelta-resource-form__input kelta-resource-form__input--rich_text">
        <FieldRenderer type="rich_text" value={html} truncate={false} />
      </div>
    )
  }
  return (
    <RichTextEditor
      value={html}
      onChange={(next) => onChange?.(next)}
      testId={`field-${field.name}-richtext`}
    />
  )
}

/** UI-FieldType → ResourceForm field renderer. */
export const FIELD_RENDERERS: Record<string, FieldRendererComponent> = {
  picklist: DropdownRenderer,
  multi_picklist: MultiPicklistRenderer,
  reference: LookupRenderer,
  lookup: LookupRenderer,
  master_detail: LookupRenderer,
  rich_text: RichTextRenderer,
}
