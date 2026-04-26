import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { ApiOperationSummary } from '@kelta/sdk'

import { useApi } from '../../context/ApiContext'
import { Input } from '@/components/ui/input'
import { FieldLabel } from '@/components/kelta'
import { cn } from '@/lib/utils'

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200',
  POST: 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-200',
  PUT: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200',
  PATCH: 'bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-200',
  DELETE: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200',
}

export interface OperationPickerProps {
  /** Restricts the search to operations within this spec. Empty searches across all specs. */
  specId?: string
  value?: string
  onChange: (syntheticOpId: string, op: ApiOperationSummary | null) => void
  label?: string
  className?: string
}

/**
 * Searchable picker for OpenAPI operations. Backed by the trigram-indexed
 * search endpoint on the worker.
 */
export function OperationPicker({
  specId,
  value,
  onChange,
  label = 'Operation',
  className,
}: OperationPickerProps): React.ReactElement {
  const { keltaClient } = useApi()
  const [query, setQuery] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['api-operations', { specId, query }],
    queryFn: () =>
      keltaClient.admin.apiSpecs.searchOperations({
        q: query || undefined,
        specId: specId || undefined,
        limit: 50,
      }),
  })

  const operations = data ?? []

  return (
    <div className={className ?? 'space-y-2'}>
      <FieldLabel>{label}</FieldLabel>
      <Input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search operations by path, summary, or tag…"
      />
      <div className="max-h-72 overflow-y-auto rounded-md border">
        {isLoading && <p className="p-3 text-xs text-muted-foreground">Searching…</p>}
        {!isLoading && operations.length === 0 && (
          <p className="p-3 text-xs text-muted-foreground">No operations match.</p>
        )}
        {operations.map((op) => {
          const isSelected = value === op.syntheticOpId
          return (
            <button
              type="button"
              key={op.id}
              onClick={() => onChange(op.syntheticOpId, op)}
              className={cn(
                'flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-muted/60 border-b last:border-b-0',
                isSelected && 'bg-primary/10'
              )}
            >
              <span
                className={cn(
                  'rounded px-1.5 py-0.5 text-[10px] font-mono font-bold',
                  METHOD_COLORS[op.httpMethod] ?? 'bg-muted'
                )}
              >
                {op.httpMethod}
              </span>
              <span className="font-mono text-xs">{op.pathTemplate}</span>
              {op.summary && (
                <span className="text-xs text-muted-foreground truncate">
                  — {op.summary}
                </span>
              )}
              {op.deprecated && (
                <span className="ml-auto text-[10px] text-muted-foreground">
                  deprecated
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
