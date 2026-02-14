/**
 * FieldEditor Component
 *
 * Form for creating and editing field definitions within a collection.
 * Uses React Hook Form with Zod validation schema.
 *
 * Requirements:
 * - 4.2: Display form for entering field details
 * - 4.3: Support all field types: string, number, boolean, date, datetime, json, reference
 * - 4.4: Display dropdown to select target collection for reference fields
 * - 4.5: Add field via API and update field list
 * - 4.6: Display validation errors inline with form fields
 * - 4.7: Pre-populate form with current values in edit mode
 * - 4.11: Allow setting validation rules (required, min, max, pattern, email, url)
 *
 * Features:
 * - Support for all field types
 * - Reference field with collection dropdown
 * - Validation rules configuration section
 * - Create and edit modes
 * - Pre-population in edit mode
 * - Disabled name and type fields in edit mode (immutable)
 * - Loading state during submission
 * - Inline validation errors
 * - Accessible with keyboard navigation and ARIA attributes
 */

import React, { useEffect, useCallback, useMemo } from 'react'
import { useForm, useFieldArray, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'
import styles from './FieldEditor.module.css'

/**
 * Field type enumeration
 */
export type FieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'
  | 'json'
  | 'reference'
  | 'picklist'
  | 'multi_picklist'
  | 'currency'
  | 'percent'
  | 'auto_number'
  | 'phone'
  | 'email'
  | 'url'
  | 'rich_text'
  | 'encrypted'
  | 'external_id'
  | 'geolocation'
  | 'lookup'
  | 'master_detail'
  | 'formula'
  | 'rollup_summary'

/**
 * Validation rule type enumeration
 */
export type ValidationRuleType = 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom'

/**
 * Validation rule interface
 */
export interface ValidationRule {
  type: ValidationRuleType
  value?: unknown
  message?: string
}

/**
 * Field definition interface
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
  type: FieldType
  required: boolean
  unique: boolean
  indexed: boolean
  defaultValue?: unknown
  validation?: ValidationRule[]
  referenceTarget?: string
  fieldTypeConfig?: string
  order: number
}

/**
 * Collection summary for reference field dropdown
 */
export interface CollectionSummary {
  id: string
  name: string
  displayName: string
}

/**
 * Global picklist summary for picklist field dropdown
 */
export interface PicklistSummary {
  id: string
  name: string
}

/**
 * Props for the FieldEditor component
 */
export interface FieldEditorProps {
  /** Collection ID for context */
  collectionId: string
  /** Existing field for edit mode */
  field?: FieldDefinition
  /** Available collections for reference field dropdown */
  collections?: CollectionSummary[]
  /** Available global picklists for picklist field dropdown */
  picklists?: PicklistSummary[]
  /** Callback when form is submitted */
  onSave: (field: FieldDefinition) => Promise<void>
  /** Callback when form is cancelled */
  onCancel: () => void
  /** Whether the form is submitting */
  isSubmitting?: boolean
  /** Test ID */
  testId?: string
}

/**
 * All available field types
 */
// eslint-disable-next-line react-refresh/only-export-components
export const FIELD_TYPES: FieldType[] = [
  'string',
  'number',
  'boolean',
  'date',
  'datetime',
  'json',
  'reference',
  'picklist',
  'multi_picklist',
  'currency',
  'percent',
  'auto_number',
  'phone',
  'email',
  'url',
  'rich_text',
  'encrypted',
  'external_id',
  'geolocation',
  'lookup',
  'master_detail',
  'formula',
  'rollup_summary',
]

/**
 * Validation rule types available for each field type
 */
// eslint-disable-next-line react-refresh/only-export-components
export const VALIDATION_RULES_BY_TYPE: Record<FieldType, ValidationRuleType[]> = {
  string: ['min', 'max', 'pattern', 'email', 'url'],
  number: ['min', 'max'],
  boolean: [],
  date: ['min', 'max'],
  datetime: ['min', 'max'],
  json: [],
  reference: [],
  picklist: [],
  multi_picklist: [],
  currency: ['min', 'max'],
  percent: ['min', 'max'],
  auto_number: [],
  phone: ['pattern'],
  email: ['pattern'],
  url: ['pattern'],
  rich_text: ['min', 'max'],
  encrypted: [],
  external_id: ['pattern'],
  geolocation: [],
  lookup: [],
  master_detail: [],
  formula: [],
  rollup_summary: [],
}

/**
 * Form validation rule schema
 */
const validationRuleSchema = z.object({
  type: z.enum(['min', 'max', 'pattern', 'email', 'url', 'custom']),
  value: z.union([z.string(), z.number(), z.null()]).optional(),
  message: z.string().optional(),
})

/**
 * Zod validation schema for field form
 */
// eslint-disable-next-line react-refresh/only-export-components
export const fieldEditorSchema = z
  .object({
    name: z
      .string()
      .min(1, 'validation.nameRequired')
      .max(50, 'validation.nameTooLong')
      .regex(/^[a-z][a-z0-9_]*$/, 'validation.nameFormat'),
    displayName: z.string().max(100, 'validation.displayNameTooLong').optional().or(z.literal('')),
    type: z.enum(
      [
        'string',
        'number',
        'boolean',
        'date',
        'datetime',
        'json',
        'reference',
        'picklist',
        'multi_picklist',
        'currency',
        'percent',
        'auto_number',
        'phone',
        'email',
        'url',
        'rich_text',
        'encrypted',
        'external_id',
        'geolocation',
        'lookup',
        'master_detail',
        'formula',
        'rollup_summary',
      ],
      {
        message: 'validation.typeRequired',
      }
    ),
    required: z.boolean(),
    unique: z.boolean(),
    indexed: z.boolean(),
    defaultValue: z.string().optional().or(z.literal('')),
    referenceTarget: z.string().optional().or(z.literal('')),
    autoNumberPrefix: z.string().optional().or(z.literal('')),
    autoNumberPadding: z.coerce.number().min(1).max(10).optional(),
    currencyCode: z.string().max(3).optional().or(z.literal('')),
    currencyPrecision: z.coerce.number().min(0).max(6).optional(),
    globalPicklistId: z.string().optional().or(z.literal('')),
    validationRules: z.array(validationRuleSchema).optional(),
  })
  .refine(
    (data) => {
      // Reference, lookup, and master_detail types require referenceTarget
      if (
        (data.type === 'reference' || data.type === 'lookup' || data.type === 'master_detail') &&
        !data.referenceTarget
      ) {
        return false
      }
      return true
    },
    {
      message: 'validation.referenceTargetRequired',
      path: ['referenceTarget'],
    }
  )
  .refine(
    (data) => {
      // Picklist and multi_picklist types require globalPicklistId
      if ((data.type === 'picklist' || data.type === 'multi_picklist') && !data.globalPicklistId) {
        return false
      }
      return true
    },
    {
      message: 'validation.picklistRequired',
      path: ['globalPicklistId'],
    }
  )

/**
 * Type inferred from the Zod schema
 */
export type FieldEditorFormData = z.infer<typeof fieldEditorSchema>

/**
 * Generate a unique ID for new fields
 */
function generateFieldId(): string {
  return `field_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
}

/**
 * FieldEditor Component
 *
 * Provides a form for creating and editing field definitions with:
 * - Name validation (alphanumeric, underscores, lowercase)
 * - Display name (optional)
 * - Field type selection
 * - Reference target selection for reference fields
 * - Required, unique, indexed flags
 * - Default value
 * - Validation rules configuration
 * - Inline validation errors
 * - Loading state during submission
 */
export function FieldEditor({
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  collectionId,
  field,
  collections = [],
  picklists = [],
  onSave,
  onCancel,
  isSubmitting = false,
  testId = 'field-editor',
}: FieldEditorProps): React.ReactElement {
  const { t } = useI18n()
  const isEditMode = !!field

  // Parse existing fieldTypeConfig for default values
  const parsedConfig = useMemo(() => {
    if (field?.fieldTypeConfig) {
      try {
        return JSON.parse(field.fieldTypeConfig) as Record<string, unknown>
      } catch {
        return {}
      }
    }
    return {}
  }, [field?.fieldTypeConfig])

  // Initialize form with React Hook Form and Zod resolver
  const {
    register,
    handleSubmit,
    reset,
    watch,
    control,
    formState: { errors, isDirty },
  } = useForm<FieldEditorFormData>({
    resolver: zodResolver(fieldEditorSchema),
    defaultValues: {
      name: field?.name ?? '',
      displayName: field?.displayName ?? '',
      type: field?.type ?? 'string',
      required: field?.required ?? false,
      unique: field?.unique ?? false,
      indexed: field?.indexed ?? false,
      defaultValue: field?.defaultValue !== undefined ? String(field.defaultValue) : '',
      referenceTarget: field?.referenceTarget ?? '',
      autoNumberPrefix: (parsedConfig.prefix as string) ?? '',
      autoNumberPadding: (parsedConfig.padding as number) ?? 4,
      currencyCode: (parsedConfig.currencyCode as string) ?? '',
      currencyPrecision: (parsedConfig.precision as number) ?? 2,
      globalPicklistId: (parsedConfig.globalPicklistId as string) ?? '',
      validationRules:
        field?.validation?.map((v) => ({
          type: v.type,
          value:
            v.value !== undefined
              ? typeof v.value === 'number'
                ? v.value
                : String(v.value)
              : undefined,
          message: v.message,
        })) ?? [],
    },
    mode: 'onBlur',
  })

  // Field array for validation rules
  const {
    fields: validationFields,
    append,
    remove,
  } = useFieldArray({
    control,
    name: 'validationRules',
  })

  // Watch field type to show/hide reference target and validation rules
  // eslint-disable-next-line react-hooks/incompatible-library
  const watchedType = watch('type')

  // Get available validation rules for current field type
  const availableValidationRules = useMemo(() => {
    return VALIDATION_RULES_BY_TYPE[watchedType] || []
  }, [watchedType])

  // Reset form when field prop changes (for edit mode)
  useEffect(() => {
    if (field) {
      reset({
        name: field.name,
        displayName: field.displayName ?? '',
        type: field.type,
        required: field.required,
        unique: field.unique,
        indexed: field.indexed,
        defaultValue: field.defaultValue !== undefined ? String(field.defaultValue) : '',
        referenceTarget: field.referenceTarget ?? '',
        autoNumberPrefix: (parsedConfig.prefix as string) ?? '',
        autoNumberPadding: (parsedConfig.padding as number) ?? 4,
        currencyCode: (parsedConfig.currencyCode as string) ?? '',
        currencyPrecision: (parsedConfig.precision as number) ?? 2,
        globalPicklistId: (parsedConfig.globalPicklistId as string) ?? '',
        validationRules:
          field.validation?.map((v) => ({
            type: v.type,
            value:
              v.value !== undefined
                ? typeof v.value === 'number'
                  ? v.value
                  : String(v.value)
                : undefined,
            message: v.message,
          })) ?? [],
      })
    }
  }, [field, reset, parsedConfig])

  // Handle form submission
  const handleFormSubmit = useCallback(
    async (data: FieldEditorFormData) => {
      // Convert validation rules
      const validationRules: ValidationRule[] = (data.validationRules || [])
        .filter((rule) => rule.type)
        .map((rule) => {
          const result: ValidationRule = { type: rule.type }

          // Convert value based on rule type
          if (rule.value !== undefined && rule.value !== null && rule.value !== '') {
            if (rule.type === 'min' || rule.type === 'max') {
              result.value = Number(rule.value)
            } else {
              result.value = rule.value
            }
          }

          if (rule.message) {
            result.message = rule.message
          }

          return result
        })

      // Parse default value based on field type
      let parsedDefaultValue: unknown = undefined
      if (data.defaultValue && data.defaultValue !== '') {
        switch (data.type) {
          case 'number':
            parsedDefaultValue = Number(data.defaultValue)
            break
          case 'boolean':
            parsedDefaultValue = data.defaultValue === 'true'
            break
          case 'json':
            try {
              parsedDefaultValue = JSON.parse(data.defaultValue)
            } catch {
              parsedDefaultValue = data.defaultValue
            }
            break
          default:
            parsedDefaultValue = data.defaultValue
        }
      }

      // Build fieldTypeConfig JSON for type-specific settings
      let fieldTypeConfig: string | undefined = undefined
      if (data.type === 'auto_number') {
        const config: Record<string, unknown> = {}
        if (data.autoNumberPrefix) config.prefix = data.autoNumberPrefix
        if (data.autoNumberPadding !== undefined) config.padding = data.autoNumberPadding
        if (Object.keys(config).length > 0) fieldTypeConfig = JSON.stringify(config)
      } else if (data.type === 'currency') {
        const config: Record<string, unknown> = {}
        if (data.currencyCode) config.currencyCode = data.currencyCode
        if (data.currencyPrecision !== undefined) config.precision = data.currencyPrecision
        if (Object.keys(config).length > 0) fieldTypeConfig = JSON.stringify(config)
      } else if (data.type === 'picklist' || data.type === 'multi_picklist') {
        if (data.globalPicklistId) {
          fieldTypeConfig = JSON.stringify({ globalPicklistId: data.globalPicklistId })
        }
      }

      const needsReferenceTarget =
        data.type === 'reference' || data.type === 'lookup' || data.type === 'master_detail'

      const fieldData: FieldDefinition = {
        id: field?.id ?? generateFieldId(),
        name: data.name,
        displayName: data.displayName || undefined,
        type: data.type,
        required: data.required,
        unique: data.unique,
        indexed: data.indexed,
        defaultValue: parsedDefaultValue,
        validation: validationRules.length > 0 ? validationRules : undefined,
        referenceTarget: needsReferenceTarget ? data.referenceTarget : undefined,
        fieldTypeConfig,
        order: field?.order ?? 0,
      }

      await onSave(fieldData)
    },
    [field, onSave]
  )

  // Get translated error message
  const getErrorMessage = useCallback(
    (errorKey: string | undefined): string | undefined => {
      if (!errorKey) return undefined
      // Check if it's a translation key
      if (errorKey.startsWith('validation.')) {
        return t(`fieldEditor.${errorKey}`)
      }
      return errorKey
    },
    [t]
  )

  // Add a new validation rule
  const handleAddValidationRule = useCallback(() => {
    if (availableValidationRules.length > 0) {
      append({ type: availableValidationRules[0], value: undefined, message: '' })
    }
  }, [append, availableValidationRules])

  // Check if a validation rule type is already used
  const isRuleTypeUsed = useCallback(
    (ruleType: ValidationRuleType) => {
      return validationFields.some((field) => field.type === ruleType)
    },
    [validationFields]
  )

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid={testId}
      noValidate
    >
      <h3 className={styles.formTitle}>
        {isEditMode ? t('collections.editField') : t('collections.addField')}
      </h3>

      {/* Name Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="field-name" className={styles.label}>
          {t('collections.fieldName')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="field-name"
          type="text"
          className={`${styles.input} ${errors.name ? styles.inputError : ''}`}
          placeholder={t('fieldEditor.namePlaceholder')}
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'field-name-error' : 'field-name-hint'}
          data-testid="field-name-input"
          {...register('name')}
        />
        {errors.name && (
          <span
            id="field-name-error"
            className={styles.errorMessage}
            role="alert"
            data-testid="field-name-error"
          >
            {getErrorMessage(errors.name.message)}
          </span>
        )}
        {!isEditMode && (
          <span id="field-name-hint" className={styles.hint} data-testid="field-name-hint">
            {t('fieldEditor.nameHint')}
          </span>
        )}
      </div>

      {/* Display Name Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="field-display-name" className={styles.label}>
          {t('collections.displayName')}
          <span className={styles.optional}>({t('common.optional')})</span>
        </label>
        <input
          id="field-display-name"
          type="text"
          className={`${styles.input} ${errors.displayName ? styles.inputError : ''}`}
          placeholder={t('fieldEditor.displayNamePlaceholder')}
          disabled={isSubmitting}
          aria-invalid={!!errors.displayName}
          aria-describedby={errors.displayName ? 'field-display-name-error' : undefined}
          data-testid="field-display-name-input"
          {...register('displayName')}
        />
        {errors.displayName && (
          <span
            id="field-display-name-error"
            className={styles.errorMessage}
            role="alert"
            data-testid="field-display-name-error"
          >
            {getErrorMessage(errors.displayName.message)}
          </span>
        )}
      </div>

      {/* Field Type */}
      <div className={styles.fieldGroup}>
        <label htmlFor="field-type" className={styles.label}>
          {t('collections.fieldType')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <select
          id="field-type"
          className={`${styles.select} ${errors.type ? styles.inputError : ''}`}
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.type}
          aria-describedby={errors.type ? 'field-type-error' : undefined}
          data-testid="field-type-select"
          {...register('type')}
        >
          {FIELD_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`fields.types.${type.toLowerCase()}`)}
            </option>
          ))}
        </select>
        {errors.type && (
          <span
            id="field-type-error"
            className={styles.errorMessage}
            role="alert"
            data-testid="field-type-error"
          >
            {getErrorMessage(errors.type.message)}
          </span>
        )}
      </div>

      {/* Reference Target (for reference, lookup, and master_detail types) */}
      {(watchedType === 'reference' ||
        watchedType === 'lookup' ||
        watchedType === 'master_detail') && (
        <div className={styles.fieldGroup}>
          <label htmlFor="field-reference-target" className={styles.label}>
            {t('fieldEditor.referenceTarget')}
            <span className={styles.required} aria-hidden="true">
              *
            </span>
          </label>
          <select
            id="field-reference-target"
            className={`${styles.select} ${errors.referenceTarget ? styles.inputError : ''}`}
            disabled={isSubmitting}
            aria-required="true"
            aria-invalid={!!errors.referenceTarget}
            aria-describedby={
              errors.referenceTarget
                ? 'field-reference-target-error'
                : 'field-reference-target-hint'
            }
            data-testid="field-reference-target-select"
            {...register('referenceTarget')}
          >
            <option value="">{t('fieldEditor.selectCollection')}</option>
            {collections.map((collection) => (
              <option key={collection.id} value={collection.name}>
                {collection.displayName || collection.name}
              </option>
            ))}
          </select>
          {errors.referenceTarget && (
            <span
              id="field-reference-target-error"
              className={styles.errorMessage}
              role="alert"
              data-testid="field-reference-target-error"
            >
              {getErrorMessage(errors.referenceTarget.message)}
            </span>
          )}
          <span id="field-reference-target-hint" className={styles.hint}>
            {t('fieldEditor.referenceTargetHint')}
          </span>
        </div>
      )}

      {/* Picklist Selection (for picklist and multi_picklist types) */}
      {(watchedType === 'picklist' || watchedType === 'multi_picklist') && (
        <div className={styles.fieldGroup}>
          <label htmlFor="field-global-picklist" className={styles.label}>
            {t('fieldEditor.globalPicklist')}
            <span className={styles.required} aria-hidden="true">
              *
            </span>
          </label>
          <select
            id="field-global-picklist"
            className={`${styles.select} ${errors.globalPicklistId ? styles.inputError : ''}`}
            disabled={isSubmitting}
            aria-required="true"
            aria-invalid={!!errors.globalPicklistId}
            aria-describedby={
              errors.globalPicklistId ? 'field-global-picklist-error' : 'field-global-picklist-hint'
            }
            data-testid="field-global-picklist-select"
            {...register('globalPicklistId')}
          >
            <option value="">{t('fieldEditor.selectPicklist')}</option>
            {picklists.map((picklist) => (
              <option key={picklist.id} value={picklist.id}>
                {picklist.name}
              </option>
            ))}
          </select>
          {errors.globalPicklistId && (
            <span
              id="field-global-picklist-error"
              className={styles.errorMessage}
              role="alert"
              data-testid="field-global-picklist-error"
            >
              {getErrorMessage(errors.globalPicklistId.message)}
            </span>
          )}
          <span id="field-global-picklist-hint" className={styles.hint}>
            {t('fieldEditor.globalPicklistHint')}
          </span>
        </div>
      )}

      {/* Auto Number Config */}
      {watchedType === 'auto_number' && (
        <div className={styles.fieldGroup}>
          <label htmlFor="field-auto-number-prefix" className={styles.label}>
            {t('fields.config.prefix')}
            <span className={styles.optional}>({t('common.optional')})</span>
          </label>
          <input
            id="field-auto-number-prefix"
            type="text"
            className={styles.input}
            placeholder={'e.g., TICKET-'}
            disabled={isSubmitting}
            data-testid="field-auto-number-prefix-input"
            {...register('autoNumberPrefix')}
          />

          <label htmlFor="field-auto-number-padding" className={styles.label}>
            {t('fields.config.padding')}
          </label>
          <input
            id="field-auto-number-padding"
            type="number"
            className={styles.input}
            min={1}
            max={10}
            disabled={isSubmitting}
            data-testid="field-auto-number-padding-input"
            {...register('autoNumberPadding')}
          />
        </div>
      )}

      {/* Currency Config */}
      {watchedType === 'currency' && (
        <div className={styles.fieldGroup}>
          <label htmlFor="field-currency-code" className={styles.label}>
            {t('fields.config.currencyCode')}
            <span className={styles.optional}>({t('common.optional')})</span>
          </label>
          <input
            id="field-currency-code"
            type="text"
            className={styles.input}
            placeholder={'USD'}
            maxLength={3}
            disabled={isSubmitting}
            data-testid="field-currency-code-input"
            {...register('currencyCode')}
          />

          <label htmlFor="field-currency-precision" className={styles.label}>
            {t('fields.config.precision')}
          </label>
          <input
            id="field-currency-precision"
            type="number"
            className={styles.input}
            min={0}
            max={6}
            disabled={isSubmitting}
            data-testid="field-currency-precision-input"
            {...register('currencyPrecision')}
          />
        </div>
      )}

      {/* Field Flags */}
      <div className={styles.flagsGroup}>
        <div className={styles.checkboxGroup}>
          <input
            id="field-required"
            type="checkbox"
            className={styles.checkbox}
            disabled={isSubmitting}
            data-testid="field-required-checkbox"
            {...register('required')}
          />
          <label htmlFor="field-required" className={styles.checkboxLabel}>
            {t('fields.validation.required')}
          </label>
        </div>

        <div className={styles.checkboxGroup}>
          <input
            id="field-unique"
            type="checkbox"
            className={styles.checkbox}
            disabled={isSubmitting}
            data-testid="field-unique-checkbox"
            {...register('unique')}
          />
          <label htmlFor="field-unique" className={styles.checkboxLabel}>
            {t('fields.validation.unique')}
          </label>
        </div>

        <div className={styles.checkboxGroup}>
          <input
            id="field-indexed"
            type="checkbox"
            className={styles.checkbox}
            disabled={isSubmitting}
            data-testid="field-indexed-checkbox"
            {...register('indexed')}
          />
          <label htmlFor="field-indexed" className={styles.checkboxLabel}>
            {t('fields.validation.indexed')}
          </label>
        </div>
      </div>

      {/* Default Value */}
      <div className={styles.fieldGroup}>
        <label htmlFor="field-default-value" className={styles.label}>
          {t('fieldEditor.defaultValue')}
          <span className={styles.optional}>({t('common.optional')})</span>
        </label>
        <input
          id="field-default-value"
          type="text"
          className={styles.input}
          placeholder={t('fieldEditor.defaultValuePlaceholder')}
          disabled={isSubmitting}
          aria-describedby="field-default-value-hint"
          data-testid="field-default-value-input"
          {...register('defaultValue')}
        />
        <span id="field-default-value-hint" className={styles.hint}>
          {t('fieldEditor.defaultValueHint')}
        </span>
      </div>

      {/* Validation Rules Section */}
      {availableValidationRules.length > 0 && (
        <div className={styles.validationSection}>
          <div className={styles.sectionHeader}>
            <h4 className={styles.sectionTitle}>{t('fieldEditor.validationRules')}</h4>
            <button
              type="button"
              className={styles.addRuleButton}
              onClick={handleAddValidationRule}
              disabled={isSubmitting || validationFields.length >= availableValidationRules.length}
              data-testid="add-validation-rule-button"
            >
              + {t('fieldEditor.addRule')}
            </button>
          </div>

          {validationFields.length === 0 && (
            <p className={styles.noRulesMessage} data-testid="no-validation-rules">
              {t('fieldEditor.noValidationRules')}
            </p>
          )}

          {validationFields.map((validationField, index) => (
            <div
              key={validationField.id}
              className={styles.validationRule}
              data-testid={`validation-rule-${index}`}
            >
              <div className={styles.ruleFields}>
                <div className={styles.ruleTypeField}>
                  <label htmlFor={`validation-rule-type-${index}`} className={styles.ruleLabel}>
                    {t('fieldEditor.ruleType')}
                  </label>
                  <Controller
                    name={`validationRules.${index}.type`}
                    control={control}
                    render={({ field: controllerField }) => (
                      <select
                        id={`validation-rule-type-${index}`}
                        className={styles.ruleSelect}
                        disabled={isSubmitting}
                        data-testid={`validation-rule-type-${index}`}
                        {...controllerField}
                      >
                        {availableValidationRules.map((ruleType) => (
                          <option
                            key={ruleType}
                            value={ruleType}
                            disabled={
                              isRuleTypeUsed(ruleType) && controllerField.value !== ruleType
                            }
                          >
                            {t(`fields.validation.${ruleType}`)}
                          </option>
                        ))}
                      </select>
                    )}
                  />
                </div>

                {/* Value field for min, max, pattern */}
                {['min', 'max', 'pattern'].includes(validationFields[index]?.type) && (
                  <div className={styles.ruleValueField}>
                    <label htmlFor={`validation-rule-value-${index}`} className={styles.ruleLabel}>
                      {t('fieldEditor.ruleValue')}
                    </label>
                    <input
                      id={`validation-rule-value-${index}`}
                      type={validationFields[index]?.type === 'pattern' ? 'text' : 'number'}
                      className={styles.ruleInput}
                      placeholder={
                        validationFields[index]?.type === 'pattern'
                          ? t('fieldEditor.patternPlaceholder')
                          : t('fieldEditor.valuePlaceholder')
                      }
                      disabled={isSubmitting}
                      data-testid={`validation-rule-value-${index}`}
                      {...register(`validationRules.${index}.value`)}
                    />
                  </div>
                )}

                <div className={styles.ruleMessageField}>
                  <label htmlFor={`validation-rule-message-${index}`} className={styles.ruleLabel}>
                    {t('fieldEditor.ruleMessage')}
                    <span className={styles.optional}>({t('common.optional')})</span>
                  </label>
                  <input
                    id={`validation-rule-message-${index}`}
                    type="text"
                    className={styles.ruleInput}
                    placeholder={t('fieldEditor.ruleMessagePlaceholder')}
                    disabled={isSubmitting}
                    data-testid={`validation-rule-message-${index}`}
                    {...register(`validationRules.${index}.message`)}
                  />
                </div>
              </div>

              <button
                type="button"
                className={styles.removeRuleButton}
                onClick={() => remove(index)}
                disabled={isSubmitting}
                aria-label={t('fieldEditor.removeRule')}
                data-testid={`remove-validation-rule-${index}`}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Form Actions */}
      <div className={styles.actions}>
        <button
          type="button"
          className={styles.cancelButton}
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="field-editor-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className={styles.submitButton}
          disabled={isSubmitting || (!isDirty && isEditMode)}
          data-testid="field-editor-submit"
        >
          {isSubmitting ? (
            <>
              <LoadingSpinner size="small" />
              <span className={styles.submitText}>
                {isEditMode ? t('common.save') : t('common.create')}
              </span>
            </>
          ) : isEditMode ? (
            t('common.save')
          ) : (
            t('common.create')
          )}
        </button>
      </div>
    </form>
  )
}

export default FieldEditor
