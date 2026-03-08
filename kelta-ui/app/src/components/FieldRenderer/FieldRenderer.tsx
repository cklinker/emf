/**
 * FieldRenderer Component
 *
 * A unified component that renders any field type in view mode.
 * Supports all 21+ field types with appropriate formatting, icons,
 * and accessibility attributes.
 *
 * View mode renders read-only field values with type-specific formatting.
 * Edit mode will be added in Phase 4.
 */

import React from 'react'
import { Link } from 'react-router-dom'
import {
  Mail,
  Phone,
  ExternalLink,
  MapPin,
  Lock,
  Hash,
  Calculator,
  Sigma,
  Check,
  X,
  Copy,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { FieldType } from '@/hooks/useCollectionSchema'

export interface FieldRendererProps {
  /** Field type */
  type: FieldType
  /** Field value */
  value: unknown
  /** Field name (for accessibility) */
  fieldName?: string
  /** Display name (for accessibility) */
  displayName?: string
  /** For lookup/reference fields: tenant slug for building links */
  tenantSlug?: string
  /** For lookup/reference fields: target collection name */
  targetCollection?: string
  /** For lookup/reference fields: resolved display label */
  displayLabel?: string
  /** Additional CSS class */
  className?: string
  /** Whether to truncate long text values */
  truncate?: boolean
}

/**
 * Format a number with locale-aware formatting.
 */
function formatNumber(value: unknown): string {
  if (typeof value === 'number') {
    return new Intl.NumberFormat().format(value)
  }
  return String(value ?? '')
}

/**
 * Format a currency value.
 */
function formatCurrency(value: unknown): string {
  if (typeof value === 'number') {
    return value.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  }
  return String(value ?? '')
}

/**
 * Format a percentage value.
 */
function formatPercent(value: unknown): string {
  if (typeof value === 'number') {
    return `${value.toFixed(2)}%`
  }
  return String(value ?? '')
}

/**
 * Format a date value.
 */
function formatDate(value: unknown): string {
  if (!value) return ''
  try {
    return new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(new Date(value as string))
  } catch {
    return String(value)
  }
}

/**
 * Format a datetime value.
 */
function formatDatetime(value: unknown): string {
  if (!value) return ''
  try {
    return new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value as string))
  } catch {
    return String(value)
  }
}

/**
 * Get a relative time string (e.g., "2h ago").
 */
function getRelativeTime(value: unknown): string {
  if (!value) return ''
  try {
    const date = new Date(value as string)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMins / 60)
    const diffDays = Math.floor(diffHours / 24)

    if (diffMins < 1) return 'just now'
    if (diffMins < 60) return `${diffMins}m ago`
    if (diffHours < 24) return `${diffHours}h ago`
    if (diffDays < 7) return `${diffDays}d ago`
    return ''
  } catch {
    return ''
  }
}

/**
 * Strip HTML tags from rich text.
 */
function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '').substring(0, 200)
}

/**
 * Renders a field value based on its type.
 */
