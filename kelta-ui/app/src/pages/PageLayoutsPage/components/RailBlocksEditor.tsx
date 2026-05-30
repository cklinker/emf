/**
 * RailBlocksEditor
 *
 * Structured editor for the ordered list of right-rail blocks on a DETAIL
 * page layout. Each block is one of six kinds (metadataCard, statStrip,
 * scoreCard, tagsCard, aiCard, timeline); this editor renders a compact
 * sub-form per kind and supports add / remove / move up / move down.
 *
 * An "Edit raw JSON" toggle exposes the raw payload as an escape hatch for
 * values the structured forms don't cover (e.g. icons, custom field names).
 * Commit-on-blur validates that the JSON parses and is an array.
 */

import React, { useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { RailBlockDto } from '@/hooks/usePageLayout'
import { cn } from '@/lib/utils'

type Tone = 'default' | 'brand' | 'success' | 'warning' | 'danger'
type ScoreTone = 'success' | 'warning' | 'danger'
type TrendDir = 'up' | 'down'

const TONE_OPTIONS: Tone[] = ['default', 'brand', 'success', 'warning', 'danger']
const SCORE_TONE_OPTIONS: ScoreTone[] = ['success', 'warning', 'danger']
const STAT_KIND_OPTIONS = ['currency', 'number', 'text'] as const

const KIND_LABELS: Record<RailBlockDto['kind'], string> = {
  metadataCard: 'Metadata card',
  statStrip: 'Stat strip',
  scoreCard: 'Score card',
  tagsCard: 'Tags card',
  aiCard: 'AI card',
  timeline: 'Timeline',
}

const KIND_ORDER: RailBlockDto['kind'][] = [
  'metadataCard',
  'statStrip',
  'scoreCard',
  'tagsCard',
  'aiCard',
  'timeline',
]

function makeEmptyBlock(kind: RailBlockDto['kind']): RailBlockDto {
  switch (kind) {
    case 'metadataCard':
      return { kind: 'metadataCard', config: { title: 'Metadata', rows: [] } }
    case 'statStrip':
      return { kind: 'statStrip', config: { tiles: [] } }
    case 'scoreCard':
      return {
        kind: 'scoreCard',
        config: { title: 'Score', score: 50, tone: 'success', metrics: [] },
      }
    case 'tagsCard':
      return { kind: 'tagsCard', config: { title: 'Tags', tags: [] } }
    case 'aiCard':
      return { kind: 'aiCard', config: { title: 'AI summary', summary: '', actions: [] } }
    case 'timeline':
      return { kind: 'timeline', config: { title: 'Timeline', events: [] } }
  }
}

export interface RailBlocksEditorProps {
  blocks: RailBlockDto[] | null
  onChange: (next: RailBlockDto[] | null) => void
}

export function RailBlocksEditor({ blocks, onChange }: RailBlocksEditorProps): React.ReactElement {
  const list = blocks ?? []
  const [showJson, setShowJson] = useState(false)
  const [addKind, setAddKind] = useState<RailBlockDto['kind']>('metadataCard')

  const commit = (next: RailBlockDto[]): void => {
    onChange(next.length > 0 ? next : null)
  }

  const addBlock = (): void => commit([...list, makeEmptyBlock(addKind)])
  const removeBlock = (idx: number): void => commit(list.filter((_, i) => i !== idx))
  const moveBlock = (idx: number, delta: -1 | 1): void => {
    const target = idx + delta
    if (target < 0 || target >= list.length) return
    const next = [...list]
    ;[next[idx], next[target]] = [next[target], next[idx]]
    commit(next)
  }
  const replaceBlock = (idx: number, next: RailBlockDto): void => {
    commit(list.map((b, i) => (i === idx ? next : b)))
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-1.5">
        <select
          value={addKind}
          onChange={(e) => setAddKind(e.target.value as RailBlockDto['kind'])}
          className="h-7 flex-1 rounded-md border border-input bg-background px-2 text-xs"
          aria-label="Block kind to add"
        >
          {KIND_ORDER.map((k) => (
            <option key={k} value={k}>
              {KIND_LABELS[k]}
            </option>
          ))}
        </select>
        <Button type="button" size="sm" variant="outline" className="h-7 px-2" onClick={addBlock}>
          <Plus className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
          Add block
        </Button>
      </div>

      {list.length === 0 && (
        <p className="text-xs text-muted-foreground">
          No blocks configured — ObjectDetailPage will render a single auto-derived
          system-information card.
        </p>
      )}

      <div className="space-y-2">
        {list.map((block, idx) => (
          <BlockCard
            key={`${block.kind}-${idx}`}
            block={block}
            index={idx}
            total={list.length}
            onMoveUp={() => moveBlock(idx, -1)}
            onMoveDown={() => moveBlock(idx, 1)}
            onRemove={() => removeBlock(idx)}
            onChange={(next) => replaceBlock(idx, next)}
          />
        ))}
      </div>

      <div className="border-t border-border pt-2">
        <button
          type="button"
          onClick={() => setShowJson((v) => !v)}
          className="inline-flex items-center gap-1 text-[11px] text-muted-foreground hover:text-foreground"
        >
          {showJson ? (
            <ChevronDown className="h-3 w-3" aria-hidden="true" />
          ) : (
            <ChevronRight className="h-3 w-3" aria-hidden="true" />
          )}
          Edit raw JSON
        </button>
        {showJson && <RawJsonEscapeHatch blocks={list} onChange={onChange} />}
      </div>
    </div>
  )
}

interface BlockCardProps {
  block: RailBlockDto
  index: number
  total: number
  onMoveUp: () => void
  onMoveDown: () => void
  onRemove: () => void
  onChange: (next: RailBlockDto) => void
}

function BlockCard({
  block,
  index,
  total,
  onMoveUp,
  onMoveDown,
  onRemove,
  onChange,
}: BlockCardProps): React.ReactElement {
  const [open, setOpen] = useState(true)
  return (
    <div className="rounded-md border border-border bg-card">
      <div className="flex items-center gap-1 px-2 py-1.5">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="inline-flex items-center gap-1 text-xs font-semibold text-foreground"
          aria-expanded={open}
        >
          {open ? (
            <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
          ) : (
            <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
          )}
          {KIND_LABELS[block.kind]}
        </button>
        <span className="ml-1 font-mono text-[10px] text-muted-foreground">#{index + 1}</span>
        <div className="ml-auto flex items-center gap-0.5">
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="h-6 w-6"
            disabled={index === 0}
            onClick={onMoveUp}
            aria-label="Move block up"
          >
            <ArrowUp className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="h-6 w-6"
            disabled={index === total - 1}
            onClick={onMoveDown}
            aria-label="Move block down"
          >
            <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="h-6 w-6 text-destructive hover:text-destructive"
            onClick={onRemove}
            aria-label="Remove block"
          >
            <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
      </div>
      {open && (
        <div className="space-y-2 border-t border-border p-2">
          <BlockSubEditor block={block} onChange={onChange} />
        </div>
      )}
    </div>
  )
}

function BlockSubEditor({
  block,
  onChange,
}: {
  block: RailBlockDto
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  switch (block.kind) {
    case 'metadataCard':
      return <MetadataCardSub block={block} onChange={onChange} />
    case 'statStrip':
      return <StatStripSub block={block} onChange={onChange} />
    case 'scoreCard':
      return <ScoreCardSub block={block} onChange={onChange} />
    case 'tagsCard':
      return <TagsCardSub block={block} onChange={onChange} />
    case 'aiCard':
      return <AICardSub block={block} onChange={onChange} />
    case 'timeline':
      return <TimelineSub block={block} onChange={onChange} />
  }
}

function FieldLabel({ children }: { children: React.ReactNode }): React.ReactElement {
  return (
    <Label className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
      {children}
    </Label>
  )
}

function ArrayHeader({ label, onAdd }: { label: string; onAdd: () => void }): React.ReactElement {
  return (
    <div className="flex items-center justify-between">
      <FieldLabel>{label}</FieldLabel>
      <Button
        type="button"
        size="sm"
        variant="outline"
        className="h-6 px-1.5"
        onClick={onAdd}
        aria-label={`Add ${label.toLowerCase()}`}
      >
        <Plus className="h-3 w-3" aria-hidden="true" />
      </Button>
    </div>
  )
}

function RowDeleteBtn({ onClick }: { onClick: () => void }): React.ReactElement {
  return (
    <Button
      type="button"
      size="icon"
      variant="ghost"
      className="h-6 w-6 text-muted-foreground hover:text-destructive"
      onClick={onClick}
      aria-label="Remove row"
    >
      <Trash2 className="h-3 w-3" aria-hidden="true" />
    </Button>
  )
}

function ToneSelect<T extends string>({
  value,
  options,
  onChange,
  ariaLabel,
}: {
  value: T
  options: readonly T[]
  onChange: (next: T) => void
  ariaLabel: string
}): React.ReactElement {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as T)}
      className="h-7 rounded-md border border-input bg-background px-1.5 text-[11px]"
      aria-label={ariaLabel}
    >
      {options.map((o) => (
        <option key={o} value={o}>
          {o}
        </option>
      ))}
    </select>
  )
}

