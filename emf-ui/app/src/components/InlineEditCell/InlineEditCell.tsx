/**
 * InlineEditCell Component
 *
 * Enables click-to-edit on individual cells in the resource list table.
 * Supports multiple field types with appropriate input controls.
 *
 * Features:
 * - Display mode with hover pencil icon
 * - Edit mode triggered by click (appropriate input per field type)
 * - Save on blur or Enter, cancel on Escape, tab saves current cell
 * - Visual feedback: loading (opacity), success (green flash), error (red border + tooltip)
 * - Boolean fields toggle immediately without explicit edit mode
 * - JSON fields are non-editable with tooltip hint
 */

import React, { useState, useRef, useEffect, useCallback } from 'react'
import { useMutation } from '@tanstack/react-query'
import type { ApiClient } from '../../services/apiClient'
import { cn } from '@/lib/utils'

/**
 * Supported field types for inline editing
 */
export type InlineEditFieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'
  | 'json'
  | 'reference'
  | 'phone'
  | 'email'
  | 'url'
  | 'external_id'
  | 'picklist'
  | 'currency'
  | 'percent'
  | 'encrypted'
  | 'auto_number'
  | 'rich_text'
  | 'geolocation'
  | 'multi_picklist'
  | 'lookup'
  | 'master_detail'
  | 'formula'
  | 'rollup_summary'

/**
 * Props for the InlineEditCell component
 */
export interface InlineEditCellProps {
  /** The raw field value */
  value: unknown
  /** The field name (API key) */
  fieldName: string
  /** The field type determining the input control */
  fieldType: InlineEditFieldType
  /** The record ID to patch */
  recordId: string
  /** The collection name for the API path */
  collectionName: string
  /** The formatted display string */
  displayValue: string
  /** Whether inline editing is enabled for this cell */
  enabled: boolean
  /** API client instance for making PATCH requests */
  apiClient: ApiClient
  /** Callback after a successful save (e.g., to invalidate queries) */
  onSaved?: () => void
}

/**
 * Convert an edited value to the appropriate type for the API
 */
function parseValueForApi(rawValue: string, fieldType: InlineEditFieldType): unknown {
  switch (fieldType) {
    case 'number': {
      const parsed = Number(rawValue)
      return isNaN(parsed) ? rawValue : parsed
    }
    case 'boolean':
      return rawValue === 'true'
    case 'date':
    case 'datetime':
      return rawValue || null
    default:
      return rawValue
  }
}

/**
 * Convert a raw value to a string suitable for the input element
 */
function valueToInputString(value: unknown, fieldType: InlineEditFieldType): string {
  if (value === null || value === undefined) {
    return ''
  }

  switch (fieldType) {
    case 'date':
      // Expect ISO date string, take just the date portion
      if (typeof value === 'string' && value.length >= 10) {
        return value.substring(0, 10)
      }
      return String(value)
    case 'datetime':
      // Expect ISO datetime, format for datetime-local input
      if (typeof value === 'string' && value.includes('T')) {
        // datetime-local expects "YYYY-MM-DDTHH:MM"
        return value.substring(0, 16)
      }
      return String(value)
    default:
      return String(value)
  }
}

/**
 * Get the HTML input type for a given field type
 */
function getInputType(fieldType: InlineEditFieldType): string {
  switch (fieldType) {
    case 'number':
    case 'currency':
    case 'percent':
      return 'number'
    case 'date':
      return 'date'
    case 'datetime':
      return 'datetime-local'
    case 'phone':
      return 'tel'
    case 'email':
      return 'email'
    case 'url':
      return 'url'
    default:
      return 'text'
  }
}

/**
 * Non-editable field types that should show a hint instead of entering edit mode
 */
const NON_EDITABLE_TYPES: Set<InlineEditFieldType> = new Set([
  'json',
  'encrypted',
  'auto_number',
  'rich_text',
  'geolocation',
  'multi_picklist',
  'lookup',
  'master_detail',
  'formula',
  'rollup_summary',
])