export function FieldRenderer({
  type,
  value,
  fieldName,
  displayName,
  tenantSlug,
  targetCollection,
  displayLabel,
  className,
  truncate = true,
}: FieldRendererProps): React.ReactElement {
  // Null/undefined values
  if (value === null || value === undefined) {
    return (
      <span
        className={cn('text-muted-foreground', className)}
        aria-label={`${displayName || fieldName}: empty`}
      >
        —
      </span>
    )
  }

  switch (type) {
    case 'string':
    case 'external_id': {
      const str = String(value)
      if (truncate && str.length > 100) {
        return (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className={cn('truncate', className)}>{str.substring(0, 100)}...</span>
              </TooltipTrigger>
              <TooltipContent className="max-w-sm">
                <p className="break-words">{str}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        )
      }
      return <span className={className}>{str}</span>
    }

    case 'number': {
      return <span className={className}>{formatNumber(value)}</span>
    }

    case 'boolean': {
      const boolValue = Boolean(value)
      return (
        <span className={cn('inline-flex items-center', className)}>
          {boolValue ? (
            <Check className="h-4 w-4 text-emerald-600" aria-hidden="true" />
          ) : (
            <X className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          )}
          <span className="sr-only">{boolValue ? 'Yes' : 'No'}</span>
        </span>
      )
    }

    case 'date': {
      return <span className={className}>{formatDate(value)}</span>
    }

    case 'datetime': {
      const relativeTime = getRelativeTime(value)
      const formattedDatetime = formatDatetime(value)
      if (relativeTime) {
        return (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className={className}>{formattedDatetime}</span>
              </TooltipTrigger>
              <TooltipContent>
                <p>{relativeTime}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        )
      }
      return <span className={className}>{formattedDatetime}</span>
    }

    case 'currency': {
      return <span className={className}>{formatCurrency(value)}</span>
    }

    case 'percent': {
      return <span className={className}>{formatPercent(value)}</span>
    }

    case 'email': {
      const emailStr = String(value)
      return (
        <a
          href={`mailto:${emailStr}`}
          className={cn('inline-flex items-center gap-1 text-primary hover:underline', className)}
          onClick={(e) => e.stopPropagation()}
        >
          <Mail className="h-3.5 w-3.5" aria-hidden="true" />
          {emailStr}
        </a>
      )
    }

    case 'phone': {
      const phoneStr = String(value)
      return (
        <a
          href={`tel:${phoneStr}`}
          className={cn('inline-flex items-center gap-1 text-primary hover:underline', className)}
          onClick={(e) => e.stopPropagation()}
        >
          <Phone className="h-3.5 w-3.5" aria-hidden="true" />
          {phoneStr}
        </a>
      )
    }

    case 'url': {
      const urlStr = String(value)
      return (
        <a
          href={urlStr.startsWith('http') ? urlStr : `https://${urlStr}`}
          target="_blank"
          rel="noopener noreferrer"
          className={cn('inline-flex items-center gap-1 text-primary hover:underline', className)}
          onClick={(e) => e.stopPropagation()}
        >
          <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          {urlStr}
        </a>
      )
    }

    case 'picklist': {
      return (
        <Badge variant="secondary" className={className}>
          {String(value)}
        </Badge>
      )
    }

    case 'multi_picklist': {
      const items = Array.isArray(value) ? value : [value]
      return (
        <div className={cn('flex flex-wrap gap-1', className)}>
          {items.map((item, idx) => (
            <Badge key={idx} variant="secondary">
              {String(item)}
            </Badge>
          ))}
        </div>
      )
    }

    case 'reference':
    case 'lookup':
    case 'master_detail': {
      const label = displayLabel || String(value)
      if (tenantSlug && targetCollection) {
        return (
          <Link
            to={`/${tenantSlug}/app/o/${targetCollection}/${String(value)}`}
            className={cn('text-primary hover:underline', className)}
            onClick={(e) => e.stopPropagation()}
          >
            {label}
          </Link>
        )
      }
      return <span className={className}>{label}</span>
    }

    case 'rich_text': {
      const stripped = stripHtml(String(value))
      if (truncate && stripped.length > 100) {
        return (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className={cn('truncate', className)}>{stripped.substring(0, 100)}...</span>
              </TooltipTrigger>
              <TooltipContent className="max-w-sm">
                <p className="break-words">{stripped}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        )
      }
      return <span className={className}>{stripped}</span>
    }

    case 'json': {
      const jsonStr = typeof value === 'object' ? JSON.stringify(value) : String(value)
      const preview = jsonStr.length > 50 ? jsonStr.substring(0, 50) + '...' : jsonStr
      return (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <code className={cn('rounded bg-muted px-1.5 py-0.5 font-mono text-xs', className)}>
                {preview}
              </code>
            </TooltipTrigger>
            <TooltipContent className="max-w-md">
              <pre className="whitespace-pre-wrap break-words font-mono text-xs">{jsonStr}</pre>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      )
    }

    case 'auto_number': {
      return (
        <span className={cn('inline-flex items-center gap-1 font-mono text-sm', className)}>
          <Hash className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
          {String(value)}
        </span>
      )
    }

    case 'formula': {
      return (
        <span className={cn('inline-flex items-center gap-1', className)}>
          <Calculator className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
          {String(value)}
        </span>
      )
    }

    case 'rollup_summary': {
      return (
        <span className={cn('inline-flex items-center gap-1', className)}>
          <Sigma className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
          {formatNumber(value)}
        </span>
      )
    }

    case 'geolocation': {
      if (typeof value === 'object' && value !== null) {
        const geo = value as Record<string, unknown>
        const lat = geo.latitude ?? geo.lat ?? '-'
        const lng = geo.longitude ?? geo.lng ?? geo.lon ?? '-'
        return (
          <span className={cn('inline-flex items-center gap-1', className)}>
            <MapPin className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
            {String(lat)}, {String(lng)}
          </span>
        )
      }
      return (
        <span className={cn('inline-flex items-center gap-1', className)}>
          <MapPin className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
          {String(value)}
        </span>
      )
    }

    case 'encrypted': {
      return (
        <span className={cn('inline-flex items-center gap-1 text-muted-foreground', className)}>
          <Lock className="h-3.5 w-3.5" aria-hidden="true" />
          {'••••••••'}
        </span>
      )
    }

    default: {
      return <span className={className}>{String(value)}</span>
    }
  }
}

// Re-export for convenience
export { Copy }
