// EmptyValue.tsx — render an em-dash for null / undefined / empty values
import * as React from 'react';
import { cn } from '@/lib/utils';

export interface EmptyValueProps extends React.HTMLAttributes<HTMLSpanElement> {}

/**
 * Renders the Kelta empty-value glyph (em-dash). Use anywhere a value
 * could be missing. Never show "N/A," "None," or a blank cell.
 *
 * @example
 *   {customer.phone ?? <EmptyValue />}
 */
export const EmptyValue = React.forwardRef<HTMLSpanElement, EmptyValueProps>(
  ({ className, ...props }, ref) => (
    <span
      ref={ref}
      aria-label="empty"
      className={cn('text-muted-foreground/70', className)}
      {...props}
    >
      —
    </span>
  ),
);

EmptyValue.displayName = 'EmptyValue';
