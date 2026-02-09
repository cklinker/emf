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
 * - Theme-aware via CSS custom properties
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
import styles from './EmptyState.module.css'

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
  const containerClasses = [
    styles.container,
    variant === 'compact' ? styles.containerCompact : styles.containerDefault,
    className,
  ]
    .filter(Boolean)
    .join(' ')

  const titleClasses = [
    styles.title,
    variant === 'compact' ? styles.titleCompact : styles.titleDefault,
  ].join(' ')

  return (
    <div className={containerClasses} data-testid="empty-state">
      {icon && (
        <div className={styles.icon} aria-hidden="true">
          {icon}
        </div>
      )}

      <h3 className={titleClasses}>{title}</h3>

      {description && <p className={styles.description}>{description}</p>}

      {action && (
        <div className={styles.action}>
          <button type="button" className={styles.actionButton} onClick={action.onClick}>
            {action.label}
          </button>
        </div>
      )}
    </div>
  )
}

export default EmptyState
