/**
 * RelatedList Component
 *
 * Displays a compact table of related (child) records for a given
 * parent record. Used on the ObjectDetailPage to show master-detail
 * relationships.
 *
 * Features:
 * - Compact 5-row table of related records
 * - "+ New" button to create a new related record (pre-fills parent lookup)
 * - "View All" link to navigate to the full list filtered by parent
 * - Field type-aware rendering via FieldRenderer
 * - Loading skeleton and empty state
 */

import React, { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Plus, ExternalLink } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { FieldRenderer } from '@/components/FieldRenderer'
import { useRelatedRecords } from '@/hooks/useRelatedRecords'
import { useCollectionSchema, fetchCollectionSchema } from '@/hooks/useCollectionSchema'
import { useObjectPermissions } from '@/hooks/useObjectPermissions'
import { buildIncludedDisplayMap } from '@/utils/jsonapi'
import { useApi } from '@/context/ApiContext'
import { useQueries } from '@tanstack/react-query'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

/** System fields to exclude from related list columns */
const SYSTEM_FIELDS = new Set([
  'createdAt',
  'updatedAt',
  'createdBy',
  'updatedBy',
  'created_at',
  'updated_at',
  'created_by',
  'updated_by',
])

/** Reference field types that indicate a foreign key to another collection */
const REFERENCE_FIELD_TYPES = new Set(['master_detail', 'lookup', 'reference'])

/** Maximum number of columns to display in the related list */
const MAX_COLUMNS = 4

/** Maximum number of records to show in the compact view */
const DEFAULT_LIMIT = 5

export interface RelatedListProps {
  /** Related collection name */
  collectionName: string
  /** Foreign key field on the related collection that points to the parent */
  foreignKeyField: string
  /** Parent record ID */
  parentRecordId: string
  /** Tenant slug for building URLs */
  tenantSlug: string
  /** Optional: override the display label */
  label?: string
  /** Optional: number of records to show (default: 5) */
  limit?: number
}

/**
 * Loading skeleton for the related list table.
 */
