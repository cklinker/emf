# Kelta UI — Design Guidelines

This document is the source of truth for *how Kelta looks and feels*. The CSS variables and shadcn primitives in `app/src/index.css` define the **system**; this document defines the **rules** for using it.

The full design-system project (interactive previews, brand assets, mocks) lives at `https://claude.ai/p/kelta-design-system` (or whichever Claude project is current). When that project is updated, sync the changes back to this file.

---

## 1 · Voice & content

Kelta talks to operators who know their data. The tone is calm, direct, and a little dry. Like a senior coworker pointing at a number.

- **Sentence case everywhere.** "New customer," not "New Customer."
- **No exclamation points** in product copy.
- **No emoji.** Use Lucide icons or nothing.
- **Empty values** are rendered as a literal em-dash `—`. Never "N/A," "None," or a blank cell.
- **Numbers are nouns.** `1,247 records · synced 2 minutes ago`, not `1247 records were last synced 2 minutes ago.`
- **No marketing-speak in product surfaces.** Save "supercharge," "AI-powered," "delight" for `kelta-marketing`.
- **Error messages name the thing and the next step.** *"Couldn't reach the API. Retry, or check Settings → Integrations."*

## 2 · Type

The repo uses the system stack today; we're standardizing on:

- **UI:** Inter (variable, weights 400–700)
- **Mono:** JetBrains Mono (record IDs, totals, timestamps, code, anything that needs columnar alignment)

Add Google Fonts to `app/index.html`:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
```

And in `index.css` `:root`:

```css
font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
```

Mono via Tailwind: `font-mono` is already wired up; we just need the import.

### The field-label rule

The signature Kelta detail/list treatment is **uppercase 11 px field labels**. Every field label in a form, detail card, KPI tile, or stat block uses this:

```tsx
<label className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
  Email
</label>
```

This is the single most distinctive piece of Kelta visual identity. Apply it consistently.

### Type scale

| Token | px | Use |
|---|---|---|
| Display | 48 / 700 | Marketing hero only |
| H1 | 26 / 700 / -0.01em | Page titles |
| H2 | 20 / 600 | Section headers |
| Lead | 16 / 400 | Page intro under H1 |
| Body | 14 / 400 / 1.55 | Default |
| Small | 13 | Secondary copy, table cells |
| Field label | 11 / 600 / 0.08em uppercase | The rule above |

## 3 · Color

Tokens live in `app/src/index.css` (already correct — slate base, Blue 600 light / Blue 400 dark). **Always** reference them as `bg-background`, `text-foreground`, `border-border`, etc. **Never hardcode hex** in components — if you find yourself reaching for `#0F172A`, you want `bg-card` instead.

### The gradient rule

The cyan→blue gradient `linear-gradient(135deg, #06B6D4 0%, #3B82F6 100%)` appears in exactly two places:

1. **The logo mark** (`app/public/logo.svg`)
2. **The AI assistant FAB** (and only the FAB — never the chat panel chrome)

Never use it as a page background, card fill, button, or hero. It loses meaning when it's everywhere.

### Status colors

Use the existing `--success`, `--warning`, `--destructive` tokens. Never invent new semantic colors per page. Status badges should match the pattern in §6.

## 4 · Layout & space

- **4 px base unit.** Tailwind already enforces this; stay on the scale.
- **Cards: 10 px radius**, 1 px border, no shadow in dark mode (separation is by border + a slightly raised surface like `#111C2E`).
- **Page width: 1180 px max** centered. Pages should breathe at 1440 px, not stretch.
- **Section header band**: cards with collapsible/header sections get a 14–16 px tall header strip with a subtle gradient `linear-gradient(180deg, rgba(148,163,184,0.06), rgba(148,163,184,0))` and a divider below. See `RecordDetail` in the design-system mocks.

## 5 · Tables

Data tables are the heart of the runtime. They follow this exact recipe:

- Wrapper: `border-1 border-border rounded-[10px] overflow-hidden bg-card`
- Header row: distinct background (`#16243A` dark / `#F1F5F9` light), uppercase 11 px heads with `tracking-[0.09em]` and `text-foreground/80`
- Body rows: zebra (every even row gets `bg-muted/40`), bottom 1 px `border-border`
- Hover: accent tint `bg-primary/10`, *not* a darker neutral
- Numerics: `font-mono tabular-nums text-right`
- Status: pill with leading dot (see §6)

The full markup is in the design-system mocks — `mocks/CustomersPage.jsx`.

## 6 · Components

For shadcn primitives (Button, Input, Select, Table, Dialog, etc.), use the repo's existing `components/ui/*`. Do not re-import shadcn or create parallel components.

For app-level patterns, prefer building on top of the primitives. The reference implementations are in the design-system project under `ui_kits/kelta-app/`:

| Pattern | Reference | Notes |
|---|---|---|
| `<TopNavBar>` | `TopNavBar.jsx` | 54 px sticky, app launcher → tenant name → tabs → admin → search/bell → avatar |
| `<RecordDetail>` / `<CollapsibleSection>` | `DetailView.jsx` | Header band, breadcrumb, uppercase field labels, em-dash empties |
| `<DataTable>` | `ListView.jsx` | The recipe in §5 |
| `<HomeView>` | `HomePage.jsx` (mocks) | KPI tiles + sparkline + recent items + activity feed |
| `<AiFab>` | `AiFab.jsx` | The only gradient surface; floats bottom-right |

### Status badge

```tsx
<span className="inline-flex items-center gap-1.5 h-[22px] px-2.5 rounded text-[11px] font-semibold border bg-emerald-500/15 text-emerald-300 border-emerald-500/55">
  <span className="size-1.5 rounded-full bg-current" /> Active
</span>
```

Variants: `Active/Paid → emerald`, `Pending → amber`, `Refunded/Inactive → slate`, `Failed → destructive`. Never invent a sixth.

## 7 · Iconography

[Lucide React](https://lucide.dev/) only. Default size 16 px in product UI, 14 px inline with text, 18–20 px in tile headers. Stroke 2 px (Lucide default). No filled or two-tone icons; no emoji.

## 8 · Brand assets

Logos live in `app/public/brand/`:

- `logo-primary.svg` — full mark on light backgrounds (marketing site, login on light theme)
- `logo-dark-bg.svg` — full mark on dark backgrounds (the runtime — use this in `TopNavBar`)
- `app/public/logo.svg` — square icon mark (browser tabs, app icons, OG images)
- `app/public/favicon.svg`

The mark is an SVG with the cyan→blue gradient. Don't recolor or rotate it.

## 9 · Accessibility

- WCAG 2.1 AA for all text (already enforced by the existing token contrast — keep it that way)
- Focus rings are non-negotiable. The `:focus-visible` styles in `index.css` are the floor.
- Every interactive element gets an accessible name (`aria-label` for icon-only buttons)
- Tables get proper `<thead>` / `<th scope="col">`
- Color is never the sole signal — status badges always pair color + dot + text

## 10 · Don't

- Don't add a sixth status color
- Don't invent gradients beyond the brand cyan→blue
- Don't use shadows in dark mode for separation (use border + surface lift)
- Don't use rounded-corner + left-border-accent containers (the AI-slop trope)
- Don't use Inter for marketing hero copy at <16 px (it gets thin); reach for a heavier weight or a display face
- Don't write "N/A" — always `—`

---

*Questions? Drop them in #design or open an issue tagged `design-system`.*
