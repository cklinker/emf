/**
 * ObjectFormPage
 *
 * Create or edit a record using a schema-driven form.
 * Renders input fields for each field in the collection schema,
 * with type-appropriate form controls.
 *
 * Features:
 * - Schema-driven form field generation
 * - Create and edit modes
 * - JSON:API request wrapping via useRecordMutation
 * - Picklist dropdowns with fetched values
 * - Reference field (master_detail/lookup) searchable dropdowns
 * - Proper date/datetime value binding
 * - Cancel/Save actions
 * - Loading states
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Save, X, Loader2, AlertCircle } from 'lucide-react'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Separator } from '@/components/ui/separator'
import { LookupSelect } from '@/components/LookupSelect'
import { useAuth } from '@/context/AuthContext'
import { useApi } from '@/context/ApiContext'
import { LayoutFormSections } from '@/components/LayoutFormSections'
import { useCollectionSchema } from '@/hooks/useCollectionSchema'
import { useRecord } from '@/hooks/useRecord'
import { useRecordMutation } from '@/hooks/useRecordMutation'
import { useCollectionPermissions } from '@/hooks/useCollectionPermissions'
import { useLookupDisplayMap } from '@/hooks/useLookupDisplayMap'
import { usePageLayout } from '@/hooks/usePageLayout'
import { InsufficientPrivileges } from '@/components/InsufficientPrivileges'
import type { FieldDefinition, FieldType } from '@/hooks/useCollectionSchema'
import type { PageLayoutDto } from '@/hooks/usePageLayout'
import type { LookupOption } from '@/components/LookupSelect'

/** Picklist value returned from the API */
interface PicklistValueDto {
  value: string
  label: string
  isDefault: boolean
  active: boolean
  sortOrder: number
}

/** System fields excluded from forms */
const SYSTEM_FIELDS = new Set([
  'id',
  'createdAt',
  'updatedAt',
  'createdBy',
  'updatedBy',
  'created_at',
  'updated_at',
  'created_by',
  'updated_by',
])

/** Read-only field types that should not be editable */
const READ_ONLY_TYPES: Set<FieldType> = new Set(['auto_number', 'formula', 'rollup_summary'])

/** Reference field types that need lookup dropdowns */
const REFERENCE_TYPES: Set<FieldType> = new Set(['master_detail', 'lookup', 'reference'])

/**
 * Get the HTML input type for a field type.
 */
function getInputType(fieldType: FieldType): string {
  switch (fieldType) {
    case 'number':
    case 'currency':
    case 'percent':
      return 'number'
    case 'date':
      return 'date'
    case 'datetime':
      return 'datetime-local'
    case 'email':
      return 'email'
    case 'phone':
      return 'tel'
    case 'url':
      return 'url'
    default:
      return 'text'
  }
}

/**
 * Check if a field should use a textarea instead of an input.
 */
function isTextareaField(fieldType: FieldType): boolean {
  return fieldType === 'rich_text' || fieldType === 'json'
}

interface FormFieldProps {
  field: FieldDefinition
  value: unknown
  onChange: (name: string, value: unknown) => void
  /** Whether the field is read-only due to field-level permissions */
  readOnly?: boolean
}

/**
 * Renders the appropriate form control for a field type.
 */