function RelatedListSkeleton({ columns }: { columns: number }) {
  return (
    <>
      {Array.from({ length: 3 }).map((_, rowIdx) => (
        <TableRow key={rowIdx}>
          {Array.from({ length: columns }).map((_, colIdx) => (
            <TableCell key={colIdx}>
              <Skeleton className="h-4 w-[80%]" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  )
}

export function RelatedList({
  collectionName,
  foreignKeyField,
  parentRecordId,
  tenantSlug,
  label,
  limit = DEFAULT_LIMIT,
}: RelatedListProps): React.ReactElement {
  const navigate = useNavigate()
  const basePath = `/${tenantSlug}/app`
  const { apiClient } = useApi()

  // Fetch schema for the related collection
  const { schema, fields, isLoading: schemaLoading } = useCollectionSchema(collectionName)

  // Fetch permissions for the related collection
  const { permissions } = useObjectPermissions(collectionName)

  // Determine visible columns (exclude system fields and FK field, limit to MAX_COLUMNS)
  const visibleFields = useMemo<FieldDefinition[]>(() => {
    if (!fields.length) return []
    return fields
      .filter((f) => !SYSTEM_FIELDS.has(f.name))
      .filter((f) => f.name !== 'id')
      .filter((f) => f.name !== foreignKeyField)
      .slice(0, MAX_COLUMNS)
  }, [fields, foreignKeyField])

  // Identify reference fields among visible columns for include resolution
  const referenceFields = useMemo(
    () => visibleFields.filter((f) => REFERENCE_FIELD_TYPES.has(f.type) && f.referenceTarget),
    [visibleFields]
  )

  // Build include param from reference target collection names
  const includeParam = useMemo(() => {
    if (referenceFields.length === 0) return undefined
    const uniqueTargets = [...new Set(referenceFields.map((f) => f.referenceTarget!))]
    return uniqueTargets.join(',')
  }, [referenceFields])

  // Fetch related records (wait for schema so include param is stable)
  const {
    data: records,
    total,
    isLoading: recordsLoading,
    rawResponse,
  } = useRelatedRecords({
    collectionName,
    foreignKeyField,
    parentRecordId,
    limit,
    include: includeParam,
    enabled: !schemaLoading,
  })

  // Fetch schemas for referenced collections to determine their display field names
  const refSchemaQueries = useQueries({
    queries: referenceFields.map((f) => ({
      queryKey: ['collection-schema', f.referenceTarget],
      queryFn: () => fetchCollectionSchema(apiClient, f.referenceTarget!),
      enabled: !!f.referenceTarget,
      staleTime: 5 * 60 * 1000,
    })),
  })

  // Build lookup display map from included resources
  const lookupDisplayMap = useMemo(() => {
    if (!rawResponse || referenceFields.length === 0) return undefined

    const map: Record<string, Record<string, string>> = {}

    referenceFields.forEach((field, idx) => {
      const refSchema = refSchemaQueries[idx]?.data
      const displayField = refSchema?.displayFieldName || 'name'
      const targetType = field.referenceTarget!

      const fieldMap = buildIncludedDisplayMap(rawResponse, targetType, displayField)
      if (Object.keys(fieldMap).length > 0) {
        map[field.name] = fieldMap
      }
    })

    return Object.keys(map).length > 0 ? map : undefined
  }, [rawResponse, referenceFields, refSchemaQueries])

  // Display name for the related collection
  const displayLabel =
    label ||
    schema?.displayName ||
    (collectionName ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1) : 'Related')

  const isLoading = schemaLoading || recordsLoading

  // Handle row click → navigate to record detail
  const handleRowClick = (record: CollectionRecord) => {
    navigate(`${basePath}/o/${collectionName}/${record.id}`)
  }

  // Handle "+ New" → navigate to new form with parent pre-filled
  const handleNew = () => {
    const params = new URLSearchParams()
    params.set(foreignKeyField, parentRecordId)
    navigate(`${basePath}/o/${collectionName}/new?${params.toString()}`)
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between py-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          {displayLabel}
          <Badge variant="outline" className="font-normal">
            {isLoading ? '…' : total}
          </Badge>
        </CardTitle>
        <div className="flex items-center gap-2">
          {total > limit && (
            <Button variant="ghost" size="sm" className="h-7 text-xs" asChild>
              <Link
                to={`${basePath}/o/${collectionName}?filter=${encodeURIComponent(
                  JSON.stringify([
                    {
                      id: 'parent',
                      field: foreignKeyField,
                      operator: 'equals',
                      value: parentRecordId,
                    },
                  ])
                )}`}
              >
                View All
                <ExternalLink className="ml-1 h-3 w-3" />
              </Link>
            </Button>
          )}
          {permissions.canCreate && (
            <Button variant="outline" size="sm" className="h-7 text-xs" onClick={handleNew}>
              <Plus className="mr-1 h-3 w-3" />
              New
            </Button>
          )}
        </div>
      </CardHeader>

      <CardContent className="pt-0">
        {isLoading ? (
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  {Array.from({ length: MAX_COLUMNS }).map((_, i) => (
                    <TableHead key={i}>
                      <Skeleton className="h-4 w-20" />
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                <RelatedListSkeleton columns={MAX_COLUMNS} />
              </TableBody>
            </Table>
          </div>
        ) : records.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted-foreground">
            No related {displayLabel.toLowerCase()} found.
          </p>
        ) : (
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  {visibleFields.map((field) => (
                    <TableHead key={field.name} className="whitespace-nowrap text-xs">
                      {field.displayName || field.name}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {records.map((record) => (
                  <TableRow
                    key={record.id}
                    className="cursor-pointer"
                    onClick={() => handleRowClick(record)}
                  >
                    {visibleFields.map((field) => {
                      const value = record[field.name]
                      const isRef = REFERENCE_FIELD_TYPES.has(field.type)
                      const displayLabel =
                        isRef && lookupDisplayMap?.[field.name]
                          ? lookupDisplayMap[field.name][String(value)] || undefined
                          : undefined

                      return (
                        <TableCell key={field.name} className="max-w-[200px] py-2">
                          <FieldRenderer
                            type={field.type}
                            value={value}
                            fieldName={field.name}
                            displayName={field.displayName || field.name}
                            tenantSlug={tenantSlug}
                            targetCollection={field.referenceTarget}
                            displayLabel={displayLabel}
                            truncate
                          />
                        </TableCell>
                      )
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
