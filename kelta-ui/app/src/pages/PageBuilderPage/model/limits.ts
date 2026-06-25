/**
 * DoS / fan-out caps for the page-builder data layer (slice 2d, parent §"DoS / fan-out caps").
 *
 * These bound the cost of a page render: each on-load data source fires its own authorized JSON:API
 * fetch, and a `list`/`repeater` renders its child subtree once per row. Both are capped so a malicious
 * or accidental config cannot fan out unbounded fetches or render an unbounded DOM.
 */

/** A page may declare at most this many on-load data sources; each fires its own fetch. */
export const MAX_PAGE_DATA_SOURCES = 12

/** A repeater/list renders at most this many rows; the rest are truncated with a "showing N of M" note. */
export const MAX_REPEATER_ROWS = 200
