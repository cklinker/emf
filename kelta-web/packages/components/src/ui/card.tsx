/**
 * Card — shadcn-style card primitive container, ported into
 * @kelta/components. Card / CardHeader / CardContent only — the full set
 * (CardTitle / CardDescription / CardFooter) isn't needed by the shared
 * detail components and stays in kelta-ui/app for now.
 */

import * as React from 'react';
import { cn } from '../detail/_utils';

export function Card({ className, ...props }: React.ComponentProps<'div'>): React.ReactElement {
  return (
    <div
      data-slot="card"
      className={cn(
        'bg-card text-card-foreground flex flex-col gap-6 rounded-xl border py-6 shadow-sm',
        className
      )}
      {...props}
    />
  );
}

export function CardHeader({
  className,
  ...props
}: React.ComponentProps<'div'>): React.ReactElement {
  return (
    <div
      data-slot="card-header"
      className={cn(
        'grid auto-rows-min grid-rows-[auto_auto] items-start gap-2 px-6 [.border-b]:pb-6',
        className
      )}
      {...props}
    />
  );
}

export function CardContent({
  className,
  ...props
}: React.ComponentProps<'div'>): React.ReactElement {
  return <div data-slot="card-content" className={cn('px-6', className)} {...props} />;
}
