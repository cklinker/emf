/**
 * VisibilityRuleEditor Component
 *
 * Simple visibility rule editor for sections and fields.
 * Allows selecting a field, operator, and value to create
 * a basic visibility condition. Serializes to/from JSON.
 */

import React, { useMemo, useCallback } from 'react'
import type { AvailableField } from './LayoutEditorContext'
import styles from './PropertyPanel.module.css'

export interface VisibilityRuleEditorProps {
  value: string | undefined
  onChange: (value: string | undefined) => void
  fields: AvailableField[]
}

interface VisibilityRule {
  fieldId: string
  operator: string
  value?: string
}

const OPERATORS = [
  { value: 'EQUALS', label: 'Equals' },
  { value: 'NOT_EQUALS', label: 'Not Equals' },
  { value: 'CONTAINS', label: 'Contains' },
  { value: 'IS_EMPTY', label: 'Is Empty' },
  { value: 'IS_NOT_EMPTY', label: 'Is Not Empty' },
] as const

const VALUE_HIDDEN_OPERATORS = new Set(['IS_EMPTY', 'IS_NOT_EMPTY'])

function parseRule(jsonString: string | undefined): VisibilityRule | null {
  if (!jsonString) return null
  try {
    const parsed = JSON.parse(jsonString) as VisibilityRule
    if (parsed.fieldId && parsed.operator) {
      return parsed
    }
    return null
  } catch {
    return null
  }
}

function serializeRule(rule: VisibilityRule | null): string | undefined {
  if (!rule || !rule.fieldId || !rule.operator) return undefined
  const serialized: VisibilityRule = {
    fieldId: rule.fieldId,
    operator: rule.operator,
  }
  if (!VALUE_HIDDEN_OPERATORS.has(rule.operator) && rule.value) {
    serialized.value = rule.value
  }
  return JSON.stringify(serialized)
}

export function VisibilityRuleEditor({
  value,
  onChange,
  fields,
}: VisibilityRuleEditorProps): React.ReactElement {
  const rule = useMemo(() => parseRule(value), [value])

  const handleFieldChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      const fieldId = e.target.value
      if (!fieldId) {
        onChange(undefined)
        return
      }
      const updated: VisibilityRule = {
        fieldId,
        operator: rule?.operator || 'EQUALS',
        value: rule?.value,
      }
      onChange(serializeRule(updated))
    },
    [rule, onChange]
  )

  const handleOperatorChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      if (!rule) return
      const updated: VisibilityRule = {
        ...rule,
        operator: e.target.value,
      }
      // Clear value if operator doesn't need one
      if (VALUE_HIDDEN_OPERATORS.has(updated.operator)) {
        delete updated.value
      }
      onChange(serializeRule(updated))
    },
    [rule, onChange]
  )

  const handleValueChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (!rule) return
      const updated: VisibilityRule = {
        ...rule,
        value: e.target.value,
      }
      onChange(serializeRule(updated))
    },
    [rule, onChange]
  )

  const handleClear = useCallback(() => {
    onChange(undefined)
  }, [onChange])

  const showValueInput = rule && !VALUE_HIDDEN_OPERATORS.has(rule.operator)

  return (
    <div data-testid="visibility-rule-editor">
      {/* Field Selector */}
      <div className={styles.formGroup}>
        <select
          className={styles.formSelect}
          value={rule?.fieldId ?? ''}
          onChange={handleFieldChange}
          aria-label="Visibility rule field"
          data-testid="visibility-rule-field"
        >
          <option value="">No visibility rule</option>
          {fields.map((f) => (
            <option key={f.id} value={f.id}>
              {f.displayName}
            </option>
          ))}
        </select>
      </div>

      {rule?.fieldId && (
        <>
          {/* Operator Selector */}
          <div className={styles.formGroup} style={{ marginTop: '8px' }}>
            <select
              className={styles.formSelect}
              value={rule.operator}
              onChange={handleOperatorChange}
              aria-label="Visibility rule operator"
              data-testid="visibility-rule-operator"
            >
              {OPERATORS.map((op) => (
                <option key={op.value} value={op.value}>
                  {op.label}
                </option>
              ))}
            </select>
          </div>

          {/* Value Input */}
          {showValueInput && (
            <div className={styles.formGroup} style={{ marginTop: '8px' }}>
              <input
                type="text"
                className={styles.formInput}
                value={rule.value ?? ''}
                onChange={handleValueChange}
                placeholder="Value"
                aria-label="Visibility rule value"
                data-testid="visibility-rule-value"
              />
            </div>
          )}

          {/* Clear Button */}
          <button
            type="button"
            style={{
              marginTop: '8px',
              padding: '4px 8px',
              fontSize: '12px',
              color: 'var(--color-text-secondary, #6b7280)',
              backgroundColor: 'transparent',
              border: '1px solid var(--color-border-input, #d1d5db)',
              borderRadius: '4px',
              cursor: 'pointer',
            }}
            onClick={handleClear}
            data-testid="visibility-rule-clear"
          >
            Clear Rule
          </button>
        </>
      )}
    </div>
  )
}
