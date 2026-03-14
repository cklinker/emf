/**
 * WebhooksPage — Full-featured webhook management portal built with svix-react.
 *
 * Replicates the functionality of the Svix Application Portal:
 * - Endpoint management (CRUD, enable/disable, rate limiting)
 * - Delivery monitoring & observability (attempts, response codes, latency)
 * - Event catalog with descriptions
 * - Event subscription filtering per endpoint
 * - Signing secret management (view & rotate)
 * - Manual retry / recover failed messages
 * - Endpoint statistics (success/fail/pending counts)
 * - Message log with payload inspection
 */
import React, { useState, useEffect, useCallback } from 'react'
import {
  SvixProvider,
  useEndpoints,
  useEndpoint,
  useNewEndpoint,
  useEndpointMessageAttempts,
  useEndpointFunctions,
  useEndpointSecret,
  useEndpointStats,
  useEventTypes,
  useMessages,
  useAttemptFunctions,
} from 'svix-react'
import type { EndpointOut, EndpointUpdate, MessageAttemptOut } from 'svix'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Switch } from '@/components/ui/switch'

export interface WebhooksPageProps {
  testId?: string
}

// ---------------------------------------------------------------------------
// Main page — fetches Svix credentials, wraps children in SvixProvider
// ---------------------------------------------------------------------------

export function WebhooksPage({ testId = 'webhooks-page' }: WebhooksPageProps): React.ReactElement {
  const { keltaClient } = useApi()
  const [svixCredentials, setSvixCredentials] = useState<{
    token: string
    appId: string
    serverUrl: string
  } | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let cancelled = false

    async function fetchAccess() {
      try {
        setIsLoading(true)
        setError(null)
        const result = await keltaClient.admin.svix.getPortalAccess()
        if (!cancelled) {
          setSvixCredentials(result)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error('Failed to load webhook portal'))
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    fetchAccess()
    return () => {
      cancelled = true
    }
  }, [keltaClient])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading webhooks..." />
        </div>
      </div>
    )
  }

  if (error || !svixCredentials) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error ?? new Error('Unable to load webhooks')}
          onRetry={() => window.location.reload()}
        />
      </div>
    )
  }

  return (
    <SvixProvider
      token={svixCredentials.token}
      appId={svixCredentials.appId}
      options={{ serverUrl: svixCredentials.serverUrl }}
    >
      <WebhooksDashboard testId={testId} />
    </SvixProvider>
  )
}

// ---------------------------------------------------------------------------
// Dashboard — tabbed layout: Endpoints | Messages | Event Catalog
// ---------------------------------------------------------------------------

type DashboardView = { kind: 'list' } | { kind: 'endpoint-detail'; endpointId: string }

