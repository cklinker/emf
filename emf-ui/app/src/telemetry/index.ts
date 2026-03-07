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
  const otlpEndpoint =
    (import.meta as Record<string, Record<string, string>>).env?.VITE_OTEL_ENDPOINT ||
    `${window.location.origin}/otel/v1/traces`

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

  const apiOrigin = window.location.origin

  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        propagateTraceHeaderCorsUrls: [
          new RegExp(apiOrigin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
        ],
      }),
      new XMLHttpRequestInstrumentation({
        propagateTraceHeaderCorsUrls: [
          new RegExp(apiOrigin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
        ],
      }),
    ],
  })
}
