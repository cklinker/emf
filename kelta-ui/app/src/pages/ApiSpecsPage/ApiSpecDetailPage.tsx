import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, BookOpen, ChevronRight } from 'lucide-react'
import type { ApiOperationDetail, ApiOperationSummary } from '@kelta/sdk'

import { useApi } from '../../context/ApiContext'
import { getTenantSlug } from '../../context/TenantContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { FieldLabel } from '@/components/kelta'
import { cn } from '@/lib/utils'

export interface ApiSpecDetailPageProps {
  className?: string
}

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200',
  POST: 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-200',
  PUT: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200',
  PATCH: 'bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-200',
  DELETE: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200',
}

export function ApiSpecDetailPage({ className }: ApiSpecDetailPageProps): React.ReactElement {
  const { specId } = useParams<{ specId: string }>()
  const { keltaClient } = useApi()

  const specQuery = useQuery({
    queryKey: ['api-spec', specId],
    queryFn: () => keltaClient.admin.apiSpecs.get(specId!),
    enabled: Boolean(specId),
  })

  const operationsQuery = useQuery({
    queryKey: ['api-spec-operations', specId],
    queryFn: () => keltaClient.admin.apiSpecs.listOperations(specId!),
    enabled: Boolean(specId),
  })

  const rawQuery = useQuery({
    queryKey: ['api-spec-raw', specId],
    queryFn: () => keltaClient.admin.apiSpecs.raw(specId!),
    enabled: Boolean(specId),
  })

  const [selected, setSelected] = useState<string | null>(null)
  const [filter, setFilter] = useState('')

  if (specQuery.isLoading) return <LoadingSpinner />
  if (specQuery.error || !specQuery.data) {
    return <ErrorMessage error="Failed to load spec" />
  }

  const spec = specQuery.data
  const operations = operationsQuery.data ?? []
  const filtered = filter
    ? operations.filter(
        (op) =>
          op.pathTemplate.toLowerCase().includes(filter.toLowerCase()) ||
          (op.summary?.toLowerCase().includes(filter.toLowerCase()) ?? false) ||
          op.httpMethod.toLowerCase().includes(filter.toLowerCase())
      )
    : operations

  const grouped = groupByTag(filtered)
  const selectedOp = selected
    ? (operations.find((op) => op.syntheticOpId === selected) ?? null)
    : null

  return (
    <div className={cn('space-y-4', className)}>
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link
          to={`/${getTenantSlug()}/api-specs`}
          className="inline-flex items-center gap-1 hover:text-foreground"
        >
          <ArrowLeft className="h-3.5 w-3.5" /> API Specs
        </Link>
        <ChevronRight className="h-3.5 w-3.5" />
        <span className="text-foreground font-medium">{spec.apiTitle || spec.name}</span>
      </div>

      <div className="flex items-center gap-3">
        <BookOpen className="h-6 w-6 text-primary" />
        <div className="min-w-0">
          <h1 className="text-[26px] font-bold tracking-[-0.01em] truncate">
            {spec.apiTitle || spec.name}
          </h1>
          <p className="text-sm text-muted-foreground truncate">
            <code className="font-mono">{spec.name}</code> · v{spec.apiVersion ?? '—'} · OpenAPI{' '}
            {spec.specVersion} · revision {spec.revision}
          </p>
        </div>
      </div>

      <Tabs defaultValue="operations">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="operations">Operations ({operations.length})</TabsTrigger>
          <TabsTrigger value="raw">Raw</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Spec details</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <KeyValue label="Name" value={spec.name} mono />
              <KeyValue label="API title" value={spec.apiTitle ?? '—'} />
              <KeyValue label="API version" value={spec.apiVersion ?? '—'} />
              <KeyValue label="OpenAPI version" value={spec.specVersion} mono />
              <KeyValue label="Base URL" value={spec.baseUrl ?? '—'} mono />
              <KeyValue label="Source" value={spec.sourceType} mono />
              {spec.sourceUrl && <KeyValue label="Source URL" value={spec.sourceUrl} mono />}
              <KeyValue
                label="Last imported"
                value={spec.lastImportedAt ? new Date(spec.lastImportedAt).toLocaleString() : '—'}
              />
              <KeyValue label="Active" value={spec.active ? 'Yes' : 'No'} />
              <KeyValue label="Revision" value={String(spec.revision)} mono />
              {spec.description && (
                <div className="sm:col-span-2">
                  <FieldLabel>Description</FieldLabel>
                  <p className="text-sm">{spec.description}</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent
          value="operations"
          className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]"
        >
          <Card>
            <CardContent className="p-3 space-y-2">
              <Input
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                placeholder="Filter by path, method, or summary"
              />
              <div className="max-h-[60vh] overflow-y-auto space-y-3 pt-1">
                {Object.entries(grouped).map(([tag, ops]) => (
                  <div key={tag}>
                    <p className="px-1 text-[10px] uppercase tracking-wider text-muted-foreground">
                      {tag}
                    </p>
                    <div className="rounded-md border">
                      {ops.map((op) => (
                        <button
                          key={op.id}
                          onClick={() => setSelected(op.syntheticOpId)}
                          className={cn(
                            'flex w-full items-center gap-2 px-2 py-1.5 text-left text-sm hover:bg-muted/50 border-b last:border-b-0',
                            selected === op.syntheticOpId && 'bg-primary/10'
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
                          <span className="font-mono text-xs truncate">{op.pathTemplate}</span>
                          {op.deprecated && (
                            <span className="ml-auto text-[10px] text-muted-foreground">
                              deprecated
                            </span>
                          )}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
                {filtered.length === 0 && (
                  <p className="p-3 text-xs text-muted-foreground">No operations match.</p>
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4">
              {selectedOp ? (
                <OperationDetailView specId={spec.id} op={selectedOp} />
              ) : (
                <p className="text-sm text-muted-foreground">
                  Select an operation to view its details.
                </p>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="raw">
          <Card>
            <CardContent className="p-0">
              <pre className="max-h-[70vh] overflow-auto bg-muted/30 p-4 text-xs font-mono">
                {rawQuery.isLoading ? 'Loading…' : (rawQuery.data ?? '')}
              </pre>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}

function groupByTag(operations: ApiOperationSummary[]): Record<string, ApiOperationSummary[]> {
  const groups: Record<string, ApiOperationSummary[]> = {}
  for (const op of operations) {
    const tags = Array.isArray(op.tags) ? (op.tags as string[]) : []
    const key = tags[0] || 'untagged'
    if (!groups[key]) groups[key] = []
    groups[key].push(op)
  }
  return groups
}

function KeyValue({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="space-y-1">
      <FieldLabel>{label}</FieldLabel>
      <p className={cn('text-sm break-all', mono && 'font-mono text-xs')}>{value}</p>
    </div>
  )
}

interface OperationDetailViewProps {
  specId: string
  op: ApiOperationSummary
}

function OperationDetailView({ specId, op }: OperationDetailViewProps) {
  const { keltaClient } = useApi()
  const detailQuery = useQuery({
    queryKey: ['api-operation', specId, op.syntheticOpId],
    queryFn: () => keltaClient.admin.apiSpecs.getOperation(specId, op.syntheticOpId),
  })

  if (detailQuery.isLoading || !detailQuery.data) {
    return <p className="text-sm text-muted-foreground">Loading operation…</p>
  }
  const detail: ApiOperationDetail = detailQuery.data

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <span
          className={cn(
            'rounded px-2 py-0.5 text-xs font-mono font-bold',
            METHOD_COLORS[detail.httpMethod] ?? 'bg-muted'
          )}
        >
          {detail.httpMethod}
        </span>
        <span className="font-mono text-sm">{detail.pathTemplate}</span>
        {detail.deprecated && <span className="text-xs text-muted-foreground">deprecated</span>}
      </div>
      {detail.summary && <p className="text-sm font-medium">{detail.summary}</p>}
      {detail.description && (
        <p className="text-sm text-muted-foreground whitespace-pre-wrap">{detail.description}</p>
      )}
      {detail.parametersSchema != null && (
        <SchemaSection title="Parameters" schema={detail.parametersSchema} />
      )}
      {detail.requestBodySchema != null && (
        <SchemaSection title="Request body" schema={detail.requestBodySchema} />
      )}
      {detail.responseSchemas != null && (
        <SchemaSection title="Responses" schema={detail.responseSchemas} />
      )}
    </div>
  )
}

function SchemaSection({ title, schema }: { title: string; schema: unknown }) {
  return (
    <div>
      <FieldLabel>{title}</FieldLabel>
      <pre className="mt-1 overflow-x-auto rounded-md border bg-muted/30 p-3 text-[11px] font-mono leading-snug">
        {JSON.stringify(schema, null, 2)}
      </pre>
    </div>
  )
}
