/**
 * FieldPropertyForm Component
 *
 * Form for editing field placement properties within the property panel.
 * Shows read-only field info and editable properties like label override,
 * help text, required, read-only, and visibility rules.
 */

import React, { useMemo, useCallback } from 'react'
import { useLayoutEditor, type EditorFieldPlacement } from './LayoutEditorContext'
import { VisibilityRuleEditor } from './VisibilityRuleEditor'
import { cn } from '@/lib/utils'

export interface FieldPropertyFormProps {
  fieldPlacementId: string
}

export function FieldPropertyForm({
  fieldPlacementId,
}: FieldPropertyFormProps): React.ReactElement | null {
  const { state, updateFieldPlacement } = useLayoutEditor()

  const fieldPlacement = useMemo(() => {
    for (const section of state.sections) {
      const found = section.fields.find((f) => f.id === fieldPlacementId)
      if (found) return found
    }
    return null
  }, [state.sections, fieldPlacementId])

  // Find the section this field belongs to (needed for max column span)
  const sectionColumns = useMemo(() => {
    for (const section of state.sections) {
      if (section.fields.some((f) => f.id === fieldPlacementId)) {
        return section.columns
      }
    }
    return 1
  }, [state.sections, fieldPlacementId])

  const handleUpdate = useCallback(
    (updates: Partial<EditorFieldPlacement>) => {
      updateFieldPlacement(fieldPlacementId, updates)
    },
    [updateFieldPlacement, fieldPlacementId]
  )

  const handleLabelOverrideChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleUpdate({ labelOverride: e.target.value || undefined })
    },
    [handleUpdate]
  )

  const handleHelpTextChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      handleUpdate({ helpTextOverride: e.target.value || undefined })
    },
    [handleUpdate]
  )

  const handleRequiredToggle = useCallback(() => {
    if (fieldPlacement) {
      handleUpdate({ requiredOnLayout: !fieldPlacement.requiredOnLayout })
    }
  }, [handleUpdate, fieldPlacement])

  const handleReadOnlyToggle = useCallback(() => {
    if (fieldPlacement) {
      handleUpdate({ readOnlyOnLayout: !fieldPlacement.readOnlyOnLayout })
    }
  }, [handleUpdate, fieldPlacement])

  const handleColumnSpanChange = useCallback(
    (span: number) => {
      handleUpdate({ columnSpan: span })
    },
    [handleUpdate]
  )

  const handleVisibilityRuleChange = useCallback(
    (value: string | undefined) => {
      handleUpdate({ visibilityRule: value })
    },
    [handleUpdate]
  )

  if (!fieldPlacement) return null

  return (
    <div data-testid={`field-property-form-${fieldPlacementId}`}>
      {/* Field Name (read-only) */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Field Name
        </span>
        <div className="py-1.5 text-[13px] text-foreground" data-testid="field-prop-name">
          {fieldPlacement.fieldName || fieldPlacement.fieldId}
        </div>
      </div>

      {/* Field Type (read-only) */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Field Type
        </span>
        <div className="py-1.5 text-[13px] text-foreground" data-testid="field-prop-type">
          {fieldPlacement.fieldType || 'Unknown'}
        </div>
      </div>

      <hr className="my-1 border-t border-border" />

      {/* Label Override */}
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
          htmlFor={`field-label-${fieldPlacementId}`}
        >
          Label Override
        </label>
        <input
          id={`field-label-${fieldPlacementId}`}
          type="text"
          className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
          value={fieldPlacement.labelOverride ?? ''}
          onChange={handleLabelOverrideChange}
          placeholder={fieldPlacement.fieldDisplayName || 'Custom label'}
          data-testid="field-prop-label-override"
        />
      </div>

      {/* Help Text Override */}
      <div className="flex flex-col gap-1">
        <label
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
          htmlFor={`field-help-${fieldPlacementId}`}
        >
          Help Text Override
        </label>
        <textarea
          id={`field-help-${fieldPlacementId}`}
          className="min-h-[60px] resize-y rounded-md border border-input bg-background px-2.5 py-1.5 font-[inherit] text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
          value={fieldPlacement.helpTextOverride ?? ''}
          onChange={handleHelpTextChange}
          placeholder="Help text for this field"
          rows={3}
          data-testid="field-prop-help-text"
        />
      </div>

      {/* Column Span (only show for multi-column sections) */}
      {sectionColumns > 1 && (
        <>
          <hr className="my-1 border-t border-border" />
          <div className="flex flex-col gap-1">
            <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Column Span
            </span>
            <div
              className="flex overflow-hidden rounded-md border border-input"
              data-testid="field-prop-column-span"
            >
              {Array.from({ length: sectionColumns }, (_, i) => i + 1).map((span) => (
                <button
                  key={span}
                  type="button"
                  className={cn(
                    'flex-1 border-none border-r border-input bg-background py-1.5 text-center text-xs text-muted-foreground cursor-pointer transition-colors duration-150 last:border-r-0 motion-reduce:transition-none',
                    'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px]',
                    (fieldPlacement.columnSpan ?? 1) === span
                      ? 'bg-primary text-primary-foreground'
                      : 'hover:bg-muted'
                  )}
                  onClick={() => handleColumnSpanChange(span)}
                  aria-pressed={(fieldPlacement.columnSpan ?? 1) === span}
                  data-testid={`field-prop-span-${span}`}
                >
                  {span}
                </button>
              ))}
            </div>
          </div>
        </>
      )}

      <hr className="my-1 border-t border-border" />

      {/* Required on Layout */}
      <div className="flex items-center justify-between py-2">
        <span className="text-[13px] text-foreground">Required on Layout</span>
        <button
          type="button"
          className={cn(
            'relative h-5 w-9 cursor-pointer rounded-full border-none p-0 transition-colors duration-200 motion-reduce:transition-none',
            'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2',
            fieldPlacement.requiredOnLayout ? 'bg-primary' : 'bg-input'
          )}
          onClick={handleRequiredToggle}
          role="switch"
          aria-checked={fieldPlacement.requiredOnLayout}
          data-testid="field-prop-required"
        >
          <span
            className={cn(
              'absolute top-0.5 h-4 w-4 rounded-full bg-background transition-[left] duration-200 motion-reduce:transition-none',
              fieldPlacement.requiredOnLayout ? 'left-[18px]' : 'left-0.5'
            )}
          />
        </button>
      </div>

      {/* Read-only on Layout */}
      <div className="flex items-center justify-between py-2">
        <span className="text-[13px] text-foreground">Read-only on Layout</span>
        <button
          type="button"
          className={cn(
            'relative h-5 w-9 cursor-pointer rounded-full border-none p-0 transition-colors duration-200 motion-reduce:transition-none',
            'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2',
            fieldPlacement.readOnlyOnLayout ? 'bg-primary' : 'bg-input'
          )}
          onClick={handleReadOnlyToggle}
          role="switch"
          aria-checked={fieldPlacement.readOnlyOnLayout}
          data-testid="field-prop-readonly"
        >
          <span
            className={cn(
              'absolute top-0.5 h-4 w-4 rounded-full bg-background transition-[left] duration-200 motion-reduce:transition-none',
              fieldPlacement.readOnlyOnLayout ? 'left-[18px]' : 'left-0.5'
            )}
          />
        </button>
      </div>

      <hr className="my-1 border-t border-border" />

      {/* Visibility Rule */}
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Visibility Rule
        </span>
        <VisibilityRuleEditor
          value={fieldPlacement.visibilityRule}
          onChange={handleVisibilityRuleChange}
          fields={state.availableFields}
        />
      </div>
    </div>
  )
}
