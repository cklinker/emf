import React, { useState } from 'react'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent } from '@/components/ui/card'
import { FieldLabel } from '@/components/kelta'
import { Plus, Trash2 } from 'lucide-react'
import { CredentialPicker } from '@/components/credentials/CredentialPicker'
import { SpecPicker } from '@/components/apiSpec/SpecPicker'
import { OperationPicker } from '@/components/apiSpec/OperationPicker'
import { cn } from '@/lib/utils'

interface CallApiParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

type Mode = 'operation' | 'raw'

interface KeyValue {
  key: string
  value: string
}

function toEntries(map: unknown): KeyValue[] {
  if (!map || typeof map !== 'object') return []
  return Object.entries(map as Record<string, unknown>).map(([key, value]) => ({
    key,
    value: typeof value === 'string' ? value : JSON.stringify(value),
  }))
}

function fromEntries(entries: KeyValue[]): Record<string, string> {
  const out: Record<string, string> = {}
  for (const e of entries) {
    if (e.key.trim()) out[e.key] = e.value
  }
  return out
}

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

/**
 * Properties editor for the CALL_API flow step. Two modes:
 * - operation: pick an OpenAPI spec + operation, fill path/query/headers/body
 * - raw: free-form URL + method (Postman-style), with credential and idempotency
 */
