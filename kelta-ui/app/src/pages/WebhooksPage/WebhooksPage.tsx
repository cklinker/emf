import React, { useState, useEffect } from 'react'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'

export interface WebhooksPageProps {
  testId?: string
}

export function WebhooksPage({ testId = 'webhooks-page' }: WebhooksPageProps): React.ReactElement {
  const { keltaClient } = useApi()
  const [portalUrl, setPortalUrl] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let cancelled = false

    async function fetchPortalUrl() {
      try {
        setIsLoading(true)
        setError(null)
        const result = await keltaClient.admin.svix.getPortalUrl()
        if (!cancelled) {
          setPortalUrl(result.url)
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

    fetchPortalUrl()
    return () => {
      cancelled = true
    }
  }, [keltaClient])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading webhook portal..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage error={error} onRetry={() => window.location.reload()} />
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col" data-testid={testId}>
      <header className="flex items-center justify-between px-6 py-4 lg:px-8">
        <h1 className="text-2xl font-semibold text-foreground">Webhooks</h1>
      </header>
      {portalUrl && (
        <iframe
          src={portalUrl}
          title="Webhook Management Portal"
          className="flex-1 border-0"
          style={{ minHeight: 'calc(100vh - 200px)' }}
          allow="clipboard-write"
          data-testid="svix-portal-iframe"
        />
      )}
    </div>
  )
}

export default WebhooksPage
