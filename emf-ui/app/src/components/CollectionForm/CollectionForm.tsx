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

import React, { useEffect, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useI18n } from '../../context/I18nContext';
import { LoadingSpinner } from '../LoadingSpinner';
import styles from './CollectionForm.module.css';

/**
 * Storage mode options for collections
 */
export type StorageMode = 'PHYSICAL_TABLE' | 'JSONB';

/**
 * Collection data for the form
 */
export interface CollectionFormData {
  /** Service ID that owns this collection */
  serviceId: string;
  /** Collection name (alphanumeric, underscores, lowercase) */
  name: string;
  /** Display name for the collection */
  displayName: string;
  /** Optional description */
  description?: string;
  /** Storage mode for the collection */
  storageMode: StorageMode;
  /** Whether the collection is active */
  active: boolean;
}

/**
 * Full collection interface for edit mode
 */
export interface Collection {
  id: string;
  serviceId: string;
  name: string;
  displayName: string;
  description?: string;
  storageMode: StorageMode;
  active: boolean;
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Props for the CollectionForm component
 */
export interface CollectionFormProps {
  /** Existing collection data for edit mode */
  collection?: Collection;
  /** Available services to choose from */
  services?: Array<{ id: string; name: string }>;
  /** Whether services are loading */
  servicesLoading?: boolean;
  /** Callback when form is submitted successfully */
  onSubmit: (data: CollectionFormData) => Promise<void>;
  /** Callback when form is cancelled */
  onCancel: () => void;
  /** Whether the form is in a loading/submitting state */
  isSubmitting?: boolean;
  /** Optional test ID for testing */
  testId?: string;
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
export const collectionFormSchema = z.object({
  serviceId: z
    .string()
    .min(1, 'validation.serviceRequired'),
  name: z
    .string()
    .min(1, 'validation.nameRequired')
    .max(50, 'validation.nameTooLong')
    .regex(/^[a-z][a-z0-9_]*$/, 'validation.nameFormat'),
  displayName: z
    .string()
    .min(1, 'validation.displayNameRequired')
    .max(100, 'validation.displayNameTooLong'),
  description: z
    .string()
    .max(500, 'validation.descriptionTooLong')
    .optional()
    .or(z.literal('')),
  storageMode: z.enum(['PHYSICAL_TABLE', 'JSONB'], {
    errorMap: () => ({ message: 'validation.storageModeRequired' }),
  }),
  active: z.boolean(),
});

/**
 * Type inferred from the Zod schema
 */
export type CollectionFormSchema = z.infer<typeof collectionFormSchema>;

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
  services = [],
  servicesLoading = false,
  onSubmit,
  onCancel,
  isSubmitting = false,
  testId = 'collection-form',
}: CollectionFormProps): React.ReactElement {
  const { t } = useI18n();
  const isEditMode = !!collection;

  // Initialize form with React Hook Form and Zod resolver
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<CollectionFormSchema>({
    resolver: zodResolver(collectionFormSchema),
    defaultValues: {
      serviceId: collection?.serviceId ?? '',
      name: collection?.name ?? '',
      displayName: collection?.displayName ?? '',
      description: collection?.description ?? '',
      storageMode: collection?.storageMode ?? 'JSONB',
      active: collection?.active ?? true,
    },
    mode: 'onBlur',
  });

  // Reset form when collection prop changes (for edit mode)
  useEffect(() => {
    if (collection) {
      reset({
        serviceId: collection.serviceId,
        name: collection.name,
        displayName: collection.displayName,
        description: collection.description ?? '',
        storageMode: collection.storageMode,
        active: collection.active,
      });
    }
  }, [collection, reset]);

  // Handle form submission
  const handleFormSubmit = useCallback(
    async (data: CollectionFormSchema) => {
      const formData: CollectionFormData = {
        serviceId: data.serviceId,
        name: data.name,
        displayName: data.displayName,
        description: data.description || undefined,
        storageMode: data.storageMode,
        active: data.active,
      };
      await onSubmit(formData);
    },
    [onSubmit]
  );

  // Get translated error message
  const getErrorMessage = useCallback(
    (errorKey: string | undefined): string | undefined => {
      if (!errorKey) return undefined;
      // Check if it's a translation key
      if (errorKey.startsWith('validation.')) {
        return t(`collectionForm.${errorKey}`);
      }
      return errorKey;
    },
    [t]
  );

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid={testId}
      noValidate
    >
      {/* Service Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="collection-service" className={styles.label}>
          {t('collections.service')}
          <span className={styles.required} aria-hidden="true">*</span>
        </label>
        <select
          id="collection-service"
          className={`${styles.select} ${errors.serviceId ? styles.inputError : ''}`}
          disabled={isEditMode || isSubmitting || servicesLoading}
          aria-required="true"
          aria-invalid={!!errors.serviceId}
          aria-describedby={errors.serviceId ? 'service-error' : undefined}
          data-testid="collection-service-select"
          {...register('serviceId')}
        >
          <option value="">{servicesLoading ? t('common.loading') : t('collections.selectService')}</option>
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
        {errors.serviceId && (
          <span
            id="service-error"
            className={styles.errorMessage}
            role="alert"
            data-testid="service-error"
          >
            {getErrorMessage(errors.serviceId.message)}
          </span>
        )}
      </div>

      {/* Name Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="collection-name" className={styles.label}>
          {t('collections.collectionName')}
          <span className={styles.required} aria-hidden="true">*</span>
        </label>
        <input
          id="collection-name"
          type="text"
          className={`${styles.input} ${errors.name ? styles.inputError : ''}`}
          placeholder={t('collectionForm.namePlaceholder')}
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'name-error' : (!isEditMode ? 'name-hint' : undefined)}
          data-testid="collection-name-input"
          {...register('name')}
        />
        {errors.name && (
          <span
            id="name-error"
            className={styles.errorMessage}
            role="alert"
            data-testid="name-error"
          >
            {getErrorMessage(errors.name.message)}
          </span>
        )}
        {!isEditMode && (
          <span id="name-hint" className={styles.hint} data-testid="name-hint">
            {t('collectionForm.nameHint')}
          </span>
        )}
      </div>

      {/* Display Name Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="collection-display-name" className={styles.label}>
          {t('collections.displayName')}
          <span className={styles.required} aria-hidden="true">*</span>
        </label>
        <input
          id="collection-display-name"
          type="text"
          className={`${styles.input} ${errors.displayName ? styles.inputError : ''}`}
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
            className={styles.errorMessage}
            role="alert"
            data-testid="display-name-error"
          >
            {getErrorMessage(errors.displayName.message)}
          </span>
        )}
      </div>

      {/* Description Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="collection-description" className={styles.label}>
          {t('collections.description')}
          <span className={styles.optional}>({t('common.optional')})</span>
        </label>
        <textarea
          id="collection-description"
          className={`${styles.textarea} ${errors.description ? styles.inputError : ''}`}
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
            className={styles.errorMessage}
            role="alert"
            data-testid="description-error"
          >
            {getErrorMessage(errors.description.message)}
          </span>
        )}
      </div>

      {/* Storage Mode Field */}
      <div className={styles.fieldGroup}>
        <label htmlFor="collection-storage-mode" className={styles.label}>
          {t('collections.storageMode')}
          <span className={styles.required} aria-hidden="true">*</span>
        </label>
        <select
          id="collection-storage-mode"
          className={`${styles.select} ${errors.storageMode ? styles.inputError : ''}`}
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.storageMode}
          aria-describedby={errors.storageMode ? 'storage-mode-error' : (!isEditMode ? 'storage-mode-hint' : undefined)}
          data-testid="collection-storage-mode-select"
          {...register('storageMode')}
        >
          <option value="JSONB">{t('collections.storageModes.jsonb')}</option>
          <option value="PHYSICAL_TABLE">{t('collections.storageModes.physicalTable')}</option>
        </select>
        {errors.storageMode && (
          <span
            id="storage-mode-error"
            className={styles.errorMessage}
            role="alert"
            data-testid="storage-mode-error"
          >
            {getErrorMessage(errors.storageMode.message)}
          </span>
        )}
        {!isEditMode && (
          <span id="storage-mode-hint" className={styles.hint} data-testid="storage-mode-hint">
            {t('collectionForm.storageModeHint')}
          </span>
        )}
      </div>

      {/* Active Status Field */}
      <div className={styles.fieldGroup}>
        <div className={styles.checkboxGroup}>
          <input
            id="collection-active"
            type="checkbox"
            className={styles.checkbox}
            disabled={isSubmitting}
            aria-describedby="active-hint"
            data-testid="collection-active-checkbox"
            {...register('active')}
          />
          <label htmlFor="collection-active" className={styles.checkboxLabel}>
            {t('collections.active')}
          </label>
        </div>
        <span id="active-hint" className={styles.hint} data-testid="active-hint">
          {t('collectionForm.activeHint')}
        </span>
      </div>

      {/* Form Actions */}
      <div className={styles.actions}>
        <button
          type="button"
          className={styles.cancelButton}
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="collection-form-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className={styles.submitButton}
          disabled={isSubmitting || (!isDirty && isEditMode)}
          data-testid="collection-form-submit"
        >
          {isSubmitting ? (
            <>
              <LoadingSpinner size="small" />
              <span className={styles.submitText}>
                {isEditMode ? t('common.save') : t('common.create')}
              </span>
            </>
          ) : (
            isEditMode ? t('common.save') : t('common.create')
          )}
        </button>
      </div>
    </form>
  );
}

export default CollectionForm;
