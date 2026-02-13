/**
 * ResourceFormPage Component
 *
 * Generates a form from collection schema for creating and editing resources.
 * Supports different field types and handles form validation.
 * Integrates with the plugin system to use custom field renderers when available.
 *
 * Requirements:
 * - 11.6: Resource browser allows creating new resources
 * - 11.9: Resource browser allows editing existing resources
 * - 12.4: Use custom field renderers when registered, fall back to defaults
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'
import { usePlugins } from '../../context/PluginContext'
import { useApi } from '../../context/ApiContext'
import { useToast, LoadingSpinner, ErrorMessage } from '../../components'
import type { ApiClient } from '../../services/apiClient'
import styles from './ResourceFormPage.module.css'

/**
 * Field definition interface for collection schema
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference'
  required: boolean
  unique?: boolean
  indexed?: boolean
  defaultValue?: unknown
  referenceTarget?: string
  order?: number
  validation?: ValidationRule[]
}

/**
 * Validation rule interface
 */
export interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom'
  value?: unknown
  message?: string
}

/**
 * Collection schema interface
 */
export interface CollectionSchema {
  id: string
  name: string
  displayName: string
  description?: string
  fields: FieldDefinition[]
}

/**
 * Resource record interface
 */
export interface Resource {
  id: string
  [key: string]: unknown
}

/**
 * Form data type
 */
export type FormData = Record<string, unknown>

/**
 * Form errors type
 */
export type FormErrors = Record<string, string>

/**
 * Props for ResourceFormPage component
 */
export interface ResourceFormPageProps {
  /** Collection name from route params (optional, can be from useParams) */
  collectionName?: string
  /** Resource ID from route params (optional, for edit mode) */
  resourceId?: string
  /** Optional test ID for testing */
  testId?: string
}

// API functions using apiClient
async function fetchCollectionSchema(
  apiClient: ApiClient,
  collectionName: string
): Promise<CollectionSchema> {
  return apiClient.get(`/control/collections/${collectionName}`)
}

async function fetchResource(
  apiClient: ApiClient,
  collectionName: string,
  resourceId: string
): Promise<Resource> {
  return apiClient.get(`/api/${collectionName}/${resourceId}`)
}

async function createResource(
  apiClient: ApiClient,
  collectionName: string,
  data: FormData
): Promise<Resource> {
  return apiClient.post(`/api/${collectionName}`, data)
}

async function updateResource(
  apiClient: ApiClient,
  collectionName: string,
  resourceId: string,
  data: FormData
): Promise<Resource> {
  return apiClient.put(`/api/${collectionName}/${resourceId}`, data)
}

/**
 * Get default value for a field based on its type
 */
function getDefaultValueForType(field: FieldDefinition): unknown {
  if (field.defaultValue !== undefined) {
    return field.defaultValue
  }

  switch (field.type) {
    case 'string':
      return ''
    case 'number':
      return ''
    case 'boolean':
      return false
    case 'date':
    case 'datetime':
      return ''
    case 'json':
      return ''
    case 'reference':
      return ''
    default:
      return ''
  }
}

/**
 * Initialize form data from schema with default values
 */
function initializeFormData(schema: CollectionSchema): FormData {
  const data: FormData = {}
  const fields = Array.isArray(schema.fields) ? schema.fields : []
  fields.forEach((field) => {
    data[field.name] = getDefaultValueForType(field)
  })
  return data
}

/**
 * Populate form data from existing resource
 */
function populateFormData(schema: CollectionSchema, resource: Resource): FormData {
  const data: FormData = {}
  const fields = Array.isArray(schema.fields) ? schema.fields : []
  fields.forEach((field) => {
    const value = resource[field.name]
    if (value !== undefined && value !== null) {
      // Format dates for input fields
      if (field.type === 'date' && typeof value === 'string') {
        data[field.name] = value.split('T')[0]
      } else if (field.type === 'datetime' && typeof value === 'string') {
        // Format datetime for datetime-local input
        data[field.name] = value.slice(0, 16)
      } else if (field.type === 'json' && typeof value === 'object') {
        data[field.name] = JSON.stringify(value, null, 2)
      } else {
        data[field.name] = value
      }
    } else {
      data[field.name] = getDefaultValueForType(field)
    }
  })
  return data
}

/**
 * ResourceFormPage Component
 *
 * Main page for creating and editing resources.
 * Generates form fields based on collection schema.
 */
