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
import { useToast, LoadingSpinner, ErrorMessage, LookupSelect } from '../../components'
import type { LookupOption } from '../../components'
import { unwrapResource, wrapResource } from '../../utils/jsonapi'
import { ApiError } from '../../services/apiClient'
import type { ApiClient } from '../../services/apiClient'
import styles from './ResourceFormPage.module.css'

/**
 * Field definition interface for collection schema
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
  type:
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
  required: boolean
  unique?: boolean
  indexed?: boolean
  defaultValue?: unknown
  referenceTarget?: string
  referenceCollectionId?: string
  fieldTypeConfig?: string
  enumValues?: string[]
  lookupOptions?: LookupOption[]
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
  displayFieldId?: string
  displayFieldName?: string
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

/**
 * Reverse mapping from backend canonical types (uppercase) to UI types (lowercase).
 * The backend stores "DOUBLE" for what the UI calls "number", etc.
 */
const BACKEND_TYPE_TO_UI: Record<string, FieldDefinition['type']> = {
  DOUBLE: 'number',
  INTEGER: 'number',
  LONG: 'number',
  JSON: 'json',
  ARRAY: 'json',
}

function normalizeFieldType(backendType: string): FieldDefinition['type'] {
  const upper = backendType.toUpperCase()
  if (upper in BACKEND_TYPE_TO_UI) {
    return BACKEND_TYPE_TO_UI[upper]
  }
  return backendType.toLowerCase() as FieldDefinition['type']
}

/** Picklist value returned from the API */
interface PicklistValueDto {
  value: string
  label: string
  isDefault: boolean
  active: boolean
  sortOrder: number
}

// API functions using apiClient
async function fetchCollectionSchema(
  apiClient: ApiClient,
  collectionName: string
): Promise<CollectionSchema> {
  const response = await apiClient.get<CollectionSchema>(`/control/collections/${collectionName}`)
  // Normalize field types from backend canonical form (e.g. "PICKLIST") to
  // UI form (e.g. "picklist") so switch-case rendering works correctly.
  if (response.fields) {
    response.fields = response.fields.map((f) => ({
      ...f,
      type: normalizeFieldType(f.type),
    }))
  }
  return response
}

async function fetchResource(
  apiClient: ApiClient,
  collectionName: string,
  resourceId: string
): Promise<Resource> {
  const response = await apiClient.get(`/api/${collectionName}/${resourceId}`)
  return unwrapResource<Resource>(response)
}

async function createResource(
  apiClient: ApiClient,
  collectionName: string,
  data: FormData
): Promise<Resource> {
  const body = wrapResource(collectionName, data)
  const response = await apiClient.post(`/api/${collectionName}`, body)
  return unwrapResource<Resource>(response)
}

