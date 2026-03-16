import React, { useEffect, useRef, useCallback } from 'react'
import { embedDashboard } from '@superset-ui/embedded-sdk'
import { useApi } from '../../context/ApiContext'

export interface SupersetEmbedProps {
  dashboardId: string
  className?: string
  testId?: string
}

/**
 * Embeds a Superset dashboard using the Superset Embedded SDK.
 *
 * Fetches a guest token from the backend, embeds the dashboard in an iframe,
 * and automatically refreshes the token before it expires (every 4 minutes,
 * with a 5-minute token TTL).
 */
export function SupersetEmbed({
  dashboardId,
  className = '',
  testId = 'superset-embed',
}: SupersetEmbedProps): React.ReactElement {
  const containerRef = useRef<HTMLDivElement>(null)
  const { keltaClient } = useApi()

  const fetchGuestToken = useCallback(async (): Promise<string> => {
    const result = await keltaClient.admin.superset.getGuestToken(dashboardId)
    return result.token
  }, [keltaClient, dashboardId])

  useEffect(() => {
    if (!containerRef.current) return

    let mounted = true

    async function embed() {
      try {
        const result = await keltaClient.admin.superset.getGuestToken(dashboardId)

        if (!mounted || !containerRef.current) return

        await embedDashboard({
          id: dashboardId,
          supersetDomain: result.supersetDomain,
          mountPoint: containerRef.current,
          fetchGuestToken: () => fetchGuestToken(),
          dashboardUiConfig: {
            hideTitle: true,
            hideChartControls: false,
            hideTab: false,
            filters: {
              visible: true,
              expanded: false,
            },
          },
        })

        // Style the iframe to fill the container
        const iframe = containerRef.current?.querySelector('iframe')
        if (iframe) {
          iframe.style.width = '100%'
          iframe.style.height = '100%'
          iframe.style.border = 'none'
          iframe.style.borderRadius = '0.5rem'
        }
      } catch (err) {
        console.error('Failed to embed Superset dashboard:', err)
      }
    }

    embed()

    return () => {
      mounted = false
    }
  }, [dashboardId, keltaClient, fetchGuestToken])

  return (
    <div
      ref={containerRef}
      className={`h-full w-full ${className}`}
      style={{ minHeight: 600 }}
      data-testid={testId}
    />
  )
}