export function ResourceFormPage({
  collectionName: propCollectionName,
  resourceId: propResourceId,
  testId = 'resource-form-page',
}: ResourceFormPageProps): React.ReactElement {
  const { collection: routeCollection, id: routeResourceId } = useParams<{
    collection: string
    id?: string
  }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Get plugin system for custom field renderers
  // Requirement 12.4: Use registered field renderers when rendering fields
  const { getFieldRenderer } = usePlugins()

  // Get collection name and resource ID from props or route params
  const collectionName = propCollectionName || routeCollection || ''
  const resourceId = propResourceId || routeResourceId
  const isEditMode = !!resourceId

  // Clone mode: detect clone source from URL search params
  const [searchParams] = useSearchParams()
  const cloneSourceId = searchParams.get('clone')
  const isCloneMode = !!cloneSourceId && !!collectionName && !isEditMode

  // Fetch clone source record when cloning
  const { data: cloneSource } = useQuery({
    queryKey: ['clone-source', collectionName, cloneSourceId],
    queryFn: () => apiClient.get<Resource>(`/api/${collectionName}/${cloneSourceId}`),
    enabled: isCloneMode,
  })

  // Fetch collection schema
  const {
    data: schema,
    isLoading: schemaLoading,
    error: schemaError,
  } = useQuery({
    queryKey: ['collection-schema', collectionName],
    queryFn: () => fetchCollectionSchema(apiClient, collectionName),
    enabled: !!collectionName,
  })

  // Fetch resource data for edit mode
  const {
    data: resource,
    isLoading: resourceLoading,
    error: resourceError,
  } = useQuery({
    queryKey: ['resource', collectionName, resourceId],
    queryFn: () => fetchResource(apiClient, collectionName, resourceId!),
    enabled: !!collectionName && !!resourceId,
  })

  // Form state
  const [formData, setFormData] = useState<FormData>({})
  const [formErrors, setFormErrors] = useState<FormErrors>({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Track the key for which form was initialized
  const currentResourceKey = isEditMode
    ? resourceId
    : isCloneMode
      ? `clone-${cloneSourceId}`
      : 'new'
  const [initializedKey, setInitializedKey] = useState<string | null>(null)

  // Compute initial form data based on schema and resource (or clone source)
  const computedInitialData = useMemo(() => {
    if (!schema) return null
    if (isEditMode && resource) {
      return populateFormData(schema, resource)
    }
    if (isCloneMode && cloneSource) {
      // Clone mode: populate from source but exclude system fields
      const cloneData = Object.fromEntries(
        Object.entries(cloneSource).filter(
          ([key]) => key !== 'id' && key !== 'createdAt' && key !== 'updatedAt'
        )
      ) as Resource
      return populateFormData(schema, cloneData)
    }
    return initializeFormData(schema)
  }, [schema, resource, isEditMode, isCloneMode, cloneSource])

  // Initialize form data when data becomes available or resource changes
  if (computedInitialData && initializedKey !== currentResourceKey) {
    setInitializedKey(currentResourceKey ?? null)
    setFormData(computedInitialData)
    setFormErrors({})
  }

  // Sort fields by order
  const sortedFields = useMemo(() => {
    if (!schema?.fields) return []
    return [...schema.fields].sort((a, b) => {
      const orderA = a.order ?? 0
      const orderB = b.order ?? 0
      return orderA - orderB
    })
  }, [schema])

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: FormData) => createResource(apiClient, collectionName, data),
    onSuccess: (newResource) => {
      queryClient.invalidateQueries({ queryKey: ['resources', collectionName] })
      showToast(t('success.created', { item: t('resources.record') }), 'success')
      navigate(`/${getTenantSlug()}/resources/${collectionName}/${newResource.id}`)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
      setIsSubmitting(false)
    },
  })

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: FormData) => updateResource(apiClient, collectionName, resourceId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['resources', collectionName] })
      queryClient.invalidateQueries({ queryKey: ['resource', collectionName, resourceId] })
      showToast(t('success.updated', { item: t('resources.record') }), 'success')
      navigate(`/${getTenantSlug()}/resources/${collectionName}/${resourceId}`)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
      setIsSubmitting(false)
    },
  })

  /**
   * Validate a single field value
   */
  const validateField = useCallback(
    (field: FieldDefinition, value: unknown): string | null => {
      // Required validation
      if (field.required) {
        if (value === undefined || value === null || value === '') {
          return t('common.required')
        }
      }

      // Skip further validation if value is empty and not required
      if (value === undefined || value === null || value === '') {
        return null
      }

      // Type-specific validation
      switch (field.type) {
        case 'number': {
          const numValue = Number(value)
          if (isNaN(numValue)) {
            return t('resourceForm.validation.invalidNumber')
          }
          break
        }
        case 'json': {
          if (typeof value === 'string' && value.trim()) {
            try {
              JSON.parse(value)
            } catch {
              return t('resourceForm.validation.invalidJson')
            }
          }
          break
        }
        case 'date':
        case 'datetime': {
          if (typeof value === 'string' && value.trim()) {
            const date = new Date(value)
            if (isNaN(date.getTime())) {
              return t('resourceForm.validation.invalidDate')
            }
          }
          break
        }
      }

      // Custom validation rules
      if (field.validation) {
        for (const rule of field.validation) {
          switch (rule.type) {
            case 'min': {
              if (field.type === 'number') {
                const numValue = Number(value)
                if (numValue < Number(rule.value)) {
                  return (
                    rule.message ||
                    t('resourceForm.validation.minValue', { min: String(rule.value) })
                  )
                }
              } else if (field.type === 'string') {
                const strValue = String(value)
                if (strValue.length < Number(rule.value)) {
                  return (
                    rule.message ||
                    t('resourceForm.validation.minLength', { min: String(rule.value) })
                  )
                }
              }
              break
            }
            case 'max': {
              if (field.type === 'number') {
                const numValue = Number(value)
                if (numValue > Number(rule.value)) {
                  return (
                    rule.message ||
                    t('resourceForm.validation.maxValue', { max: String(rule.value) })
                  )
                }
              } else if (field.type === 'string') {
                const strValue = String(value)
                if (strValue.length > Number(rule.value)) {
                  return (
                    rule.message ||
                    t('resourceForm.validation.maxLength', { max: String(rule.value) })
                  )
                }
              }
              break
            }
            case 'pattern': {
              if (typeof value === 'string' && rule.value) {
                const regex = new RegExp(String(rule.value))
                if (!regex.test(value)) {
                  return rule.message || t('resourceForm.validation.pattern')
                }
              }
              break
            }
            case 'email': {
              if (typeof value === 'string') {
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
                if (!emailRegex.test(value)) {
                  return rule.message || t('resourceForm.validation.email')
                }
              }
              break
            }
            case 'url': {
              if (typeof value === 'string') {
                try {
                  new URL(value)
                } catch {
                  return rule.message || t('resourceForm.validation.url')
                }
              }
              break
            }
          }
        }
      }

      return null
    },
    [t]
  )

  /**
   * Validate all form fields
   */
  const validateForm = useCallback((): boolean => {
    const errors: FormErrors = {}
    let isValid = true

    sortedFields.forEach((field) => {
      const error = validateField(field, formData[field.name])
      if (error) {
        errors[field.name] = error
        isValid = false
      }
    })

    setFormErrors(errors)
    return isValid
  }, [sortedFields, formData, validateField])

  /**
   * Handle field value change
   */
  const handleFieldChange = useCallback(
    (fieldName: string, value: unknown) => {
      setFormData((prev) => ({
        ...prev,
        [fieldName]: value,
      }))

      // Clear error when field is modified
      if (formErrors[fieldName]) {
        setFormErrors((prev) => {
          const next = { ...prev }
          delete next[fieldName]
          return next
        })
      }
    },
    [formErrors]
  )

  /**
   * Handle form submission
   */
  const handleSubmit = useCallback(
    (event: React.FormEvent) => {
      event.preventDefault()

      if (!validateForm()) {
        showToast(t('errors.validation'), 'error')
        return
      }

      setIsSubmitting(true)

      // Prepare data for submission
      const submitData: FormData = {}
      sortedFields.forEach((field) => {
        let value = formData[field.name]

        // Convert values based on field type
        switch (field.type) {
          case 'number':
            if (value !== '' && value !== undefined && value !== null) {
              value = Number(value)
            } else {
              value = null
            }
            break
          case 'boolean':
            value = Boolean(value)
            break
          case 'json':
            if (typeof value === 'string' && value.trim()) {
              try {
                value = JSON.parse(value)
              } catch {
                // Keep as string if invalid JSON
              }
            } else {
              value = null
            }
            break
          case 'date':
          case 'datetime':
            if (value === '') {
              value = null
            }
            break
          case 'reference':
            if (value === '') {
              value = null
            }
            break
        }

        submitData[field.name] = value
      })

      if (isEditMode) {
        updateMutation.mutate(submitData)
      } else {
        createMutation.mutate(submitData)
      }
    },
    [validateForm, sortedFields, formData, isEditMode, updateMutation, createMutation, showToast, t]
  )

  /**
   * Handle cancel action
   */
  const handleCancel = useCallback(() => {
    if (isEditMode) {
      navigate(`/${getTenantSlug()}/resources/${collectionName}/${resourceId}`)
    } else {
      navigate(`/${getTenantSlug()}/resources/${collectionName}`)
    }
  }, [navigate, collectionName, resourceId, isEditMode])

  /**
   * Render form field based on field type
   * Requirement 11.6: Generate form from collection schema
   * Requirement 12.4: Use custom field renderers when registered, fall back to defaults
   */
  const renderField = useCallback(
    (field: FieldDefinition, index: number): React.ReactNode => {
      const value = formData[field.name]
      const error = formErrors[field.name]
      const fieldId = `field-${field.name}`
      const errorId = `${fieldId}-error`

      // Check for custom renderer from plugin system
      // Requirement 12.4: Use registered field renderers when rendering fields
      const CustomRenderer = getFieldRenderer(field.type)

      if (CustomRenderer) {
        // Use custom renderer from plugin
        return (
          <div key={field.id} className={styles.fieldGroup} data-testid={`field-group-${index}`}>
            <label htmlFor={fieldId} className={styles.label}>
              {field.displayName || field.name}
              {field.required && <span className={styles.required}>*</span>}
            </label>
            <div className={styles.fieldTypeHint}>
              {t(`fields.types.${field.type.toLowerCase()}`)}
              {field.referenceTarget && ` → ${field.referenceTarget}`}
            </div>
            <div data-testid={`custom-renderer-${field.name}`}>
              <CustomRenderer
                name={field.name}
                value={value}
                onChange={(newValue) => handleFieldChange(field.name, newValue)}
                error={error}
                metadata={{
                  fieldId,
                  type: field.type,
                  required: field.required,
                  referenceTarget: field.referenceTarget,
                  validation: field.validation,
                  displayName: field.displayName,
                }}
              />
            </div>
            {error && (
              <div
                id={errorId}
                className={styles.errorMessage}
                role="alert"
                data-testid={`error-${field.name}`}
              >
                {error}
              </div>
            )}
          </div>
        )
      }

      // Fall back to default renderers for unregistered types
      const commonProps = {
        id: fieldId,
        name: field.name,
        'aria-invalid': !!error,
        'aria-describedby': error ? errorId : undefined,
        'data-testid': `field-${field.name}`,
      }

      let input: React.ReactNode

      switch (field.type) {
        case 'string':
          input = (
            <input
              {...commonProps}
              type="text"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={String(value ?? '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              placeholder={field.displayName || field.name}
            />
          )
          break

        case 'number':
          input = (
            <input
              {...commonProps}
              type="number"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={value === '' || value === null || value === undefined ? '' : String(value)}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              placeholder={field.displayName || field.name}
              step="any"
            />
          )
          break

        case 'boolean':
          input = (
            <div className={styles.checkboxWrapper}>
              <input
                {...commonProps}
                type="checkbox"
                className={styles.checkbox}
                checked={Boolean(value)}
                onChange={(e) => handleFieldChange(field.name, e.target.checked)}
              />
              <span className={styles.checkboxLabel}>
                {value ? t('common.yes') : t('common.no')}
              </span>
            </div>
          )
          break

        case 'date':
          input = (
            <input
              {...commonProps}
              type="date"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={String(value ?? '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
            />
          )
          break

        case 'datetime':
          input = (
            <input
              {...commonProps}
              type="datetime-local"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={String(value ?? '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
            />
          )
          break

        case 'json':
          input = (
            <textarea
              {...commonProps}
              className={`${styles.textarea} ${styles.jsonTextarea} ${error ? styles.inputError : ''}`}
              value={String(value ?? '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              placeholder='{"key": "value"}'
              rows={5}
            />
          )
          break

        case 'reference':
          input = (
            <input
              {...commonProps}
              type="text"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={String(value ?? '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              placeholder={
                field.referenceTarget
                  ? t('resourceForm.referenceIdPlaceholder', { collection: field.referenceTarget })
                  : t('resourceForm.referenceId')
              }
            />
          )
          break

        default:
          input = (
            <input
              {...commonProps}
              type="text"
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              value={String(value ?? '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
            />
          )
      }

      return (
        <div key={field.id} className={styles.fieldGroup} data-testid={`field-group-${index}`}>
          <label htmlFor={fieldId} className={styles.label}>
            {field.displayName || field.name}
            {field.required && <span className={styles.required}>*</span>}
          </label>
          <div className={styles.fieldTypeHint}>
            {t(`fields.types.${field.type.toLowerCase()}`)}
            {field.referenceTarget && ` → ${field.referenceTarget}`}
          </div>
          {input}
          {error && (
            <div
              id={errorId}
              className={styles.errorMessage}
              role="alert"
              data-testid={`error-${field.name}`}
            >
              {error}
            </div>
          )}
        </div>
      )
    },
    [formData, formErrors, handleFieldChange, t, getFieldRenderer]
  )

  // Loading state
  const isLoading =
    schemaLoading || (isEditMode && resourceLoading) || (isCloneMode && !cloneSource)

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Error state - schema error
  if (schemaError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={schemaError instanceof Error ? schemaError : new Error(t('errors.generic'))}
          onRetry={() =>
            queryClient.invalidateQueries({ queryKey: ['collection-schema', collectionName] })
          }
        />
      </div>
    )
  }

  // Error state - resource error (edit mode)
  if (isEditMode && resourceError) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={resourceError instanceof Error ? resourceError : new Error(t('errors.generic'))}
          onRetry={() =>
            queryClient.invalidateQueries({ queryKey: ['resource', collectionName, resourceId] })
          }
        />
      </div>
    )
  }

  // Not found state
  if (!schema) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage error={new Error(t('errors.notFound'))} />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      {/* Page Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <nav className={styles.breadcrumb} aria-label="Breadcrumb">
            <Link to={`/${getTenantSlug()}/resources`} className={styles.breadcrumbLink}>
              {t('resources.title')}
            </Link>
            <span className={styles.breadcrumbSeparator} aria-hidden="true">
              /
            </span>
            <Link
              to={`/${getTenantSlug()}/resources/${collectionName}`}
              className={styles.breadcrumbLink}
            >
              {schema.displayName}
            </Link>
            <span className={styles.breadcrumbSeparator} aria-hidden="true">
              /
            </span>
            <span className={styles.breadcrumbCurrent}>
              {isEditMode
                ? t('resources.editRecord')
                : isCloneMode
                  ? t('resources.cloneRecord', { collection: schema.displayName })
                  : t('resources.createRecord')}
            </span>
          </nav>
          <h1 className={styles.title} data-testid="page-title">
            {isEditMode
              ? t('resources.editRecord')
              : isCloneMode
                ? t('resources.cloneRecord', { collection: schema.displayName })
                : t('resources.createRecord')}
          </h1>
          {isEditMode && resourceId && (
            <p className={styles.subtitle} data-testid="resource-id">
              ID: {resourceId}
            </p>
          )}
          {isCloneMode && cloneSourceId && (
            <div className={styles.cloneBanner} data-testid="clone-banner">
              <span>
                {t('resources.cloningFrom')} {cloneSourceId}
              </span>
              <Link
                to={`/${getTenantSlug()}/resources/${collectionName}/${cloneSourceId}`}
                className={styles.cloneSourceLink}
              >
                {t('resources.viewSource')}
              </Link>
            </div>
          )}
        </div>
      </header>

      {/* Form */}
      <form className={styles.form} onSubmit={handleSubmit} noValidate data-testid="resource-form">
        {/* Form Fields */}
        <div className={styles.fieldsContainer}>
          {sortedFields.length === 0 ? (
            <div className={styles.emptyState} data-testid="no-fields">
              <p>{t('resourceForm.noFields')}</p>
            </div>
          ) : (
            sortedFields.map((field, index) => renderField(field, index))
          )}
        </div>

        {/* Form Actions */}
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={handleCancel}
            disabled={isSubmitting}
            data-testid="cancel-button"
          >
            {t('common.cancel')}
          </button>
          <button
            type="submit"
            className={styles.submitButton}
            disabled={isSubmitting || sortedFields.length === 0}
            data-testid="submit-button"
          >
            {isSubmitting ? (
              <>
                <LoadingSpinner size="small" />
                {t('common.saving')}
              </>
            ) : (
              t('common.save')
            )}
          </button>
        </div>
      </form>
    </div>
  )
}

export default ResourceFormPage
