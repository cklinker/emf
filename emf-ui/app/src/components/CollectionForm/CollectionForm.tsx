/**
 * CollectionForm Component
 *
 * Form for creating and editing collections with validation.
 * Uses React Hook Form with Zod validation schema.
 *
 * Requirements:
 * - 3.4: Display form for entering collection details
 * - 3.5: Create collection via API and display success message
 * - 3.6: Display validation errors inline with form fields
 * - 3.9: Pre-populate form with current values in edit mode
 */

import React, { useEffect, useCallback } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'
import { cn } from '@/lib/utils'

const inputClasses =
  'px-3 py-2 text-base leading-relaxed text-foreground bg-background border border-border rounded-md transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-ring disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed placeholder:text-muted-foreground'

const selectAddons =
  "appearance-none bg-[url(\"data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e\")] bg-[right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer disabled:cursor-not-allowed"

const inputErrorClasses = 'border-destructive focus:border-destructive focus:ring-destructive/25'

/**
 * Storage mode options for collections
 */
export type StorageMode = 'PHYSICAL_TABLE' | 'JSONB'

/**
 * Collection data for the form
 */
export interface CollectionFormData {
  /** Collection name (alphanumeric, underscores, lowercase) */
  name: string
  /** Display name for the collection */
  displayName: string
  /** Optional description */
  description?: string
  /** Storage mode for the collection */
  storageMode: StorageMode
  /** Whether the collection is active */
  active: boolean
  /** ID of the field used as display field in lookup dropdowns */
  displayFieldId?: string
}

/**
 * Available field for display field dropdown
 */
export interface AvailableField {
  id: string
  name: string
  displayName: string
}

/**
 * Full collection interface for edit mode
 */
export interface Collection {
  id: string
  name: string
  displayName: string
  description?: string
  storageMode: StorageMode
  active: boolean
  displayFieldId?: string
  currentVersion: number
  createdAt: string
  updatedAt: string
}

/**
 * Props for the CollectionForm component
 */
