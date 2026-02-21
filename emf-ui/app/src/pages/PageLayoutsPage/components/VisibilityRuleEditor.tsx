/**
 * VisibilityRuleEditor Component
 *
 * Simple visibility rule editor for sections and fields.
 * Allows selecting a field, operator, and value to create
 * a basic visibility condition. Serializes to/from JSON.
 */

import React, { useMemo, useCallback } from 'react'
import type { AvailableField } from './LayoutEditorContext'

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
      <div className="flex flex-col gap-1">
        <select
          className="cursor-pointer appearance-none rounded-md border border-input bg-background bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[length:1.25em_1.25em] bg-[right_6px_center] bg-no-repeat px-2.5 py-1.5 pr-7 text-[13px] text-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
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
          <div className="mt-2 flex flex-col gap-1">
            <select
              className="cursor-pointer appearance-none rounded-md border border-input bg-background bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[length:1.25em_1.25em] bg-[right_6px_center] bg-no-repeat px-2.5 py-1.5 pr-7 text-[13px] text-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
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
            <div className="mt-2 flex flex-col gap-1">
              <input
                type="text"
                className="rounded-md border border-input bg-background px-2.5 py-1.5 text-[13px] text-foreground placeholder:text-muted-foreground transition-colors duration-150 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground motion-reduce:transition-none"
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
            className="mt-2 cursor-pointer rounded border border-input bg-transparent px-2 py-1 text-xs text-muted-foreground hover:bg-muted"
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
