/**
 * Collapsible — Radix wrappers, mirrors the shadcn (new-york slate) ports
 * in kelta-ui/app. Passes props through with `data-slot` attributes so
 * consumer-side CSS rules can hook into the open/closed state.
 */

import * as React from 'react';
import { Collapsible as CollapsiblePrimitive } from 'radix-ui';

export function Collapsible(
  props: React.ComponentProps<typeof CollapsiblePrimitive.Root>
): React.ReactElement {
  return <CollapsiblePrimitive.Root data-slot="collapsible" {...props} />;
}

export function CollapsibleTrigger(
  props: React.ComponentProps<typeof CollapsiblePrimitive.CollapsibleTrigger>
): React.ReactElement {
  return <CollapsiblePrimitive.CollapsibleTrigger data-slot="collapsible-trigger" {...props} />;
}

export function CollapsibleContent(
  props: React.ComponentProps<typeof CollapsiblePrimitive.CollapsibleContent>
): React.ReactElement {
  return <CollapsiblePrimitive.CollapsibleContent data-slot="collapsible-content" {...props} />;
}
