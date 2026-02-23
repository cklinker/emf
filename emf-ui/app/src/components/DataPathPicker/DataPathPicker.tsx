import React, { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

/**
 * A field node from the data-paths API response.
 */
interface FieldNode {
  name: string
  displayName: string
  type: string
  isRelationship: boolean
  targetCollectionId?: string
  children?: FieldNode[]
}

export interface DataPathPickerProps {
  /** The root collection ID to start from */
  collectionId: string
  /** Current selected path expression (dot-notation, e.g. "order_id.customer_id.email") */
  value: string
  /** Called when user selects a field path */
  onChange: (expression: string) => void
  /** Whether to allow multi-segment relationship traversal (default true) */
  allowTraversal?: boolean
  /** Maximum traversal depth for the API query (default 3) */
  depth?: number
  /** Whether the picker is disabled */
  disabled?: boolean
  /** Test ID prefix */
  testId?: string
}

/**
 * DataPathPicker — a reusable component for visually building DataPath expressions.
 *
 * Displays the field tree for a collection, expanding relationship fields to
 * show their target collection's fields. Users click on a terminal field to
 * select it, building a dot-notation path expression.
 *
 * Used in:
 * - Workflow action config editors (data payload builder)
 * - Email template merge field inserter
 * - Webhook body template builder
 */
export function DataPathPicker({
  collectionId,
  value,
  onChange,
  allowTraversal = true,
  depth = 3,
  testId = 'data-path-picker',
}: DataPathPickerProps): React.ReactElement {
  const { apiClient } = useApi()

  const { data: fields, isLoading } = useQuery({
    queryKey: ['data-paths', collectionId, depth],
    queryFn: () =>
      apiClient.get<FieldNode[]>(`/control/collections/${collectionId}/data-paths?depth=${depth}`),
    enabled: !!collectionId,
  })

  // Track which relationship fields are expanded
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(new Set())

  // Parse current value into breadcrumbs
  const breadcrumbs = value ? value.split('.') : []

  const toggleExpand = useCallback((path: string) => {
    setExpandedPaths((prev) => {
      const next = new Set(prev)
      if (next.has(path)) {
        next.delete(path)
      } else {
        next.add(path)
      }
      return next
    })
  }, [])

  const handleFieldClick = useCallback(
    (fieldName: string, parentPath: string, isRelationship: boolean) => {
      const fullPath = parentPath ? `${parentPath}.${fieldName}` : fieldName
      if (isRelationship && allowTraversal) {
        toggleExpand(fullPath)
      } else {
        onChange(fullPath)
      }
    },
    [onChange, allowTraversal, toggleExpand]
  )

  const renderFieldTree = (
    nodes: FieldNode[],
    parentPath: string,
    indentLevel: number
  ): React.ReactElement[] => {
    return nodes.map((node) => {
      const fullPath = parentPath ? `${parentPath}.${node.name}` : node.name
      const isExpanded = expandedPaths.has(fullPath)
      const isSelected = value === fullPath

      return (
        <React.Fragment key={fullPath}>
          <button
            type="button"
            className={cn(
              'flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm transition-colors',
              'hover:bg-muted/60',
              isSelected && 'bg-primary/10 text-primary font-medium',
              !isSelected && 'text-foreground'
            )}
            style={{ paddingLeft: `${indentLevel * 16 + 8}px` }}
            onClick={() => handleFieldClick(node.name, parentPath, node.isRelationship)}
            data-testid={`${testId}-field-${fullPath}`}
          >
            {node.isRelationship && allowTraversal && (
              <span className="w-4 text-center text-xs text-muted-foreground">
                {isExpanded ? '\u25BE' : '\u25B8'}
              </span>
            )}
            {!node.isRelationship && (
              <span className="w-4 text-center text-xs text-muted-foreground">&bull;</span>
            )}
            <span className="flex-1 truncate">{node.displayName}</span>
            <span
              className={cn(
                'rounded px-1.5 py-0.5 text-[10px] font-medium',
                node.isRelationship
                  ? 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300'
                  : 'bg-muted text-muted-foreground'
              )}
            >
              {node.type}
            </span>
          </button>
          {node.isRelationship &&
            isExpanded &&
            node.children &&
            renderFieldTree(node.children, fullPath, indentLevel + 1)}
        </React.Fragment>
      )
    })
  }

  return (
    <div className="flex flex-col gap-2" data-testid={testId}>
      {/* Breadcrumb display of current path */}
      {value && (
        <div className="flex items-center gap-1 text-xs">
          <span className="text-muted-foreground">Path:</span>
          {breadcrumbs.map((segment, i) => (
            <React.Fragment key={i}>
              {i > 0 && <span className="text-muted-foreground">&rarr;</span>}
              <span
                className={cn(
                  'rounded px-1.5 py-0.5',
                  i === breadcrumbs.length - 1
                    ? 'bg-primary/10 font-medium text-primary'
                    : 'bg-muted text-muted-foreground'
                )}
              >
                {segment}
              </span>
            </React.Fragment>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="ml-2 h-5 px-1.5 text-[10px]"
            onClick={() => onChange('')}
            data-testid={`${testId}-clear`}
          >
            Clear
          </Button>
        </div>
      )}

      {/* Field tree */}
      <div
        className={cn(
          'max-h-[300px] overflow-y-auto rounded-md border border-border bg-background',
          isLoading && 'flex items-center justify-center py-8'
        )}
        data-testid={`${testId}-tree`}
      >
        {isLoading ? (
          <span className="text-sm text-muted-foreground">Loading fields...</span>
        ) : !fields || fields.length === 0 ? (
          <div className="p-4 text-center text-sm text-muted-foreground">
            No fields found for this collection.
          </div>
        ) : (
          <div className="py-1">{renderFieldTree(fields, '', 0)}</div>
        )}
      </div>
    </div>
  )
}

export default DataPathPicker