function WebhooksDashboard({ testId }: { testId: string }) {
  const [view, setView] = useState<DashboardView>({ kind: 'list' })
  const [showCreateDialog, setShowCreateDialog] = useState(false)
  const [activeTab, setActiveTab] = useState('endpoints')

  // When selecting an endpoint, switch to the detail view
  const handleSelectEndpoint = useCallback((ep: EndpointOut) => {
    setView({ kind: 'endpoint-detail', endpointId: ep.id })
  }, [])

  const handleBack = useCallback(() => {
    setView({ kind: 'list' })
  }, [])

  if (view.kind === 'endpoint-detail') {
    return (
      <div className="flex h-full flex-col" data-testid={testId}>
        <div className="flex-1 overflow-y-auto p-6 lg:p-8">
          <EndpointDetail endpointId={view.endpointId} onBack={handleBack} />
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col" data-testid={testId}>
      <header className="flex items-center justify-between border-b px-6 py-4 lg:px-8">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">Webhooks</h1>
          <p className="text-sm text-muted-foreground">
            Manage webhook endpoints, monitor deliveries, and browse available events
          </p>
        </div>
        {activeTab === 'endpoints' && (
          <Button onClick={() => setShowCreateDialog(true)}>Add Endpoint</Button>
        )}
      </header>

      <div className="flex-1 overflow-y-auto p-6 lg:p-8">
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="endpoints">Endpoints</TabsTrigger>
            <TabsTrigger value="messages">Messages</TabsTrigger>
            <TabsTrigger value="event-catalog">Event Catalog</TabsTrigger>
          </TabsList>

          <TabsContent value="endpoints" className="mt-6">
            <EndpointList onSelect={handleSelectEndpoint} />
          </TabsContent>

          <TabsContent value="messages" className="mt-6">
            <MessageLog />
          </TabsContent>

          <TabsContent value="event-catalog" className="mt-6">
            <EventCatalog />
          </TabsContent>
        </Tabs>
      </div>

      <CreateEndpointDialog open={showCreateDialog} onOpenChange={setShowCreateDialog} />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Endpoint list with stats summary
// ---------------------------------------------------------------------------

function EndpointList({ onSelect }: { onSelect: (ep: EndpointOut) => void }) {
  const endpoints = useEndpoints({ limit: 50 })

  if (endpoints.loading) {
    return (
      <div className="flex min-h-[200px] items-center justify-center">
        <LoadingSpinner size="medium" label="Loading endpoints..." />
      </div>
    )
  }

  if (endpoints.error) {
    return <ErrorMessage error={endpoints.error} onRetry={endpoints.reload} />
  }

  if (!endpoints.data || endpoints.data.length === 0) {
    return (
      <div className="flex min-h-[300px] flex-col items-center justify-center gap-4 text-center">
        <div className="rounded-full bg-muted p-4">
          <svg
            className="h-8 w-8 text-muted-foreground"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m9.193-9.193a4.5 4.5 0 016.364 6.364l-4.5 4.5a4.5 4.5 0 01-7.244-1.242"
            />
          </svg>
        </div>
        <div>
          <h3 className="text-lg font-medium text-foreground">No webhook endpoints</h3>
          <p className="text-sm text-muted-foreground">
            Create an endpoint to start receiving webhook notifications.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        {endpoints.data.map((ep) => (
          <EndpointCard key={ep.id} endpoint={ep} onSelect={onSelect} />
        ))}
      </div>

      <PaginationControls
        hasPrev={endpoints.hasPrevPage}
        hasNext={endpoints.hasNextPage}
        onPrev={endpoints.prevPage}
        onNext={endpoints.nextPage}
      />
    </div>
  )
}

function EndpointCard({
  endpoint: ep,
  onSelect,
}: {
  endpoint: EndpointOut
  onSelect: (ep: EndpointOut) => void
}) {
  const stats = useEndpointStats(ep.id)

  return (
    <button
      onClick={() => onSelect(ep)}
      className="flex w-full items-center justify-between rounded-lg border bg-card p-4 text-left transition-colors hover:bg-accent"
    >
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate font-medium text-foreground">{ep.description || ep.url}</span>
          {ep.disabled ? (
            <Badge variant="secondary">Disabled</Badge>
          ) : (
            <Badge className="bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
              Active
            </Badge>
          )}
          {ep.rateLimit && (
            <Badge variant="outline" className="text-xs">
              {ep.rateLimit}/s
            </Badge>
          )}
        </div>
        <p className="mt-1 truncate text-sm text-muted-foreground">{ep.url}</p>
        {ep.filterTypes && ep.filterTypes.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {ep.filterTypes.slice(0, 3).map((t) => (
              <Badge key={t} variant="outline" className="text-xs">
                {t}
              </Badge>
            ))}
            {ep.filterTypes.length > 3 && (
              <Badge variant="outline" className="text-xs">
                +{ep.filterTypes.length - 3} more
              </Badge>
            )}
          </div>
        )}
      </div>
      {/* Mini stats */}
      <div className="ml-4 flex shrink-0 items-center gap-3">
        {stats.data && (
          <div className="flex gap-2 text-xs">
            {stats.data.success > 0 && (
              <span className="text-green-600 dark:text-green-400">{stats.data.success} ok</span>
            )}
            {stats.data.fail > 0 && (
              <span className="text-red-600 dark:text-red-400">{stats.data.fail} fail</span>
            )}
            {stats.data.pending > 0 && (
              <span className="text-yellow-600 dark:text-yellow-400">
                {stats.data.pending} pending
              </span>
            )}
          </div>
        )}
        <svg
          className="h-5 w-5 shrink-0 text-muted-foreground"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
        </svg>
      </div>
    </button>
  )
}

// ---------------------------------------------------------------------------
// Endpoint detail — tabbed: Overview | Deliveries | Settings
// ---------------------------------------------------------------------------

function EndpointDetail({ endpointId, onBack }: { endpointId: string; onBack: () => void }) {
  const endpointEntity = useEndpoint(endpointId)
  const stats = useEndpointStats(endpointId)
  const secret = useEndpointSecret(endpointId)
  const { deleteEndpoint, updateEndpoint, recoverEndpointMessages } =
    useEndpointFunctions(endpointId)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showRecoverDialog, setShowRecoverDialog] = useState(false)
  const [showRotateSecretDialog, setShowRotateSecretDialog] = useState(false)
  const [showEditDialog, setShowEditDialog] = useState(false)

  const handleDelete = useCallback(async () => {
    try {
      await deleteEndpoint()
      onBack()
    } catch {
      // error surfaced by hook
    }
  }, [deleteEndpoint, onBack])

  if (endpointEntity.loading) {
    return (
      <div className="flex min-h-[200px] items-center justify-center">
        <LoadingSpinner size="medium" label="Loading endpoint..." />
      </div>
    )
  }

  if (endpointEntity.error || !endpointEntity.data) {
    return (
      <ErrorMessage
        error={endpointEntity.error ?? new Error('Endpoint not found')}
        onRetry={endpointEntity.reload}
      />
    )
  }

  const endpoint = endpointEntity.data

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={onBack}>
            <svg
              className="mr-1 h-4 w-4"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
            </svg>
            Back
          </Button>
          <div>
            <h2 className="text-xl font-semibold text-foreground">
              {endpoint.description || 'Endpoint'}
            </h2>
            <p className="text-sm text-muted-foreground">{endpoint.url}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setShowEditDialog(true)}>
            Edit
          </Button>
          <Button variant="destructive" size="sm" onClick={() => setShowDeleteConfirm(true)}>
            Delete
          </Button>
        </div>
      </div>

      {/* Stats summary bar */}
      {stats.data && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatCard label="Successful" value={stats.data.success} color="green" />
          <StatCard label="Failed" value={stats.data.fail} color="red" />
          <StatCard label="Pending" value={stats.data.pending} color="yellow" />
          <StatCard label="Sending" value={stats.data.sending} color="blue" />
        </div>
      )}

      {/* Tabbed content */}
      <Tabs defaultValue="deliveries">
        <TabsList>
          <TabsTrigger value="deliveries">Deliveries</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>

        <TabsContent value="deliveries" className="mt-4">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-sm text-muted-foreground">
              Recent delivery attempts to this endpoint
            </p>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => setShowRecoverDialog(true)}>
                Recover Failed
              </Button>
            </div>
          </div>
          <EndpointDeliveries endpointId={endpointId} />
        </TabsContent>

        <TabsContent value="settings" className="mt-4">
          <EndpointSettings
            endpoint={endpoint}
            secret={secret}
            onUpdate={updateEndpoint}
            onRotateSecret={() => setShowRotateSecretDialog(true)}
            onReload={endpointEntity.reload}
          />
        </TabsContent>
      </Tabs>

      {/* Delete confirmation dialog */}
      <Dialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Endpoint</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete this endpoint? This action cannot be undone. All
              future webhook deliveries to this URL will stop.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeleteConfirm(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete}>
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Recover failed messages dialog */}
      <RecoverDialog
        open={showRecoverDialog}
        onOpenChange={setShowRecoverDialog}
        onRecover={recoverEndpointMessages}
      />

      {/* Rotate secret dialog */}
      <RotateSecretDialog
        open={showRotateSecretDialog}
        onOpenChange={setShowRotateSecretDialog}
        onRotate={secret.rotateSecret}
        onReload={secret.reload}
      />

      {/* Edit endpoint dialog */}
      <EditEndpointDialog
        open={showEditDialog}
        onOpenChange={setShowEditDialog}
        endpoint={endpoint}
        onUpdate={updateEndpoint}
        onReload={endpointEntity.reload}
      />
    </div>
  )
}

