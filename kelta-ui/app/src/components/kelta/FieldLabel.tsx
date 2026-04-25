// FieldLabel.tsx — the signature Kelta uppercase 11px field label
import * as React from 'react'
import { cn } from '@/lib/utils'

export type FieldLabelProps = React.LabelHTMLAttributes<HTMLLabelElement>

/**
 * The Kelta field label. Use above every field value in a detail card,
 * KPI tile, stat block, or form. Tracking and casing are part of the brand.
 *
 * @example
 *   <FieldLabel htmlFor="email">Email</FieldLabel>
 *   <Input id="email" />
 */
export const FieldLabel = React.forwardRef<HTMLLabelElement, FieldLabelProps>(
  ({ className, ...props }, ref) => (
    // jsx-a11y/label-has-associated-control: callers pass htmlFor or wrap a control;
    // we forward all label props so association is the consumer's concern.
    // eslint-disable-next-line jsx-a11y/label-has-associated-control
    <label
      ref={ref}
      className={cn(
        'block text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground mb-1.5',
        className
      )}
      {...props}
    />
  )
)

FieldLabel.displayName = 'FieldLabel'
