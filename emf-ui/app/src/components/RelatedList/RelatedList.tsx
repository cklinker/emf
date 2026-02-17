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
import { useCollectionSchema } from '@/hooks/useCollectionSchema'
import { useObjectPermissions } from '@/hooks/useObjectPermissions'
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

  // Fetch schema for the related collection
  const { schema, fields, isLoading: schemaLoading } = useCollectionSchema(collectionName)

  // Fetch permissions for the related collection
  const { permissions } = useObjectPermissions(collectionName)

  // Fetch related records
  const {
    data: records,
    total,
    isLoading: recordsLoading,
  } = useRelatedRecords({
    collectionName,
    foreignKeyField,
    parentRecordId,
    limit,
  })

  // Determine visible columns (exclude system fields and FK field, limit to MAX_COLUMNS)
  const visibleFields = useMemo<FieldDefinition[]>(() => {
    if (!fields.length) return []
    return fields
      .filter((f) => !SYSTEM_FIELDS.has(f.name))
      .filter((f) => f.name !== 'id')
      .filter((f) => f.name !== foreignKeyField)
      .slice(0, MAX_COLUMNS)
  }, [fields, foreignKeyField])

  // Display name for the related collection
  const displayLabel =
    label || schema?.displayName || collectionName.charAt(0).toUpperCase() + collectionName.slice(1)

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
                    {visibleFields.map((field) => (
                      <TableCell key={field.name} className="max-w-[200px] py-2">
                        <FieldRenderer
                          type={field.type}
                          value={record[field.name]}
                          fieldName={field.name}
                          displayName={field.displayName || field.name}
                          tenantSlug={tenantSlug}
                          targetCollection={field.referenceTarget}
                          truncate
                        />
                      </TableCell>
                    ))}
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