export function CallApiParams({ parameters, onUpdate }: CallApiParamsProps) {
  const params = parameters ?? {}
  const mode = ((params.mode as string) ?? 'raw') as Mode

  const update = (next: Record<string, unknown>) => onUpdate({ ...params, ...next })

  const [headerEntries, setHeaderEntries] = useState<KeyValue[]>(() =>
    toEntries(params.headers)
  )
  const [pathParamEntries, setPathParamEntries] = useState<KeyValue[]>(() =>
    toEntries(params.pathParams)
  )
  const [queryEntries, setQueryEntries] = useState<KeyValue[]>(() =>
    toEntries(params.queryParams)
  )

  const updateHeaders = (entries: KeyValue[]) => {
    setHeaderEntries(entries)
    update({ headers: fromEntries(entries) })
  }
  const updatePathParams = (entries: KeyValue[]) => {
    setPathParamEntries(entries)
    update({ pathParams: fromEntries(entries) })
  }
  const updateQueryParams = (entries: KeyValue[]) => {
    setQueryEntries(entries)
    update({ queryParams: fromEntries(entries) })
  }

  const credentialId = (params.credentialRef as string) ?? ''
  const specId = (params.specId as string) ?? ''
  const operationId = (params.operationId as string) ?? ''

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <FieldLabel>Mode</FieldLabel>
        <div
          role="tablist"
          className="grid grid-cols-2 rounded-md border p-1 text-sm bg-muted/30"
        >
          <ModeTab
            active={mode === 'operation'}
            onClick={() => update({ mode: 'operation' })}
          >
            From OpenAPI spec
          </ModeTab>
          <ModeTab
            active={mode === 'raw'}
            onClick={() => update({ mode: 'raw' })}
          >
            Ad-hoc (Postman)
          </ModeTab>
        </div>
        <p className="text-xs text-muted-foreground">
          {mode === 'operation'
            ? 'Pick an imported spec and operation; URL + method are derived from the spec.'
            : 'Free-form URL + method. Use ${$.path} placeholders or =jsonata expressions in any field.'}
        </p>
      </div>

      {mode === 'operation' ? (
        <Card className="bg-muted/20">
          <CardContent className="space-y-3 p-3">
            <SpecPicker
              value={specId}
              onChange={(id) => update({ specId: id, operationId: '' })}
            />
            {specId && (
              <OperationPicker
                specId={specId}
                value={operationId}
                onChange={(id) => update({ operationId: id })}
              />
            )}
          </CardContent>
        </Card>
      ) : (
        <Card className="bg-muted/20">
          <CardContent className="space-y-3 p-3">
            <div className="flex gap-2">
              <div className="space-y-1 w-32">
                <FieldLabel>Method</FieldLabel>
                <Select
                  value={(params.method as string) ?? 'GET'}
                  onValueChange={(v) => update({ method: v })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {HTTP_METHODS.map((m) => (
                      <SelectItem key={m} value={m}>
                        {m}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1 flex-1">
                <FieldLabel>URL</FieldLabel>
                <Input
                  value={(params.url as string) ?? ''}
                  onChange={(e) => update({ url: e.target.value })}
                  placeholder="https://api.example.com/things/${$.input.id}"
                  className="font-mono text-xs"
                />
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <CredentialPicker
        value={credentialId}
        onChange={(id) => update({ credentialRef: id })}
      />

      {mode === 'operation' && (
        <KeyValueList
          label="Path parameters"
          entries={pathParamEntries}
          onChange={updatePathParams}
          placeholderKey="petId"
          placeholderValue="${$.input.id}"
        />
      )}

      <KeyValueList
        label="Query parameters"
        entries={queryEntries}
        onChange={updateQueryParams}
        placeholderKey="status"
        placeholderValue="active"
      />

      <KeyValueList
        label="Headers"
        entries={headerEntries}
        onChange={updateHeaders}
        placeholderKey="X-Trace"
        placeholderValue="${$.executionId}"
      />

      <div className="space-y-1">
        <FieldLabel>{mode === 'operation' ? 'Request body' : 'Body'}</FieldLabel>
        <Textarea
          rows={6}
          value={
            typeof params[mode === 'operation' ? 'requestBody' : 'body'] === 'string'
              ? (params[mode === 'operation' ? 'requestBody' : 'body'] as string)
              : params[mode === 'operation' ? 'requestBody' : 'body']
                ? JSON.stringify(params[mode === 'operation' ? 'requestBody' : 'body'], null, 2)
                : ''
          }
          onChange={(e) => {
            const trimmed = e.target.value.trim()
            let parsed: unknown = e.target.value
            if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
              try {
                parsed = JSON.parse(trimmed)
              } catch {
                parsed = e.target.value
              }
            }
            update({ [mode === 'operation' ? 'requestBody' : 'body']: parsed })
          }}
          className="font-mono text-xs"
          placeholder={'{ "name": "${$.record.data.name}" }'}
        />
        <p className="text-xs text-muted-foreground">
          Strings starting with <code className="font-mono">=</code> are evaluated as
          JSONata; everything else flows through <code className="font-mono">${'${$.path}'}</code>{' '}
          substitution.
        </p>
      </div>

      <div className="space-y-1">
        <FieldLabel>Response variable</FieldLabel>
        <Input
          value={(params.responseVariable as string) ?? ''}
          onChange={(e) => update({ responseVariable: e.target.value })}
          placeholder="apiResult"
        />
        <p className="text-xs text-muted-foreground">
          Optional. Persists the response under this name on the flow state.
        </p>
      </div>

      <details className="rounded-md border bg-muted/10">
        <summary className="cursor-pointer px-3 py-2 text-sm font-medium">
          Advanced — idempotency &amp; retry
        </summary>
        <div className="space-y-3 p-3">
          <div className="space-y-1">
            <FieldLabel>Idempotency key (optional)</FieldLabel>
            <Input
              value={
                ((params.idempotency as Record<string, unknown> | undefined)?.key as string) ?? ''
              }
              onChange={(e) =>
                update({
                  idempotency: {
                    ...(params.idempotency as object | undefined),
                    enabled: true,
                    key: e.target.value,
                  },
                })
              }
              placeholder="${$.input.orderId}"
              className="font-mono text-xs"
            />
            <p className="text-xs text-muted-foreground">
              When set, the platform sends an Idempotency-Key header upstream and replays
              cached responses on retry.
            </p>
          </div>
        </div>
      </details>
    </div>
  )
}

function ModeTab({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        'rounded px-3 py-1.5 transition-colors',
        active ? 'bg-background shadow-sm font-medium' : 'text-muted-foreground'
      )}
    >
      {children}
    </button>
  )
}

interface KeyValueListProps {
  label: string
  entries: KeyValue[]
  onChange: (entries: KeyValue[]) => void
  placeholderKey: string
  placeholderValue: string
}

function KeyValueList({
  label,
  entries,
  onChange,
  placeholderKey,
  placeholderValue,
}: KeyValueListProps) {
  return (
    <div className="space-y-1">
      <FieldLabel>{label}</FieldLabel>
      <div className="space-y-1">
        {entries.map((entry, i) => (
          <div key={i} className="flex gap-2">
            <Input
              className="flex-1 font-mono text-xs"
              value={entry.key}
              onChange={(e) => {
                const next = [...entries]
                next[i] = { ...entry, key: e.target.value }
                onChange(next)
              }}
              placeholder={placeholderKey}
            />
            <Input
              className="flex-1 font-mono text-xs"
              value={entry.value}
              onChange={(e) => {
                const next = [...entries]
                next[i] = { ...entry, value: e.target.value }
                onChange(next)
              }}
              placeholder={placeholderValue}
            />
            <Button
              size="sm"
              variant="outline"
              onClick={() => onChange(entries.filter((_, idx) => idx !== i))}
              title="Remove"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        ))}
        <Button
          size="sm"
          variant="outline"
          onClick={() => onChange([...entries, { key: '', value: '' }])}
        >
          <Plus className="mr-1 h-3.5 w-3.5" />
          Add
        </Button>
      </div>
    </div>
  )
}