/**
 * InlineEditCell Component
 *
 * Renders a table cell that supports click-to-edit functionality.
 * In display mode, shows formatted text with a hover pencil icon.
 * In edit mode, shows an appropriate input control based on field type.
 */
export function InlineEditCell({
  value,
  fieldName,
  fieldType,
  recordId,
  collectionName,
  displayValue,
  enabled,
  apiClient,
  onSaved,
}: InlineEditCellProps): React.ReactElement {
  const [isEditing, setIsEditing] = useState(false)
  const [editValue, setEditValue] = useState('')
  const [showSuccess, setShowSuccess] = useState(false)
  const [showJsonHint, setShowJsonHint] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const successTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Patch mutation — send JSON:API formatted request
  const mutation = useMutation({
    mutationFn: async (newValue: unknown) => {
      return apiClient.patch(`/api/${collectionName}/${recordId}`, {
        data: {
          type: collectionName,
          id: recordId,
          attributes: {
            [fieldName]: newValue,
          },
        },
      })
    },
    onSuccess: () => {
      setIsEditing(false)
      setShowSuccess(true)

      // Clear previous timeout if any
      if (successTimeoutRef.current) {
        clearTimeout(successTimeoutRef.current)
      }

      // Remove success flash after 1 second
      successTimeoutRef.current = setTimeout(() => {
        setShowSuccess(false)
        successTimeoutRef.current = null
      }, 1000)

      onSaved?.()
    },
    onError: () => {
      // Stay in edit mode on error so user can retry or cancel
    },
  })

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (successTimeoutRef.current) {
        clearTimeout(successTimeoutRef.current)
      }
    }
  }, [])

  // Auto-focus input when entering edit mode
  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus()
      // Select all text for easy replacement
      if (inputRef.current.type === 'text' || inputRef.current.type === 'number') {
        inputRef.current.select()
      }
    }
  }, [isEditing])

  // Enter edit mode
  const handleCellClick = useCallback(() => {
    if (!enabled || mutation.isPending) return

    if (NON_EDITABLE_TYPES.has(fieldType)) {
      setShowJsonHint(true)
      setTimeout(() => setShowJsonHint(false), 2000)
      return
    }

    // Boolean fields toggle immediately
    if (fieldType === 'boolean') {
      const newValue = !value
      mutation.mutate(newValue)
      return
    }

    setEditValue(valueToInputString(value, fieldType))
    setIsEditing(true)
    mutation.reset()
  }, [enabled, fieldType, value, mutation])

  // Save the current value
  const handleSave = useCallback(() => {
    const parsed = parseValueForApi(editValue, fieldType)

    // Only save if the value actually changed
    const originalString = valueToInputString(value, fieldType)
    if (editValue === originalString) {
      setIsEditing(false)
      return
    }

    mutation.mutate(parsed)
  }, [editValue, fieldType, value, mutation])

  // Cancel editing
  const handleCancel = useCallback(() => {
    setIsEditing(false)
    mutation.reset()
  }, [mutation])

  // Handle key events
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') {
        e.preventDefault()
        handleSave()
      } else if (e.key === 'Escape') {
        e.preventDefault()
        handleCancel()
      }
      // Tab triggers blur which triggers save via handleBlur
    },
    [handleSave, handleCancel]
  )

  // Handle blur (save)
  const handleBlur = useCallback(() => {
    if (isEditing) {
      handleSave()
    }
  }, [isEditing, handleSave])

  // Handle input change
  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setEditValue(e.target.value)
  }, [])

  const isEditableType = enabled && !NON_EDITABLE_TYPES.has(fieldType)

  // Build cell class name
  const cellClassName = cn(
    'relative flex items-center min-h-[32px] px-2 py-1 border border-transparent rounded transition-all',
    isEditableType &&
      !isEditing &&
      'cursor-pointer hover:bg-primary/[0.04] dark:hover:bg-primary/[0.08]',
    isEditing && 'border-primary bg-background dark:bg-muted cursor-default',
    mutation.isPending && 'opacity-60 pointer-events-none',
    showSuccess && 'border-green-500',
    mutation.isError && !isEditing && 'border-destructive',
    NON_EDITABLE_TYPES.has(fieldType) && enabled && 'text-muted-foreground cursor-default',
    isEditableType && !isEditing && 'group'
  )

  // Render boolean field as checkbox (always visible, toggles on click)
  if (fieldType === 'boolean' && enabled) {
    return (
      <div className={cellClassName} data-testid={`inline-edit-cell-${fieldName}`}>
        <input
          type="checkbox"
          className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer m-0 disabled:cursor-not-allowed disabled:opacity-50"
          checked={!!value}
          onChange={handleCellClick}
          disabled={mutation.isPending}
          aria-label={`${fieldName}: ${value ? 'true' : 'false'}`}
          data-testid={`inline-edit-checkbox-${fieldName}`}
        />
        {mutation.isError && (
          <span
            className="absolute bottom-[calc(100%+4px)] left-0 z-10 max-w-[250px] px-2 py-1 text-xs leading-[1.4] text-white bg-destructive rounded whitespace-nowrap overflow-hidden text-ellipsis pointer-events-none shadow-md"
            role="alert"
          >
            {mutation.error?.message || 'Save failed'}
          </span>
        )}
      </div>
    )
  }

  // Render edit mode
  if (isEditing) {
    return (
      <div className={cellClassName} data-testid={`inline-edit-cell-${fieldName}`}>
        <input
          ref={inputRef}
          type={getInputType(fieldType)}
          className="w-full px-2 py-1 text-sm leading-relaxed text-foreground bg-background dark:bg-muted border-0 rounded outline-none"
          value={editValue}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onBlur={handleBlur}
          aria-label={`Edit ${fieldName}`}
          data-testid={`inline-edit-input-${fieldName}`}
        />
        {mutation.isError && (
          <span
            className="absolute bottom-[calc(100%+4px)] left-0 z-10 max-w-[250px] px-2 py-1 text-xs leading-[1.4] text-white bg-destructive rounded whitespace-nowrap overflow-hidden text-ellipsis pointer-events-none shadow-md"
            role="alert"
          >
            {mutation.error?.message || 'Save failed'}
          </span>
        )}
      </div>
    )
  }

  // Render display mode
  return (
    <div
      className={cellClassName}
      onClick={handleCellClick}
      role={isEditableType ? 'button' : undefined}
      tabIndex={isEditableType ? 0 : undefined}
      onKeyDown={
        isEditableType
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                handleCellClick()
              }
            }
          : undefined
      }
      aria-label={isEditableType ? `Edit ${fieldName}: ${displayValue}` : undefined}
      data-testid={`inline-edit-cell-${fieldName}`}
    >
      <span className="overflow-hidden text-ellipsis whitespace-nowrap flex-1 text-sm text-foreground leading-relaxed">
        {displayValue}
      </span>
      {isEditableType && (
        <span
          className="hidden items-center justify-center w-4 h-4 ml-1 shrink-0 text-muted-foreground text-xs leading-none group-hover:flex"
          aria-hidden="true"
        >
          &#9998;
        </span>
      )}
      {mutation.isError && (
        <span
          className="absolute bottom-[calc(100%+4px)] left-0 z-10 max-w-[250px] px-2 py-1 text-xs leading-[1.4] text-white bg-destructive rounded whitespace-nowrap overflow-hidden text-ellipsis pointer-events-none shadow-md"
          role="alert"
        >
          {mutation.error?.message || 'Save failed'}
        </span>
      )}
      {showJsonHint && (
        <span className="absolute bottom-[calc(100%+4px)] left-0 z-10 px-2 py-1 text-xs leading-[1.4] text-foreground bg-muted border border-border rounded whitespace-nowrap pointer-events-none shadow-sm">
          Edit in detail view
        </span>
      )}
    </div>
  )
}

export default InlineEditCell