function MetadataCardSub({
  block,
  onChange,
}: {
  block: Extract<RailBlockDto, { kind: 'metadataCard' }>
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  const { title, rows } = block.config
  const update = (patch: Partial<typeof block.config>): void =>
    onChange({ kind: 'metadataCard', config: { ...block.config, ...patch } })

  return (
    <div className="space-y-2">
      <div className="space-y-1">
        <FieldLabel>Title</FieldLabel>
        <Input
          value={title}
          onChange={(e) => update({ title: e.target.value })}
          className="h-7 text-xs"
        />
      </div>
      <ArrayHeader
        label="Rows"
        onAdd={() => update({ rows: [...rows, { label: '', value: '' }] })}
      />
      <div className="space-y-1">
        {rows.map((row, idx) => (
          <div key={idx} className="flex items-center gap-1">
            <Input
              value={row.label}
              onChange={(e) =>
                update({
                  rows: rows.map((r, i) => (i === idx ? { ...r, label: e.target.value } : r)),
                })
              }
              placeholder="Label"
              className="h-7 flex-1 text-xs"
              aria-label={`Row ${idx + 1} label`}
            />
            <Input
              value={row.value}
              onChange={(e) =>
                update({
                  rows: rows.map((r, i) => (i === idx ? { ...r, value: e.target.value } : r)),
                })
              }
              placeholder="Value"
              className={cn('h-7 flex-1 text-xs', row.mono && 'font-mono')}
              aria-label={`Row ${idx + 1} value`}
            />
            <label
              className="inline-flex h-7 items-center gap-1 px-1 text-[10px] text-muted-foreground"
              title="Render value in monospace"
            >
              <input
                type="checkbox"
                checked={!!row.mono}
                onChange={(e) =>
                  update({
                    rows: rows.map((r, i) => (i === idx ? { ...r, mono: e.target.checked } : r)),
                  })
                }
                className="h-3 w-3"
                aria-label={`Row ${idx + 1} monospace`}
              />
              mono
            </label>
            <RowDeleteBtn onClick={() => update({ rows: rows.filter((_, i) => i !== idx) })} />
          </div>
        ))}
      </div>
    </div>
  )
}

function StatStripSub({
  block,
  onChange,
}: {
  block: Extract<RailBlockDto, { kind: 'statStrip' }>
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  const tiles = block.config.tiles
  const update = (next: typeof tiles): void =>
    onChange({ kind: 'statStrip', config: { tiles: next } })

  const addTile = (): void =>
    update([...tiles, { label: '', value: '', kind: 'number' } as Record<string, unknown>])
  const setTile = (idx: number, patch: Record<string, unknown>): void =>
    update(tiles.map((t, i) => (i === idx ? { ...t, ...patch } : t)))
  const removeTile = (idx: number): void => update(tiles.filter((_, i) => i !== idx))

  return (
    <div className="space-y-2">
      <ArrayHeader label="Tiles" onAdd={addTile} />
      {tiles.map((tile, idx) => {
        const t = tile as Record<string, unknown>
        const trend = (t.trend as Record<string, unknown> | undefined) ?? null
        return (
          <div key={idx} className="space-y-1 rounded-sm border border-border/60 p-1.5">
            <div className="flex items-center gap-1">
              <Input
                value={String(t.label ?? '')}
                onChange={(e) => setTile(idx, { label: e.target.value })}
                placeholder="Label"
                className="h-7 flex-1 text-xs"
                aria-label={`Tile ${idx + 1} label`}
              />
              <RowDeleteBtn onClick={() => removeTile(idx)} />
            </div>
            <div className="flex items-center gap-1">
              <Input
                value={String(t.value ?? '')}
                onChange={(e) => setTile(idx, { value: e.target.value })}
                placeholder="Pre-formatted value"
                className="h-7 flex-1 text-xs"
                aria-label={`Tile ${idx + 1} value`}
              />
              <ToneSelect
                value={(t.kind as (typeof STAT_KIND_OPTIONS)[number] | undefined) ?? 'number'}
                options={STAT_KIND_OPTIONS}
                onChange={(next) => setTile(idx, { kind: next })}
                ariaLabel={`Tile ${idx + 1} kind`}
              />
            </div>
            <div className="flex items-center gap-1">
              <ToneSelect
                value={(trend?.dir as TrendDir | undefined) ?? 'up'}
                options={['up', 'down'] as const}
                onChange={(dir) => setTile(idx, { trend: { ...(trend ?? {}), dir } })}
                ariaLabel={`Tile ${idx + 1} trend direction`}
              />
              <Input
                value={String(trend?.label ?? '')}
                onChange={(e) =>
                  setTile(idx, {
                    trend: e.target.value
                      ? { ...(trend ?? { dir: 'up' }), label: e.target.value }
                      : null,
                  })
                }
                placeholder="Trend label (e.g. +12% YoY)"
                className="h-7 flex-1 text-xs"
                aria-label={`Tile ${idx + 1} trend label`}
              />
            </div>
            <Input
              value={String(t.sub ?? '')}
              onChange={(e) => setTile(idx, { sub: e.target.value || null })}
              placeholder="Subtitle (optional)"
              className="h-7 text-xs"
              aria-label={`Tile ${idx + 1} subtitle`}
            />
          </div>
        )
      })}
    </div>
  )
}

function ScoreCardSub({
  block,
  onChange,
}: {
  block: Extract<RailBlockDto, { kind: 'scoreCard' }>
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  const cfg = block.config as Record<string, unknown>
  const metrics = (cfg.metrics as Array<{ label: string; value: string; ok?: boolean }>) ?? []
  const set = (patch: Record<string, unknown>): void =>
    onChange({ kind: 'scoreCard', config: { ...cfg, ...patch } })
  const setMetrics = (next: typeof metrics): void => set({ metrics: next })

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-1">
        <div className="space-y-1">
          <FieldLabel>Title</FieldLabel>
          <Input
            value={String(cfg.title ?? '')}
            onChange={(e) => set({ title: e.target.value })}
            className="h-7 text-xs"
          />
        </div>
        <div className="space-y-1">
          <FieldLabel>Score (0–100)</FieldLabel>
          <Input
            type="number"
            min={0}
            max={100}
            value={Number(cfg.score ?? 0)}
            onChange={(e) => set({ score: Number(e.target.value) })}
            className="h-7 text-xs"
          />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-1">
        <div className="space-y-1">
          <FieldLabel>Tone</FieldLabel>
          <ToneSelect
            value={(cfg.tone as ScoreTone | undefined) ?? 'success'}
            options={SCORE_TONE_OPTIONS}
            onChange={(t) => set({ tone: t })}
            ariaLabel="Score tone"
          />
        </div>
        <div className="space-y-1">
          <FieldLabel>Status label</FieldLabel>
          <Input
            value={String(cfg.statusLabel ?? '')}
            onChange={(e) => set({ statusLabel: e.target.value || null })}
            placeholder="e.g. Excellent"
            className="h-7 text-xs"
          />
        </div>
      </div>
      <div className="space-y-1">
        <FieldLabel>Delta</FieldLabel>
        <Input
          value={String(cfg.delta ?? '')}
          onChange={(e) => set({ delta: e.target.value || null })}
          placeholder="e.g. +4 this month"
          className="h-7 text-xs"
        />
      </div>
      <ArrayHeader
        label="Metrics"
        onAdd={() => setMetrics([...metrics, { label: '', value: '' }])}
      />
      {metrics.map((m, idx) => (
        <div key={idx} className="flex items-center gap-1">
          <Input
            value={m.label}
            onChange={(e) =>
              setMetrics(metrics.map((x, i) => (i === idx ? { ...x, label: e.target.value } : x)))
            }
            placeholder="Label"
            className="h-7 flex-1 text-xs"
            aria-label={`Metric ${idx + 1} label`}
          />
          <Input
            value={m.value}
            onChange={(e) =>
              setMetrics(metrics.map((x, i) => (i === idx ? { ...x, value: e.target.value } : x)))
            }
            placeholder="Value"
            className="h-7 flex-1 text-xs"
            aria-label={`Metric ${idx + 1} value`}
          />
          <label className="inline-flex h-7 items-center gap-1 px-1 text-[10px] text-muted-foreground">
            <input
              type="checkbox"
              checked={!!m.ok}
              onChange={(e) =>
                setMetrics(metrics.map((x, i) => (i === idx ? { ...x, ok: e.target.checked } : x)))
              }
              className="h-3 w-3"
              aria-label={`Metric ${idx + 1} ok-tinted`}
            />
            ok
          </label>
          <RowDeleteBtn onClick={() => setMetrics(metrics.filter((_, i) => i !== idx))} />
        </div>
      ))}
    </div>
  )
}

function TagsCardSub({
  block,
  onChange,
}: {
  block: Extract<RailBlockDto, { kind: 'tagsCard' }>
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  const { title, tags } = block.config
  const set = (patch: Partial<typeof block.config>): void =>
    onChange({ kind: 'tagsCard', config: { ...block.config, ...patch } })

  return (
    <div className="space-y-2">
      <div className="space-y-1">
        <FieldLabel>Title</FieldLabel>
        <Input
          value={title}
          onChange={(e) => set({ title: e.target.value })}
          className="h-7 text-xs"
        />
      </div>
      <ArrayHeader
        label="Tags"
        onAdd={() => set({ tags: [...tags, { label: '', tone: 'default' }] })}
      />
      {tags.map((tag, idx) => (
        <div key={idx} className="flex items-center gap-1">
          <Input
            value={tag.label}
            onChange={(e) =>
              set({ tags: tags.map((t, i) => (i === idx ? { ...t, label: e.target.value } : t)) })
            }
            placeholder="Tag label"
            className="h-7 flex-1 text-xs"
            aria-label={`Tag ${idx + 1} label`}
          />
          <ToneSelect
            value={(tag.tone as Tone | undefined) ?? 'default'}
            options={TONE_OPTIONS}
            onChange={(tone) => set({ tags: tags.map((t, i) => (i === idx ? { ...t, tone } : t)) })}
            ariaLabel={`Tag ${idx + 1} tone`}
          />
          <RowDeleteBtn onClick={() => set({ tags: tags.filter((_, i) => i !== idx) })} />
        </div>
      ))}
    </div>
  )
}

function AICardSub({
  block,
  onChange,
}: {
  block: Extract<RailBlockDto, { kind: 'aiCard' }>
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  const { title, summary, actions = [] } = block.config
  const set = (patch: Partial<typeof block.config>): void =>
    onChange({ kind: 'aiCard', config: { ...block.config, ...patch } })

  return (
    <div className="space-y-2">
      <div className="space-y-1">
        <FieldLabel>Title</FieldLabel>
        <Input
          value={title}
          onChange={(e) => set({ title: e.target.value })}
          className="h-7 text-xs"
        />
      </div>
      <div className="space-y-1">
        <FieldLabel>Summary</FieldLabel>
        <Textarea
          value={summary}
          onChange={(e) => set({ summary: e.target.value })}
          rows={3}
          className="text-xs"
        />
      </div>
      <ArrayHeader label="Actions" onAdd={() => set({ actions: [...actions, { label: '' }] })} />
      {actions.map((a, idx) => (
        <div key={idx} className="flex items-center gap-1">
          <Input
            value={a.label}
            onChange={(e) =>
              set({
                actions: actions.map((x, i) => (i === idx ? { ...x, label: e.target.value } : x)),
              })
            }
            placeholder="Action label"
            className="h-7 flex-1 text-xs"
            aria-label={`Action ${idx + 1} label`}
          />
          <RowDeleteBtn onClick={() => set({ actions: actions.filter((_, i) => i !== idx) })} />
        </div>
      ))}
    </div>
  )
}

function TimelineSub({
  block,
  onChange,
}: {
  block: Extract<RailBlockDto, { kind: 'timeline' }>
  onChange: (next: RailBlockDto) => void
}): React.ReactElement {
  const { title, events } = block.config
  const set = (patch: Partial<typeof block.config>): void =>
    onChange({ kind: 'timeline', config: { ...block.config, ...patch } })

  return (
    <div className="space-y-2">
      <div className="space-y-1">
        <FieldLabel>Title</FieldLabel>
        <Input
          value={title}
          onChange={(e) => set({ title: e.target.value })}
          className="h-7 text-xs"
        />
      </div>
      <ArrayHeader
        label="Events"
        onAdd={() =>
          set({
            events: [
              ...events,
              { at: '', title: '', body: '', tone: 'default' } as Record<string, unknown>,
            ],
          })
        }
      />
      {events.map((event, idx) => {
        const e = event as Record<string, unknown>
        return (
          <div key={idx} className="space-y-1 rounded-sm border border-border/60 p-1.5">
            <div className="flex items-center gap-1">
              <Input
                value={String(e.at ?? '')}
                onChange={(ev) =>
                  set({
                    events: events.map((x, i) =>
                      i === idx ? { ...(x as Record<string, unknown>), at: ev.target.value } : x
                    ),
                  })
                }
                placeholder="Timestamp (e.g. 2h ago)"
                className="h-7 flex-1 text-xs"
                aria-label={`Event ${idx + 1} timestamp`}
              />
              <ToneSelect
                value={(e.tone as Tone | undefined) ?? 'default'}
                options={TONE_OPTIONS}
                onChange={(tone) =>
                  set({
                    events: events.map((x, i) =>
                      i === idx ? { ...(x as Record<string, unknown>), tone } : x
                    ),
                  })
                }
                ariaLabel={`Event ${idx + 1} tone`}
              />
              <RowDeleteBtn onClick={() => set({ events: events.filter((_, i) => i !== idx) })} />
            </div>
            <Input
              value={String(e.title ?? '')}
              onChange={(ev) =>
                set({
                  events: events.map((x, i) =>
                    i === idx ? { ...(x as Record<string, unknown>), title: ev.target.value } : x
                  ),
                })
              }
              placeholder="Title"
              className="h-7 text-xs"
              aria-label={`Event ${idx + 1} title`}
            />
            <Input
              value={String(e.body ?? '')}
              onChange={(ev) =>
                set({
                  events: events.map((x, i) =>
                    i === idx ? { ...(x as Record<string, unknown>), body: ev.target.value } : x
                  ),
                })
              }
              placeholder="Body (optional)"
              className="h-7 text-xs"
              aria-label={`Event ${idx + 1} body`}
            />
          </div>
        )
      })}
    </div>
  )
}

function RawJsonEscapeHatch({
  blocks,
  onChange,
}: {
  blocks: RailBlockDto[]
  onChange: (next: RailBlockDto[] | null) => void
}): React.ReactElement {
  const snapshot = useMemo(() => JSON.stringify(blocks, null, 2), [blocks])
  const [text, setText] = useState<string>(snapshot)
  const [error, setError] = useState<string | null>(null)

  const commit = (): void => {
    const trimmed = text.trim()
    if (trimmed === '' || trimmed === '[]') {
      onChange(null)
      setError(null)
      return
    }
    try {
      const parsed = JSON.parse(trimmed) as unknown
      if (!Array.isArray(parsed)) {
        setError('Must be a JSON array of { kind, config } blocks.')
        return
      }
      onChange(parsed as RailBlockDto[])
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid JSON')
    }
  }

  return (
    <div className="mt-2 space-y-1">
      <Textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        onBlur={commit}
        rows={8}
        className="font-mono text-[11px] leading-relaxed"
        aria-label="Rail blocks raw JSON"
      />
      {error && (
        <p className="text-xs text-destructive" role="alert">
          {error}
        </p>
      )}
      <p className="text-[10px] text-muted-foreground">
        Structured editor above writes the same payload — use raw JSON for fields the structured
        forms don't cover (e.g. icons, custom keys). Commits on blur.
      </p>
    </div>
  )
}
