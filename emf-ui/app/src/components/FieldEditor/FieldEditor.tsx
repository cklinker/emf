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
import { cn } from '@/lib/utils'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'

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
  fieldTypeConfig?: string | Record<string, unknown>
  order: number
  description?: string
  trackHistory?: boolean
  constraints?: string
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
  'master_detail',
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
    description: z.string().max(500, 'validation.descriptionTooLong').optional().or(z.literal('')),
    trackHistory: z.boolean(),
    validationRules: z.array(validationRuleSchema).optional(),
  })
  .refine(
    (data) => {
      // master_detail type requires referenceTarget
      if (data.type === 'master_detail' && !data.referenceTarget) {
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

// Common input classes
const inputClasses = cn(
  'px-3 py-2 text-base leading-6 text-foreground bg-background border border-input rounded-md',
  'transition-colors duration-150 motion-reduce:transition-none',
  'focus:outline-none focus:border-primary focus:ring-2 focus:ring-ring',
  'disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed',
  'placeholder:text-muted-foreground'
)

const selectClasses = cn(
  inputClasses,
  'appearance-none bg-[length:1.5em_1.5em] bg-[right_0.5rem_center] bg-no-repeat pr-10 cursor-pointer',
  "bg-[url(\"data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e\")]",
  'disabled:cursor-not-allowed'
)

const errorInputClasses = 'border-destructive focus:border-destructive focus:ring-destructive/25'

/**
 * FieldEditor Component
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

  // Parse existing fieldTypeConfig for default values.
  // fieldTypeConfig may arrive as a parsed object (JSONB column) or a
  // JSON string depending on the serialization path.  Handle both.
  const parsedConfig = useMemo(() => {
    const raw = field?.fieldTypeConfig
    if (!raw) return {}
    if (typeof raw === 'object') return raw as Record<string, unknown>
    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw) as Record<string, unknown>
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
      description: field?.description ?? '',
      trackHistory: field?.trackHistory ?? false,
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
        description: field.description ?? '',
        trackHistory: field.trackHistory ?? false,
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

      const needsReferenceTarget = data.type === 'master_detail'

      // Map validation rules to constraints JSON for backend
      let constraints: string | undefined = undefined
      if (validationRules.length > 0) {
        const constraintObj: Record<string, unknown> = {}
        for (const rule of validationRules) {
          if (rule.type === 'min' && rule.value !== undefined) constraintObj.min = rule.value
          else if (rule.type === 'max' && rule.value !== undefined) constraintObj.max = rule.value
          else if (rule.type === 'pattern' && rule.value !== undefined)
            constraintObj.pattern = rule.value
          else if (rule.type === 'email') constraintObj.email = true
          else if (rule.type === 'url') constraintObj.url = true
        }
        if (Object.keys(constraintObj).length > 0) constraints = JSON.stringify(constraintObj)
      }

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
        description: data.description || undefined,
        trackHistory: data.trackHistory,
        constraints,
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
      className="flex flex-col gap-6 max-w-[600px] w-full md:gap-4"
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid={testId}
      noValidate
    >
      <h3 className="m-0 mb-4 text-lg font-semibold text-foreground">
        {isEditMode ? t('collections.editField') : t('collections.addField')}
      </h3>

      {/* Name Field */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="field-name"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.fieldName')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="field-name"
          type="text"
          className={cn(inputClasses, errors.name && errorInputClasses)}
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
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['\u26A0'] before:text-xs"
            role="alert"
            data-testid="field-name-error"
          >
            {getErrorMessage(errors.name.message)}
          </span>
        )}
        {!isEditMode && (
          <span
            id="field-name-hint"
            className="text-xs text-muted-foreground mt-1"
            data-testid="field-name-hint"
          >
            {t('fieldEditor.nameHint')}
          </span>
        )}
      </div>

      {/* Display Name Field */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="field-display-name"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.displayName')}
          <span className="text-xs font-normal text-muted-foreground ml-1">
            ({t('common.optional')})
          </span>
        </label>
        <input
          id="field-display-name"
          type="text"
          className={cn(inputClasses, errors.displayName && errorInputClasses)}
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
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['\u26A0'] before:text-xs"
            role="alert"
            data-testid="field-display-name-error"
          >
            {getErrorMessage(errors.displayName.message)}
          </span>
        )}
      </div>

      {/* Field Type */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="field-type"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.fieldType')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <select
          id="field-type"
          className={cn(selectClasses, errors.type && errorInputClasses)}
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
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['\u26A0'] before:text-xs"
            role="alert"
            data-testid="field-type-error"
          >
            {getErrorMessage(errors.type.message)}
          </span>
        )}
      </div>

      {/* Reference Target (for master_detail type) */}
      {watchedType === 'master_detail' && (
        <div className="flex flex-col gap-1">
          <label
            htmlFor="field-reference-target"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('fieldEditor.referenceTarget')}
            <span className="text-destructive font-semibold" aria-hidden="true">
              *
            </span>
          </label>
          <select
            id="field-reference-target"
            className={cn(selectClasses, errors.referenceTarget && errorInputClasses)}
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
              className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['\u26A0'] before:text-xs"
              role="alert"
              data-testid="field-reference-target-error"
            >
              {getErrorMessage(errors.referenceTarget.message)}
            </span>
          )}
          <span id="field-reference-target-hint" className="text-xs text-muted-foreground mt-1">
            {t('fieldEditor.referenceTargetHint')}
          </span>
        </div>
      )}

      {/* Picklist Selection (for picklist and multi_picklist types) */}
      {(watchedType === 'picklist' || watchedType === 'multi_picklist') && (
        <div className="flex flex-col gap-1">
          <label
            htmlFor="field-global-picklist"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('fieldEditor.globalPicklist')}
            <span className="text-destructive font-semibold" aria-hidden="true">
              *
            </span>
          </label>
          <select
            id="field-global-picklist"
            className={cn(selectClasses, errors.globalPicklistId && errorInputClasses)}
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
              className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['\u26A0'] before:text-xs"
              role="alert"
              data-testid="field-global-picklist-error"
            >
              {getErrorMessage(errors.globalPicklistId.message)}
            </span>
          )}
          <span id="field-global-picklist-hint" className="text-xs text-muted-foreground mt-1">
            {t('fieldEditor.globalPicklistHint')}
          </span>
        </div>
      )}

      {/* Auto Number Config */}
      {watchedType === 'auto_number' && (
        <div className="flex flex-col gap-1">
          <label
            htmlFor="field-auto-number-prefix"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('fields.config.prefix')}
            <span className="text-xs font-normal text-muted-foreground ml-1">
              ({t('common.optional')})
            </span>
          </label>
          <input
            id="field-auto-number-prefix"
            type="text"
            className={inputClasses}
            placeholder={'e.g., TICKET-'}
            disabled={isSubmitting}
            data-testid="field-auto-number-prefix-input"
            {...register('autoNumberPrefix')}
          />

          <label
            htmlFor="field-auto-number-padding"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('fields.config.padding')}
          </label>
          <input
            id="field-auto-number-padding"
            type="number"
            className={inputClasses}
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
        <div className="flex flex-col gap-1">
          <label
            htmlFor="field-currency-code"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('fields.config.currencyCode')}
            <span className="text-xs font-normal text-muted-foreground ml-1">
              ({t('common.optional')})
            </span>
          </label>
          <input
            id="field-currency-code"
            type="text"
            className={inputClasses}
            placeholder={'USD'}
            maxLength={3}
            disabled={isSubmitting}
            data-testid="field-currency-code-input"
            {...register('currencyCode')}
          />

          <label
            htmlFor="field-currency-precision"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('fields.config.precision')}
          </label>
          <input
            id="field-currency-precision"
            type="number"
            className={inputClasses}
            min={0}
            max={6}
            disabled={isSubmitting}
            data-testid="field-currency-precision-input"
            {...register('currencyPrecision')}
          />
        </div>
      )}

      {/* Field Flags */}
      <div className="flex flex-wrap gap-6 p-4 bg-secondary rounded-md max-md:flex-col max-md:gap-2">
        <div className="flex items-center gap-2">
          <input
            id="field-required"
            type="checkbox"
            className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting}
            data-testid="field-required-checkbox"
            {...register('required')}
          />
          <label htmlFor="field-required" className="text-sm text-foreground cursor-pointer">
            {t('fields.validation.required')}
          </label>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="field-unique"
            type="checkbox"
            className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting}
            data-testid="field-unique-checkbox"
            {...register('unique')}
          />
          <label htmlFor="field-unique" className="text-sm text-foreground cursor-pointer">
            {t('fields.validation.unique')}
          </label>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="field-indexed"
            type="checkbox"
            className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting}
            data-testid="field-indexed-checkbox"
            {...register('indexed')}
          />
          <label htmlFor="field-indexed" className="text-sm text-foreground cursor-pointer">
            {t('fields.validation.indexed')}
          </label>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="field-track-history"
            type="checkbox"
            className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting}
            data-testid="field-track-history-checkbox"
            {...register('trackHistory')}
          />
          <label htmlFor="field-track-history" className="text-sm text-foreground cursor-pointer">
            {t('fieldEditor.trackHistory')}
          </label>
        </div>
      </div>

      {/* Description */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="field-description"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.description')}
          <span className="text-xs font-normal text-muted-foreground ml-1">
            ({t('common.optional')})
          </span>
        </label>
        <textarea
          id="field-description"
          className={cn(inputClasses, 'resize-y min-h-12', errors.description && errorInputClasses)}
          placeholder={t('fieldEditor.descriptionPlaceholder')}
          rows={3}
          disabled={isSubmitting}
          aria-invalid={!!errors.description}
          aria-describedby={errors.description ? 'field-description-error' : undefined}
          data-testid="field-description-input"
          {...register('description')}
        />
        {errors.description && (
          <span
            id="field-description-error"
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['\u26A0'] before:text-xs"
            role="alert"
            data-testid="field-description-error"
          >
            {getErrorMessage(errors.description.message)}
          </span>
        )}
      </div>

      {/* Default Value */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="field-default-value"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('fieldEditor.defaultValue')}
          <span className="text-xs font-normal text-muted-foreground ml-1">
            ({t('common.optional')})
          </span>
        </label>
        <input
          id="field-default-value"
          type="text"
          className={inputClasses}
          placeholder={t('fieldEditor.defaultValuePlaceholder')}
          disabled={isSubmitting}
          aria-describedby="field-default-value-hint"
          data-testid="field-default-value-input"
          {...register('defaultValue')}
        />
        <span id="field-default-value-hint" className="text-xs text-muted-foreground mt-1">
          {t('fieldEditor.defaultValueHint')}
        </span>
      </div>

      {/* Validation Rules Section */}
      {availableValidationRules.length > 0 && (
        <div className="flex flex-col gap-4 p-4 bg-secondary border border-border rounded-md">
          <div className="flex justify-between items-center">
            <h4 className="m-0 text-base font-medium text-foreground">
              {t('fieldEditor.validationRules')}
            </h4>
            <button
              type="button"
              className={cn(
                'inline-flex items-center px-2 py-1 text-sm font-medium text-primary',
                'bg-transparent border border-primary rounded',
                'transition-colors duration-150 motion-reduce:transition-none',
                'hover:bg-primary/10 disabled:opacity-50 disabled:cursor-not-allowed'
              )}
              onClick={handleAddValidationRule}
              disabled={isSubmitting || validationFields.length >= availableValidationRules.length}
              data-testid="add-validation-rule-button"
            >
              + {t('fieldEditor.addRule')}
            </button>
          </div>

          {validationFields.length === 0 && (
            <p
              className="m-0 text-sm text-muted-foreground italic"
              data-testid="no-validation-rules"
            >
              {t('fieldEditor.noValidationRules')}
            </p>
          )}

          {validationFields.map((validationField, index) => (
            <div
              key={validationField.id}
              className="flex items-start gap-2 p-2 bg-background border border-border rounded"
              data-testid={`validation-rule-${index}`}
            >
              <div className="flex flex-wrap gap-2 flex-1 max-md:flex-col">
                <div className="flex-none min-w-[120px] max-md:min-w-full">
                  <label
                    htmlFor={`validation-rule-type-${index}`}
                    className="block text-xs font-medium text-muted-foreground mb-1"
                  >
                    {t('fieldEditor.ruleType')}
                  </label>
                  <Controller
                    name={`validationRules.${index}.type`}
                    control={control}
                    render={({ field: controllerField }) => (
                      <select
                        id={`validation-rule-type-${index}`}
                        className={cn(
                          'w-full px-2 py-1 text-sm text-foreground bg-background border border-input rounded',
                          'appearance-none bg-[length:1.25em_1.25em] bg-[right_0.25rem_center] bg-no-repeat pr-7 cursor-pointer',
                          "bg-[url(\"data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e\")]",
                          'disabled:bg-muted disabled:cursor-not-allowed'
                        )}
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
                  <div className="flex-none min-w-[100px] max-md:min-w-full">
                    <label
                      htmlFor={`validation-rule-value-${index}`}
                      className="block text-xs font-medium text-muted-foreground mb-1"
                    >
                      {t('fieldEditor.ruleValue')}
                    </label>
                    <input
                      id={`validation-rule-value-${index}`}
                      type={validationFields[index]?.type === 'pattern' ? 'text' : 'number'}
                      className="w-full px-2 py-1 text-sm text-foreground bg-background border border-input rounded disabled:bg-muted disabled:cursor-not-allowed"
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

                <div className="flex-1 min-w-[150px] max-md:min-w-full">
                  <label
                    htmlFor={`validation-rule-message-${index}`}
                    className="block text-xs font-medium text-muted-foreground mb-1"
                  >
                    {t('fieldEditor.ruleMessage')}
                    <span className="text-xs font-normal text-muted-foreground ml-1">
                      ({t('common.optional')})
                    </span>
                  </label>
                  <input
                    id={`validation-rule-message-${index}`}
                    type="text"
                    className="w-full px-2 py-1 text-sm text-foreground bg-background border border-input rounded disabled:bg-muted disabled:cursor-not-allowed"
                    placeholder={t('fieldEditor.ruleMessagePlaceholder')}
                    disabled={isSubmitting}
                    data-testid={`validation-rule-message-${index}`}
                    {...register(`validationRules.${index}.message`)}
                  />
                </div>
              </div>

              <button
                type="button"
                className={cn(
                  'flex items-center justify-center w-6 h-6 mt-5 text-lg font-semibold',
                  'text-muted-foreground bg-transparent border-none rounded cursor-pointer',
                  'transition-colors duration-150 motion-reduce:transition-none',
                  'hover:text-destructive hover:bg-destructive/10',
                  'disabled:opacity-50 disabled:cursor-not-allowed'
                )}
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
      <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-border max-md:flex-col-reverse max-md:gap-2">
        <button
          type="button"
          className={cn(
            'inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-6 rounded-md cursor-pointer',
            'text-foreground bg-secondary border border-input',
            'transition-colors duration-150 motion-reduce:transition-none',
            'hover:bg-accent focus:outline-none focus:ring-2 focus:ring-ring',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            'max-md:w-full'
          )}
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="field-editor-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className={cn(
            'inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-6 rounded-md cursor-pointer',
            'text-primary-foreground bg-primary border border-transparent',
            'transition-colors duration-150 motion-reduce:transition-none',
            'hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-ring',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            'max-md:w-full'
          )}
          disabled={isSubmitting || (!isDirty && isEditMode)}
          data-testid="field-editor-submit"
        >
          {isSubmitting ? (
            <>
              <LoadingSpinner size="small" />
              <span className="ml-1">{isEditMode ? t('common.save') : t('common.create')}</span>
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
