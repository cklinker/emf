/**
 * EmptyState Component
 *
 * Displays a designed, contextual empty state to replace plain-text placeholders.
 * Supports an optional icon, title, description, and call-to-action button.
 *
 * Features:
 * - Centered vertical layout with icon, title, description, and CTA
 * - Default and compact variants
 * - Accessible with semantic HTML
 * - Theme-aware via Tailwind CSS / shadcn design tokens
 *
 * @example
 * ```tsx
 * import { FolderOpen } from 'lucide-react'
 *
 * // Full empty state with CTA
 * <EmptyState
 *   icon={<FolderOpen size={48} />}
 *   title="No records found"
 *   description="Create your first record to get started."
 *   action={{ label: "Create Record", onClick: () => navigate('/new') }}
 * />
 *
 * // Compact variant for inline panels
 * <EmptyState
 *   icon={<Search size={36} />}
 *   title="No results"
 *   variant="compact"
 * />
 * ```
 */

import React from 'react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

export interface EmptyStateProps {
  /** Lucide icon element (e.g., <FolderOpen size={48} />) */
  icon?: React.ReactNode
  /** Heading text */
  title: string
  /** Explanatory text */
  description?: string
  /** Call-to-action button */
  action?: { label: string; onClick: () => void }
  /** Display variant: default=full size, compact=smaller for inline panels */
  variant?: 'default' | 'compact'
  /** Optional custom class name */
  className?: string
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  variant = 'default',
  className,
}: EmptyStateProps): React.ReactElement {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center gap-3',
        variant === 'compact' ? 'py-6 px-4 min-h-[120px]' : 'py-12 px-6 min-h-[200px]',
        className
      )}
      data-testid="empty-state"
    >
      {icon && (
        <div className="text-muted-foreground mb-2" aria-hidden="true">
          {icon}
        </div>
      )}

      <h3
        className={cn(
          'font-semibold text-foreground m-0',
          variant === 'compact' ? 'text-sm' : 'text-base'
        )}
      >
        {title}
      </h3>

      {description && (
        <p className="text-sm text-muted-foreground max-w-[400px] m-0 leading-relaxed">
          {description}
        </p>
      )}

      {action && (
        <div className="mt-2">
          <Button type="button" onClick={action.onClick}>
            {action.label}
          </Button>
        </div>
      )}
    </div>
  )
}

export default EmptyState
