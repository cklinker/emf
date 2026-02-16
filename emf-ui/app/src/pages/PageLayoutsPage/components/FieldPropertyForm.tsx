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
import styles from './PropertyPanel.module.css'

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
      <div className={styles.formGroup}>
        <span className={styles.formLabel}>Field Name</span>
        <div className={styles.fieldInfoReadOnly} data-testid="field-prop-name">
          {fieldPlacement.fieldName || fieldPlacement.fieldId}
        </div>
      </div>

      {/* Field Type (read-only) */}
      <div className={styles.formGroup}>
        <span className={styles.formLabel}>Field Type</span>
        <div className={styles.fieldInfoReadOnly} data-testid="field-prop-type">
          {fieldPlacement.fieldType || 'Unknown'}
        </div>
      </div>

      <hr className={styles.sectionDivider} />

      {/* Label Override */}
      <div className={styles.formGroup}>
        <label className={styles.formLabel} htmlFor={`field-label-${fieldPlacementId}`}>
          Label Override
        </label>
        <input
          id={`field-label-${fieldPlacementId}`}
          type="text"
          className={styles.formInput}
          value={fieldPlacement.labelOverride ?? ''}
          onChange={handleLabelOverrideChange}
          placeholder={fieldPlacement.fieldDisplayName || 'Custom label'}
          data-testid="field-prop-label-override"
        />
      </div>

      {/* Help Text Override */}
      <div className={styles.formGroup}>
        <label className={styles.formLabel} htmlFor={`field-help-${fieldPlacementId}`}>
          Help Text Override
        </label>
        <textarea
          id={`field-help-${fieldPlacementId}`}
          className={`${styles.formInput} ${styles.formTextarea}`}
          value={fieldPlacement.helpTextOverride ?? ''}
          onChange={handleHelpTextChange}
          placeholder="Help text for this field"
          rows={3}
          data-testid="field-prop-help-text"
        />
      </div>

      <hr className={styles.sectionDivider} />

      {/* Required on Layout */}
      <div className={styles.toggleGroup}>
        <span className={styles.toggleLabel}>Required on Layout</span>
        <button
          type="button"
          className={`${styles.toggle} ${fieldPlacement.requiredOnLayout ? styles.toggleActive : ''}`}
          onClick={handleRequiredToggle}
          role="switch"
          aria-checked={fieldPlacement.requiredOnLayout}
          data-testid="field-prop-required"
        >
          <span className={styles.toggleDot} />
        </button>
      </div>

      {/* Read-only on Layout */}
      <div className={styles.toggleGroup}>
        <span className={styles.toggleLabel}>Read-only on Layout</span>
        <button
          type="button"
          className={`${styles.toggle} ${fieldPlacement.readOnlyOnLayout ? styles.toggleActive : ''}`}
          onClick={handleReadOnlyToggle}
          role="switch"
          aria-checked={fieldPlacement.readOnlyOnLayout}
          data-testid="field-prop-readonly"
        >
          <span className={styles.toggleDot} />
        </button>
      </div>

      <hr className={styles.sectionDivider} />

      {/* Visibility Rule */}
      <div className={styles.formGroup}>
        <span className={styles.formLabel}>Visibility Rule</span>
        <VisibilityRuleEditor
          value={fieldPlacement.visibilityRule}
          onChange={handleVisibilityRuleChange}
          fields={state.availableFields}
        />
      </div>
    </div>
  )
}
