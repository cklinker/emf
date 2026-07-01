/**
 * InlineFieldValue — a single field's value that reads through the FieldControl registry and, when
 * editing is enabled, becomes click-to-edit in place (commit on Enter/blur, cancel on Escape). One
 * component powers both the admin (`/resources`) and end-user (`/app/o`) record detail bodies, so
 * inline editing is identical everywhere. See `.claude/docs/specs/unified-record/2-record-shell.md`.
 */
import React, { useState } from 'react'
import { Pencil } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import { getFieldControl } from '@/components/fieldControl'
import type { FieldControlContext } from '@/components/fieldControl'

/** Types whose inline editor needs options fetched into the schema field before it can edit. */
const NEEDS_OPTIONS = new Set([
  'picklist',
  'multi_picklist',
  'reference',
  'lookup',
  'master_detail',
])
/** Types we keep view-only inline (edit via the full form) even though the registry can edit them. */
const INLINE_EXCLUDED = new Set(['rich_text'])

export interface InlineFieldValueProps {
  field: FieldDefinition
  value: unknown
  displayLabel?: string
  tenantSlug?: string
  /** Page-level edit permission. When false, always renders the read view. */
  editable?: boolean
  /** Placement-level read-only override. */
  readOnly?: boolean
  /** Placement-level required override (defaults to the schema field's `required`). */
  required?: boolean
  /** Persist a committed value. Rejects → the error is surfaced and edit mode stays open. */
  onCommit?: (fieldName: string, value: unknown) => Promise<void>
  className?: string
}

function buildContext(props: InlineFieldValueProps): FieldControlContext {
  const { field, tenantSlug, displayLabel, readOnly, required } = props
  return {
    fieldName: field.name,
    displayName: field.displayName || field.name,
    tenantSlug,
    targetCollection: field.referenceTarget,
    displayLabel,
    enumValues: field.enumValues,
    referenceOptions: field.lookupOptions,
    readOnly,
    required: required ?? field.required,
  }
}

export function InlineFieldValue(props: InlineFieldValueProps): React.ReactElement {
  const { field, value, editable, readOnly, onCommit, className } = props
  const control = getFieldControl(field.type)
  const ctx = buildContext(props)

  const [isEditing, setIsEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const hasOptions = !NEEDS_OPTIONS.has(field.type)
    ? true
    : (field.enumValues?.length ?? 0) > 0 || (field.lookupOptions?.length ?? 0) > 0

  const canInlineEdit =
    !!editable &&
    !!onCommit &&
    control.editable &&
    !readOnly &&
    !INLINE_EXCLUDED.has(field.type) &&
    hasOptions

  const View = control.View
  const readView = <View type={field.type} value={value} ctx={ctx} truncate={false} />

  if (!canInlineEdit) {
    return <div className={className}>{readView}</div>
  }

  if (!isEditing) {
    return (
      <button
        type="button"
        className={cn(
          'group -mx-1.5 flex w-full items-center gap-1 rounded px-1.5 py-0.5 text-left',
          'hover:bg-primary/[0.04] dark:hover:bg-primary/[0.08]',
          className
        )}
        onClick={() => {
          setError(null)
          setIsEditing(true)
        }}
        aria-label={`Edit ${ctx.displayName}`}
        data-testid={`inline-field-${field.name}`}
      >
        <span className="min-w-0 flex-1">{readView}</span>
        <Pencil
          className="h-3 w-3 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100"
          aria-hidden="true"
        />
      </button>
    )
  }

  const InlineEdit = control.InlineEdit
  const commit = async (coerced: unknown): Promise<void> => {
    const validationError = control.validate(coerced, ctx)
    if (validationError) {
      setError(validationError)
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onCommit!(field.name, coerced)
      setIsEditing(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={cn('space-y-1', saving && 'opacity-60', className)}>
      <InlineEdit
        type={field.type}
        value={value}
        ctx={ctx}
        onChange={() => setError(null)}
        onCommit={(next) => void commit(next)}
        onCancel={() => {
          setError(null)
          setIsEditing(false)
        }}
      />
      {error && (
        <p
          className="text-xs text-destructive"
          role="alert"
          data-testid={`inline-field-error-${field.name}`}
        >
          {error}
        </p>
      )}
    </div>
  )
}

export default InlineFieldValue
