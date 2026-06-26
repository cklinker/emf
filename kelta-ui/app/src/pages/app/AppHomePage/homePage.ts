/**
 * Resolve which custom page (if any) overrides the app's default landing page.
 *
 * A page opts in with `config.isHomePage === true` (authored in the page-builder's page-settings
 * drawer). The bootstrap `config.pages` entries are raw `ui-pages` records (full attributes, loosely
 * typed), so we read `slug` / `published` / `active` / `config.isHomePage` defensively. The first
 * published + active page that opts in wins (at most one should).
 */

interface RawPage {
  slug?: unknown
  published?: unknown
  active?: unknown
  config?: { isHomePage?: unknown } | null
}

/** Returns the slug of the home-override page, or `undefined` when none is configured. */
export function resolveHomePageSlug(pages: unknown): string | undefined {
  if (!Array.isArray(pages)) return undefined
  for (const raw of pages as RawPage[]) {
    if (!raw || typeof raw !== 'object') continue
    const slug = typeof raw.slug === 'string' ? raw.slug : undefined
    const published = raw.published !== false // default-true: absent ⇒ treat as published
    const active = raw.active !== false
    const isHome = raw.config?.isHomePage === true
    if (slug && published && active && isHome) return slug
  }
  return undefined
}