async function updateResource(
  apiClient: ApiClient,
  collectionName: string,
  resourceId: string,
  data: FormData
): Promise<Resource> {
  const body = wrapResource(collectionName, data, resourceId)
  const response = await apiClient.patch(`/api/${collectionName}/${resourceId}`, body)
  return unwrapResource<Resource>(response)
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
    queryFn: async () => {
      const response = await apiClient.get(`/api/${collectionName}/${cloneSourceId}`)
      return unwrapResource<Resource>(response)
    },
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

  // Identify picklist fields so we can fetch their values
  const picklistFields = useMemo(() => {
    if (!schema?.fields) return []
    return schema.fields.filter((f) => f.type === 'picklist' || f.type === 'multi_picklist')
  }, [schema])

  // Fetch picklist values for all picklist/multi_picklist fields.
  // We use the field's fieldTypeConfig.globalPicklistId to resolve values,
  // falling back to the field-level picklist values endpoint.
  const { data: picklistValuesMap } = useQuery({
    queryKey: ['picklist-values-for-form', collectionName, picklistFields.map((f) => f.id)],
    queryFn: async () => {
      const map: Record<string, string[]> = {}
      await Promise.all(
        picklistFields.map(async (field) => {
          try {
            // Try field-level endpoint which resolves global picklist automatically
            const values = await apiClient.get<PicklistValueDto[]>(
              `/control/picklists/fields/${field.id}/values`
            )
            map[field.id] = values
              .filter((v) => v.active)
              .sort((a, b) => a.sortOrder - b.sortOrder)
              .map((v) => v.value)
          } catch {
            map[field.id] = []
          }
        })
      )
      return map
    },
    enabled: picklistFields.length > 0,
  })

  // Identify lookup/master_detail/reference fields that need dropdown options
  const lookupFields = useMemo(() => {
    if (!schema?.fields) return []
    return schema.fields.filter(
      (f) =>
        (f.type === 'lookup' || f.type === 'master_detail' || f.type === 'reference') &&
        (f.referenceTarget || f.referenceCollectionId)
    )
  }, [schema])

  // Fetch lookup options for all lookup/master_detail/reference fields.
  // For each target collection, fetch its schema (to find display field),
  // then fetch records to build the options list.
  const { data: lookupOptionsMap } = useQuery({
    queryKey: ['lookup-options-for-form', collectionName, lookupFields.map((f) => f.id)],
    queryFn: async () => {
      const map: Record<string, LookupOption[]> = {}
      // Group fields by target collection to avoid duplicate requests.
      // Use referenceTarget (collection name) when available, otherwise
      // fall back to referenceCollectionId (UUID). The /control/collections/
      // endpoint accepts both names and UUIDs.
      const targetMap = new Map<string, FieldDefinition[]>()
      for (const field of lookupFields) {
        const target = field.referenceTarget || field.referenceCollectionId
        if (!target) continue
        if (!targetMap.has(target)) {
          targetMap.set(target, [])
        }
        targetMap.get(target)!.push(field)
      }

      await Promise.all(
        Array.from(targetMap.entries()).map(async ([target, fields]) => {
          try {
            // Fetch the target collection schema to find display field.
            // The endpoint accepts both collection name and UUID.
            const targetSchema = await apiClient.get<CollectionSchema>(
              `/control/collections/${target}`
            )
            const targetName = targetSchema.name

            // Determine display field: displayFieldName → 'name' → first string field → 'id'
            let displayFieldName = 'id'
            if (targetSchema.displayFieldName) {
              displayFieldName = targetSchema.displayFieldName
            } else if (targetSchema.fields) {
              const nameField = targetSchema.fields.find((f) => f.name.toLowerCase() === 'name')
              if (nameField) {
                displayFieldName = nameField.name
              } else {
                const firstStringField = targetSchema.fields.find(
                  (f) => f.type.toUpperCase() === 'STRING'
                )
                if (firstStringField) {
                  displayFieldName = firstStringField.name
                }
              }
            }

            // Fetch records from the target collection
            const recordsResponse = await apiClient.get<Record<string, unknown>>(
              `/api/${targetName}?page[size]=200`
            )

            // Extract records from JSON:API response
            const data = recordsResponse?.data
            const records: Array<Record<string, unknown>> = Array.isArray(data) ? data : []

            // Build options from records
            const options: LookupOption[] = records.map((record: Record<string, unknown>) => {
              const attrs = (record.attributes || record) as Record<string, unknown>
              const id = String(record.id || attrs.id || '')
              const label = attrs[displayFieldName] ? String(attrs[displayFieldName]) : id
              return { id, label }
            })

            // Assign to all fields targeting this collection
            for (const field of fields) {
              map[field.id] = options
            }
          } catch {
            // If we fail to fetch options, leave the fields empty (graceful degradation)
            for (const field of fields) {
              map[field.id] = []
            }
          }
        })
      )
      return map
    },
    enabled: lookupFields.length > 0,
  })

  // Merge picklist values and lookup options into the schema fields
  const schemaWithPicklistValues = useMemo<CollectionSchema | undefined>(() => {
    if (!schema) return undefined
    const hasPicklists = picklistValuesMap && picklistFields.length > 0
    const hasLookups = lookupOptionsMap && lookupFields.length > 0
    if (!hasPicklists && !hasLookups) return schema
    return {
      ...schema,
      fields: schema.fields.map((f) => {
        let updated = f
        if ((f.type === 'picklist' || f.type === 'multi_picklist') && picklistValuesMap?.[f.id]) {
          updated = { ...updated, enumValues: picklistValuesMap[f.id] }
        }
        if (
          (f.type === 'lookup' || f.type === 'master_detail' || f.type === 'reference') &&
          lookupOptionsMap?.[f.id]
        ) {
          updated = { ...updated, lookupOptions: lookupOptionsMap[f.id] }
        }
        return updated
      }),
    }
  }, [schema, picklistValuesMap, picklistFields, lookupOptionsMap, lookupFields])

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
  // In edit/clone mode, wait for the resource data before initializing to avoid
  // populating with empty defaults that then block re-initialization.
  // Use schemaWithPicklistValues so picklist defaults are resolved correctly.
  const effectiveSchema = schemaWithPicklistValues
  const computedInitialData = useMemo(() => {
    if (!effectiveSchema) return null
    if (isEditMode) {
      // Wait for resource to load before initializing form
      if (!resource) return null
      return populateFormData(effectiveSchema, resource)
    }
    if (isCloneMode) {
      // Wait for clone source to load before initializing form
      if (!cloneSource) return null
      const cloneData = Object.fromEntries(
        Object.entries(cloneSource).filter(
          ([key]) => key !== 'id' && key !== 'createdAt' && key !== 'updatedAt'
        )
      ) as Resource
      return populateFormData(effectiveSchema, cloneData)
    }
    return initializeFormData(effectiveSchema)
  }, [effectiveSchema, resource, isEditMode, isCloneMode, cloneSource])

  // Initialize form data when data becomes available or resource changes
  if (computedInitialData && initializedKey !== currentResourceKey) {
    setInitializedKey(currentResourceKey ?? null)
    setFormData(computedInitialData)
    setFormErrors({})
  }

  // Sort fields by order
  const sortedFields = useMemo(() => {
    if (!effectiveSchema?.fields) return []
    return [...effectiveSchema.fields].sort((a, b) => {
      const orderA = a.order ?? 0
      const orderB = b.order ?? 0
      return orderA - orderB
    })
  }, [effectiveSchema])

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
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        const serverErrors: FormErrors = {}
        for (const fe of error.fieldErrors) {
          serverErrors[fe.field] = fe.message
        }
        setFormErrors((prev) => ({ ...prev, ...serverErrors }))
      }
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
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        const serverErrors: FormErrors = {}
        for (const fe of error.fieldErrors) {
          serverErrors[fe.field] = fe.message
        }
        setFormErrors((prev) => ({ ...prev, ...serverErrors }))
      }
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
        case 'phone': {
          if (typeof value === 'string' && value.trim()) {
            const phoneRegex = /^[+]?[\d\s\-().]*$/
            if (!phoneRegex.test(value)) {
              return t('resourceForm.validation.invalidPhone', 'Invalid phone number format')
            }
          }
          break
        }
        case 'email': {
          if (typeof value === 'string' && value.trim()) {
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
            if (!emailRegex.test(value)) {
              return t('resourceForm.validation.email', 'Invalid email address')
            }
          }
          break
        }
        case 'url': {
          if (typeof value === 'string' && value.trim()) {
            try {
              new URL(value)
            } catch {
              return t('resourceForm.validation.url', 'Invalid URL format')
            }
          }
          break
        }
        case 'geolocation': {
          if (typeof value === 'object' && value !== null) {
            const geo = value as Record<string, unknown>
            if (geo.latitude !== null && geo.latitude !== undefined) {
              const lat = Number(geo.latitude)
              if (isNaN(lat) || lat < -90 || lat > 90) {
                return t(
                  'resourceForm.validation.invalidLatitude',
                  'Latitude must be between -90 and 90'
                )
              }
            }
            if (geo.longitude !== null && geo.longitude !== undefined) {
              const lng = Number(geo.longitude)
              if (isNaN(lng) || lng < -180 || lng > 180) {
                return t(
                  'resourceForm.validation.invalidLongitude',
                  'Longitude must be between -180 and 180'
                )
              }
            }
          }
          break
        }
        case 'currency':
        case 'percent': {
          if (value !== '' && value !== undefined && value !== null) {
            const numValue = Number(value)
            if (isNaN(numValue)) {
              return t('resourceForm.validation.invalidNumber')
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
          case 'lookup':
          case 'master_detail':
            if (value === '') {
              value = null
            }
            break
          case 'currency':
          case 'percent':
            if (value !== '' && value !== undefined && value !== null) {
              value = Number(value)
            } else {
              value = null
            }
            break
          case 'auto_number':
            // Skip auto_number fields - they are auto-generated
            return
          case 'multi_picklist':
            if (!Array.isArray(value)) {
              value = value ? [value] : []
            }
            break
          case 'geolocation':
            // Ensure geolocation stays as an object
            if (typeof value !== 'object' || value === null) {
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
          if (field.lookupOptions && field.lookupOptions.length > 0) {
            input = (
              <LookupSelect
                id={fieldId}
                name={field.name}
                value={String(value ?? '')}
                options={field.lookupOptions}
                onChange={(v) => handleFieldChange(field.name, v)}
                placeholder={
                  field.referenceTarget
                    ? t('resourceForm.referenceIdPlaceholder', {
                        collection: field.referenceTarget,
                      })
                    : t('resourceForm.referenceId')
                }
                required={field.required}
                disabled={false}
                error={!!error}
                data-testid={`field-${field.name}`}
              />
            )
          } else {
            input = (
              <input
                {...commonProps}
                type="text"
                className={`${styles.input} ${error ? styles.inputError : ''}`}
                value={String(value ?? '')}
                onChange={(e) => handleFieldChange(field.name, e.target.value)}
                placeholder={
                  field.referenceTarget
                    ? t('resourceForm.referenceIdPlaceholder', {
                        collection: field.referenceTarget,
                      })
                    : t('resourceForm.referenceId')
                }
              />
            )
          }
          break

        case 'picklist':
          input = (
            <select
              id={fieldId}
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={styles.input}
            >
              <option value="">{t('common.select')}</option>
              {(field.enumValues || []).map((val: string) => (
                <option key={val} value={val}>
                  {val}
                </option>
              ))}
            </select>
          )
          break

        case 'multi_picklist':
          input = (
            <div className={styles.checkboxGroup}>
              {(field.enumValues || []).map((val: string) => (
                <label key={val} className={styles.checkboxLabel}>
                  <input
                    type="checkbox"
                    checked={Array.isArray(value) && value.includes(val)}
                    onChange={(e) => {
                      const current = Array.isArray(value) ? [...value] : []
                      if (e.target.checked) {
                        current.push(val)
                      } else {
                        const idx = current.indexOf(val)
                        if (idx >= 0) current.splice(idx, 1)
                      }
                      handleFieldChange(field.name, current)
                    }}
                  />
                  {val}
                </label>
              ))}
              {(!field.enumValues || field.enumValues.length === 0) && (
                <input
                  type="text"
                  id={fieldId}
                  value={String(value || '')}
                  onChange={(e) => handleFieldChange(field.name, e.target.value)}
                  className={styles.input}
                  placeholder={t('fields.types.multi_picklist')}
                />
              )}
            </div>
          )
          break

        case 'currency':
          input = (
            <input
              {...commonProps}
              type="number"
              step="0.01"
              value={value !== null && value !== undefined ? String(value) : ''}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              placeholder="0.00"
            />
          )
          break

        case 'percent':
          input = (
            <div className={styles.inputGroup}>
              <input
                {...commonProps}
                type="number"
                step="0.01"
                value={value !== null && value !== undefined ? String(value) : ''}
                onChange={(e) => handleFieldChange(field.name, e.target.value)}
                className={`${styles.input} ${error ? styles.inputError : ''}`}
                placeholder="0.00"
              />
              <span className={styles.inputSuffix}>%</span>
            </div>
          )
          break

        case 'auto_number':
          input = (
            <input
              {...commonProps}
              type="text"
              value={String(value || '')}
              className={styles.input}
              disabled
              placeholder={t('fields.autoGenerated', 'Auto-generated')}
            />
          )
          break

        case 'phone':
          input = (
            <input
              {...commonProps}
              type="tel"
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              placeholder="+1 (555) 123-4567"
            />
          )
          break

        case 'email':
          input = (
            <input
              {...commonProps}
              type="email"
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              placeholder="user@example.com"
            />
          )
          break

        case 'url':
          input = (
            <input
              {...commonProps}
              type="url"
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              placeholder="https://example.com"
            />
          )
          break

        case 'rich_text':
          input = (
            <textarea
              {...commonProps}
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.textarea} ${error ? styles.inputError : ''}`}
              rows={8}
              placeholder={t('fields.types.rich_text')}
            />
          )
          break

        case 'encrypted':
          input = (
            <input
              {...commonProps}
              type="password"
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              placeholder="••••••••"
            />
          )
          break

        case 'external_id':
          input = (
            <input
              {...commonProps}
              type="text"
              value={String(value || '')}
              onChange={(e) => handleFieldChange(field.name, e.target.value)}
              className={`${styles.input} ${error ? styles.inputError : ''}`}
              placeholder={t('fields.types.external_id')}
            />
          )
          break

        case 'geolocation': {
          const geoValue =
            typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}
          input = (
            <div className={styles.inputGroup}>
              <input
                type="number"
                step="0.000001"
                min="-90"
                max="90"
                value={geoValue.latitude !== undefined ? String(geoValue.latitude) : ''}
                onChange={(e) =>
                  handleFieldChange(field.name, {
                    ...geoValue,
                    latitude: e.target.value ? Number(e.target.value) : null,
                  })
                }
                className={`${styles.input} ${error ? styles.inputError : ''}`}
                placeholder={t('fields.config.latitude', 'Latitude')}
              />
              <input
                type="number"
                step="0.000001"
                min="-180"
                max="180"
                value={geoValue.longitude !== undefined ? String(geoValue.longitude) : ''}
                onChange={(e) =>
                  handleFieldChange(field.name, {
                    ...geoValue,
                    longitude: e.target.value ? Number(e.target.value) : null,
                  })
                }
                className={`${styles.input} ${error ? styles.inputError : ''}`}
                placeholder={t('fields.config.longitude', 'Longitude')}
              />
            </div>
          )
          break
        }

        case 'lookup':
        case 'master_detail':
          if (field.lookupOptions && field.lookupOptions.length > 0) {
            input = (
              <LookupSelect
                id={fieldId}
                name={field.name}
                value={String(value ?? '')}
                options={field.lookupOptions}
                onChange={(v) => handleFieldChange(field.name, v)}
                placeholder={
                  field.referenceTarget
                    ? `${t('common.select')} ${field.referenceTarget}`
                    : t('common.select')
                }
                required={field.required}
                disabled={false}
                error={!!error}
                data-testid={`field-${field.name}`}
              />
            )
          } else {
            input = (
              <input
                {...commonProps}
                type="text"
                value={String(value || '')}
                onChange={(e) => handleFieldChange(field.name, e.target.value)}
                className={`${styles.input} ${error ? styles.inputError : ''}`}
                placeholder={
                  field.referenceTarget
                    ? `${t('fields.types.reference')} → ${field.referenceTarget}`
                    : t('fields.types.reference')
                }
              />
            )
          }
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
  if (!effectiveSchema) {
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
              {effectiveSchema.displayName}
            </Link>
            <span className={styles.breadcrumbSeparator} aria-hidden="true">
              /
            </span>
            <span className={styles.breadcrumbCurrent}>
              {isEditMode
                ? t('resources.editRecord')
                : isCloneMode
                  ? t('resources.cloneRecord', { collection: effectiveSchema.displayName })
                  : t('resources.createRecord')}
            </span>
          </nav>
          <h1 className={styles.title} data-testid="page-title">
            {isEditMode
              ? t('resources.editRecord')
              : isCloneMode
                ? t('resources.cloneRecord', { collection: effectiveSchema.displayName })
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