export interface CollectionFormProps {
  /** Existing collection data for edit mode */
  collection?: Collection
  /** Available fields for display field dropdown (edit mode only) */
  availableFields?: AvailableField[]
  /** Callback when form is submitted successfully */
  onSubmit: (data: CollectionFormData) => Promise<void>
  /** Callback when form is cancelled */
  onCancel: () => void
  /** Whether the form is in a loading/submitting state */
  isSubmitting?: boolean
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Zod validation schema for collection form
 *
 * Name validation:
 * - Required
 * - 1-50 characters
 * - Lowercase alphanumeric and underscores only
 * - Must start with a letter
 */
// eslint-disable-next-line react-refresh/only-export-components
export const collectionFormSchema = z.object({
  name: z
    .string()
    .min(1, 'validation.nameRequired')
    .max(50, 'validation.nameTooLong')
    .regex(/^[a-z][a-z0-9_]*$/, 'validation.nameFormat'),
  displayName: z
    .string()
    .min(1, 'validation.displayNameRequired')
    .max(100, 'validation.displayNameTooLong'),
  description: z.string().max(500, 'validation.descriptionTooLong').optional().or(z.literal('')),
  storageMode: z.enum(['PHYSICAL_TABLE', 'JSONB'], {
    errorMap: () => ({ message: 'validation.storageModeRequired' }),
  }),
  active: z.boolean(),
  displayFieldId: z.string().optional().or(z.literal('')),
})

/**
 * Type inferred from the Zod schema
 */
export type CollectionFormSchema = z.infer<typeof collectionFormSchema>

/**
 * CollectionForm Component
 *
 * Provides a form for creating and editing collections with:
 * - Name validation (alphanumeric, underscores, lowercase)
 * - Display name validation
 * - Optional description
 * - Storage mode selection
 * - Active status toggle
 * - Inline validation errors
 * - Loading state during submission
 */
export function CollectionForm({
  collection,
  availableFields = [],
  onSubmit,
  onCancel,
  isSubmitting = false,
  testId = 'collection-form',
}: CollectionFormProps): React.ReactElement {
  const { t } = useI18n()
  const isEditMode = !!collection

  // Initialize form with React Hook Form and Zod resolver
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<CollectionFormSchema>({
    resolver: zodResolver(collectionFormSchema),
    defaultValues: {
      name: collection?.name ?? '',
      displayName: collection?.displayName ?? '',
      description: collection?.description ?? '',
      storageMode: collection?.storageMode ?? 'JSONB',
      active: collection?.active ?? true,
      displayFieldId: collection?.displayFieldId ?? '',
    },
    mode: 'onBlur',
  })

  // Reset form when collection prop changes (for edit mode)
  useEffect(() => {
    if (collection) {
      reset({
        name: collection.name,
        displayName: collection.displayName,
        description: collection.description ?? '',
        storageMode: collection.storageMode,
        active: collection.active,
        displayFieldId: collection.displayFieldId ?? '',
      })
    }
  }, [collection, reset])

  // Handle form submission
  const handleFormSubmit = useCallback(
    async (data: CollectionFormSchema) => {
      const formData: CollectionFormData = {
        name: data.name,
        displayName: data.displayName,
        description: data.description || undefined,
        storageMode: data.storageMode,
        active: data.active,
        displayFieldId: data.displayFieldId || undefined,
      }
      await onSubmit(formData)
    },
    [onSubmit]
  )

  // Get translated error message
  const getErrorMessage = useCallback(
    (errorKey: string | undefined): string | undefined => {
      if (!errorKey) return undefined
      // Check if it's a translation key
      if (errorKey.startsWith('validation.')) {
        return t(`collectionForm.${errorKey}`)
      }
      return errorKey
    },
    [t]
  )

  return (
    <form
      className="flex flex-col gap-6 max-w-[600px] w-full"
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid={testId}
      noValidate
    >
      {/* Name Field */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="collection-name"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.collectionName')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="collection-name"
          type="text"
          className={cn(inputClasses, errors.name && inputErrorClasses)}
          placeholder={t('collectionForm.namePlaceholder')}
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'name-error' : !isEditMode ? 'name-hint' : undefined}
          data-testid="collection-name-input"
          {...register('name')}
        />
        {errors.name && (
          <span
            id="name-error"
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['⚠'] before:text-xs"
            role="alert"
            data-testid="name-error"
          >
            {getErrorMessage(errors.name.message)}
          </span>
        )}
        {!isEditMode && (
          <span
            id="name-hint"
            className="text-xs text-muted-foreground mt-1"
            data-testid="name-hint"
          >
            {t('collectionForm.nameHint')}
          </span>
        )}
      </div>

      {/* Display Name Field */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="collection-display-name"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.displayName')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="collection-display-name"
          type="text"
          className={cn(inputClasses, errors.displayName && inputErrorClasses)}
          placeholder={t('collectionForm.displayNamePlaceholder')}
          disabled={isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.displayName}
          aria-describedby={errors.displayName ? 'display-name-error' : undefined}
          data-testid="collection-display-name-input"
          {...register('displayName')}
        />
        {errors.displayName && (
          <span
            id="display-name-error"
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['⚠'] before:text-xs"
            role="alert"
            data-testid="display-name-error"
          >
            {getErrorMessage(errors.displayName.message)}
          </span>
        )}
      </div>

      {/* Description Field */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="collection-description"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.description')}
          <span className="text-xs font-normal text-muted-foreground ml-1">
            ({t('common.optional')})
          </span>
        </label>
        <textarea
          id="collection-description"
          className={cn(
            inputClasses,
            'resize-y min-h-[80px]',
            errors.description && inputErrorClasses
          )}
          placeholder={t('collectionForm.descriptionPlaceholder')}
          disabled={isSubmitting}
          rows={3}
          aria-invalid={!!errors.description}
          aria-describedby={errors.description ? 'description-error' : undefined}
          data-testid="collection-description-input"
          {...register('description')}
        />
        {errors.description && (
          <span
            id="description-error"
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['⚠'] before:text-xs"
            role="alert"
            data-testid="description-error"
          >
            {getErrorMessage(errors.description.message)}
          </span>
        )}
      </div>

      {/* Storage Mode Field */}
      <div className="flex flex-col gap-1">
        <label
          htmlFor="collection-storage-mode"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.storageMode')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <select
          id="collection-storage-mode"
          className={cn(inputClasses, selectAddons, errors.storageMode && inputErrorClasses)}
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.storageMode}
          aria-describedby={
            errors.storageMode
              ? 'storage-mode-error'
              : !isEditMode
                ? 'storage-mode-hint'
                : undefined
          }
          data-testid="collection-storage-mode-select"
          {...register('storageMode')}
        >
          <option value="JSONB">{t('collections.storageModes.jsonb')}</option>
          <option value="PHYSICAL_TABLE">{t('collections.storageModes.physicalTable')}</option>
        </select>
        {errors.storageMode && (
          <span
            id="storage-mode-error"
            className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['⚠'] before:text-xs"
            role="alert"
            data-testid="storage-mode-error"
          >
            {getErrorMessage(errors.storageMode.message)}
          </span>
        )}
        {!isEditMode && (
          <span
            id="storage-mode-hint"
            className="text-xs text-muted-foreground mt-1"
            data-testid="storage-mode-hint"
          >
            {t('collectionForm.storageModeHint')}
          </span>
        )}
      </div>

      {/* Display Field (edit mode only, when fields exist) */}
      {isEditMode && availableFields.length > 0 && (
        <div className="flex flex-col gap-1">
          <label
            htmlFor="collection-display-field"
            className="flex items-center gap-1 text-sm font-medium text-foreground"
          >
            {t('collections.displayField', 'Display Field')}
            <span className="text-xs font-normal text-muted-foreground ml-1">
              ({t('common.optional')})
            </span>
          </label>
          <select
            id="collection-display-field"
            className={cn(inputClasses, selectAddons)}
            disabled={isSubmitting}
            aria-describedby="display-field-hint"
            data-testid="collection-display-field-select"
            {...register('displayFieldId')}
          >
            <option value="">{t('collectionForm.displayFieldNone', 'Auto-detect')}</option>
            {availableFields.map((field) => (
              <option key={field.id} value={field.id}>
                {field.displayName || field.name}
              </option>
            ))}
          </select>
          <span
            id="display-field-hint"
            className="text-xs text-muted-foreground mt-1"
            data-testid="display-field-hint"
          >
            {t(
              'collectionForm.displayFieldHint',
              'The field shown when records appear in lookup dropdowns. If not set, defaults to a field named "name" or the first text field.'
            )}
          </span>
        </div>
      )}

      {/* Active Status Field */}
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <input
            id="collection-active"
            type="checkbox"
            className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting}
            aria-describedby="active-hint"
            data-testid="collection-active-checkbox"
            {...register('active')}
          />
          <label htmlFor="collection-active" className="text-base text-foreground cursor-pointer">
            {t('collections.active')}
          </label>
        </div>
        <span
          id="active-hint"
          className="text-xs text-muted-foreground mt-1"
          data-testid="active-hint"
        >
          {t('collectionForm.activeHint')}
        </span>
      </div>

      {/* Form Actions */}
      <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-border">
        <button
          type="button"
          className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-relaxed rounded-md cursor-pointer text-foreground bg-muted border border-border hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed"
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="collection-form-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-relaxed rounded-md cursor-pointer text-primary-foreground bg-primary border border-transparent hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
          disabled={isSubmitting || (!isDirty && isEditMode)}
          data-testid="collection-form-submit"
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

export default CollectionForm
