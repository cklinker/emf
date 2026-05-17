/**
 * Tiny shared helpers for the detail-block components. We avoid pulling in
 * shadcn primitives, so Card / Badge / etc. are inlined as plain HTML +
 * Tailwind class strings. Consumers' Tailwind configs need to scan this
 * package's source (or `dist`) for class generation.
 */

import clsx, { type ClassValue } from 'clsx';

export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs);
}
