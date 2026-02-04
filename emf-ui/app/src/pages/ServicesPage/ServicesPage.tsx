/**
 * ServicesPage Component
 *
 * Displays a list of all domain services with status indicators and provides
 * add, edit, and delete functionality.
 * Uses TanStack Query for data fetching and includes a modal form for service management.
 *
 * Requirements:
 * - Display a list of all services with status (active/inactive)
 * - Add new service action
 * - Edit existing service
 * - Delete service with confirmation dialog
 * - Display service metadata (name, environment, base path, database URL)
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../../context/I18nContext';
import { useApi } from '../../context/ApiContext';
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components';
import styles from './ServicesPage.module.css';

/**
 * Service interface matching the API response
 */
export interface Service {
  id: string;
  name: string;
  displayName: string;
  description: string;
  basePath: string;
  environment: string;
  databaseUrl: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Form data for creating/editing a service
 */
interface ServiceFormData {
  name: string;
  displayName: string;
  description: string;
  basePath: string;
  environment: string;
  databaseUrl: string;
}

/**
 * Form validation errors
 */
interface FormErrors {
  name?: string;
  displayName?: string;
  description?: string;
  basePath?: string;
  environment?: string;
  databaseUrl?: string;
}

/**
 * Props for ServicesPage component
 */
export interface ServicesPageProps {
  /** Optional test ID for testing */
  testId?: string;
}

/**
 * Validate service form data
 */
function validateForm(data: ServiceFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {};

  // Name validation
  if (!data.name.trim()) {
    errors.name = t('services.validation.nameRequired');
  } else if (data.name.length > 100) {
    errors.name = t('services.validation.nameTooLong');
  } else if (!/^[a-z0-9-]+$/.test(data.name)) {
    errors.name = t('services.validation.nameInvalid');
  }

  // Display name validation
  if (data.displayName && data.displayName.length > 100) {
    errors.displayName = t('services.validation.displayNameTooLong');
  }

  // Description validation
  if (data.description && data.description.length > 500) {
    errors.description = t('services.validation.descriptionTooLong');
  }

  // Base path validation
  if (data.basePath && data.basePath.length > 100) {
    errors.basePath = t('services.validation.basePathTooLong');
  } else if (data.basePath && !/^\/[a-z0-9/-]*$/.test(data.basePath)) {
    errors.basePath = t('services.validation.basePathInvalid');
  }

  // Environment validation
  if (data.environment && data.environment.length > 50) {
    errors.environment = t('services.validation.environmentTooLong');
  }

  // Database URL validation
  if (data.databaseUrl && data.databaseUrl.length > 500) {
    errors.databaseUrl = t('services.validation.databaseUrlTooLong');
  }

  return errors;
}

/**
 * ServiceForm Component
 *
 * Modal form for creating and editing services.
 */
interface ServiceFormProps {
  service?: Service;
  onSubmit: (data: ServiceFormData) => void;
  onCancel: () => void;
  isSubmitting: boolean;
}

function ServiceForm({ service, onSubmit, onCancel, isSubmitting }: ServiceFormProps): React.ReactElement {
  const { t } = useI18n();
  const isEditing = !!service;
  const [formData, setFormData] = useState<ServiceFormData>({
    name: service?.name ?? '',
    displayName: service?.displayName ?? '',
    description: service?.description ?? '',
    basePath: service?.basePath ?? '/api',
    environment: service?.environment ?? '',
    databaseUrl: service?.databaseUrl ?? '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const nameInputRef = useRef<HTMLInputElement>(null);

  // Focus name input on mount
  useEffect(() => {
    nameInputRef.current?.focus();
  }, []);

  const handleChange = useCallback((field: keyof ServiceFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear error when user starts typing
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  }, [errors]);

  const handleBlur = useCallback((field: keyof ServiceFormData) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
    // Validate on blur
    const validationErrors = validateForm(formData, t);
    if (validationErrors[field]) {
      setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }));
    }
  }, [formData, t]);

  const handleSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    
    // Validate all fields
    const validationErrors = validateForm(formData, t);
    setErrors(validationErrors);
    setTouched({ name: true, displayName: true, description: true, basePath: true, environment: true, databaseUrl: true });

    // If no errors, submit
    if (Object.keys(validationErrors).length === 0) {
      onSubmit(formData);
    }
  }, [formData, onSubmit, t]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      onCancel();
    }
  }, [onCancel]);

  const title = isEditing ? t('services.editService') : t('services.addService');

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="service-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="service-form-title"
        data-testid="service-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="service-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="service-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            {/* Name Field */}
            <div className={styles.formGroup}>
              <label htmlFor="service-name" className={styles.formLabel}>
                {t('services.serviceName')}
                <span className={styles.required} aria-hidden="true">*</span>
              </label>
              <input
                ref={nameInputRef}
                id="service-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder={t('services.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'service-name-error' : 'service-name-hint'}
                disabled={isSubmitting || isEditing}
                data-testid="service-name-input"
              />
              <span id="service-name-hint" className={styles.formHint}>
                {t('services.nameHint')}
              </span>
              {touched.name && errors.name && (
                <span id="service-name-error" className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            {/* Display Name Field */}
            <div className={styles.formGroup}>
              <label htmlFor="service-display-name" className={styles.formLabel}>
                {t('services.displayName')}
              </label>
              <input
                id="service-display-name"
                type="text"
                className={`${styles.formInput} ${touched.displayName && errors.displayName ? styles.hasError : ''}`}
                value={formData.displayName}
                onChange={(e) => handleChange('displayName', e.target.value)}
                onBlur={() => handleBlur('displayName')}
                placeholder={t('services.displayNamePlaceholder')}
                aria-invalid={touched.displayName && !!errors.displayName}
                aria-describedby={errors.displayName ? 'service-display-name-error' : undefined}
                disabled={isSubmitting}
                data-testid="service-display-name-input"
              />
              {touched.displayName && errors.displayName && (
                <span id="service-display-name-error" className={styles.formError} role="alert">
                  {errors.displayName}
                </span>
              )}
            </div>

            {/* Description Field */}
            <div className={styles.formGroup}>
              <label htmlFor="service-description" className={styles.formLabel}>
                {t('services.description')}
              </label>
              <textarea
                id="service-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder={t('services.descriptionPlaceholder')}
                aria-invalid={touched.description && !!errors.description}
                aria-describedby={errors.description ? 'service-description-error' : undefined}
                disabled={isSubmitting}
                rows={3}
                data-testid="service-description-input"
              />
              {touched.description && errors.description && (
                <span id="service-description-error" className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            {/* Base Path Field */}
            <div className={styles.formGroup}>
              <label htmlFor="service-base-path" className={styles.formLabel}>
                {t('services.basePath')}
              </label>
              <input
                id="service-base-path"
                type="text"
                className={`${styles.formInput} ${touched.basePath && errors.basePath ? styles.hasError : ''}`}
                value={formData.basePath}
                onChange={(e) => handleChange('basePath', e.target.value)}
                onBlur={() => handleBlur('basePath')}
                placeholder="/api"
                aria-invalid={touched.basePath && !!errors.basePath}
                aria-describedby={errors.basePath ? 'service-base-path-error' : 'service-base-path-hint'}
                disabled={isSubmitting}
                data-testid="service-base-path-input"
              />
              <span id="service-base-path-hint" className={styles.formHint}>
                {t('services.basePathHint')}
              </span>
              {touched.basePath && errors.basePath && (
                <span id="service-base-path-error" className={styles.formError} role="alert">
                  {errors.basePath}
                </span>
              )}
            </div>

            {/* Environment Field */}
            <div className={styles.formGroup}>
              <label htmlFor="service-environment" className={styles.formLabel}>
                {t('services.environment')}
              </label>
              <input
                id="service-environment"
                type="text"
                className={`${styles.formInput} ${touched.environment && errors.environment ? styles.hasError : ''}`}
                value={formData.environment}
                onChange={(e) => handleChange('environment', e.target.value)}
                onBlur={() => handleBlur('environment')}
                placeholder={t('services.environmentPlaceholder')}
                aria-invalid={touched.environment && !!errors.environment}
                aria-describedby={errors.environment ? 'service-environment-error' : undefined}
                disabled={isSubmitting}
                data-testid="service-environment-input"
              />
              {touched.environment && errors.environment && (
                <span id="service-environment-error" className={styles.formError} role="alert">
                  {errors.environment}
                </span>
              )}
            </div>

            {/* Database URL Field */}
            <div className={styles.formGroup}>
              <label htmlFor="service-database-url" className={styles.formLabel}>
                {t('services.databaseUrl')}
              </label>
              <input
                id="service-database-url"
                type="text"
                className={`${styles.formInput} ${touched.databaseUrl && errors.databaseUrl ? styles.hasError : ''}`}
                value={formData.databaseUrl}
                onChange={(e) => handleChange('databaseUrl', e.target.value)}
                onBlur={() => handleBlur('databaseUrl')}
                placeholder={t('services.databaseUrlPlaceholder')}
                aria-invalid={touched.databaseUrl && !!errors.databaseUrl}
                aria-describedby={errors.databaseUrl ? 'service-database-url-error' : undefined}
                disabled={isSubmitting}
                data-testid="service-database-url-input"
              />
              {touched.databaseUrl && errors.databaseUrl && (
                <span id="service-database-url-error" className={styles.formError} role="alert">
                  {errors.databaseUrl}
                </span>
              )}
            </div>

            {/* Form Actions */}
            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="service-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="service-form-submit"
              >
                {isSubmitting ? t('common.loading') : t('common.save')}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

/**
 * Status Badge Component
 */
interface StatusBadgeProps {
  active: boolean;
}

function StatusBadge({ active }: StatusBadgeProps): React.ReactElement {
  const { t } = useI18n();
  return (
    <span
      className={`${styles.statusBadge} ${active ? styles.statusActive : styles.statusInactive}`}
      data-testid="status-badge"
    >
      {active ? t('collections.active') : t('collections.inactive')}
    </span>
  );
}

/**
 * ServicesPage Component
 *
 * Main page for managing domain services in the EMF Admin UI.
 * Provides listing and CRUD operations for services.
 */
export function ServicesPage({ testId = 'services-page' }: ServicesPageProps): React.ReactElement {
  const queryClient = useQueryClient();
  const { t, formatDate } = useI18n();
  const { apiClient } = useApi();
  const { showToast } = useToast();

  // Modal state
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingService, setEditingService] = useState<Service | undefined>(undefined);

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [serviceToDelete, setServiceToDelete] = useState<Service | null>(null);

  // Fetch services query
  const {
    data: servicesPage,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['services'],
    queryFn: () => apiClient.get<{ content: Service[] }>('/control/services'),
  });

  const services = servicesPage?.content ?? [];

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: ServiceFormData) => 
      apiClient.post<Service>('/control/services', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] });
      showToast(t('success.created', { item: t('navigation.services') }), 'success');
      handleCloseForm();
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ServiceFormData }) => 
      apiClient.put<Service>(`/control/services/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] });
      showToast(t('success.updated', { item: t('navigation.services') }), 'success');
      handleCloseForm();
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/services/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] });
      showToast(t('success.deleted', { item: t('navigation.services') }), 'success');
      setDeleteDialogOpen(false);
      setServiceToDelete(null);
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Handle create action
  const handleCreate = useCallback(() => {
    setEditingService(undefined);
    setIsFormOpen(true);
  }, []);

  // Handle edit action
  const handleEdit = useCallback((service: Service) => {
    setEditingService(service);
    setIsFormOpen(true);
  }, []);

  // Handle close form
  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false);
    setEditingService(undefined);
  }, []);

  // Handle form submit
  const handleFormSubmit = useCallback((data: ServiceFormData) => {
    if (editingService) {
      updateMutation.mutate({ id: editingService.id, data });
    } else {
      createMutation.mutate(data);
    }
  }, [editingService, createMutation, updateMutation]);

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback((service: Service) => {
    setServiceToDelete(service);
    setDeleteDialogOpen(true);
  }, []);

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (serviceToDelete) {
      deleteMutation.mutate(serviceToDelete.id);
    }
  }, [serviceToDelete, deleteMutation]);

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false);
    setServiceToDelete(null);
  }, []);

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    );
  }

  // Render error state
  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => refetch()}
        />
      </div>
    );
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending;

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <h1 className={styles.title}>{t('services.title')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label={t('services.addService')}
          data-testid="add-service-button"
        >
          {t('services.addService')}
        </button>
      </header>

      {/* Services Table */}
      {services.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('services.title')}
            data-testid="services-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('services.serviceName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('services.displayName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('services.environment')}
                </th>
                <th role="columnheader" scope="col">
                  {t('services.basePath')}
                </th>
                <th role="columnheader" scope="col">
                  {t('collections.status')}
                </th>
                <th role="columnheader" scope="col">
                  {t('collections.created')}
                </th>
                <th role="columnheader" scope="col">
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {services.map((service, index) => (
                <tr
                  key={service.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`service-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    <code>{service.name}</code>
                  </td>
                  <td role="gridcell" className={styles.displayNameCell}>
                    {service.displayName || '-'}
                  </td>
                  <td role="gridcell" className={styles.environmentCell}>
                    {service.environment || '-'}
                  </td>
                  <td role="gridcell" className={styles.basePathCell}>
                    <code>{service.basePath}</code>
                  </td>
                  <td role="gridcell" className={styles.statusCell}>
                    <StatusBadge active={service.active} />
                  </td>
                  <td role="gridcell" className={styles.dateCell}>
                    {formatDate(new Date(service.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(service)}
                        aria-label={`${t('common.edit')} ${service.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(service)}
                        aria-label={`${t('common.delete')} ${service.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        {t('common.delete')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Service Form Modal */}
      {isFormOpen && (
        <ServiceForm
          service={editingService}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('services.deleteService')}
        message={t('services.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  );
}

export default ServicesPage;
