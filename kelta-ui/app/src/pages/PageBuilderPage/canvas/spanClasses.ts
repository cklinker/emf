/**
 * Maps a responsive `span` ({ base, sm?, md?, lg? }, each 1..12) to literal Tailwind `col-span-*` classes.
 *
 * Tailwind v4's JIT scanner cannot see dynamic `` `col-span-${n}` `` — so the 48 class names (4
 * breakpoints × 12) are spelled out as full literals here, which is the ONLY way they survive the build.
 * `spanClasses.test.ts` asserts every emitted class is one of these literals, so an accidental
 * template-string regression fails CI instead of silently dropping the class. Only `span` is ever
 * persisted as layout state — no pixel/row/column coords.
 */
import type { ResponsiveSpan } from '../model/pageModel'

const COL_SPAN: Record<number, string> = {
  1: 'col-span-1',
  2: 'col-span-2',
  3: 'col-span-3',
  4: 'col-span-4',
  5: 'col-span-5',
  6: 'col-span-6',
  7: 'col-span-7',
  8: 'col-span-8',
  9: 'col-span-9',
  10: 'col-span-10',
  11: 'col-span-11',
  12: 'col-span-12',
}

const COL_SPAN_SM: Record<number, string> = {
  1: 'sm:col-span-1',
  2: 'sm:col-span-2',
  3: 'sm:col-span-3',
  4: 'sm:col-span-4',
  5: 'sm:col-span-5',
  6: 'sm:col-span-6',
  7: 'sm:col-span-7',
  8: 'sm:col-span-8',
  9: 'sm:col-span-9',
  10: 'sm:col-span-10',
  11: 'sm:col-span-11',
  12: 'sm:col-span-12',
}

const COL_SPAN_MD: Record<number, string> = {
  1: 'md:col-span-1',
  2: 'md:col-span-2',
  3: 'md:col-span-3',
  4: 'md:col-span-4',
  5: 'md:col-span-5',
  6: 'md:col-span-6',
  7: 'md:col-span-7',
  8: 'md:col-span-8',
  9: 'md:col-span-9',
  10: 'md:col-span-10',
  11: 'md:col-span-11',
  12: 'md:col-span-12',
}

const COL_SPAN_LG: Record<number, string> = {
  1: 'lg:col-span-1',
  2: 'lg:col-span-2',
  3: 'lg:col-span-3',
  4: 'lg:col-span-4',
  5: 'lg:col-span-5',
  6: 'lg:col-span-6',
  7: 'lg:col-span-7',
  8: 'lg:col-span-8',
  9: 'lg:col-span-9',
  10: 'lg:col-span-10',
  11: 'lg:col-span-11',
  12: 'lg:col-span-12',
}

/** The 12-col CSS-grid track shared by `grid` and `row` containers. */
export const GRID_CONTAINER_CLASS = 'grid grid-cols-12 gap-4'

/** Clamp + round a value into the valid 1..12 grid range. */
export function clampSpan(n: number): number {
  if (!Number.isFinite(n)) return 12
  return Math.min(12, Math.max(1, Math.round(n)))
}

/**
 * Map a span to its Tailwind classes. A missing span defaults to `col-span-12` (full width, matching a
 * plain stacked child). Breakpoint keys are additive: base plus any of sm/md/lg present, in that order.
 */
export function spanToClasses(span: ResponsiveSpan | undefined): string {
  if (!span) return COL_SPAN[12]
  const out = [COL_SPAN[clampSpan(span.base)]]
  if (span.sm != null) out.push(COL_SPAN_SM[clampSpan(span.sm)])
  if (span.md != null) out.push(COL_SPAN_MD[clampSpan(span.md)])
  if (span.lg != null) out.push(COL_SPAN_LG[clampSpan(span.lg)])
  return out.join(' ')
}