function StatCard({
  label,
  value,
  color,
}: {
  label: string
  value: number
  color: 'green' | 'red' | 'yellow' | 'blue'
}) {
  const colorClasses = {
    green: 'bg-green-50 text-green-700 dark:bg-green-950/30 dark:text-green-400',
    red: 'bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400',
    yellow: 'bg-yellow-50 text-yellow-700 dark:bg-yellow-950/30 dark:text-yellow-400',
    blue: 'bg-blue-50 text-blue-700 dark:bg-blue-950/30 dark:text-blue-400',
  }
  return (
    <div className={`rounded-lg p-3 ${colorClasses[color]}`}>
      <p className="text-2xl font-bold">{value}</p>
      <p className="text-xs font-medium opacity-80">{label}</p>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Endpoint deliveries — attempt list with resend
// ---------------------------------------------------------------------------

function EndpointDeliveries({ endpointId }: { endpointId: string }) {
  const attempts = useEndpointMessageAttempts(endpointId, { limit: 25, withMsg: true })

  if (attempts.loading) {
    return (
      <div className="flex min-h-[100px] items-center justify-center">
        <LoadingSpinner size="small" label="Loading deliveries..." />
      </div>
    )
  }

  if (attempts.error) {
    return <ErrorMessage error={attempts.error} onRetry={attempts.reload} />
  }

  if (!attempts.data || attempts.data.length === 0) {
    return (
      <div className="rounded-lg border bg-muted/30 py-8 text-center">
        <p className="text-sm text-muted-foreground">No deliveries yet</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="overflow-hidden rounded-lg border">
        <table className="w-full text-sm">
          <thead className="border-b bg-muted/50">
            <tr>
              <th className="px-4 py-2 text-left font-medium text-muted-foreground">Status</th>
              <th className="px-4 py-2 text-left font-medium text-muted-foreground">Event Type</th>
              <th className="px-4 py-2 text-left font-medium text-muted-foreground">Response</th>
              <th className="px-4 py-2 text-left font-medium text-muted-foreground">Latency</th>
              <th className="px-4 py-2 text-left font-medium text-muted-foreground">Timestamp</th>
              <th className="px-4 py-2 text-right font-medium text-muted-foreground">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {attempts.data.map((attempt) => (
              <AttemptRow key={attempt.id} attempt={attempt} />
            ))}
          </tbody>
        </table>
      </div>

      <PaginationControls
        hasPrev={attempts.hasPrevPage}
        hasNext={attempts.hasNextPage}
        onPrev={attempts.prevPage}
        onNext={attempts.nextPage}
      />
    </div>
  )
}

function AttemptRow({ attempt }: { attempt: MessageAttemptOut }) {
  const { resendAttempt } = useAttemptFunctions(attempt)
  const [resending, setResending] = useState(false)
  const [showPayload, setShowPayload] = useState(false)

  const handleResend = useCallback(async () => {
    setResending(true)
    try {
      await resendAttempt()
    } catch {
      // handled by hook
    } finally {
      setResending(false)
    }
  }, [resendAttempt])

  return (
    <>
      <tr className="hover:bg-muted/30">
        <td className="px-4 py-2">
          <AttemptStatusBadge status={attempt.status} />
        </td>
        <td className="px-4 py-2 text-foreground">{attempt.msg?.eventType ?? '-'}</td>
        <td className="px-4 py-2 text-foreground">
          <ResponseCodeBadge code={attempt.responseStatusCode} />
        </td>
        <td className="px-4 py-2 text-muted-foreground">{attempt.responseDurationMs}ms</td>
        <td className="px-4 py-2 text-muted-foreground">
          {new Date(attempt.timestamp).toLocaleString()}
        </td>
        <td className="px-4 py-2 text-right">
          <div className="flex items-center justify-end gap-1">
            {attempt.msg?.payload && (
              <Button variant="ghost" size="sm" onClick={() => setShowPayload(!showPayload)}>
                {showPayload ? 'Hide' : 'Payload'}
              </Button>
            )}
            <Button variant="ghost" size="sm" onClick={handleResend} disabled={resending}>
              {resending ? 'Resending...' : 'Retry'}
            </Button>
          </div>
        </td>
      </tr>
      {showPayload && attempt.msg?.payload && (
        <tr>
          <td colSpan={6} className="bg-muted/20 px-4 py-3">
            <pre className="max-h-[300px] overflow-auto rounded-md bg-muted p-3 text-xs">
              {typeof attempt.msg.payload === 'string'
                ? attempt.msg.payload
                : JSON.stringify(attempt.msg.payload, null, 2)}
            </pre>
          </td>
        </tr>
      )}
    </>
  )
}

function ResponseCodeBadge({ code }: { code: number }) {
  if (code >= 200 && code < 300) {
    return <span className="font-mono text-xs text-green-700 dark:text-green-400">{code}</span>
  }
  if (code >= 400 && code < 500) {
    return <span className="font-mono text-xs text-yellow-700 dark:text-yellow-400">{code}</span>
  }
  if (code >= 500) {
    return <span className="font-mono text-xs text-red-700 dark:text-red-400">{code}</span>
  }
  return <span className="font-mono text-xs text-muted-foreground">{code || '-'}</span>
}

// ---------------------------------------------------------------------------
// Endpoint settings — secret, event filters, enable/disable, rate limit
// ---------------------------------------------------------------------------

function EndpointSettings({
  endpoint,
  secret,
  onUpdate,
  onRotateSecret,
  onReload,
}: {
  endpoint: EndpointOut
  secret: ReturnType<typeof useEndpointSecret>
  onUpdate: (opts: EndpointUpdate) => Promise<EndpointOut>
  onRotateSecret: () => void
  onReload: () => void
}) {
  const [showSecret, setShowSecret] = useState(false)
  const [toggling, setToggling] = useState(false)

  const handleToggleEnabled = useCallback(async () => {
    setToggling(true)
    try {
      await onUpdate({
        url: endpoint.url,
        description: endpoint.description,
        filterTypes: endpoint.filterTypes,
        rateLimit: endpoint.rateLimit,
        disabled: !endpoint.disabled,
      })
      onReload()
    } catch {
      // handled by hook
    } finally {
      setToggling(false)
    }
  }, [onUpdate, endpoint, onReload])

  return (
    <div className="space-y-6">
      {/* Endpoint details */}
      <div className="space-y-4">
        <h3 className="text-sm font-semibold text-foreground">Endpoint Configuration</h3>
        <div className="grid gap-4 sm:grid-cols-2">
          <InfoCard label="URL" value={endpoint.url} />
          <InfoCard label="Created" value={new Date(endpoint.createdAt).toLocaleString()} />
          <InfoCard label="Updated" value={new Date(endpoint.updatedAt).toLocaleString()} />
          <InfoCard label="Version" value={String(endpoint.version)} />
        </div>
      </div>

      {/* Enable/Disable toggle */}
      <div className="rounded-lg border p-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="font-medium text-foreground">Endpoint Status</p>
            <p className="text-sm text-muted-foreground">
              {endpoint.disabled
                ? 'This endpoint is disabled and will not receive webhook deliveries.'
                : 'This endpoint is active and receiving webhook deliveries.'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">
              {endpoint.disabled ? 'Disabled' : 'Enabled'}
            </span>
            <Switch
              checked={!endpoint.disabled}
              onCheckedChange={handleToggleEnabled}
              disabled={toggling}
            />
          </div>
        </div>
      </div>

      {/* Rate limit */}
      <div className="rounded-lg border p-4">
        <p className="font-medium text-foreground">Rate Limiting</p>
        <p className="text-sm text-muted-foreground">
          {endpoint.rateLimit
            ? `This endpoint is rate limited to ${endpoint.rateLimit} messages per second.`
            : 'No rate limit configured. Messages are delivered as fast as possible.'}
        </p>
      </div>

      {/* Event type filters */}
      <div className="rounded-lg border p-4">
        <p className="font-medium text-foreground">Event Type Filters</p>
        {endpoint.filterTypes && endpoint.filterTypes.length > 0 ? (
          <div className="mt-2 flex flex-wrap gap-1">
            {endpoint.filterTypes.map((t) => (
              <Badge key={t} variant="outline">
                {t}
              </Badge>
            ))}
          </div>
        ) : (
          <p className="mt-1 text-sm text-muted-foreground">
            Subscribed to all event types. Use the Edit button to filter specific events.
          </p>
        )}
      </div>

      {/* Signing secret */}
      <div className="rounded-lg border p-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="font-medium text-foreground">Signing Secret</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Use this secret to verify webhook signatures and ensure messages are authentic.
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setShowSecret(!showSecret)}>
              {showSecret ? 'Hide' : 'Reveal'}
            </Button>
            <Button variant="outline" size="sm" onClick={onRotateSecret}>
              Rotate
            </Button>
          </div>
        </div>
        {showSecret && (
          <div className="mt-3">
            {secret.loading ? (
              <p className="text-sm text-muted-foreground">Loading...</p>
            ) : secret.error ? (
              <p className="text-sm text-destructive">Failed to load secret</p>
            ) : secret.data ? (
              <code className="block rounded-md bg-muted p-3 font-mono text-sm break-all">
                {secret.data.key}
              </code>
            ) : null}
          </div>
        )}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Message log — global message feed across all endpoints
// ---------------------------------------------------------------------------

function MessageLog() {
  const messages = useMessages({ limit: 25, withContent: true })

  if (messages.loading) {
    return (
      <div className="flex min-h-[200px] items-center justify-center">
        <LoadingSpinner size="medium" label="Loading messages..." />
      </div>
    )
  }

  if (messages.error) {
    return <ErrorMessage error={messages.error} onRetry={messages.reload} />
  }

  if (!messages.data || messages.data.length === 0) {
    return (
      <div className="rounded-lg border bg-muted/30 py-8 text-center">
        <p className="text-sm text-muted-foreground">No messages yet</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Messages will appear here as webhook events are triggered.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        All webhook messages sent across all endpoints
      </p>
      <div className="space-y-2">
        {messages.data.map((msg) => (
          <MessageCard key={msg.id} message={msg} />
        ))}
      </div>

      <PaginationControls
        hasPrev={messages.hasPrevPage}
        hasNext={messages.hasNextPage}
        onPrev={messages.prevPage}
        onNext={messages.nextPage}
      />
    </div>
  )
}

function MessageCard({
  message: msg,
}: {
  message: {
    id: string
    eventType: string
    eventId?: string | null
    timestamp: Date
    payload?: unknown
    channels?: string[] | null
    tags?: string[] | null
  }
}) {
  const [showPayload, setShowPayload] = useState(false)

  return (
    <div className="rounded-lg border bg-card">
      <button
        onClick={() => setShowPayload(!showPayload)}
        className="flex w-full items-center justify-between p-4 text-left hover:bg-accent/50"
      >
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <Badge variant="outline">{msg.eventType}</Badge>
            {msg.eventId && (
              <span className="truncate font-mono text-xs text-muted-foreground">
                {msg.eventId}
              </span>
            )}
            {msg.tags &&
              msg.tags.map((tag) => (
                <Badge key={tag} variant="secondary" className="text-xs">
                  {tag}
                </Badge>
              ))}
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {new Date(msg.timestamp).toLocaleString()} &middot; {msg.id}
          </p>
        </div>
        <svg
          className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform ${showPayload ? 'rotate-90' : ''}`}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
        </svg>
      </button>
      {showPayload && msg.payload && (
        <div className="border-t px-4 py-3">
          <pre className="max-h-[400px] overflow-auto rounded-md bg-muted p-3 text-xs">
            {typeof msg.payload === 'string' ? msg.payload : JSON.stringify(msg.payload, null, 2)}
          </pre>
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Event catalog — browse available event types
// ---------------------------------------------------------------------------

function EventCatalog() {
  const eventTypes = useEventTypes({ limit: 100 })

  if (eventTypes.loading) {
    return (
      <div className="flex min-h-[200px] items-center justify-center">
        <LoadingSpinner size="medium" label="Loading event types..." />
      </div>
    )
  }

  if (eventTypes.error) {
    return <ErrorMessage error={eventTypes.error} onRetry={eventTypes.reload} />
  }

  if (!eventTypes.data || eventTypes.data.length === 0) {
    return (
      <div className="rounded-lg border bg-muted/30 py-8 text-center">
        <p className="text-sm text-muted-foreground">No event types configured</p>
      </div>
    )
  }

  // Group by groupName if available
  const grouped = eventTypes.data.reduce(
    (acc, et) => {
      const group = et.groupName ?? 'General'
      if (!acc[group]) acc[group] = []
      acc[group].push(et)
      return acc
    },
    {} as Record<string, typeof eventTypes.data>
  )

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        Browse all available webhook event types you can subscribe to.
      </p>

      {Object.entries(grouped).map(([group, types]) => (
        <div key={group}>
          <h3 className="mb-2 text-sm font-semibold text-foreground">{group}</h3>
          <div className="space-y-2">
            {types.map((et) => (
              <div key={et.name} className="rounded-lg border bg-card p-4">
                <div className="flex items-center gap-2">
                  <code className="rounded bg-muted px-2 py-0.5 font-mono text-sm">{et.name}</code>
                  {et.deprecated && <Badge variant="secondary">Deprecated</Badge>}
                  {et.archived && <Badge variant="secondary">Archived</Badge>}
                </div>
                {et.description && (
                  <p className="mt-1 text-sm text-muted-foreground">{et.description}</p>
                )}
                {et.schemas && (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-xs text-muted-foreground hover:text-foreground">
                      View Schema
                    </summary>
                    <pre className="mt-2 max-h-[300px] overflow-auto rounded-md bg-muted p-3 text-xs">
                      {JSON.stringify(et.schemas, null, 2)}
                    </pre>
                  </details>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}

      <PaginationControls
        hasPrev={eventTypes.hasPrevPage}
        hasNext={eventTypes.hasNextPage}
        onPrev={eventTypes.prevPage}
        onNext={eventTypes.nextPage}
      />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      <p className="mt-1 break-all text-sm text-foreground">{value}</p>
    </div>
  )
}

function AttemptStatusBadge({ status }: { status: number }) {
  switch (status) {
    case 0:
      return (
        <Badge className="bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
          Success
        </Badge>
      )
    case 1:
      return (
        <Badge className="bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400">
          Pending
        </Badge>
      )
    case 2:
      return <Badge variant="destructive">Failed</Badge>
    case 3:
      return (
        <Badge className="bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400">
          Sending
        </Badge>
      )
    default:
      return <Badge variant="secondary">Unknown</Badge>
  }
}

function PaginationControls({
  hasPrev,
  hasNext,
  onPrev,
  onNext,
}: {
  hasPrev: boolean
  hasNext: boolean
  onPrev: () => void
  onNext: () => void
}) {
  if (!hasPrev && !hasNext) return null
  return (
    <div className="flex justify-center gap-2 pt-2">
      <Button variant="outline" size="sm" disabled={!hasPrev} onClick={onPrev}>
        Previous
      </Button>
      <Button variant="outline" size="sm" disabled={!hasNext} onClick={onNext}>
        Next
      </Button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Create endpoint dialog
// ---------------------------------------------------------------------------

function CreateEndpointDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const {
    url,
    description,
    eventTypes: selectedEventTypes,
    rateLimitPerSecond,
    createEndpoint,
  } = useNewEndpoint()
  const eventTypes = useEventTypes({ limit: 100 })
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const handleCreate = useCallback(async () => {
    if (!url.value.trim()) {
      setCreateError('URL is required')
      return
    }
    setCreating(true)
    setCreateError(null)
    try {
      const result = await createEndpoint()
      if (result.error) {
        setCreateError(result.error.message)
      } else {
        onOpenChange(false)
        url.setValue('')
        description.setValue('')
        selectedEventTypes.setValue([])
        rateLimitPerSecond.setValue(undefined)
      }
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Failed to create endpoint')
    } finally {
      setCreating(false)
    }
  }, [url, description, selectedEventTypes, rateLimitPerSecond, createEndpoint, onOpenChange])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Add Webhook Endpoint</DialogTitle>
          <DialogDescription>
            Create a new endpoint to receive webhook notifications.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="endpoint-url">Endpoint URL</Label>
            <Input
              id="endpoint-url"
              placeholder="https://example.com/webhooks"
              value={url.value}
              onChange={(e) => url.setValue(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="endpoint-description">Description</Label>
            <Input
              id="endpoint-description"
              placeholder="My webhook endpoint"
              value={description.value}
              onChange={(e) => description.setValue(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="endpoint-rate-limit">
              Rate Limit <span className="text-muted-foreground">(messages/second, optional)</span>
            </Label>
            <Input
              id="endpoint-rate-limit"
              type="number"
              placeholder="No limit"
              min={1}
              value={rateLimitPerSecond.value ?? ''}
              onChange={(e) =>
                rateLimitPerSecond.setValue(e.target.value ? Number(e.target.value) : undefined)
              }
            />
          </div>

          {eventTypes.data && eventTypes.data.length > 0 && (
            <div className="space-y-2">
              <Label>Event Types</Label>
              <p className="text-xs text-muted-foreground">
                Select which events this endpoint should receive. Leave empty for all events.
              </p>
              <div className="max-h-[200px] space-y-1 overflow-y-auto rounded-md border p-3">
                {eventTypes.data.map((et) => (
                  <label
                    key={et.name}
                    className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent"
                  >
                    <input
                      type="checkbox"
                      className="rounded border-input"
                      checked={selectedEventTypes.value.includes(et.name)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          selectedEventTypes.setValue([...selectedEventTypes.value, et.name])
                        } else {
                          selectedEventTypes.setValue(
                            selectedEventTypes.value.filter((n) => n !== et.name)
                          )
                        }
                      }}
                    />
                    <span className="text-foreground">{et.name}</span>
                    {et.description && (
                      <span className="text-xs text-muted-foreground">- {et.description}</span>
                    )}
                  </label>
                ))}
              </div>
            </div>
          )}

          {createError && <p className="text-sm text-destructive">{createError}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleCreate} disabled={creating}>
            {creating ? 'Creating...' : 'Create Endpoint'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------
// Edit endpoint dialog
// ---------------------------------------------------------------------------

function EditEndpointDialog({
  open,
  onOpenChange,
  endpoint,
  onUpdate,
  onReload,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  endpoint: EndpointOut
  onUpdate: (opts: EndpointUpdate) => Promise<EndpointOut>
  onReload: () => void
}) {
  const [editUrl, setEditUrl] = useState(endpoint.url)
  const [editDescription, setEditDescription] = useState(endpoint.description)
  const [editRateLimit, setEditRateLimit] = useState<number | undefined>(
    endpoint.rateLimit ?? undefined
  )
  const [editFilterTypes, setEditFilterTypes] = useState<string[]>(endpoint.filterTypes ?? [])
  const [saving, setSaving] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const eventTypes = useEventTypes({ limit: 100 })

  // Reset form when dialog opens with new endpoint
  useEffect(() => {
    if (open) {
      setEditUrl(endpoint.url)
      setEditDescription(endpoint.description)
      setEditRateLimit(endpoint.rateLimit ?? undefined)
      setEditFilterTypes(endpoint.filterTypes ?? [])
      setEditError(null)
    }
  }, [open, endpoint])

  const handleSave = useCallback(async () => {
    if (!editUrl.trim()) {
      setEditError('URL is required')
      return
    }
    setSaving(true)
    setEditError(null)
    try {
      await onUpdate({
        url: editUrl,
        description: editDescription,
        filterTypes: editFilterTypes.length > 0 ? editFilterTypes : null,
        rateLimit: editRateLimit ?? null,
        disabled: endpoint.disabled,
      })
      onReload()
      onOpenChange(false)
    } catch (err) {
      setEditError(err instanceof Error ? err.message : 'Failed to update endpoint')
    } finally {
      setSaving(false)
    }
  }, [
    editUrl,
    editDescription,
    editFilterTypes,
    editRateLimit,
    endpoint.disabled,
    onUpdate,
    onReload,
    onOpenChange,
  ])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Edit Endpoint</DialogTitle>
          <DialogDescription>Update the endpoint configuration.</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="edit-url">Endpoint URL</Label>
            <Input id="edit-url" value={editUrl} onChange={(e) => setEditUrl(e.target.value)} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-description">Description</Label>
            <Input
              id="edit-description"
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-rate-limit">
              Rate Limit <span className="text-muted-foreground">(messages/second, optional)</span>
            </Label>
            <Input
              id="edit-rate-limit"
              type="number"
              placeholder="No limit"
              min={1}
              value={editRateLimit ?? ''}
              onChange={(e) =>
                setEditRateLimit(e.target.value ? Number(e.target.value) : undefined)
              }
            />
          </div>

          {eventTypes.data && eventTypes.data.length > 0 && (
            <div className="space-y-2">
              <Label>Event Types</Label>
              <p className="text-xs text-muted-foreground">
                Select which events this endpoint should receive. Leave empty for all events.
              </p>
              <div className="max-h-[200px] space-y-1 overflow-y-auto rounded-md border p-3">
                {eventTypes.data.map((et) => (
                  <label
                    key={et.name}
                    className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent"
                  >
                    <input
                      type="checkbox"
                      className="rounded border-input"
                      checked={editFilterTypes.includes(et.name)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setEditFilterTypes([...editFilterTypes, et.name])
                        } else {
                          setEditFilterTypes(editFilterTypes.filter((n) => n !== et.name))
                        }
                      }}
                    />
                    <span className="text-foreground">{et.name}</span>
                    {et.description && (
                      <span className="text-xs text-muted-foreground">- {et.description}</span>
                    )}
                  </label>
                ))}
              </div>
            </div>
          )}

          {editError && <p className="text-sm text-destructive">{editError}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------
// Recover failed messages dialog
// ---------------------------------------------------------------------------

function RecoverDialog({
  open,
  onOpenChange,
  onRecover,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onRecover: (opts: { since: Date; until?: Date | null }) => Promise<void>
}) {
  const [since, setSince] = useState('')
  const [recovering, setRecovering] = useState(false)
  const [recoverError, setRecoverError] = useState<string | null>(null)
  const [recoverSuccess, setRecoverSuccess] = useState(false)

  useEffect(() => {
    if (open) {
      // Default to 24 hours ago
      const d = new Date()
      d.setHours(d.getHours() - 24)
      setSince(d.toISOString().slice(0, 16))
      setRecoverError(null)
      setRecoverSuccess(false)
    }
  }, [open])

  const handleRecover = useCallback(async () => {
    if (!since) {
      setRecoverError('Please select a start time')
      return
    }
    setRecovering(true)
    setRecoverError(null)
    try {
      await onRecover({ since: new Date(since) })
      setRecoverSuccess(true)
    } catch (err) {
      setRecoverError(err instanceof Error ? err.message : 'Failed to recover messages')
    } finally {
      setRecovering(false)
    }
  }, [since, onRecover])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Recover Failed Messages</DialogTitle>
          <DialogDescription>
            Retry all failed messages since the specified time. Successfully delivered messages will
            not be resent.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="recover-since">Recover messages since</Label>
            <Input
              id="recover-since"
              type="datetime-local"
              value={since}
              onChange={(e) => setSince(e.target.value)}
            />
          </div>

          {recoverError && <p className="text-sm text-destructive">{recoverError}</p>}
          {recoverSuccess && (
            <p className="text-sm text-green-600 dark:text-green-400">
              Recovery initiated. Failed messages will be retried shortly.
            </p>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {recoverSuccess ? 'Close' : 'Cancel'}
          </Button>
          {!recoverSuccess && (
            <Button onClick={handleRecover} disabled={recovering}>
              {recovering ? 'Recovering...' : 'Recover Messages'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------
// Rotate secret dialog
// ---------------------------------------------------------------------------

function RotateSecretDialog({
  open,
  onOpenChange,
  onRotate,
  onReload,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onRotate: (opts: { key?: string }) => Promise<void>
  onReload: () => void
}) {
  const [newKey, setNewKey] = useState('')
  const [rotating, setRotating] = useState(false)
  const [rotateError, setRotateError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      setNewKey('')
      setRotateError(null)
    }
  }, [open])

  const handleRotate = useCallback(async () => {
    setRotating(true)
    setRotateError(null)
    try {
      await onRotate(newKey ? { key: newKey } : {})
      onReload()
      onOpenChange(false)
    } catch (err) {
      setRotateError(err instanceof Error ? err.message : 'Failed to rotate secret')
    } finally {
      setRotating(false)
    }
  }, [newKey, onRotate, onReload, onOpenChange])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Rotate Signing Secret</DialogTitle>
          <DialogDescription>
            Generate a new signing secret for this endpoint. The old secret will be immediately
            invalidated. Make sure to update your webhook receiver with the new secret.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="new-secret">
              New Secret{' '}
              <span className="text-muted-foreground">(leave empty to auto-generate)</span>
            </Label>
            <Input
              id="new-secret"
              placeholder="Auto-generate"
              value={newKey}
              onChange={(e) => setNewKey(e.target.value)}
              className="font-mono"
            />
          </div>

          {rotateError && <p className="text-sm text-destructive">{rotateError}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleRotate} disabled={rotating}>
            {rotating ? 'Rotating...' : 'Rotate Secret'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default WebhooksPage