function FormField({
  field,
  value,
  onChange,
  readOnly = false,
}: FormFieldProps): React.ReactElement {
  const isReadOnly = READ_ONLY_TYPES.has(field.type) || readOnly
  const fieldId = `field-${field.name}`

  const labelEl = (
    <Label htmlFor={fieldId} className="text-sm font-medium">
      {field.displayName || field.name}
      {field.required && <span className="ml-1 text-destructive">*</span>}
    </Label>
  )

  // Boolean fields use a checkbox
  if (field.type === 'boolean') {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <Checkbox
            id={fieldId}
            checked={Boolean(value)}
            onCheckedChange={(checked) => onChange(field.name, checked)}
            disabled={isReadOnly}
          />
          <Label htmlFor={fieldId} className="text-sm font-medium">
            {field.displayName || field.name}
            {field.required && <span className="ml-1 text-destructive">*</span>}
          </Label>
        </div>
      </div>
    )
  }

  // Picklist fields use a dropdown select
  if (field.type === 'picklist' || field.type === 'multi_picklist') {
    return (
      <div className="space-y-2">
        {labelEl}
        <select
          id={fieldId}
          value={value != null ? String(value) : ''}
          onChange={(e) => onChange(field.name, e.target.value)}
          disabled={isReadOnly}
          className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          <option value="">Select...</option>
          {(field.enumValues || []).map((val: string) => (
            <option key={val} value={val}>
              {val}
            </option>
          ))}
        </select>
      </div>
    )
  }

  // Reference fields (master_detail, lookup, reference) use a searchable LookupSelect
  if (REFERENCE_TYPES.has(field.type)) {
    const options = field.lookupOptions || []
    return (
      <div className="space-y-2">
        {labelEl}
        <LookupSelect
          id={fieldId}
          name={field.name}
          value={value != null ? String(value) : ''}
          options={options}
          onChange={(v) => onChange(field.name, v)}
          placeholder="Select..."
          required={field.required}
          disabled={isReadOnly}
        />
      </div>
    )
  }

  // Textarea fields (rich_text, json)
  if (isTextareaField(field.type)) {
    return (
      <div className="space-y-2">
        {labelEl}
        <Textarea
          id={fieldId}
          value={value != null ? String(value) : ''}
          onChange={(e) => onChange(field.name, e.target.value)}
          disabled={isReadOnly}
          rows={field.type === 'json' ? 6 : 4}
          className={field.type === 'json' ? 'font-mono text-sm' : ''}
          placeholder={field.type === 'json' ? '{}' : ''}
        />
      </div>
    )
  }

  // Encrypted fields show masked value
  if (field.type === 'encrypted') {
    return (
      <div className="space-y-2">
        {labelEl}
        <Input
          id={fieldId}
          type="password"
          value={value != null ? String(value) : ''}
          onChange={(e) => onChange(field.name, e.target.value)}
        />
      </div>
    )
  }

  // Default: text input with appropriate type
  const inputType = getInputType(field.type)
  const step = field.type === 'currency' ? '0.01' : field.type === 'percent' ? '0.01' : undefined

  return (
    <div className="space-y-2">
      {labelEl}
      <Input
        id={fieldId}
        type={inputType}
        value={value != null ? String(value) : ''}
        onChange={(e) => {
          const newValue =
            inputType === 'number'
              ? e.target.value === ''
                ? ''
                : Number(e.target.value)
              : e.target.value
          onChange(field.name, newValue)
        }}
        disabled={isReadOnly}
        step={step}
        required={field.required}
      />
    </div>
  )
}

/**
 * Compute initial form data from record (edit) or field defaults (create).
 * Formats date/datetime values for HTML input compatibility.
 */
function computeInitialFormData(
  isNew: boolean,
  record: Record<string, unknown> | undefined,
  fields: FieldDefinition[]
): Record<string, unknown> {
  if (!isNew && record) {
    const data: Record<string, unknown> = { ...record }
    // Format date/datetime values for HTML inputs
    for (const field of fields) {
      const value = data[field.name]
      if (value != null && typeof value === 'string') {
        if (field.type === 'date') {
          // HTML date input expects YYYY-MM-DD
          data[field.name] = value.split('T')[0]
        } else if (field.type === 'datetime') {
          // HTML datetime-local input expects YYYY-MM-DDTHH:MM
          data[field.name] = value.slice(0, 16)
        }
      }
    }
    return data
  }
  const defaults: Record<string, unknown> = {}
  for (const field of fields) {
    if (field.type === 'boolean') {
      defaults[field.name] = false
    }
  }
  return defaults
}

interface ObjectFormBodyProps {
  isNew: boolean
  initialData: Record<string, unknown>
  fields: FieldDefinition[]
  collectionName: string
  collectionLabel: string
  recordId?: string
  basePath: string
  /** Check if a field is editable (VISIBLE vs READ_ONLY from field permissions) */
  isFieldEditable?: (fieldName: string) => boolean
  /** Resolved page layout (null when none configured — falls back to flat grid) */
  layout?: PageLayoutDto | null
}

/**
 * Inner form body — receives initialData so useState initializer runs
 * with the correct value on mount. Parent uses `key` to force remount
 * when the data source changes.
 */
