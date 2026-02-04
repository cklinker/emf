/**
 * OIDCProvidersPage Component
 *
 * Displays a list of all OIDC providers with status indicators and provides
 * add, edit, delete, and test connection functionality.
 * Uses TanStack Query for data fetching and includes a modal form for provider management.
 *
 * Requirements:
 * - 6.1: Display a list of all OIDC providers with status (active/inactive)
 * - 6.2: Add new OIDC provider action
 * - 6.7: Delete OIDC provider with confirmation dialog
 * - 6.8: Test connection functionality to verify provider configuration
 * - 6.9: Display provider status (connected/disconnected)
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../../context/I18nContext';
import { useApi } from '../../context/ApiContext';
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components';
import styles from './OIDCProvidersPage.module.css';

/**
 * OIDC Provider interface matching the API response
 */
export interface OIDCProvider {
  id: string;
  name: string;
  issuer: string;
  clientId: string;
  scopes: string[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
  // Claim mapping fields (Requirements 6.1, 6.2)
  rolesClaim?: string;
  rolesMapping?: string;
  emailClaim?: string;
  usernameClaim?: string;
  nameClaim?: string;
}

/**
 * Form data for creating/editing an OIDC provider
 */
interface OIDCProviderFormData {
  name: string;
  issuer: string;
  clientId: string;
  clientSecret: string;
  scopes: string;
  // Claim mapping fields (Requirements 6.1, 6.2)
  rolesClaim?: string;
  rolesMapping?: string;
  emailClaim?: string;
  usernameClaim?: string;
  nameClaim?: string;
}

/**
 * Form validation errors
 */
interface FormErrors {
  name?: string;
  issuer?: string;
  clientId?: string;
  clientSecret?: string;
  scopes?: string;
  // Claim mapping error fields (Requirement 6.6)
  rolesClaim?: string;
  rolesMapping?: string;
  emailClaim?: string;
  usernameClaim?: string;
  nameClaim?: string;
}

/**
 * Test connection result
 */
interface TestConnectionResult {
  success: boolean;
  message: string;
}

/**
 * Props for OIDCProvidersPage component
 */
export interface OIDCProvidersPageProps {
  /** Optional test ID for testing */
  testId?: string;
}

/**
 * Validate OIDC provider form data
 */
function validateForm(data: OIDCProviderFormData, t: (key: string) => string, isEditing: boolean): FormErrors {
  const errors: FormErrors = {};

  // Name validation
  if (!data.name.trim()) {
    errors.name = t('oidc.validation.nameRequired');
  } else if (data.name.length > 100) {
    errors.name = t('oidc.validation.nameTooLong');
  }

  // Issuer URL validation
  if (!data.issuer.trim()) {
    errors.issuer = t('oidc.validation.issuerRequired');
  } else {
    try {
      new URL(data.issuer);
    } catch {
      errors.issuer = t('oidc.validation.issuerInvalid');
    }
  }

  // Client ID validation
  if (!data.clientId.trim()) {
    errors.clientId = t('oidc.validation.clientIdRequired');
  } else if (data.clientId.length > 255) {
    errors.clientId = t('oidc.validation.clientIdTooLong');
  }

  // Client Secret validation (required only for new providers)
  if (!isEditing && !data.clientSecret.trim()) {
    errors.clientSecret = t('oidc.validation.clientSecretRequired');
  }

  // Scopes validation
  if (!data.scopes.trim()) {
    errors.scopes = t('oidc.validation.scopesRequired');
  }

  // Roles mapping JSON validation (Requirement 6.5)
  if (data.rolesMapping && data.rolesMapping.trim()) {
    try {
      JSON.parse(data.rolesMapping);
    } catch {
      errors.rolesMapping = t('oidc.validation.rolesMappingInvalidJson');
    }
  }

  // Claim path length validations (Requirement 6.6)
  if (data.rolesClaim && data.rolesClaim.length > 200) {
    errors.rolesClaim = t('oidc.validation.claimPathTooLong');
  }
  if (data.emailClaim && data.emailClaim.length > 200) {
    errors.emailClaim = t('oidc.validation.claimPathTooLong');
  }
  if (data.usernameClaim && data.usernameClaim.length > 200) {
    errors.usernameClaim = t('oidc.validation.claimPathTooLong');
  }
  if (data.nameClaim && data.nameClaim.length > 200) {
    errors.nameClaim = t('oidc.validation.claimPathTooLong');
  }

  return errors;
}

/**
 * OIDCProviderForm Component
 *
 * Modal form for creating and editing OIDC providers.
 */
interface OIDCProviderFormProps {
  provider?: OIDCProvider;
  onSubmit: (data: OIDCProviderFormData) => void;
  onCancel: () => void;
  isSubmitting: boolean;
}

function OIDCProviderForm({ provider, onSubmit, onCancel, isSubmitting }: OIDCProviderFormProps): React.ReactElement {
  const { t } = useI18n();
  const isEditing = !!provider;
  const [formData, setFormData] = useState<OIDCProviderFormData>({
    name: provider?.name ?? '',
    issuer: provider?.issuer ?? '',
    clientId: provider?.clientId ?? '',
    clientSecret: '',
    scopes: provider?.scopes?.join(', ') ?? 'openid, profile, email, roles',
    // Claim mapping fields (Requirement 6.7)
    rolesClaim: provider?.rolesClaim ?? '',
    rolesMapping: provider?.rolesMapping ?? '',
    emailClaim: provider?.emailClaim ?? '',
    usernameClaim: provider?.usernameClaim ?? '',
    nameClaim: provider?.nameClaim ?? '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const nameInputRef = useRef<HTMLInputElement>(null);

  // Focus name input on mount
  useEffect(() => {
    nameInputRef.current?.focus();
  }, []);

  const handleChange = useCallback((field: keyof OIDCProviderFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear error when user starts typing
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  }, [errors]);

  const handleBlur = useCallback((field: keyof OIDCProviderFormData) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
    // Validate on blur
    const validationErrors = validateForm(formData, t, isEditing);
    if (validationErrors[field]) {
      setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }));
    }
  }, [formData, t, isEditing]);

  const handleSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    
    // Validate all fields
    const validationErrors = validateForm(formData, t, isEditing);
    setErrors(validationErrors);
    setTouched({ name: true, issuer: true, clientId: true, clientSecret: true, scopes: true });

    // If no errors, submit
    if (Object.keys(validationErrors).length === 0) {
      onSubmit(formData);
    }
  }, [formData, onSubmit, t, isEditing]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      onCancel();
    }
  }, [onCancel]);

  const title = isEditing ? t('oidc.editProvider') : t('oidc.addProvider');

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="oidc-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="oidc-form-title"
        data-testid="oidc-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="oidc-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="oidc-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            {/* Name Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-name" className={styles.formLabel}>
                {t('oidc.providerName')}
                <span className={styles.required} aria-hidden="true">*</span>
              </label>
              <input
                ref={nameInputRef}
                id="oidc-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder={t('oidc.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'oidc-name-error' : undefined}
                disabled={isSubmitting}
                data-testid="oidc-name-input"
              />
              {touched.name && errors.name && (
                <span id="oidc-name-error" className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            {/* Issuer URL Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-issuer" className={styles.formLabel}>
                {t('oidc.issuer')}
                <span className={styles.required} aria-hidden="true">*</span>
              </label>
              <input
                id="oidc-issuer"
                type="url"
                className={`${styles.formInput} ${touched.issuer && errors.issuer ? styles.hasError : ''}`}
                value={formData.issuer}
                onChange={(e) => handleChange('issuer', e.target.value)}
                onBlur={() => handleBlur('issuer')}
                placeholder={t('oidc.issuerPlaceholder')}
                aria-required="true"
                aria-invalid={touched.issuer && !!errors.issuer}
                aria-describedby={errors.issuer ? 'oidc-issuer-error' : 'oidc-issuer-hint'}
                disabled={isSubmitting}
                data-testid="oidc-issuer-input"
              />
              <span id="oidc-issuer-hint" className={styles.formHint}>
                {t('oidc.issuerHint')}
              </span>
              {touched.issuer && errors.issuer && (
                <span id="oidc-issuer-error" className={styles.formError} role="alert">
                  {errors.issuer}
                </span>
              )}
            </div>

            {/* Client ID Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-client-id" className={styles.formLabel}>
                {t('oidc.clientId')}
                <span className={styles.required} aria-hidden="true">*</span>
              </label>
              <input
                id="oidc-client-id"
                type="text"
                className={`${styles.formInput} ${touched.clientId && errors.clientId ? styles.hasError : ''}`}
                value={formData.clientId}
                onChange={(e) => handleChange('clientId', e.target.value)}
                onBlur={() => handleBlur('clientId')}
                placeholder={t('oidc.clientIdPlaceholder')}
                aria-required="true"
                aria-invalid={touched.clientId && !!errors.clientId}
                aria-describedby={errors.clientId ? 'oidc-client-id-error' : undefined}
                disabled={isSubmitting}
                data-testid="oidc-client-id-input"
              />
              {touched.clientId && errors.clientId && (
                <span id="oidc-client-id-error" className={styles.formError} role="alert">
                  {errors.clientId}
                </span>
              )}
            </div>

            {/* Client Secret Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-client-secret" className={styles.formLabel}>
                {t('oidc.clientSecret')}
                {!isEditing && <span className={styles.required} aria-hidden="true">*</span>}
              </label>
              <input
                id="oidc-client-secret"
                type="password"
                className={`${styles.formInput} ${touched.clientSecret && errors.clientSecret ? styles.hasError : ''}`}
                value={formData.clientSecret}
                onChange={(e) => handleChange('clientSecret', e.target.value)}
                onBlur={() => handleBlur('clientSecret')}
                placeholder={isEditing ? t('oidc.clientSecretPlaceholderEdit') : t('oidc.clientSecretPlaceholder')}
                aria-required={!isEditing}
                aria-invalid={touched.clientSecret && !!errors.clientSecret}
                aria-describedby={errors.clientSecret ? 'oidc-client-secret-error' : 'oidc-client-secret-hint'}
                disabled={isSubmitting}
                data-testid="oidc-client-secret-input"
              />
              <span id="oidc-client-secret-hint" className={styles.formHint}>
                {isEditing ? t('oidc.clientSecretHintEdit') : t('oidc.clientSecretHint')}
              </span>
              {touched.clientSecret && errors.clientSecret && (
                <span id="oidc-client-secret-error" className={styles.formError} role="alert">
                  {errors.clientSecret}
                </span>
              )}
            </div>

            {/* Scopes Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-scopes" className={styles.formLabel}>
                {t('oidc.scopes')}
                <span className={styles.required} aria-hidden="true">*</span>
              </label>
              <input
                id="oidc-scopes"
                type="text"
                className={`${styles.formInput} ${touched.scopes && errors.scopes ? styles.hasError : ''}`}
                value={formData.scopes}
                onChange={(e) => handleChange('scopes', e.target.value)}
                onBlur={() => handleBlur('scopes')}
                placeholder={t('oidc.scopesPlaceholder')}
                aria-required="true"
                aria-invalid={touched.scopes && !!errors.scopes}
                aria-describedby={errors.scopes ? 'oidc-scopes-error' : 'oidc-scopes-hint'}
                disabled={isSubmitting}
                data-testid="oidc-scopes-input"
              />
              <span id="oidc-scopes-hint" className={styles.formHint}>
                {t('oidc.scopesHint')}
              </span>
              {touched.scopes && errors.scopes && (
                <span id="oidc-scopes-error" className={styles.formError} role="alert">
                  {errors.scopes}
                </span>
              )}
            </div>

            {/* Roles Claim Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-roles-claim" className={styles.formLabel}>
                {t('oidc.rolesClaim')}
              </label>
              <input
                id="oidc-roles-claim"
                type="text"
                className={`${styles.formInput} ${touched.rolesClaim && errors.rolesClaim ? styles.hasError : ''}`}
                value={formData.rolesClaim || ''}
                onChange={(e) => handleChange('rolesClaim', e.target.value)}
                onBlur={() => handleBlur('rolesClaim')}
                placeholder="roles, realm_access.roles, groups"
                aria-describedby="oidc-roles-claim-hint"
                disabled={isSubmitting}
                data-testid="oidc-roles-claim-input"
              />
              <span id="oidc-roles-claim-hint" className={styles.formHint}>
                {t('oidc.rolesClaimHint')}
              </span>
              {touched.rolesClaim && errors.rolesClaim && (
                <span className={styles.formError} role="alert">
                  {errors.rolesClaim}
                </span>
              )}
            </div>

            {/* Roles Mapping Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-roles-mapping" className={styles.formLabel}>
                {t('oidc.rolesMapping')}
              </label>
              <textarea
                id="oidc-roles-mapping"
                className={`${styles.formTextarea} ${touched.rolesMapping && errors.rolesMapping ? styles.hasError : ''}`}
                value={formData.rolesMapping || ''}
                onChange={(e) => handleChange('rolesMapping', e.target.value)}
                onBlur={() => handleBlur('rolesMapping')}
                placeholder='{"external-admin": "ADMIN", "external-user": "USER"}'
                rows={4}
                aria-describedby="oidc-roles-mapping-hint"
                disabled={isSubmitting}
                data-testid="oidc-roles-mapping-input"
              />
              <span id="oidc-roles-mapping-hint" className={styles.formHint}>
                {t('oidc.rolesMappingHint')}
              </span>
              {touched.rolesMapping && errors.rolesMapping && (
                <span className={styles.formError} role="alert">
                  {errors.rolesMapping}
                </span>
              )}
            </div>

            {/* Email Claim Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-email-claim" className={styles.formLabel}>
                {t('oidc.emailClaim')}
              </label>
              <input
                id="oidc-email-claim"
                type="text"
                className={`${styles.formInput} ${touched.emailClaim && errors.emailClaim ? styles.hasError : ''}`}
                value={formData.emailClaim || ''}
                onChange={(e) => handleChange('emailClaim', e.target.value)}
                onBlur={() => handleBlur('emailClaim')}
                placeholder="email (default)"
                aria-describedby="oidc-email-claim-hint"
                disabled={isSubmitting}
                data-testid="oidc-email-claim-input"
              />
              <span id="oidc-email-claim-hint" className={styles.formHint}>
                {t('oidc.emailClaimHint')}
              </span>
              {touched.emailClaim && errors.emailClaim && (
                <span className={styles.formError} role="alert">
                  {errors.emailClaim}
                </span>
              )}
            </div>

            {/* Username Claim Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-username-claim" className={styles.formLabel}>
                {t('oidc.usernameClaim')}
              </label>
              <input
                id="oidc-username-claim"
                type="text"
                className={`${styles.formInput} ${touched.usernameClaim && errors.usernameClaim ? styles.hasError : ''}`}
                value={formData.usernameClaim || ''}
                onChange={(e) => handleChange('usernameClaim', e.target.value)}
                onBlur={() => handleBlur('usernameClaim')}
                placeholder="preferred_username (default)"
                aria-describedby="oidc-username-claim-hint"
                disabled={isSubmitting}
                data-testid="oidc-username-claim-input"
              />
              <span id="oidc-username-claim-hint" className={styles.formHint}>
                {t('oidc.usernameClaimHint')}
              </span>
              {touched.usernameClaim && errors.usernameClaim && (
                <span className={styles.formError} role="alert">
                  {errors.usernameClaim}
                </span>
              )}
            </div>

            {/* Name Claim Field */}
            <div className={styles.formGroup}>
              <label htmlFor="oidc-name-claim" className={styles.formLabel}>
                {t('oidc.nameClaim')}
              </label>
              <input
                id="oidc-name-claim"
                type="text"
                className={`${styles.formInput} ${touched.nameClaim && errors.nameClaim ? styles.hasError : ''}`}
                value={formData.nameClaim || ''}
                onChange={(e) => handleChange('nameClaim', e.target.value)}
                onBlur={() => handleBlur('nameClaim')}
                placeholder="name (default)"
                aria-describedby="oidc-name-claim-hint"
                disabled={isSubmitting}
                data-testid="oidc-name-claim-input"
              />
              <span id="oidc-name-claim-hint" className={styles.formHint}>
                {t('oidc.nameClaimHint')}
              </span>
              {touched.nameClaim && errors.nameClaim && (
                <span className={styles.formError} role="alert">
                  {errors.nameClaim}
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
                data-testid="oidc-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="oidc-form-submit"
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
 * OIDCProvidersPage Component
 *
 * Main page for managing OIDC providers in the EMF Admin UI.
 * Provides listing and CRUD operations for OIDC providers.
 */
export function OIDCProvidersPage({ testId = 'oidc-providers-page' }: OIDCProvidersPageProps): React.ReactElement {
  const queryClient = useQueryClient();
  const { t, formatDate } = useI18n();
  const { apiClient } = useApi();
  const { showToast } = useToast();

  // Modal state
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<OIDCProvider | undefined>(undefined);

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [providerToDelete, setProviderToDelete] = useState<OIDCProvider | null>(null);

  // Test connection state
  const [testingProviderId, setTestingProviderId] = useState<string | null>(null);

  // Fetch OIDC providers query
  const {
    data: providers = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['oidc-providers'],
    queryFn: () => apiClient.get<OIDCProvider[]>('/control/oidc/providers'),
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: OIDCProviderFormData) => {
      const payload: any = {
        ...data,
        scopes: data.scopes.split(',').map((s) => s.trim()).filter(Boolean),
      };
      // Only include claim fields if they have values (Requirement 12.2)
      if (data.rolesClaim?.trim()) payload.rolesClaim = data.rolesClaim.trim();
      if (data.rolesMapping?.trim()) payload.rolesMapping = data.rolesMapping.trim();
      if (data.emailClaim?.trim()) payload.emailClaim = data.emailClaim.trim();
      if (data.usernameClaim?.trim()) payload.usernameClaim = data.usernameClaim.trim();
      if (data.nameClaim?.trim()) payload.nameClaim = data.nameClaim.trim();
      return apiClient.post<OIDCProvider>('/control/oidc/providers', payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['oidc-providers'] });
      showToast(t('success.created', { item: t('navigation.oidcProviders') }), 'success');
      handleCloseForm();
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: OIDCProviderFormData }) => {
      const payload: any = {
        ...data,
        scopes: data.scopes.split(',').map((s) => s.trim()).filter(Boolean),
      };
      // Only include claim fields if they have values (Requirement 12.2)
      if (data.rolesClaim?.trim()) payload.rolesClaim = data.rolesClaim.trim();
      if (data.rolesMapping?.trim()) payload.rolesMapping = data.rolesMapping.trim();
      if (data.emailClaim?.trim()) payload.emailClaim = data.emailClaim.trim();
      if (data.usernameClaim?.trim()) payload.usernameClaim = data.usernameClaim.trim();
      if (data.nameClaim?.trim()) payload.nameClaim = data.nameClaim.trim();
      return apiClient.put<OIDCProvider>(`/control/oidc/providers/${id}`, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['oidc-providers'] });
      showToast(t('success.updated', { item: t('navigation.oidcProviders') }), 'success');
      handleCloseForm();
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/oidc/providers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['oidc-providers'] });
      showToast(t('success.deleted', { item: t('navigation.oidcProviders') }), 'success');
      setDeleteDialogOpen(false);
      setProviderToDelete(null);
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error');
    },
  });

  // Test connection mutation
  const testConnectionMutation = useMutation({
    mutationFn: (id: string) => apiClient.post<TestConnectionResult>(`/control/oidc/providers/${id}/test`, {}),
    onSuccess: (result) => {
      if (result.success) {
        showToast(t('oidc.connectionSuccess'), 'success');
      } else {
        showToast(`${t('oidc.connectionFailed')}: ${result.message}`, 'error');
      }
      setTestingProviderId(null);
    },
    onError: (error: Error) => {
      showToast(`${t('oidc.connectionFailed')}: ${error.message}`, 'error');
      setTestingProviderId(null);
    },
  });

  // Handle create action
  const handleCreate = useCallback(() => {
    setEditingProvider(undefined);
    setIsFormOpen(true);
  }, []);

  // Handle edit action
  const handleEdit = useCallback((provider: OIDCProvider) => {
    setEditingProvider(provider);
    setIsFormOpen(true);
  }, []);

  // Handle close form
  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false);
    setEditingProvider(undefined);
  }, []);

  // Handle form submit
  const handleFormSubmit = useCallback((data: OIDCProviderFormData) => {
    if (editingProvider) {
      updateMutation.mutate({ id: editingProvider.id, data });
    } else {
      createMutation.mutate(data);
    }
  }, [editingProvider, createMutation, updateMutation]);

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback((provider: OIDCProvider) => {
    setProviderToDelete(provider);
    setDeleteDialogOpen(true);
  }, []);

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (providerToDelete) {
      deleteMutation.mutate(providerToDelete.id);
    }
  }, [providerToDelete, deleteMutation]);

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false);
    setProviderToDelete(null);
  }, []);

  // Handle test connection
  const handleTestConnection = useCallback((provider: OIDCProvider) => {
    setTestingProviderId(provider.id);
    testConnectionMutation.mutate(provider.id);
  }, [testConnectionMutation]);

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
        <h1 className={styles.title}>{t('oidc.title')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label={t('oidc.addProvider')}
          data-testid="add-provider-button"
        >
          {t('oidc.addProvider')}
        </button>
      </header>

      {/* Providers Table */}
      {providers.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('oidc.title')}
            data-testid="providers-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('oidc.providerName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('oidc.issuer')}
                </th>
                <th role="columnheader" scope="col">
                  {t('oidc.clientId')}
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
              {providers.map((provider, index) => (
                <tr
                  key={provider.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`provider-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    {provider.name}
                  </td>
                  <td role="gridcell" className={styles.issuerCell}>
                    <span className={styles.issuerUrl} title={provider.issuer}>
                      {provider.issuer}
                    </span>
                  </td>
                  <td role="gridcell" className={styles.clientIdCell}>
                    <code className={styles.clientIdCode}>{provider.clientId}</code>
                  </td>
                  <td role="gridcell" className={styles.statusCell}>
                    <StatusBadge active={provider.active} />
                  </td>
                  <td role="gridcell" className={styles.dateCell}>
                    {formatDate(new Date(provider.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.testButton}`}
                        onClick={() => handleTestConnection(provider)}
                        disabled={testingProviderId === provider.id}
                        aria-label={`${t('oidc.testConnection')} ${provider.name}`}
                        data-testid={`test-button-${index}`}
                      >
                        {testingProviderId === provider.id ? t('common.loading') : t('oidc.testConnection')}
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(provider)}
                        aria-label={`${t('common.edit')} ${provider.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(provider)}
                        aria-label={`${t('common.delete')} ${provider.name}`}
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

      {/* OIDC Provider Form Modal */}
      {isFormOpen && (
        <OIDCProviderForm
          provider={editingProvider}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('oidc.deleteProvider')}
        message={t('oidc.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  );
}

export default OIDCProvidersPage;
