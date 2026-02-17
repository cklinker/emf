import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Merges class names using clsx and tailwind-merge.
 * This is the standard shadcn/ui utility for combining Tailwind classes
 * while properly handling conflicts (e.g., `p-4` + `p-2` → `p-2`).
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