function ObjectFormBody({
  isNew,
  initialData,
  fields,
  collectionName,
  collectionLabel,
  recordId,
  basePath,
  isFieldEditable,
  layout,
}: ObjectFormBodyProps): React.ReactElement {
  const navigate = useNavigate()
  const [formData, setFormData] = useState<Record<string, unknown>>(initialData)

  // Editable fields (exclude system, read-only types, and permission-read-only fields)
  const editableFields = useMemo(() => {
    return fields.filter(
      (f) =>
        !SYSTEM_FIELDS.has(f.name) &&
        !READ_ONLY_TYPES.has(f.type) &&
        (!isFieldEditable || isFieldEditable(f.name))
    )
  }, [fields, isFieldEditable])

  // All visible non-system fields (including read-only for display)
  const displayFields = useMemo(() => {
    return fields.filter((f) => !SYSTEM_FIELDS.has(f.name) && !READ_ONLY_TYPES.has(f.type))
  }, [fields])

  // Mutations
  const mutations = useRecordMutation({
    collectionName,
    onSuccess: () => {
      if (isNew) {
        navigate(`${basePath}/o/${collectionName}`)
      } else {
        navigate(`${basePath}/o/${collectionName}/${recordId}`)
      }
    },
  })

  const pageTitle = isNew ? `New ${collectionLabel}` : `Edit ${collectionLabel}`

  // Handle field change
  const handleFieldChange = useCallback((name: string, value: unknown) => {
    setFormData((prev) => ({ ...prev, [name]: value }))
  }, [])

  // Handle save
  const handleSave = useCallback(() => {
    const attributes: Record<string, unknown> = {}
    for (const field of editableFields) {
      const value = formData[field.name]
      if (value !== undefined && value !== '') {
        attributes[field.name] = value
      }
    }

    if (isNew) {
      mutations.create.mutate(attributes)
    } else {
      mutations.update.mutate({ id: recordId!, data: attributes })
    }
  }, [editableFields, formData, isNew, mutations, recordId])

  // Handle cancel
  const handleCancel = useCallback(() => {
    if (recordId) {
      navigate(`${basePath}/o/${collectionName}/${recordId}`)
    } else {
      navigate(`${basePath}/o/${collectionName}`)
    }
  }, [navigate, basePath, collectionName, recordId])

  const isSaving = mutations.create.isPending || mutations.update.isPending

  return (
    <div className="space-y-4 p-6">
      {/* Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/home`}>Home</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/o/${collectionName}`}>{collectionLabel}</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{isNew ? 'New' : 'Edit'}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Form header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight text-foreground">{pageTitle}</h1>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" onClick={handleCancel} disabled={isSaving}>
            <X className="mr-1.5 h-3.5 w-3.5" />
            Cancel
          </Button>
          <Button size="sm" onClick={handleSave} disabled={isSaving}>
            {isSaving ? (
              <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
            ) : (
              <Save className="mr-1.5 h-3.5 w-3.5" />
            )}
            {isSaving ? 'Saving...' : 'Save'}
          </Button>
        </div>
      </div>

      <Separator />

      {/* Form Fields — use page layout sections when available, otherwise flat grid */}
      {layout && layout.sections.length > 0 ? (
        <LayoutFormSections
          sections={layout.sections}
          schemaFields={displayFields}
          renderField={(field) => {
            const fieldIsEditable = !isFieldEditable || isFieldEditable(field.name)
            return (
              <FormField
                key={field.name}
                field={field as FieldDefinition}
                value={formData[field.name]}
                onChange={fieldIsEditable ? handleFieldChange : () => {}}
                readOnly={!fieldIsEditable}
              />
            )
          }}
        />
      ) : (
        <Card>
          <CardHeader className="py-3">
            <CardTitle className="text-sm font-medium">{collectionLabel} Information</CardTitle>
          </CardHeader>
          <CardContent>
            {displayFields.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No editable fields in this collection.
              </p>
            ) : (
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                {displayFields.map((field) => {
                  const fieldIsEditable = !isFieldEditable || isFieldEditable(field.name)
                  return (
                    <FormField
                      key={field.name}
                      field={field}
                      value={formData[field.name]}
                      onChange={fieldIsEditable ? handleFieldChange : () => {}}
                      readOnly={!fieldIsEditable}
                    />
                  )
                })}
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}

export function ObjectFormPage(): React.ReactElement {
  const {
    tenantSlug,
    collection: collectionName,
    id: recordId,
  } = useParams<{
    tenantSlug: string
    collection: string
    id?: string
  }>()
  const basePath = `/${tenantSlug}/app`
  const isNew = !recordId
  const navigate = useNavigate()
  const { apiClient } = useApi()

  // Fetch collection schema
  const {
    schema,
    fields,
    isLoading: schemaLoading,
    error: schemaError,
  } = useCollectionSchema(collectionName)

  // Fetch existing record for edit mode
  const { record, isLoading: recordLoading } = useRecord({
    collectionName,
    recordId,
    enabled: !isNew,
  })

  // Fetch permissions (combined object + field in one call)
  const {
    permissions,
    isFieldVisible,
    isFieldEditable,
    isLoading: permissionsLoading,
  } = useCollectionPermissions(collectionName)

  // Resolve page layout for this collection (returns null if none configured)
  const { user } = useAuth()
  const { layout, isLoading: layoutLoading } = usePageLayout(schema?.id, user?.id)

  // Filter fields by field-level permissions (hidden fields excluded, read-only shown as disabled)
  const permissionFilteredFields = useMemo(() => {
    return fields.filter((f) => isFieldVisible(f.name))
  }, [fields, isFieldVisible])

  // ---------------------------------------------------------------
  // Picklist values: fetch enum values for picklist/multi_picklist fields
  // ---------------------------------------------------------------
  const picklistFields = useMemo(() => {
    return permissionFilteredFields.filter(
      (f) => f.type === 'picklist' || f.type === 'multi_picklist'
    )
  }, [permissionFilteredFields])

  const { data: picklistValuesMap } = useQuery({
    queryKey: ['picklist-values-for-form', collectionName, picklistFields.map((f) => f.id)],
    queryFn: async () => {
      const map: Record<string, string[]> = {}
      await Promise.all(
        picklistFields.map(async (field) => {
          try {
            const values = await apiClient.getList<PicklistValueDto>(
              `/api/picklist-values?filter[picklistSourceId][eq]=${encodeURIComponent(field.id)}&filter[picklistSourceType][eq]=FIELD`
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

  // ---------------------------------------------------------------
  // Lookup options: reuse shared useLookupDisplayMap cache to avoid
  // re-fetching the same target collection data as the detail page.
  // ---------------------------------------------------------------
  const lookupFields = useMemo(() => {
    return permissionFilteredFields.filter(
      (f) => REFERENCE_TYPES.has(f.type) && (f.referenceCollectionId || f.referenceTarget)
    )
  }, [permissionFilteredFields])

  const { lookupDisplayMap } = useLookupDisplayMap(permissionFilteredFields)

  // Transform the display map (fieldName → { recordId: label }) into
  // the options map (fieldId → LookupOption[]) needed by the form fields.
  const lookupOptionsMap = useMemo(() => {
    if (!lookupDisplayMap) return undefined
    const result: Record<string, LookupOption[]> = {}
    for (const field of lookupFields) {
      const idToLabel = lookupDisplayMap[field.name]
      if (idToLabel) {
        result[field.id] = Object.entries(idToLabel).map(([id, label]) => ({ id, label }))
      }
    }
    return result
  }, [lookupDisplayMap, lookupFields])

  // ---------------------------------------------------------------
  // Merge picklist values and lookup options into enriched fields
  // ---------------------------------------------------------------
  const enrichedFields = useMemo(() => {
    const hasPicklists = picklistValuesMap && picklistFields.length > 0
    const hasLookups = lookupOptionsMap && lookupFields.length > 0
    if (!hasPicklists && !hasLookups) return permissionFilteredFields
    return permissionFilteredFields.map((f) => {
      let updated = f
      if ((f.type === 'picklist' || f.type === 'multi_picklist') && picklistValuesMap?.[f.id]) {
        updated = { ...updated, enumValues: picklistValuesMap[f.id] }
      }
      if (REFERENCE_TYPES.has(f.type) && lookupOptionsMap?.[f.id]) {
        updated = { ...updated, lookupOptions: lookupOptionsMap[f.id] }
      }
      return updated
    })
  }, [permissionFilteredFields, picklistValuesMap, picklistFields, lookupOptionsMap, lookupFields])

  const isLoading =
    schemaLoading || (!isNew && recordLoading) || permissionsLoading || layoutLoading

  // Collection label
  const collectionLabel =
    schema?.displayName ||
    (collectionName ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1) : 'Object')

  // Compute initial data and a key that changes when the data source changes.
  // The key forces ObjectFormBody to remount, running useState with fresh initialData.
  const initialData = useMemo(
    () => computeInitialFormData(isNew, record, enrichedFields),
    [isNew, record, enrichedFields]
  )
  const formKey = isNew
    ? `new:${enrichedFields.length}`
    : `edit:${recordId}:${record?.id ?? 'loading'}`

  // Handle cancel (needed for error state)
  const handleCancel = useCallback(() => {
    if (recordId) {
      navigate(`${basePath}/o/${collectionName}/${recordId}`)
    } else {
      navigate(`${basePath}/o/${collectionName}`)
    }
  }, [navigate, basePath, collectionName, recordId])

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // Permission check: canCreate for new, canEdit for edit
  const requiredPermission = isNew ? permissions.canCreate : permissions.canEdit
  if (!requiredPermission) {
    return (
      <InsufficientPrivileges
        action={isNew ? 'create' : 'edit'}
        resource={collectionLabel}
        backPath={
          recordId
            ? `${basePath}/o/${collectionName}/${recordId}`
            : `${basePath}/o/${collectionName}`
        }
      />
    )
  }

  // Error state
  if (schemaError) {
    return (
      <div className="space-y-4 p-6">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>
            {schemaError.message || 'Failed to load collection schema.'}
          </AlertDescription>
        </Alert>
        <Button variant="outline" onClick={handleCancel}>
          Go back
        </Button>
      </div>
    )
  }

  return (
    <ObjectFormBody
      key={formKey}
      isNew={isNew}
      initialData={initialData}
      fields={enrichedFields}
      collectionName={collectionName || ''}
      collectionLabel={collectionLabel}
      recordId={recordId}
      basePath={basePath}
      isFieldEditable={isFieldEditable}
      layout={layout}
    />
  )
}
