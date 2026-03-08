import { WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { ZoneContextManager } from '@opentelemetry/context-zone'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request'
import { Resource } from '@opentelemetry/resources'
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions'
import { W3CTraceContextPropagator } from '@opentelemetry/core'
import { CompositePropagator } from '@opentelemetry/core'
import { B3Propagator } from '@opentelemetry/propagator-b3'
import { registerInstrumentations } from '@opentelemetry/instrumentation'

/**
 * Initialize OpenTelemetry for the browser.
 *
 * Sends trace spans via OTLP HTTP to the gateway, which proxies them to Jaeger V2.
 * Auto-instruments fetch and XMLHttpRequest to propagate trace context headers.
 */
export function initTelemetry(): void {
  const env = (import.meta as Record<string, Record<string, string>>).env
  // Use the gateway origin (VITE_API_BASE_URL) for OTEL traces, not window.location.origin.
  // The UI is served from emf-ui.rzware.com but the OTEL route is on the gateway (emf.rzware.com).
  const gatewayOrigin = env?.VITE_API_BASE_URL || window.location.origin
  const otlpEndpoint = env?.VITE_OTEL_ENDPOINT || `${gatewayOrigin}/otel/v1/traces`

  const resource = new Resource({
    [ATTR_SERVICE_NAME]: 'emf-ui',
  })

  const exporter = new OTLPTraceExporter({
    url: otlpEndpoint,
  })

  const provider = new WebTracerProvider({
    resource,
    spanProcessors: [new BatchSpanProcessor(exporter)],
  })

  provider.register({
    contextManager: new ZoneContextManager(),
    propagator: new CompositePropagator({
      propagators: [new W3CTraceContextPropagator(), new B3Propagator()],
    }),
  })

  // Propagate trace context headers to both the UI origin and the gateway origin
  const corsUrls: RegExp[] = [
    new RegExp(window.location.origin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
  ]
  if (gatewayOrigin && gatewayOrigin !== window.location.origin) {
    corsUrls.push(new RegExp(gatewayOrigin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  }

  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        propagateTraceHeaderCorsUrls: corsUrls,
      }),
      new XMLHttpRequestInstrumentation({
        propagateTraceHeaderCorsUrls: corsUrls,
      }),
    ],
  })
}
